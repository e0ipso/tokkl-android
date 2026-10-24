"""Exact final-gate routes. No workloads, resources or baseline writes on import.

The source table is reviewed data, not a Kotlin/JUnit discovery algorithm. Changes
to any covered source require reviewing a new table and runtime snapshot.
"""
from pathlib import Path
import hashlib
import importlib.util
import json
import os
import re
import stat
import struct
import xml.etree.ElementTree as ET
import zlib
import zipfile

if __package__:
    from . import scoped_results as sr
else:
    import scoped_results as sr

SCREENSHOT = 'screenshot-api36-v1'
NATIVE = 'native-permission-method-v1'
WIDGET = 'widget-clock-api31-v1'
BENCHMARK = 'benchmark-api31-v1'
FULL = 'final-app-bench-v1'
PROFILES = {SCREENSHOT, NATIVE, WIDGET, BENCHMARK, FULL}
PACKAGE = 'com.github.e0ipso.tokkl.'
SCREEN_PACKAGE = PACKAGE+'ui.screenshot.'
SCREEN_SOURCE = 'app/src/androidTest/java/com/github/e0ipso/tokkl/ui/screenshot/'
REFERENCE_ROOT = 'app/src/androidTest/assets/screenshots'
CAPTURE_ROOT = 'app/build/outputs/connected_android_test_additional_output/debugAndroidTest'
SCREEN_ARGS = ['scripts/instrumented-tests.sh',
               '-Pandroid.testInstrumentationRunnerArguments.package='+SCREEN_PACKAGE.rstrip('.')]
FULL_ARGS = ['scripts/instrumented-tests.sh', ':macrobenchmark:connectedBenchmarkAndroidTest',
             '-Pandroid.testInstrumentationRunnerArguments.notClass='
             'com.github.e0ipso.tokkl.catalogprofile.CatalogFrameBenchmark', '--continue']
WIDGET_CASE = (PACKAGE+'widget.WidgetServerTest',
               'nativeClockTicksWithoutWidgetUpdatesAndProviderHasNoPeriod')
NATIVE_CASES = [
    (PACKAGE+'notification.NativeNotificationPermissionTest',
     'allowingTheNativeDialogPostsTheAlreadyRunningTimer'),
    (PACKAGE+'notification.NativeNotificationPermissionTest',
     'denyingTheNativeDialogKeepsTheTimerAndOpensItsSettings'),
    (PACKAGE+'pomodoro.NativePomodoroPermissionTest',
     'grantingTheFirstSessionDialogKeepsTheSameStartedSession'),
    (PACKAGE+'pomodoro.NativePomodoroPermissionTest',
     'denyingThenRecreatingKeepsPromptsAndLaterStartsAskOnlyOnExplicitRetry'),
]
SCREEN_METHODS = {
    'signInWithApiToken': 'sign-in-api-token',
    'signInWithEmailAndPassword': 'sign-in-email-password',
    'timer': 'timer', 'calendar': 'calendar', 'timerBesideTheRail': 'timer-rail',
    'entryBesideTheHistory': 'entry-beside', 'timerTotals': 'timer-totals',
    'calendarTotals': 'calendar-totals', 'pomodoroIdle': 'pomodoro-idle',
    'pomodoroIdleActions': 'pomodoro-idle-actions', 'pomodoroOvertime': 'pomodoro-overtime',
    'pomodoroOvertimeActions': 'pomodoro-overtime-actions', 'pomodoroAlarm': 'pomodoro-alarm',
    'pomodoroAlarmActions': 'pomodoro-alarm-actions', 'settings': 'settings', 'licences': 'licences',
    'reports': 'reports', 'detailedReport': 'reports-detailed', 'weeklyReport': 'reports-weekly',
    'projects': 'projects', 'clients': 'clients', 'tags': 'tags', 'moreSheet': 'more-sheet',
    'httpWarning': 'http-warning', 'certificatePrompt': 'certificate-prompt',
}
SCREEN_VARIANTS = {
    'LightScreenshotTest': 'light-100', 'DarkScreenshotTest': 'dark-100',
    'LightLargestFontScreenshotTest': 'light-200', 'DarkLargestFontScreenshotTest': 'dark-200',
}
PENDING_METHODS = {'timerTotals', 'calendarTotals', 'pomodoroIdle', 'pomodoroIdleActions',
                   'pomodoroOvertime', 'pomodoroOvertimeActions', 'pomodoroAlarm', 'pomodoroAlarmActions'}
INVENTORY_SHA256 = '312a8cdfb392882c8a01d0c1553945056022ac3eba78c6b0e99933e314359c0e'
COMMON = ['scripts/instrumented-tests.sh', 'scripts/sift_telemetry.py',
          'app/build.gradle.kts', 'gradle/libs.versions.toml']
