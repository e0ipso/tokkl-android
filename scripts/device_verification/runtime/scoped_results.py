"""Declared direct-JUnit4 class scopes. Files only; never starts a workload."""
from pathlib import Path
import hashlib
import importlib.util
import json
import re
import xml.etree.ElementTree as ET

PROFILE = 'scoped-app-classes-v1'
PREFIX = '-Pandroid.testInstrumentationRunnerArguments.class='
FULL_ARGS = ['scripts/instrumented-tests.sh', ':macrobenchmark:connectedBenchmarkAndroidTest', '--continue']
COMPILE_FIRST = 'compile-then-scoped-app-v1'
COMPILE_ARGS = ['./gradlew', '--no-daemon', 'assembleDebug', 'assembleDebugAndroidTest', 'check', '--stacktrace']


def validate_plan(config):
    jobs = config['plan']
    if 'plan_recipe' not in config:
        require(not jobs[0]['standalone'], 'first opening preparation preserves the device budget')
        return
    require(config['plan_recipe'] == COMPILE_FIRST, 'unsupported declared plan recipe')
    require(len(jobs) == 2 and jobs[0].get('args') == COMPILE_ARGS
            and jobs[0].get('label') == 'assemble-check' and jobs[0].get('api') == 0
            and jobs[0].get('standalone') is True and 'result_recipe' not in jobs[0]
            and jobs[1].get('standalone') is False and 'result_recipe' in jobs[1],
            'compile-first recipe requires exactly APK assemble/check then one declared scoped app job')


def require(condition, message):
    if not condition:
        raise ValueError(message)


def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def masked_kotlin(text):
    """Mask nested comments and string/character literals, preserving positions."""
    out = list(text)
    i = 0
    while i < len(text):
        start = i
        if text.startswith('//', i):
            end = text.find('\n', i)
            i = len(text) if end < 0 else end
        elif text.startswith('/*', i):
            depth = 1
            i += 2
            while i < len(text) and depth:
                if text.startswith('/*', i): depth += 1; i += 2
                elif text.startswith('*/', i): depth -= 1; i += 2
                else: i += 1
            require(depth == 0, 'unclosed Kotlin comment')
        elif text.startswith('"""', i):
            end = text.find('"""', i+3)
            require(end >= 0, 'unclosed Kotlin raw string')
            i = end+3
        elif text[i] in ['"', "'"]:
            quote = text[i]
            i += 1
            while i < len(text) and text[i] != quote:
                i += 2 if text[i] == '\\' else 1
            require(i < len(text), 'unclosed Kotlin literal')
            i += 1
        else:
            i += 1
            continue
        for pos in range(start, i):
            if out[pos] != '\n': out[pos] = ' '
    return ''.join(out)


def inventory(project, class_name):
    require(isinstance(class_name, str) and re.fullmatch(r'(?:[a-zA-Z_]\w*\.)+[A-Z]\w*', class_name),
            'whole qualified class names required; no method/package selectors')
    project = Path(project).resolve()
    relative = 'app/src/androidTest/java/'+class_name.replace('.', '/')+'.kt'
    path = project/relative
    require(path.resolve().is_relative_to(project) and not path.is_symlink(), 'source must stay inside checked project')
    raw = path.read_bytes()
    text = masked_kotlin(raw.decode())
    package, name = class_name.rsplit('.', 1)
    require(re.findall(r'(?m)^package\s+([\w.]+)\s*$', text) == [package], 'source package mismatch')
    test_imports = re.findall(r'(?m)^import[ \t]+(?:org\.junit\.(?:jupiter\.api\.)?Test|kotlin\.test\.Test)[^\n]*$', text)
    require([line.strip() for line in test_imports] == ['import org.junit.Test'],
            'only explicit unaliased JUnit4 Test import is supported')
    require(not re.search(r'@(?:[a-zA-Z_]\w*\.)*(RunWith|Parameterized|Ignore|SdkSuppress|Theory|TestFactory|RepeatedTest|JvmName)\b', text)
            and not re.search(r'@(?:[a-zA-Z_]\w*\.)+Test\b', text),
            'custom runner, parameterization or conditional test selection requires another reviewed recipe')
    header = re.search(r'(?m)^class\s+'+re.escape(name)+r'\s*\{', text)
    require(header is not None, 'only concrete direct classes without inheritance/constructor parameters are supported')
    depth = 0
    methods = []
    positions = {m.start(): m for m in re.finditer(r'@Test\b', text)}
    body_start = header.end()-1
    body_end = None
    for pos, char in enumerate(text):
        if pos in positions:
            require(pos > body_start and body_end is None and depth == 1, 'nested/inherited/other-class tests are unsupported')
            method = re.match(r'@Test\s+(?:public\s+)?fun\s+([a-zA-Z_]\w*)\s*\(\s*\)\s*(?::\s*Unit\s*)?\{', text[pos:])
            require(method is not None, 'test must be a direct zero-argument unparameterized Unit method')
            methods.append(method[1])
        if char == '{': depth += 1
        elif char == '}':
            depth -= 1
            if pos > body_start and depth == 0 and body_end is None: body_end = pos
        require(depth >= 0, 'unbalanced Kotlin source')
    require(depth == 0 and methods and len(methods) == len(set(methods)), 'empty/duplicate/unbalanced source test inventory')
    return {'class': class_name, 'source': relative, 'sha256': hashlib.sha256(raw).hexdigest(),
            'shape': 'direct-junit4-unparameterized', 'methods': sorted(methods)}


