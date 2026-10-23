"""Synthetic Catalog evidence. Git/device/tool execution is explicitly modeled.

Historical source and driver bytes are real. No Android build, server, processor,
systemd or resource-window operation is performed by this fixture.
"""
from contextlib import ExitStack
import copy
import csv
from functools import lru_cache
import gzip
import base64
import json
from pathlib import Path
import shutil
import subprocess
from unittest.mock import patch

import catalog_profile as cat
from scoped_controller_fixture import WorkloadFixture, DEVICE_BUILD
from test_candidate import PACKAGE
from prepare import prepare
from runtime.configuration import adopted_configuration, sha
import runtime.catalog_profile as packaged


def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, separators=(',', ':')))


def clock(ns):
    return {'mono_ns': ns, 'boot_ns': ns+100000, 'uptime_ms': ns//1000000}


ENV = {'width': 400, 'height': 800, 'density_dpi': 160, 'font_scale': 1,
       'locale': 'en-US', 'orientation': 1, 'refresh_hz': 60, 'bars': [0, 0, 0, 0], 'ime_visible': False, 'theme': 'light'}


def fixture_data():
    return {'schema': 'catalog-fixture-v1', 'timezone': 'Europe/Madrid',
            'tags': [{'id': i+1, 'name': f'Measured tag {i:03d}'} for i in range(500)],
            'trash': [{'id': i+1001, 'description': f'Measured trash {i}', 'start': '2026-10-19T08:00:00Z',
                       'stop': '2026-10-19T08:05:00Z', 'duration': 300} for i in range(1000)],
            'origin': {'kind': 'real-server', 'declared_server_image': cat.SERVER,
                       'cleanup': {'user_delete_status': 204, 'deleted_auth_status': 401}}}


@lru_cache(maxsize=2)
def sources(revision):
    root = PACKAGE.parent.parent
    result = {}
    for entry in subprocess.check_output(['git', 'ls-tree', '-rz', revision], cwd=root).split(b'\0'):
        if not entry: continue
        meta, name = entry.split(b'\t', 1); name = name.decode()
        if name in cat.source_paths([name]):
            result[name] = subprocess.check_output(['git', 'cat-file', 'blob', meta.decode().split()[2]], cwd=root)
            if name == 'gradlew.bat': result[name] = result[name].replace(b'\n', b'\r\n')
    overlay = json.loads((PACKAGE/'tests/fixtures/catalog-driver-source.json').read_text())
    result.update({name: raw.encode() for name, raw in overlay['files'].items()})
    return result


def metadata(project, *args):
    # Only synthetic Git metadata is modeled. ls-tree/cat-file read the actual
    # approved historical objects and every checked source byte is validated.
    if args == ('rev-parse', '--show-toplevel'): return str(project).encode()+b'\n'
    if args == ('rev-parse', 'HEAD'): return b'555a6ad82309423bfcfad5bf87d31035a0578ba0\n'
    if args == ('rev-parse', 'HEAD^{tree}'): return b'1'*40+b'\n'
    if args[0] == 'diff':
        if args[-1] == 'HEAD' and args[-2] in cat.REVISIONS:
            names = set(cat.OVERLAY) | set(cat.VERIFICATION) | {'docs/TAGS-TRASH-PROFILING.md', 'test/catalog_frame_summary_test.py'}
            return ('\n'.join(sorted(names))+'\n').encode()
        return b''
    if args[0] == 'status': return b''
    return subprocess.check_output(['git', *args], cwd=PACKAGE.parent.parent)


def modeled_metadata():
    stack = ExitStack()
    stack.enter_context(patch.object(cat, 'git', metadata))
    stack.enter_context(patch.object(packaged, 'git', metadata))
    return stack


class CatalogFixture(WorkloadFixture):
    def __init__(self, root, revision=cat.REVISIONS[0], seed=True, accepted_seed=None, reverse_methods=False):
        self.reverse_methods = reverse_methods
        super().__init__(root, benchmark_api=True)
        shutil.rmtree(self.base)
        shutil.rmtree(self.project); self.project.mkdir()
        for name, raw in sources(revision).items():
            path = self.project/name; path.parent.mkdir(parents=True, exist_ok=True); path.write_bytes(raw)
        self.fixture_raw = json.dumps(fixture_data(), separators=(',', ':')).encode()
        encoded = base64.b64encode(gzip.compress(self.fixture_raw, mtime=0)).decode()
        self.transport = {'encoded': encoded, 'sha256': cat.digest(self.fixture_raw)}
        self.parameters = dict(zip(cat.COMMON, ['synthetic', revision, cat.digest(json.dumps(ENV, separators=(',', ':')).encode()), '0:0:400:800', '0:0:400:800']))
        if seed:
            self.parameters = {k: self.parameters[k] for k in cat.COMMON[:2]}
        elif accepted_seed:
            state = json.loads(Path(accepted_seed['controller_state']).read_text())
            observed = state['tests'][0]['result_contract']
            self.parameters.update(observed['replay_arguments'])
            self.transport = observed['fixture_transport']
            self.fixture_raw = cat.fixture_bytes(self.transport['encoded'], self.transport['sha256'])[0]
            encoded = self.transport['encoded']
        self.parameters.update({'catalogSeed': 'true', 'catalogServerImage': cat.SERVER} if seed else
                               {'catalogFixture': encoded, 'catalogFixtureSha256': self.transport['sha256']})
        args = cat.ARGS+[cat.ARG+k+'='+v for k, v in self.parameters.items()]
        for name in cat.scoped.APK_PATHS:
            path = self.project/name; path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(b'SYNTHETIC LOCAL APK '+name.encode())
        jdk = self.root/'tools/jdk'; sdk = self.root/'tools/sdk'
        tool_paths = dict(java=jdk/'bin/java', java_release=jdk/'release', android_jar=sdk/'platforms/android-36/android.jar',
            adb=sdk/'platform-tools/adb', aapt2=sdk/'build-tools/36.0.0/aapt2',
            gradle_launcher=self.root/'tools/gradle-launcher-9.8.1.jar', resolved_dependencies=self.root/'tools/dependencies.json')
        for key, path in tool_paths.items():
            path.parent.mkdir(parents=True, exist_ok=True); path.write_text('SYNTHETIC '+key)
        dependency = self.root/'tools/library.jar'; dependency.write_text('SYNTHETIC reviewed dependency')
        rows = cat.historical_sources(self.project, revision)
        build_rows = [r for r in rows if r['source'].endswith(('.kts', '.toml', '.properties', '.jar')) or r['source'] in ['gradlew', 'gradlew.bat']]
        write(tool_paths['resolved_dependencies'], dict(schema='catalog-resolved-dependencies-v1',
            build_sources_sha256=cat.digest(cat.canonical(build_rows)), files=[dict(artifact='synthetic:library:1', path=str(dependency), sha256=sha(dependency))]))
        self.tool_inputs = {key: dict(path=str(path), sha256=sha(path)) for key, path in tool_paths.items()}
        self.configuration['environment'].update(JAVA_HOME=str(jdk), ANDROID_HOME=str(sdk))
        recipe = cat.declare(self.project, args, DEVICE_BUILD, self.tool_inputs, accepted_seed)
        self.configuration.update(checked_head=recipe['checked_head'])
        self.configuration['input_pins'] = {p: h for p, h in self.configuration['input_pins'].items() if not Path(p).is_relative_to(self.project)}
        self.configuration['input_pins'].update({str(self.project/r['source']): r['sha256'] for r in recipe['sources']+recipe['apks']})
        self.configuration['input_pins'].update({r['path']: r['sha256'] for r in list(recipe['tool_inputs'].values())+recipe['dependency_inputs']})
        if accepted_seed: self.configuration['input_pins'].update(accepted_seed['inputs'])
        self.configuration['plan'] = [dict(api=36, label=cat.LABEL, standalone=False, seconds=7200, args=args, result_recipe=recipe)]
        proof = prepare(self.configuration)
        self.adoption = self.root/'HOST-MODELED-adoption.json'
        write(self.adoption, dict(configuration_sha256=proof['configuration_sha256'], runtime_sha256=proof['runtime_sha256'],
                                  lease_id=self.configuration['lease_id'], approved_by='HOST-MODEL-ONLY', cheap_live_controller_fixtures_verified=True))
        self.checked = adopted_configuration(self.base, self.adoption)
        self.job = [36, cat.LABEL, args, 7200, False]
        self.ns['config'] = self.checked
        self.info['expected_app_cases'] = [[r['class'], m] for r in recipe['inventory'][:2] for m in r['methods']]

    def snap(self, workload, ns, index=0):
        return dict(clock=clock(ns), ready=True, workload=workload, fixture_sha256=self.transport['sha256'], draw_serial=2,
                    last_draw_ns=ns-1, exposed_row_texts=10, viewport=[0, 0, 400, 800], environment=copy.deepcopy(ENV),
                    anchors=[dict(id=fixture_data()[workload][index]['id'], index=index, left=0, right=400,
                                  top=10, bottom=40, clipped_top=10, clipped_bottom=40)])

    def frame(self, seq, ns):
        return dict(seq=seq, callback=clock(ns+20000000), intended_vsync_ns=ns, vsync_ns=ns,
                    total_ns=12000000, deadline_duration_ns=10000000, first_draw=0, dropped_reports=0)

    def fence(self, frames, touches, frame_from, touch_from, phases, final, ns):
        return dict(frame_total=frame_from+len(frames), touch_total=touch_from+len(touches), phases=copy.deepcopy(phases),
                    listener_removed=final, barrier_ack=clock(ns), dropped_boundary_attribution='UNKNOWN',
                    frame_from=frame_from, touch_from=touch_from, frames=frames, touches=touches)

    def benchmark_results(self):
        self.xml(self.project/'macrobenchmark/build/outputs/androidTest-results/connected/benchmark/TEST-bench.xml', [[cat.CLASS, m] for m in cat.METHODS])
        folder = self.project/'macrobenchmark/build/outputs/connected_android_test_additional_output/benchmark'
        folder.mkdir(parents=True, exist_ok=True)
        data = []
        self.raw_windows = {}; self.raw_traces = {}
        self.baselines = {}
        methods = list(cat.METHODS.items())
        if self.reverse_methods: methods = [methods[1], methods[0], methods[3], methods[2]]
        for cell, (method, (workload, mode)) in enumerate(methods):
            prefix = f"synthetic-{self.parameters['catalogRevision']}-{workload}-{mode}-"
            (folder/(prefix+'fixture.json')).write_bytes(self.fixture_raw)
            write(folder/(prefix+'fixture-transport.json'), self.transport)
            if cell == 0 and 'catalogSeed' in self.parameters:
                write(folder/(prefix+'seed-receipt.json'), dict(phase='closed', declared_server_image=cat.SERVER,
                    server='http://10.0.2.2:8080', email='catalog-0123456789abcdef@example.com', user_id=1, workspace_id=2,
                    cleanup={'user_delete_status': 204, 'deleted_auth_status': 401}))
            frame_cursor = 0; touch_cursor = 0; phases = []; outputs = []
            for index in range(10):
                base = 10000000000+cell*100000000000+index*2000000000
                common = dict(schema='catalog-window-v1', run='synthetic', revision=self.parameters['catalogRevision'],
                              workload=workload, phase=mode, fixture_sha256=self.transport['sha256'], compilation='Full', absolute_pixel_travel=None)
                if mode == 'first' or index == 0:
                    phases = [dict(id='opening', start=clock(base+1000000), end=clock(base+20000000))]
                    frame_cursor = 1; touch_cursor = 0
                    write(folder/(prefix+f'ready-{index}.json'), dict(self.snap(workload, base+30000000), quiet_draw_ns=10000000, last_draw_ns=base+20000000))
                    if workload not in self.baselines:
                        env = cat.compact(ENV); view = [0, 0, 400, 800]
                        record = dict(schema='catalog-workload-baseline-v1', run='synthetic', revision=self.parameters['catalogRevision'],
                            mode='seed' if 'catalogSeed' in self.parameters else 'replay', fixture_sha256=self.transport['sha256'], workload=workload,
                            source_phase=mode, environment=copy.deepcopy(ENV), environment_serialized=env, environment_sha256=cat.digest(env.encode()),
                            viewport=view, viewport_serialized=cat.compact(view), viewport_argument=':'.join(str(float(v)) for v in view))
                        self.baselines[workload] = record
                        baseline_prefix = f"synthetic-{self.parameters['catalogRevision']}-"
                        write(folder/(baseline_prefix+workload+'-baseline.json'), record)
                        if len(self.baselines) == 2:
                            write(folder/(baseline_prefix+'baseline.json'), dict(schema='catalog-baseline-v1', run='synthetic',
                                revision=self.parameters['catalogRevision'], mode=record['mode'], fixture_sha256=self.transport['sha256'],
                                workloads=self.baselines, replay_arguments=dict(catalogEnvironmentSha256=record['environment_sha256'],
                                    catalogTagsViewport=self.baselines['tags']['viewport_argument'], catalogTrashViewport=self.baselines['trash']['viewport_argument'])))
                    write(folder/(prefix+f'opening-{index}.json'), dict(common, generation=index, snapshot=self.snap(workload, base+40000000),
                        stream=self.fence([self.frame(0, base+2000000)], [], 0, 0, phases, False, base+50000000)))
                start = base+100000000; end = start+1000000000
                identity = f'{workload}-{mode}-{index}'
                complete_phase = dict(id=identity, start=clock(start), end=clock(end)); phases.append(complete_phase)
                gestures = []; touches = []
                for g in range(5):
                    events = []; down = start//1000000+g*180
                    for i in range(16):
                        action = 0 if i == 0 else 1 if i == 15 else 2
                        event_ms = down+i*10; ns = event_ms*1000000
                        y = 800*(.8-.6*i/15)
                        events.append(dict(offset_ms=i*10, event_ms=event_ms, action=action, x=200, y=y,
                                           before=clock(ns), after=clock(ns+1), accepted=True))
                        touches.append(dict(seq=touch_cursor+len(touches), clock=clock(ns+1), action=action,
                                            event_ms=event_ms, down_ms=down, source=4098, tool_type=1, x=200, y=y))
                    gestures.append(dict(events=events, requested_ms=150, finger_pixels_per_second=-3200.,
                                         after=dict(self.snap(workload, (down+160)*1000000, (g+1)*10), quiet_draw_ns=50000000, last_draw_ns=(down+110)*1000000)))
                window = dict(common, index=index, generation=index if mode == 'first' else 0, marker='catalog/'+identity,
                    gestures=gestures, before=self.snap(workload, start-1), begin=dict(id=identity, start=clock(start)),
                    after=self.snap(workload, end-1, 50), end=complete_phase, complete=True, wall_ns=1100000000,
                    stream=self.fence([self.frame(frame_cursor, start+1000000)], touches, frame_cursor, touch_cursor,
                                      phases, mode == 'first' or index == 9, end+1000000))
                write(folder/(prefix+f'window-{index}.json'), window)
                frame_cursor += 1; touch_cursor += 80
                trace_name = f'CatalogFrameBenchmark_{method}_iter{index:03d}_2026-10-23-00-00-00.perfetto-trace'
                revision_offset = 40 if self.parameters['catalogRevision'] == cat.REVISIONS[1] else 0
                (folder/trace_name).write_bytes(bytes([10, 2, 64, revision_offset+cell*10+index+1]))
                outputs.append(dict(type='PerfettoTrace', label=f'Trace Iteration {index}', filename=trace_name))
                self.raw_windows[(workload, mode, index)] = folder/(prefix+f'window-{index}.json')
                self.raw_traces[(workload, mode, index)] = folder/trace_name
            write(folder/(prefix+'final-anchor.json'), self.snap(workload, end+2000000, 499 if workload == 'tags' else 999))
            data.append(dict(className=cat.CLASS, name=method, repeatIterations=10, warmupIterations=0,
                             params={}, metrics={'frameDurationCpuMs': {'runs': [12.]*10}}, sampledMetrics={}, profilerOutputs=outputs))
        write(folder/'fixture-benchmarkData.json', dict(context=dict(build=DEVICE_BUILD), benchmarks=data))


def offline(root, eager, lazy):
    """Declare 560 synthetic export receipts; no query process is invoked."""
    root = Path(root)
    def rel(path): return Path(path).relative_to(root).as_posix()
    declaration = dict(schema='catalog-offline-declaration-v1', reviewed_by='HOST-MODEL-ONLY', captures=[], corpus='corpus.json',
                       host=dict(boot_id='synthetic-boot', runtime_id='synthetic-runtime', host_id='synthetic-host'),
                       queries={}, quiet_authority='quiet-authority.txt')
    (root/'quiet-authority.txt').write_text('SYNTHETIC external receipt authority, not host sampling')
    (root/'thresholds.txt').write_text('SYNTHETIC existing threshold interpretation')
    (root/'processor').write_text('SYNTHETIC retained processor bytes, never executable')
    (root/'processor-source.txt').write_text('SYNTHETIC reviewed tool source')
    declaration['processor'] = dict(path='processor', sha256=sha(root/'processor'), version='synthetic', source_version='synthetic',
        source_url='https://example.invalid/synthetic', source='processor-source.txt', source_sha256=sha(root/'processor-source.txt'))
    (root/'wrapper-source.txt').write_text('SYNTHETIC bounded wrapper source, never executed')
    (root/'credential-source.txt').write_text('SYNTHETIC credential handling source, no credential values')
    (root/'origin-source.txt').write_text('SYNTHETIC origin receipt')
    summary = cat.query_module(eager.project)
    for key, sql in summary.QUERIES.items():
        path = root/'queries'/(key+'.sql'); path.parent.mkdir(exist_ok=True); path.write_text(sql+'\n')
        declaration['queries'][key] = rel(path)
    corpus = dict(schema='catalog-corpus-v1', overlay_sha256=cat.digest(cat.canonical(cat.OVERLAY)), revisions=[])
    for number, f in enumerate([eager, lazy]):
        record = f.ns['state']['tests'][0]
        folder = Path(record['benchmark-data'])
        revision = f.parameters['catalogRevision']
        item = dict(revision=revision, run=rel(f.base), adoption=rel(f.adoption), controller_state=rel(f.base/'controller-state.json'),
                    started=record['result_contract']['capture_started'], fixture_origin=f'origin-{number}.json', quiet_receipt=f'quiet-{number}.json')
        declaration['captures'].append(item)
        spec = dict(revision=revision, benchmarks=[rel(folder/'fixture-benchmarkData.json')],
                    windows=[rel(folder/p.name) for p in f.raw_windows.values()], analysis={})
        corpus['revisions'].append(spec)
        intervals = []
        for key, path in f.raw_windows.items():
            w = json.loads(path.read_text()); start = w['begin']['start']['mono_ns']; end = w['end']['end']['mono_ns']
            intervals.append([start, end])
            trace = folder/f.raw_traces[key].name
            directory = root/f'exports-{number}'/(key[0]+'-'+key[1]+'-'+str(key[2])); directory.mkdir(parents=True)
            export = dict(trace_sha256=sha(trace), directory=rel(directory), csv_sha256={}, commands={})
            spec['analysis'][trace.name] = export
            table = dict(frames=[['1', str(start+1000000), '12000000', '1', 'CatalogProfileActivity', '2', str(start+1000000), '10000000']],
                         expected=[['2', str(start+1000000), '10000000']], markers=[[w['marker'], str(start), str(end-start)]],
                         clocks=[[str(start), '3', str(start)], [str(end), '3', str(end)]],
                         sched=[[str(start), str(end-start), '42']], runnable=[[str(start), '0']], loss=[['0']])
            for name, header in cat.HEADERS.items():
                output = directory/(name+'.csv')
                with output.open('w', newline='') as stream:
                    writer = csv.writer(stream); writer.writerow(header); writer.writerows(table[name])
                error = directory/(name+'.stderr'); error.write_bytes(b'')
                receipt_path = directory/(name+'-command.json'); wrapper_path = directory/(name+'-wrapper.json')
                write(wrapper_path, dict(schema='catalog-bounded-export-v1', closed=True, cleanup_complete=True,
                    actual_affinity=[0], cpu=0, source_sha256=sha(root/'wrapper-source.txt'), source='wrapper-source.txt',
                    wall_seconds=1, wall_limit_seconds=60, cpu_seconds=.1, cpu_limit_seconds=30, command_receipt=rel(receipt_path)))
                query = declaration['queries'][name]
                write(receipt_path, dict(schema='catalog-export-command-v1', trace=rel(trace), trace_sha256=sha(trace),
                    processor_sha256=declaration['processor']['sha256'], query_sha256=sha(root/query), stdout=rel(output),
                    stdout_sha256=sha(output), argv=[str(root/'processor'), 'query', '-f', str(root/query), str(trace)], exit=0,
                    stderr=rel(error), started=output.stat().st_mtime-.001, finished=output.stat().st_mtime+.001, wrapper=rel(wrapper_path)))
                export['csv_sha256'][name] = sha(output); export['commands'][name] = rel(receipt_path)
        write(root/item['fixture_origin'], dict(schema='catalog-fixture-origin-v1', fixture_sha256=f.transport['sha256'],
            server_image=cat.SERVER, proxy_image=f.configuration['proxy_image'], owned_server_identity='SYNTHETIC server',
            credential_source='credential-source.txt', credential_source_sha256=sha(root/'credential-source.txt'),
            source_receipt='origin-source.txt', source_receipt_sha256=sha(root/'origin-source.txt'), credentials_retained=False,
            historical_diagnostic_server='1.5.0', cleanup=dict(user_delete_status=204, deleted_auth_status=401)))
        write(root/item['quiet_receipt'], dict(schema='catalog-quiet-receipt-v1', authority_sha256=sha(root/'quiet-authority.txt'),
            revision=revision, configuration_sha256=f.checked['_sha256'], host=declaration['host'], intervals=intervals,
            continuous=True, observer_closed=True, unknown_intervals=[], gaps=[], breaches=[], verdict='PASS',
            thresholds='thresholds.txt', thresholds_sha256=sha(root/'thresholds.txt'), inputs={'origin-source.txt': sha(root/'origin-source.txt')}))
    write(root/'corpus.json', corpus)
    declaration['pins'] = {rel(p): sha(p) for p in root.rglob('*') if p.is_file()}
    declaration['external_pins'] = {name: value for f in [eager, lazy] for name, value in f.ns['state']['tests'][0]['result_contract']['original_files'].items()}
    write(root/'offline-declaration.json', declaration)
    return root/'offline-declaration.json'