require = sr.require


def selected(job):
    return isinstance(job.get('result_recipe'), dict) and job['result_recipe'].get('profile') in PROFILES


def source_table():
    path = Path(__file__).with_name('final_gate_inventory.json')
    raw = path.read_bytes()
    require(hashlib.sha256(raw).hexdigest() == INVENTORY_SHA256, 'reviewed full inventory metadata changed')
    return sr.strict_json(raw)


def source_rows(project, names):
    return [{'source': name, 'sha256': sr.sha(sr.checked_file(project, name))} for name in sorted(set(names))]


def checked_sources(project, rows):
    for row in rows:
        require(sr.sha(sr.checked_file(project, row['source'])) == row['sha256'],
                'reviewed source changed: '+row['source'])


def full_inventory(project, api):
    require(type(api) is int and api in (31, 36), 'reviewed full API31/36 inventory required')
    table = source_table()
    actual = sorted(p.relative_to(project).as_posix() for p in Path(project).glob('app/src/androidTest/**/*')
                    if p.suffix in ('.kt', '.java'))
    require(actual == sorted(r['source'] for r in table['sources']), 'full app source membership changed')
    checked_sources(project, table['sources'])
    cases = sorted([c['class'], m['name']] for c in table['classes'] for m in c['methods'] if api in m['apis'])
    filtered = sorted([c['class'], m['name']] for c in table['classes'] for m in c['methods'] if api not in m['apis'])
    require(cases and len(cases) == len({tuple(c) for c in cases}), 'invalid full app identity metadata')
    return {'api': api, 'cases': cases, 'sdk_filtered_out': filtered,
            'optional_native_sources': 'excluded; four separate one-method preparations',
            'catalog_experiments': 'four separate experiments excluded from benchmark task only',
            'source_table_sha256': INVENTORY_SHA256}


def screen_cases():
    return {(SCREEN_PACKAGE+cls, method): SCREEN_METHODS[method]+'-'+variant+'.png'
            for cls, variant in SCREEN_VARIANTS.items() for method in SCREEN_METHODS}


def references(project, phase):
    require(phase in ('capture', 'compare'), 'exact screenshot capture/compare phase required')
    expected = screen_cases()
    pending = {name for (cls, method), name in expected.items() if method in PENDING_METHODS}
    names = set(expected.values()) - pending if phase == 'capture' else set(expected.values())
    root = Path(project)/REFERENCE_ROOT
    require(root.is_dir() and root.resolve() == root and not root.is_symlink(), 'canonical screenshot reference root required')
    files = sorted(root.rglob('*'))
    require(all(p.parent == root and p.is_file() and not p.is_symlink() for p in files),
            'nested/symlink/unsafe screenshot reference entry')
    require({p.relative_to(root).as_posix() for p in files} == names,
            'screenshot reference names differ from exact 68+32 capture or 100 comparison phase')
    rows = source_rows(project, [p.relative_to(project).as_posix() for p in files])
    return {'phase': phase, 'references': rows, 'pending': sorted(pending) if phase == 'capture' else [],
            'comparison_certified': False}


def screenshot_audit(project, phase=None):
    """No resource reads. Without a phase, report either exact reference state."""
    if phase is not None:
        return references(Path(project), phase)
    try:
        return references(Path(project), 'compare')
    except ValueError:
        return references(Path(project), 'capture')


def reviewed_build(build, api):
    require(isinstance(build, dict) and isinstance(build.get('version'), dict)
            and type(build['version'].get('sdk')) is int and build['version']['sdk'] == api
            and all(isinstance(build.get(k), str) and build[k].strip()
                    for k in ['brand', 'device', 'fingerprint', 'id', 'model', 'type']),
            'complete reviewed API'+str(api)+' device build required')
    return build


def benchmark_sources(project):
    directory = Path(project)/sr.BENCHMARK_SOURCE
    require(sorted(p.name for p in directory.rglob('*.kt')) == sorted([*sr.BENCHMARK_SOURCES, 'CatalogFrameBenchmark.kt', 'CatalogProbe.kt']),
            'production benchmark source membership changed')
    rows = [{'source': sr.BENCHMARK_SOURCE+name, 'sha256': digest}
            for name, digest in sr.BENCHMARK_SOURCES.items()]
    checked_sources(project, rows+CATALOG_SOURCES)
    return rows+CATALOG_SOURCES