def declare(project, classes):
    require(isinstance(classes, list) and 1 <= len(classes) <= 5
            and len(set(classes)) == len(classes), 'one to five distinct whole classes required')
    return {'profile': PROFILE, 'classes': classes, 'inventory': [inventory(project, name) for name in classes]}


def validate_recipe(project, job, pins):
    recipe = job.get('result_recipe')
    require(isinstance(recipe, dict) and recipe.get('profile') == PROFILE, 'unsupported declared result recipe')
    expected = declare(project, recipe.get('classes'))
    require(recipe == expected, 'declared inventory must equal every selected class source Test method and fingerprint')
    require(job['api'] == 36 and job['standalone'] is False, 'scoped app recipe is API36 device only')
    require(job['args'] == ['scripts/instrumented-tests.sh', PREFIX+','.join(recipe['classes'])],
            'scoped recipe requires exact whole-class-list argv; benchmark/package/method tasks forbidden')
    for row in expected['inventory']:
        require(pins.get(str(Path(project)/row['source'])) == row['sha256'], 'selected source missing from checked input pins')
    parser = Path(project)/'scripts/sift_telemetry.py'
    require(pins.get(str(parser)) == sha(parser), 'actual finalized JUnit parser must be input-pinned')
    return expected


def assess_scoped(config, api, label, args, seconds, record, started, exit_code, stop_reason):
    result = {'passed': False, 'profile': PROFILE, 'reason': None}
    try:
        if __package__:
            from .configuration import validate
        else:
            from configuration import validate
        current = validate(json.loads((Path(config['run_dir'])/'run-config.json').read_text()), fresh=False)
        require(current == config, 'frozen configuration identity changed during execution')
        jobs = [job for job in config['plan'] if job['label'] == label]
        require(len(jobs) == 1, 'check identity missing/duplicated in configuration')
        job = jobs[0]
        require((api, args, seconds, False) == (job['api'], job['args'], job['seconds'], job['standalone']),
                'actual job/API/filter/deadline differs from declared configuration')
        recipe = validate_recipe(config['project'], job, config['input_pins'])
        require(exit_code == 0 and not stop_reason, 'payload/resource/finalization outcome failed')
        require(record['api_requested'] == api and record['label'] == label and record['standalone'] is False
                and record['command'][-len(args):] == args and 'TOKKL_TEST_AVD=tokkl-api36' in record['command'],
                'controller record identity/argv mismatch')
        log = Path(record['log'])
        require(log.stat().st_mtime >= started, 'stale execution log')
        devices = re.findall(r'(?m)^emulator-[0-9]+ runs .* on API [0-9]+$', log.read_text())
        require(len(devices) == 1 and re.fullmatch(r'emulator-[0-9]+ runs tokkl-api36 on API 36', devices[0])
                and devices == record['device_lines'], 'missing/mismatched actual API36 device evidence')
        files = sorted(Path(record['results']).rglob('TEST-*.xml')) if record.get('results') else []
        parser = Path(config['project'])/'scripts/sift_telemetry.py'
        spec = importlib.util.spec_from_file_location('pinned_sift_junit_parser', parser)
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        counts = module.junit_counts([str(path) for path in files], started)
        cases = set()
        for path in files:
            root = ET.parse(path).getroot()
            require(root.tag in ['testsuite', 'testsuites'], 'unsupported JUnit root')
            suites = [root] if root.tag == 'testsuite' else root.findall('testsuite')
            if root.tag == 'testsuites':
                require(len(suites) == len(root), 'nested/unknown JUnit suite structure')
                for key in ['tests', 'failures', 'errors', 'skipped']:
                    if key in root.attrib:
                        require(int(root.get(key)) == sum(int(s.get(key, 0)) for s in suites), 'aggregate JUnit count mismatch')
            for suite in suites:
                cases.update((case.get('classname'), case.get('name')) for case in suite.findall('testcase'))
        expected = {(row['class'], method) for row in recipe['inventory'] for method in row['methods']}
        require(counts == record['counts'] and cases == expected and counts['tests'] == len(expected),
                'finalized count/class/method coverage differs from complete source inventory')
        require(not any(counts[key] for key in ['failures', 'errors', 'skipped']), 'failed/error/skipped app result')
        for key, pattern in [('benchmark-results', 'TEST-*.xml'), ('benchmark-data', '*-benchmarkData.json')]:
            directory = record.get(key)
            require(not directory or not any(p.stat().st_mtime >= started for p in Path(directory).rglob(pattern)),
                    'fresh unrequested benchmark execution cannot use scoped app contract')
        result.update(passed=True, app_counts=counts, app_cases=[list(case) for case in sorted(cases)],
                      junit=[{'path': str(path), 'sha256': sha(path)} for path in files],
                      recipe_sha256=hashlib.sha256(json.dumps(recipe, sort_keys=True, separators=(',', ':')).encode()).hexdigest(),
                      configuration_sha256=config['_sha256'], expected_benchmark_cases=[])
    except (OSError, ValueError, KeyError, TypeError, ET.ParseError) as error:
        result['reason'] = str(error)
    return result
