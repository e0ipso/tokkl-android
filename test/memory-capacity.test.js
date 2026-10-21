import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { chmodSync, existsSync, mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { test } from 'node:test';
import { fileURLToPath } from 'node:url';

const probe = fileURLToPath(new URL('../scripts/memory-capacity.py', import.meta.url));
const gate = fileURLToPath(new URL('../scripts/mem-gate.sh', import.meta.url));
const mib = 1024 * 1024;

function fixture(run) {
  const root = mkdtempSync(join(tmpdir(), 'tokkl-memory-'));
  const proc = join(root, 'proc');
  // Exercise mountinfo's octal escaping, not a hard-coded /sys/fs/cgroup.
  const mount = join(root, 'cgroup space');
  mkdirSync(join(proc, 'self'), { recursive: true });
  mkdirSync(join(mount, 'app.slice', 'work.scope'), { recursive: true });
  writeFileSync(join(proc, 'meminfo'), 'MemAvailable:    12582912 kB\n');
  writeFileSync(join(proc, 'self/cgroup'), '0::/app.slice/work.scope\n');
  writeFileSync(join(proc, 'self/mountinfo'), `37 27 0:31 / ${mount.replaceAll(' ', '\\040')} rw - cgroup2 cgroup2 rw\n`);
  const group = (name, values = {}) => {
    const directory = join(mount, name);
    for (const [key, value] of Object.entries({
      'memory.current': 1024 * mib, 'memory.high': 'max', 'memory.max': 'max',
      'memory.swap.current': 2048 * mib, 'memory.swap.max': 2048 * mib,
      'memory.events': 'high 44\noom_kill 0', 'memory.pressure': 'full avg10=80.00', ...values,
    })) writeFileSync(join(directory, key), `${value}\n`);
  };
  group('app.slice');
  group('app.slice/work.scope');
  const check = (...args) => {
    const result = spawnSync('python3', [probe, '--proc-root', proc, ...args], { encoding: 'utf8' });
    return { ...result, report: result.stdout ? JSON.parse(result.stdout) : null };
  };
  try { run({ root, proc, mount, group, check }); }
  finally { rmSync(root, { recursive: true, force: true }); }
}

test('host RAM does not admit an emulator below ancestor memory.high headroom', () => fixture(({ group, check }) => {
  group('app.slice', { 'memory.current': 8860418048, 'memory.high': 10 * 1024 * mib, 'memory.max': 12 * 1024 * mib });
  const { status, report } = check('--required-mb', '8192');
  assert.equal(status, 75);
  assert.equal(report.host_available_bytes, 12 * 1024 * mib);
  assert.match(report.blockers.join(), /app.slice\/memory.high: headroom=1877000192/);
  assert.match(report.ancestors[1]['memory.events'], /high 44/);
  assert.match(report.ancestors[1]['memory.pressure'], /80.00/);
}));

test('every nested ancestor and both high and max constrain admission', () => fixture(({ group, check }) => {
  group('app.slice/work.scope', { 'memory.high': 10 * 1024 * mib });
  group('app.slice', { 'memory.max': 5 * 1024 * mib });
  const { status, report } = check();
  assert.equal(status, 75);
  assert.equal(report.blockers.length, 1);
  assert.match(report.blockers[0], /app.slice\/memory.max/);
}));

test('unlimited ancestors and missing controller files at hierarchy root admit', () => fixture(({ check }) => {
  const { status, report } = check();
  assert.equal(status, 0);
  assert.equal(report.ancestors.length, 3);
  assert.equal(report.admitted, true);
}));

test('exact headroom admits; one byte less and current above high refuse', () => fixture(({ group, check }) => {
  group('app.slice', { 'memory.current': mib, 'memory.high': 5001 * mib });
  assert.equal(check().status, 0);
  group('app.slice', { 'memory.current': mib + 1, 'memory.high': 5001 * mib });
  assert.equal(check().status, 75);
  group('app.slice', { 'memory.current': 5002 * mib, 'memory.high': 5001 * mib });
  assert.equal(check().report.ancestors[1]['memory.high.headroom'], 0);
}));

test('swap and unlimited cgroups cannot substitute for the physical RAM floor', () => fixture(({ proc, check }) => {
  writeFileSync(join(proc, 'meminfo'), 'MemAvailable:    5119999 kB\n');
  assert.equal(check().status, 75);
  assert.match(check().report.blockers[0], /host MemAvailable/);
  assert.equal(check('--host-mb', '4999').status, 2);
  assert.equal(check('--required-mb', '4999').status, 2);
}));

test('missing or malformed mandatory ancestor counters fail closed', () => fixture(({ mount, check }) => {
  rmSync(join(mount, 'app.slice/memory.high'));
  assert.equal(check().status, 2);
  writeFileSync(join(mount, 'app.slice/memory.high'), 'unknown\n');
  assert.equal(check().status, 2);
}));

test('hidden ancestors and absent cgroup v2 membership fail closed', () => fixture(({ proc, mount, check }) => {
  writeFileSync(join(proc, 'self/mountinfo'), `37 27 0:31 /app.slice ${mount.replaceAll(' ', '\\040')} rw - cgroup2 cgroup2 rw\n`);
  assert.equal(check().status, 2);
  assert.match(check().report.error, /ancestor limits are not visible/);
  writeFileSync(join(proc, 'self/cgroup'), '1:memory:/app.slice\n');
  assert.equal(check().status, 2);
}));

// Run the real wrapper, replacing only its proc reader's input with fixtures.
// No device, systemd scope or memory allocation is involved.
function wrapped(f, args, environment = {}, prefix = []) {
  const bin = join(f.root, 'bin');
  mkdirSync(bin, { recursive: true });
  const python = spawnSync('sh', ['-c', 'command -v python3'], { encoding: 'utf8' }).stdout.trim();
  const quote = value => `'${value.replaceAll("'", "'\\''")}'`;
  writeFileSync(join(bin, 'python3'), `#!/bin/sh\nexec ${quote(python)} "$@" --proc-root ${quote(f.proc)}\n`);
  chmodSync(join(bin, 'python3'), 0o755);
  return spawnSync(prefix[0] ?? gate, prefix.length ? [...prefix.slice(1), gate, ...args] : args,
    { encoding: 'utf8', timeout: 5000, env: {
    ...process.env, PATH: `${bin}:${process.env.PATH}`, XDG_RUNTIME_DIR: f.root,
    MEM_GATE_WAIT_S: '0', MEM_GATE_MIN_MB: '5000', MEM_GATE_CGROUP_MB: '5000',
    MEM_GATE_SETTLE_S: '60', ...environment,
  } });
}

test('wrapper never launches rejected device work and automatically requires 8192 MiB', () => fixture(f => {
  f.group('app.slice', { 'memory.high': 8 * 1024 * mib });
  const marker = join(f.root, 'started');
  const result = wrapped(f, ['sh', '-c', 'touch "$1"', 'instrumented-tests.sh', marker]);
  assert.equal(result.status, 75, result.stderr);
  assert.match(result.stderr, /"required_cgroup_mb": 8192/);
  assert.match(result.stderr, /workload was not started/);
  assert.equal(existsSync(marker), false);
}));

test('wrapper preserves command status and releases the lock after a short command', () => fixture(f => {
  assert.equal(wrapped(f, ['sh', '-c', 'exit 17']).status, 17);
  const marker = join(f.root, 'started');
  assert.equal(wrapped(f, ['sh', '-c', 'echo ran > "$1"', 'sh', marker]).status, 0);
  assert.equal(readFileSync(marker, 'utf8'), 'ran\n');
}));

test('wrapper shares the drain startup lock and refuses a concurrent start', () => fixture(f => {
  const marker = join(f.root, 'started');
  const result = wrapped(f, ['touch', marker], {},
    ['flock', join(f.root, 'tokkl-android-mem-gate.lock')]);
  assert.equal(result.status, 75, result.stderr);
  assert.equal(existsSync(marker), false);
}));

test('wrapper refuses lower floors, shorter startup ramp and malformed settings', () => fixture(f => {
  for (const env of [{ MEM_GATE_MIN_MB: '4999' }, { MEM_GATE_CGROUP_MB: '4999' },
    { MEM_GATE_SETTLE_S: '59' }, { MEM_GATE_WAIT_S: '-1' }, { MEM_GATE_MIN_MB: 'x' }]) {
    assert.equal(wrapped(f, ['true'], env).status, 2);
  }
}));