def declare(project, profile, device_build, *, phase=None, method=None, api=None):
    """Read reviewed sources/artifacts. Root must separately freeze all input pins."""
    project = Path(project).resolve()
    require(profile in PROFILES, 'unsupported final-gate profile')
    api = (31 if profile in (WIDGET, BENCHMARK) else 36) if api is None else api
    require(type(api) is int and api in (31, 36), 'exact final-gate API required')
    require(api == (31 if profile in (WIDGET, BENCHMARK) else 36) or profile == FULL,
            'profile/API mismatch')
    require(phase is None or profile == SCREENSHOT, 'screenshot phase belongs only to screenshot profile')
    require(method is None or profile == NATIVE, 'native method belongs only to native profile')
    recipe = {'profile': profile, 'api': api, 'device_build': reviewed_build(device_build, api)}
    checked_sources(project, COMMON_SOURCES)
    names = list(COMMON)
    table = source_table()
    if profile == SCREENSHOT:
        rows = [r for r in table['sources'] if r['source'].startswith(SCREEN_SOURCE)]
        require(len(rows) == 2, 'reviewed screenshot package membership metadata incomplete')
        actual = sorted(p.relative_to(project).as_posix() for p in (project/SCREEN_SOURCE).rglob('*.kt'))
        require(actual == sorted(r['source'] for r in rows), 'screenshot package source membership changed')
        checked_sources(project, rows)
        font = 'app/src/androidTest/java/com/github/e0ipso/tokkl/ui/theme/SystemFontScale.kt'
        font_row = next(r for r in table['sources'] if r['source'] == font)
        checked_sources(project, [font_row])
        names += [r['source'] for r in rows]+[font]
        recipe.update(references(project, phase), cases=[list(c) for c in sorted(screen_cases())],
                      comparison={'scale': 3, 'channel_tolerance': 8, 'max_differing_pixels': 20})
    elif profile == NATIVE:
        require(isinstance(method, str) and method in ['#'.join(c) for c in NATIVE_CASES],
                'exactly one of four native permission methods required')
        native = sorted(project.glob('app/src/notificationPermissionTest/**/*.kt'))
        expected = sorted('app/src/notificationPermissionTest/java/'+c.replace('.', '/')+'.kt' for c in {c for c, m in NATIVE_CASES})
        require(sorted(p.relative_to(project).as_posix() for p in native) == expected,
                'both optional native source files required, no extra members')
        # These exact reviewed bytes include SDK guards and native dialog assertions.
        checked_sources(project, NATIVE_SOURCES)
        names += expected
        recipe.update(method=method, cases=[method.split('#')], reset_required=True)
    elif profile == WIDGET:
        row = next(r for r in table['sources'] if r['source'].endswith('/widget/WidgetServerTest.kt'))
        checked_sources(project, [row])
        names.append(row['source'])
        recipe['cases'] = [list(WIDGET_CASE)]
    elif profile == BENCHMARK:
        api_names = sorted('app/src/androidTest/java/'+c.replace('.', '/')+'.kt' for c in sr.API_CLASSES)
        api_dir = project/'app/src/androidTest/java/com/github/e0ipso/tokkl/api'
        require(sorted(p.relative_to(project).as_posix() for p in api_dir.rglob('*')
                       if p.suffix in ('.kt', '.java')) == api_names, 'exact API diagnostic package membership required')
        checked_sources(project, [r for r in table['sources'] if r['source'] in api_names])
        rows = [sr.inventory(project, c) for c in sr.API_CLASSES]
        require([len(r['methods']) for r in rows] == [3, 7], 'exact ten API diagnostic methods required')
        recipe['cases'] = sorted([r['class'], m] for r in rows for m in r['methods'])
        names += [r['source'] for r in rows]
    elif profile == FULL:
        recipe['app_inventory'] = full_inventory(project, api)
        recipe['cases'] = recipe['app_inventory']['cases']
        names += [r['source'] for r in table['sources']]
        if api == 36:
            recipe['screenshots'] = references(project, 'compare')
        # Membership of the four excluded experiments is also source-bound.
        catalog = 'macrobenchmark/src/main/java/com/github/e0ipso/tokkl/benchmark/CatalogFrameBenchmark.kt'
        checked_sources(project, CATALOG_SOURCES)
        names += [catalog]
    if profile in (BENCHMARK, FULL):
        names += [r['source'] for r in benchmark_sources(project)]+['macrobenchmark/build.gradle.kts']
        recipe['apks'] = source_rows(project, sr.APK_PATHS)
        require(all((project/r['source']).stat().st_size > 0 for r in recipe['apks']), 'empty reviewed APK')
        recipe['benchmark_cases'] = [list(c) for c in sr.BENCHMARK_CASES]
    recipe['sources'] = source_rows(project, names)
    return recipe


def job_spec(recipe):
    profile, api = recipe['profile'], recipe['api']
    if profile == SCREENSHOT:
        return 'screenshot-'+recipe['phase'], SCREEN_ARGS, 1800
    if profile == NATIVE:
        return 'native-permission-'+str(['#'.join(c) for c in NATIVE_CASES].index(recipe['method'])+1), [
            'scripts/instrumented-tests.sh', sr.PREFIX+recipe['method']], 1800
    if profile == WIDGET:
        return 'widget-clock-api31', ['scripts/instrumented-tests.sh', sr.PREFIX+'#'.join(WIDGET_CASE)], 1800
    if profile == BENCHMARK:
        return 'benchmark-api31', sr.BENCHMARK_ARGS, 1800
    return 'final-app-bench-api'+str(api), FULL_ARGS, 5400


def validate_recipe(project, job, pins):
    r = job['result_recipe']
    expected = declare(project, r.get('profile'), r.get('device_build'), phase=r.get('phase'),
                       method=r.get('method'), api=r.get('api'))
    require(r == expected, 'final-gate recipe differs from exact source/phase/device inventory')
    label, args, seconds = job_spec(expected)
    require(job.get('label') == label and job.get('api') == r['api'] and job.get('standalone') is False
            and type(job.get('seconds')) is int
            and (5400 <= job['seconds'] <= 7200 if r['profile'] == FULL else job['seconds'] == seconds)
            and job.get('args') == args, 'exact final-gate label/API/argv/deadline required')
    rows = r['sources'] + r.get('apks', []) + r.get('references', []) + r.get('screenshots', {}).get('references', [])
    for row in rows:
        require(pins.get(str(Path(project)/row['source'])) == row['sha256'], 'final-gate source/artifact missing checked input pin')
    return expected


def validate_stage(config):
    stage = config.get('stage')
    require(stage in ('development', 'compatibility'), 'unsupported verification stage')
    devices = [j for j in config['plan'] if not j.get('standalone')]
    if stage == 'compatibility':
        require(devices and len(devices) == len(config['plan']) and 'plan_recipe' not in config
                and all(selected(j) and j.get('api') == 31
                        and j['result_recipe']['profile'] in (WIDGET, BENCHMARK, FULL) for j in devices),
                'compatibility admits only exact API31 diagnostic/final profiles')
    else:
        require(all(j.get('api') == 36 for j in devices), 'development remains API36 only')
    captures = [j for j in devices if selected(j) and j['result_recipe']['profile'] == SCREENSHOT]
    if captures:
        require(len(config['plan']) == 1, 'screenshot capture/comparison requires one sole fresh job')


def reset_flag(config, label):
    jobs = [j for j in config['plan'] if j['label'] == label]
    if len(jobs) != 1:
        return '0'
    job = jobs[0]
    if not selected(job) or job['result_recipe']['profile'] != NATIVE:
        return '0'
    r = job['result_recipe']
    name, args, seconds = job_spec(r)
    require(job['label'] == name and job['api'] == 36 and job['standalone'] is False
            and job['seconds'] == seconds and job['args'] == args, 'unvalidated native reset command')
    return '1'


def png(raw):
    require(raw.startswith(b'\x89PNG\r\n\x1a\n'), 'invalid PNG signature')
    position = 8
    kinds = []
    image_data = []
    while position < len(raw):
        require(position+12 <= len(raw), 'truncated PNG chunk')
        size = struct.unpack('>I', raw[position:position+4])[0]
        kind = raw[position+4:position+8]
        end = position+12+size
        require(end <= len(raw) and zlib.crc32(raw[position+4:end-4]) & 0xffffffff
                == struct.unpack('>I', raw[end-4:end])[0], 'invalid PNG chunk/CRC')
        if not kinds:
            require(kind == b'IHDR' and size == 13, 'missing PNG header')
            width, height = struct.unpack('>II', raw[position+8:position+16])
            require(0 < width <= 8192 and 0 < height <= 8192, 'invalid PNG dimensions')
            depth, color, compression, filtering, interlace = raw[position+16:position+21]
            require(depth == 8 and color in (2, 6) and compression == filtering == interlace == 0,
                    'unsupported capture PNG encoding')
            stride = 1+width*(3 if color == 2 else 4)
            expected = stride*height
            require(expected <= 32*1024*1024, 'oversized decoded capture PNG')
        require(re.fullmatch(b'[A-Za-z]{4}', kind) is not None, 'invalid PNG chunk type')
        require(kind[0] & 32 or kind in (b'IHDR', b'PLTE', b'IDAT', b'IEND'), 'unknown critical PNG chunk')
        require(kind != b'IHDR' or not kinds, 'duplicate PNG header')
        if kind == b'IDAT':
            require(not image_data or kinds[-1] == b'IDAT', 'noncontiguous PNG image data')
            image_data.append(memoryview(raw)[position+8:end-4])
        kinds.append(kind)
        position = end
        if kind == b'IEND':
            require(size == 0 and position == len(raw), 'invalid PNG end')
            break
    require(kinds and kinds[-1] == b'IEND' and b'IDAT' in kinds, 'incomplete PNG')
    decoder = zlib.decompressobj()
    decoded = 0
    for data in image_data:
        while len(data):
            block = decoder.decompress(data, min(65536, expected-decoded+1))
            require(decoded+len(block) <= expected, 'oversized decoded PNG image data')
            require(all(block[i] <= 4 for i in range((-decoded) % stride, len(block), stride)),
                    'invalid PNG scanline filter')
            decoded += len(block)
            data = decoder.unconsumed_tail
            require(not decoder.unused_data, 'extra compressed PNG data')
    require(decoder.eof and decoded == expected, 'truncated/invalid compressed PNG data')


def retain_captures(config, label, prefix, started):
    """Exclusive bounded copy before assessment, including failed test captures."""
    jobs = [j for j in config['plan'] if j['label'] == label]
    if len(jobs) != 1 or not selected(jobs[0]):
        return None
    recipe = jobs[0]['result_recipe']
    if recipe['profile'] != SCREENSHOT and not (recipe['profile'] == FULL and recipe['api'] == 36):
        return None
    base = Path(config['run_dir'])
    receipt = {'files': [], 'errors': [], 'complete': False}
    dest = None
    created = False
    try:
        require(re.fullmatch(r'[a-z0-9-]+', prefix) is not None, 'unsafe capture destination prefix')
        dest = base/'app-additional-output'
        require(base.resolve() == base and base.is_dir() and dest.parent == base, 'unsafe capture evidence root')
        dest.mkdir(mode=0o700)
        created = True
        receipt['root'] = str(dest)
        source = Path(config['project'])/CAPTURE_ROOT
        receipt['source_root'] = str(source)
        if not source.exists():
            require(not source.is_symlink(), 'symlink capture root')
            receipt['complete'] = True
            return receipt
        require(source.resolve() == source and source.is_dir(), 'escaped/symlink capture root')
        names = set(screen_cases().values())
        inodes = set()
        seen = set()
        total = 0
        entries = 0
        for directory, dirs, files in os.walk(source, followlinks=False):
            entries += len(dirs)+len(files)
            require(len(dirs)+len(files) <= 256 and entries <= 1024
                    and len(Path(directory).relative_to(source).parts) <= 5, 'excess/deep capture directory entries')
            for name in dirs+files:
                path = Path(directory)/name
                require(not path.is_symlink() and path.resolve() == path, 'escaped/symlink capture entry')
            for name in files:
                path = Path(directory)/name
                relative = path.relative_to(source)
                if 'screenshots' not in relative.parts:
                    continue
                require(re.fullmatch(r'connected/[^/]+/screenshots/(actual|diff)/[^/]+\.png', relative.as_posix())
                        and name in names, 'noncanonical/foreign capture filename')
                kind = relative.parts[-2]
                require((kind, name) not in seen, 'duplicate capture identity')
                seen.add((kind, name))
                before = path.stat()
                require(stat.S_ISREG(before.st_mode) and before.st_nlink == 1
                        and (before.st_dev, before.st_ino) not in inodes, 'nonregular/duplicate capture file')
                inodes.add((before.st_dev, before.st_ino))
                raw = sr.fresh_bytes(source, path, started, 16*1024*1024)
                total += len(raw)
                require(total <= 128*1024*1024 and len(seen) <= 200, 'capture corpus exceeds bounds')
                png(raw)
                target = dest/relative
                target.parent.mkdir(parents=True, mode=0o700, exist_ok=True)
                with target.open('xb') as stream:
                    stream.write(raw)
                    stream.flush()
                    os.fsync(stream.fileno())
                digest = hashlib.sha256(raw).hexdigest()
                require(sr.sha(target) == digest and sr.sha(path) == digest
                        and sr.checked_file(source, relative) == path
                        and sr.checked_file(dest, relative) == target
                        and _capture_identity(before) == _capture_identity(path.stat()),
                        'capture changed during copy')
                receipt['files'].append({'relative': relative.as_posix(), 'original': str(path), 'path': str(target),
                                         'sha256': digest, 'bytes': len(raw), 'source_mtime_ns': before.st_mtime_ns})
        receipt['complete'] = True
    except (OSError, ValueError, KeyError, TypeError) as error:
        receipt['errors'].append(str(error))
    finally:
        if created:
            target = base/'app-additional-output.json'
            try:
                with target.open('x') as stream:
                    json.dump(receipt, stream, sort_keys=True)
                    stream.write('\n')
                    stream.flush()
                    os.fsync(stream.fileno())
            except OSError as error:
                receipt['complete'] = False
                receipt['errors'].append('retention receipt: '+str(error))
    return receipt


def _junit(parser, directory, expected, recorded, started, run_dir):
    directory = Path(directory)
    require(directory.resolve() == directory and directory.is_relative_to(run_dir), 'unsafe JUnit corpus')
    files = sorted(directory.rglob('TEST-*.xml'))
    require(0 < len(files) <= 12, 'missing/excess finalized JUnit corpus')
    cases = {}
    evidence = []
    for path in files:
        raw = sr.fresh_bytes(directory, path, started, 2*1024*1024)
        root = ET.fromstring(raw)
        require(root.tag in ('testsuite', 'testsuites'), 'unsupported JUnit root')
        suites = [root] if root.tag == 'testsuite' else list(root)
        require(all(s.tag == 'testsuite' for s in suites), 'unknown/nested JUnit structure')
        for suite in suites:
            require(not any(n.tag in ('testsuite', 'testsuites') for n in suite.iter() if n is not suite),
                    'nested JUnit structure')
            for node in suite.findall('testcase'):
                identity = (node.get('classname'), node.get('name'))
                require(identity not in cases, 'duplicate finalized case identity')
                cases[identity] = node
        if root.tag == 'testsuites':
            for key in ('tests', 'failures', 'errors', 'skipped'):
                if key in root.attrib:
                    require(int(root.get(key)) == sum(int(s.get(key, 0)) for s in suites), 'aggregate JUnit count mismatch')
        evidence.append({'path': str(path), 'sha256': hashlib.sha256(raw).hexdigest()})
    counts = parser.junit_counts([str(p) for p in files], started)
    require(set(cases) == expected and counts == recorded and counts['tests'] == len(expected),
            'exact finalized per-API source identities/counts required')
    return counts, cases, evidence


def _captures(config, record, started, cases, recipe):
    receipt = record.get('app_additional_output')
    require(isinstance(receipt, dict) and receipt.get('complete') is True and not receipt['errors'],
            'missing/incomplete canonical capture retention')
    root = Path(receipt['root'])
    run_dir = Path(config['run_dir'])
    require(root.resolve() == root and root.parent == run_dir and root == run_dir/'app-additional-output',
            'unsafe retained capture root')
    seen = set()
    mapping = screen_cases()
    emitted = {'actual': set(), 'diff': set()}
    device_dirs = set()
    for row in receipt['files']:
        relative = row['relative']
        require(re.fullmatch(r'connected/[^/]+/screenshots/(actual|diff)/[^/]+\.png', relative)
                and Path(row['path']) == root/relative and relative not in seen, 'unsafe/duplicate retained capture')
        parts = Path(relative).parts
        kind, name = parts[-2:]
        require(name in mapping.values() and name not in emitted[kind], 'foreign/duplicate screenshot name')
        emitted[kind].add(name)
        device_dirs.add(parts[1])
        seen.add(relative)
        original = Path(config['project'])/CAPTURE_ROOT/relative
        require(row['original'] == str(original) and row['source_mtime_ns'] >= int(started*1e9),
                'stale/mismatched capture origin')
        raw = sr.fresh_bytes(root, root/relative, started, 16*1024*1024)
        png(raw)
        require(len(raw) == row['bytes'] and hashlib.sha256(raw).hexdigest() == row['sha256']
                and sr.sha(sr.checked_file(config['project'], original.relative_to(config['project']))) == row['sha256'],
                'retained/original capture hash mismatch')
    require(len(device_dirs) <= 1 and emitted['diff'] <= emitted['actual'], 'duplicate device or orphan diff')
    actual_files = {p.relative_to(root).as_posix() for p in root.rglob('*') if not p.is_dir()}
    require(actual_files == seen, 'unrecorded/missing retained capture files')
    failures = set()
    required_diff = set()
    for identity, node in cases.items():
        if identity not in mapping:
            continue
        require(not node.findall('error') and not node.findall('skipped'), 'screenshot error/skipped case')
        failed = node.findall('failure')
        if failed:
            require(len(failed) == 1 and failed[0].get('type') == 'java.lang.AssertionError',
                    'unexpected screenshot exception')
            name = mapping[identity]
            message = failed[0].get('message', '')
            require(name in emitted['actual'] and (name in message or name[:-4] in message),
                    'failed screenshot lacks corresponding fresh actual/name')
            missing = message.startswith('no reference image screenshots/'+name+' in the test assets. This capture is in ')
            dimension = re.match(re.escape(name[:-4])+r' is [1-9][0-9]*x[1-9][0-9]*, but its reference is [1-9][0-9]*x[1-9][0-9]*\. Record the references on tokkl-api36\. This capture is in ', message)
            differs = re.match(re.escape(name[:-4])+r' differs from its reference in ([0-9]+) pixels, more than 20, within .+\. The capture is in .+ and the diff in ', message)
            require(missing or dimension or (differs and int(differs[1]) > 20),
                    'unexpected screenshot assertion; preserve failure without capture completion')
            if differs:
                required_diff.add(name)
            failures.add(name)
    require(emitted['actual'] == failures and emitted['diff'] == required_diff,
            'capture actual/diff output does not match exact failing screenshot identities')
    phase = recipe.get('phase', 'compare')
    if phase == 'capture':
        pending = set(recipe['pending'])
        require(pending <= failures, 'missing-reference screenshot unexpectedly passed')
        for identity, name in mapping.items():
            if name in pending:
                require('no reference image '+REFERENCE_ROOT.split('/')[-1]+'/'+name
                        in cases[identity].find('failure').get('message', ''), 'pending reference failure differs')
    return receipt


def assess(config, api, label, args, seconds, record, started, exit_code, stop_reason):
    result = {'passed': False, 'capture_complete': False, 'comparison': 'not performed', 'reason': None}
    try:
        if __package__:
            from .configuration import validate
        else:
            from configuration import validate
        run_dir = Path(config['run_dir'])
        current = validate(sr.strict_json((run_dir/'run-config.json').read_bytes()), fresh=False)
        require(current == config, 'frozen configuration identity changed during execution')
        jobs = [j for j in config['plan'] if j['label'] == label]
        require(len(jobs) == 1, 'missing/duplicate configured job')
        job = jobs[0]
        require((api, args, seconds, False) == (job['api'], job['args'], job['seconds'], job['standalone']),
                'actual final-gate job/API/argv/deadline mismatch')
        recipe = validate_recipe(config['project'], job, config['input_pins'])
        result['profile'] = recipe['profile']
        require(not stop_reason and not record['stop_reason'] and exit_code == record['exit'],
                'payload/resource/finalization outcome failed')
        require(record['api_requested'] == api and record['label'] == label and record['standalone'] is False
                and record['command'][-len(args):] == args
                and 'TOKKL_TEST_AVD=tokkl-api'+str(api) in record['command']
                and [x for x in record['command'] if x.startswith('TOKKL_TEST_FRESH_NOTIFICATION_PERMISSION=')]
                    == ['TOKKL_TEST_FRESH_NOTIFICATION_PERMISSION='+reset_flag(config, label)],
                'controller device/reset/argv identity mismatch')
        cleanup = record['cleanup']
        require(all(cleanup[k] is True for k in ('work_directories_removed', 'private_android_tmp_removed', 'emulator_scopes_absent'))
                and cleanup['new_shared_android_files'] == [] and cleanup['work_directories']
                and cleanup['private_android_tmp'] and cleanup['observed_android_tmp_env']
                and all(v['ANDROID_TMP'] in cleanup['private_android_tmp'] for v in cleanup['observed_android_tmp_env'].values()),
                'incomplete controller cleanup evidence')
        text = sr.fresh_bytes(run_dir, Path(record['log']), started, 16*1024*1024).decode()
        devices = re.findall(r'(?m)^emulator-[0-9]+ runs .* on API [0-9]+$', text)
        require(len(devices) == 1 and re.fullmatch(r'emulator-[0-9]+ runs tokkl-api'+str(api)+' on API '+str(api), devices[0])
                and devices == record['device_lines'], 'wrong/missing owned AVD/API evidence')
        resets = re.findall(r'(?m)^Reset notification permission before instrumentation on (.*)$', text)
        serial = devices[0].split()[0]
        require(resets == ([serial] if recipe['profile'] == NATIVE else []), 'missing/duplicate/foreign/leaked native reset')
        manifest = sr.strict_json((run_dir/'runtime-manifest.json').read_bytes())
        require(manifest and all(sr.sha(sr.checked_file(run_dir, n)) == d for n, d in manifest.items())
                and manifest.get('final_gate_profiles.py') == sr.sha(__file__)
                and manifest.get('final_gate_inventory.json') == INVENTORY_SHA256, 'frozen final-gate runtime changed')
        parser_path = Path(config['project'])/'scripts/sift_telemetry.py'
        spec = importlib.util.spec_from_file_location('pinned_final_gate_junit', parser_path)
        parser = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(parser)
        counts, cases, junit = _junit(parser, record['results'], {tuple(c) for c in recipe['cases']},
                                      record['counts'], started, run_dir)
        result.update(app_counts=counts, app_cases=[list(c) for c in sorted(cases)], junit=junit,
                      failures=record['failures'], configuration_sha256=config['_sha256'],
                      recipe_sha256=hashlib.sha256(json.dumps(recipe, sort_keys=True, separators=(',', ':')).encode()).hexdigest())
        require(not counts['errors'] and not counts['skipped'], 'error/skipped applicable app cases')
        if recipe['profile'] == SCREENSHOT or (recipe['profile'] == FULL and api == 36):
            result['captures'] = _captures(config, record, started, cases, recipe)
            result['capture_complete'] = True
        if recipe['profile'] in (BENCHMARK, FULL):
            require(exit_code == 0 and not counts['failures'], 'failed app/benchmark payload')
            for key in ('benchmark-results', 'benchmark-data'):
                directory = Path(record[key])
                require(directory.resolve() == directory and directory.is_relative_to(run_dir), 'unsafe benchmark evidence root')
            bench = sr.exact_junit(parser, record['benchmark-results'], set(sr.BENCHMARK_CASES), record['benchmark_counts'], started)
            require(sorted((c['class'], c['test']) for c in record['benchmark_cases']) == bench[1],
                    'controller production benchmark identities mismatch')
            result.update(sr.benchmark_data(record['benchmark-data'], recipe, started))
            result.update(benchmark_counts=bench[0], benchmark_cases=bench[1], junit=junit+bench[2])
            result['passed'] = True
            result['full_app_gate'] = recipe['profile'] == FULL
        else:
            for key, pattern in [('benchmark-results', 'TEST-*.xml'), ('benchmark-data', '*-benchmarkData.json')]:
                directory = record.get(key)
                require(not directory or not any(p.stat().st_mtime >= started for p in Path(directory).rglob(pattern)),
                        'unrequested fresh benchmark execution')
            if recipe['profile'] == SCREENSHOT and recipe['phase'] == 'capture':
                require(exit_code != 0 and counts['failures'] > 0, 'capture failures must remain failed payload/controller')
            # The unchanged launcher emits AVD/SDK, not a build fingerprint.
            # Never invent that receipt or promote synthetic fixtures to live proof.
            result['unresolved_metadata'] = ['actual app-only device build fingerprint is not emitted by the maintained launcher/parser']
            raise ValueError(result['unresolved_metadata'][0])
    except (OSError, ValueError, KeyError, TypeError, AttributeError, OverflowError, ET.ParseError,
            struct.error, zlib.error, zipfile.BadZipFile, RuntimeError) as error:
        result['reason'] = str(error)
    return result


NATIVE_SOURCES = [{'source': 'app/src/notificationPermissionTest/java/com/github/e0ipso/tokkl/notification/NativeNotificationPermissionTest.kt', 'sha256': '9cc1a1a1662627793491d5dd9e4be682d64d722b75da9a6c583fcbd723244e6b'}, {'source': 'app/src/notificationPermissionTest/java/com/github/e0ipso/tokkl/pomodoro/NativePomodoroPermissionTest.kt', 'sha256': '2ed0a2038280f800f3b55a1dd638e6c35e9d09cd4e9d03be54d8dc7b95365047'}]

CATALOG_SOURCES = [{'source': 'macrobenchmark/src/main/java/com/github/e0ipso/tokkl/benchmark/CatalogFrameBenchmark.kt', 'sha256': 'a15038b3b2c63c87b9dda70dbe7abd7ca8d2e7da560d156cac83480a0861cdfa'}, {'source': 'macrobenchmark/src/main/java/com/github/e0ipso/tokkl/benchmark/CatalogProbe.kt', 'sha256': 'ce6c4ca31e0c3c4420249ee780eb64af9b1da0327f5ea35f3a1bde85461ecb5f'}]

COMMON_SOURCES = [{'source': 'scripts/instrumented-tests.sh', 'sha256': '176c21969af9df0e8bf65975c376c46fddd9a79096eea688f8f8824d49151bbd'}, {'source': 'scripts/sift_telemetry.py', 'sha256': '62ca64cee8734b2563603a525931950cd2fe034f1500494e6d91b5dae70f620f'}, {'source': 'app/build.gradle.kts', 'sha256': '4d15b4a51356d7906cd6a29bdd5c651204c467d3bb70ad94d6d97d80a89e1719'}, {'source': 'macrobenchmark/build.gradle.kts', 'sha256': 'd68c49b4d16656e1d34adc7b411d981338f7d9a6c2f304c1ee9d0b797374a5a4'}]

COMMON_SOURCES.append({'source': 'gradle/libs.versions.toml', 'sha256': '5c802bab8713c5ce1a0c48965b0a20ad669b9efc32b709198f45e75979dd8f35'})


def _capture_identity(info):
    return info.st_dev, info.st_ino, info.st_size, info.st_mtime_ns, info.st_ctime_ns
