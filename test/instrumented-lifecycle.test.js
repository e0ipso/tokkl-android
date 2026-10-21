import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { existsSync, mkdirSync, mkdtempSync, readFileSync, rmSync, symlinkSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { test } from 'node:test';
import { fileURLToPath } from 'node:url';

const script = readFileSync(fileURLToPath(new URL('../scripts/instrumented-tests.sh', import.meta.url)), 'utf8');
// Run the production lifecycle functions and traps with disposable host files
// and a shell process standing in for qemu. Never source the script's entry
// point, which would acquire real slots or invoke device/Gradle dependencies.
const functions = [
  'slot_file', 'describe_run', 'report_emulator_exit', 'stop_emulator',
  'emulator_scope_stopped', 'remove_run_directory', 'cleanup', 'boot_emulator',
  'remove_leftover_runs',
].map(name => {
  const match = script.match(new RegExp(`^${name}\\(\\) \\{[^\\n]*\\}$`, 'm'))
    ?? script.match(new RegExp(`^${name}\\(\\) \\{[^]*?^\\}`, 'm'));
  assert.ok(match, `production function ${name}`);
  return match[0];
}).join('\n');
const traps = script.match(/^trap cleanup EXIT\ntrap .* INT\ntrap .* TERM$/m)?.[0];
assert.ok(traps, 'production cleanup and signal traps');

function fixture(run) {
  const root = mkdtempSync(join(tmpdir(), 'tokkl-emulator-lifecycle-'));
  const bin = join(root, 'bin');
  const runs = join(root, 'runs');
  const owned = join(runs, 'owned');
  const baseline = join(root, 'baseline.avd');
  const concurrent = join(runs, 'concurrent');
  for (const dir of [bin, owned, baseline, concurrent]) mkdirSync(dir, { recursive: true });
  writeFileSync(join(baseline, 'userdata.img'), 'saved AVD data');
  writeFileSync(join(baseline, 'unattributed-overlay'), 'baseline temporary data');
  writeFileSync(join(concurrent, 'overlay'), 'live concurrent data');
  writeFileSync(join(root, 'other-slot'), `PID other, work directory ${concurrent}, server other\n`);
  const tool = (name, body) => writeFileSync(join(bin, name), `#!/bin/bash\nset -eu\n${body}\n`, { mode: 0o755 });
  tool('emulator', `
test "$ANDROID_TMP" = "$FIXTURE_ROOT/runs/owned/emulator-tmp"
test -f "$FIXTURE_ROOT/runs/owned/emulator.scope"
grep -qF "temporary emulator data $ANDROID_TMP" "$FIXTURE_ROOT/lock-slot-1.lock"
printf '%s\\n' "$ANDROID_TMP" "\${XDG_RUNTIME_DIR:-}" "$*" >"$FIXTURE_ROOT/launch"
stat -c %a "$ANDROID_TMP" >"$FIXTURE_ROOT/temp-mode"
touch "$ANDROID_TMP/overlay.qcow2"
ln -s "$FIXTURE_ROOT/baseline.avd" "$ANDROID_TMP/baseline-link"
echo $$ >"$FIXTURE_ROOT/emulator.pid"
trap 'test -f "$ANDROID_TMP/overlay.qcow2"; echo stopped >"$FIXTURE_ROOT/stopped"; exit 0' TERM
if [ "\${STARTUP_FAILURE:-0}" = 1 ]; then exit 27; fi
while :; do sleep 0.02; done
`);
  tool('systemd-run', `
printf '%s\\n' "$@" >"$FIXTURE_ROOT/scope-args"
while [ "$1" != -- ]; do shift; done
shift
exec "$@"
`);
  tool('systemctl', `
case "$*" in
  *--property=Version*) test "$USE_SCOPE" = 1; echo fixture ;;
  *whoami*) echo fixture-parent.scope ;;
  *stop*) test "\${STOP_FAILURE:-0}" = 0 ;;
  *--property=ActiveState*)
    test "\${SCOPE_STATE:-inactive}" != query-error
    echo "\${SCOPE_STATE:-inactive}" ;;
  *) exit 88 ;;
esac
`);
  // Functions below replace all device/server dependencies. PATH sentinels
  // make accidental calls to real binaries fail even from child processes.
  for (const name of ['adb', 'docker', 'gradle', 'java']) tool(name, 'exit 88');
  const setup = `
set -euo pipefail
${functions}
work_dir="$FIXTURE_ROOT/runs/owned"
runs_dir="$FIXTURE_ROOT/runs"
lock_prefix="$FIXTURE_ROOT/lock"
slot=1; slot_fd=''; run_started=fixture; run_testing=''; container=''; proxy=''
emulator_pid=''; emulator_serial=''; emulator_unit=''; emulator_tmp=''
adb_marker="$FIXTURE_ROOT/adb-marker"
AVD=fixture-avd; EMULATOR_MEMORY_MAX=6G
exec {slot_fd}<>"$FIXTURE_ROOT/lock-slot-1.lock"
exec {device_fd}<>"$FIXTURE_ROOT/device.lock"
exec {boot_fd}<>"$FIXTURE_ROOT/boot.lock"
boot_lock="$FIXTURE_ROOT/boot.lock"
random_hex() { echo abcd; }
wait_for_memory() { :; }
avd_api_level() { echo "$FIXTURE_API"; }
owned_serials() { :; }
read_device() { device_avd=$AVD; }
report_emulator_log() { :; }
remove_device_files() { :; }
remove_user_ca() { :; }
held_slot_files() { echo "$FIXTURE_ROOT/other-slot"; }
docker() { test "$*" = "ps --all --format {{.Names}}"; }
adb() {
  case "$*" in
    devices) return ;;
    *'emu kill') kill -TERM "$(cat "$FIXTURE_ROOT/emulator.pid")"; return ;;
    *sys.boot_completed*)
      for ((i=0; i<100; i++)); do
        if [ -f "$FIXTURE_ROOT/emulator.pid" ]; then break; fi
        sleep 0.01
      done
      test -f "$FIXTURE_ROOT/emulator.pid"
      if [ "\${STARTUP_FAILURE:-0}" = 1 ]; then
        while kill -0 "$(cat "$FIXTURE_ROOT/emulator.pid")" 2>/dev/null; do sleep 0.01; done
        echo 0
      else echo 1; fi ;;
    *ro.build.version.sdk*) echo "$FIXTURE_API" ;;
    *shell*) return ;;
    *) return 88 ;;
  esac
}
`;
  const execute = (body, environment = {}) => spawnSync('/bin/bash', ['-c', `${setup}\n${body}`], {
    encoding: 'utf8', timeout: 10000,
    env: {
      ...process.env, PATH: `${bin}:${process.env.PATH}`, FIXTURE_ROOT: root,
      ANDROID_TMP: baseline, XDG_RUNTIME_DIR: baseline,
      USE_SCOPE: '1', SLOTS: '2', FIXTURE_API: '36', ...environment,
    },
  });
  try {
    run({ root, owned, baseline, concurrent, execute });
    assert.equal(readFileSync(join(baseline, 'userdata.img'), 'utf8'), 'saved AVD data');
    assert.equal(readFileSync(join(baseline, 'unattributed-overlay'), 'utf8'), 'baseline temporary data');
    assert.equal(readFileSync(join(concurrent, 'overlay'), 'utf8'), 'live concurrent data');
  } finally {
    // If a regression made Bash time out, kill only this fixture's fake child.
    const pidFile = join(root, 'emulator.pid');
    if (existsSync(pidFile) && !existsSync(join(root, 'stopped'))) {
      const pid = Number(readFileSync(pidFile, 'utf8'));
      try { process.kill(pid, 'SIGTERM'); } catch (error) { if (error.code !== 'ESRCH') throw error; }
    }
    rmSync(root, { recursive: true, force: true });
  }
}

function resultIs(result, status) {
  assert.ifError(result.error);
  assert.equal(result.status, status, `${result.stdout}\n${result.stderr}`);
}

// A reaped fixture child supplies a dead PID without guessing a high number
// that could belong to another process. PID reuse is conservatively retained.
function deadPid() {
  const child = spawnSync('/bin/true');
  assert.ifError(child.error);
  assert.equal(child.status, 0);
  return child.pid;
}

for (const useScope of ['0', '1']) {
  test(`owned overlays are allocated before launch and removed after shutdown, scope=${useScope}`, () => fixture(f => {
    const result = f.execute(`${traps}\nboot_emulator`, { USE_SCOPE: useScope, SLOTS: useScope === '1' ? '2' : '1', FIXTURE_API: useScope === '1' ? '36' : '31' });
    resultIs(result, 0);
    assert.equal(existsSync(f.owned), false);
    assert.equal(readFileSync(join(f.root, 'stopped'), 'utf8'), 'stopped\n');
    assert.equal(readFileSync(join(f.root, 'temp-mode'), 'utf8'), '700\n');
    const [temp, runtime, args] = readFileSync(join(f.root, 'launch'), 'utf8').trim().split('\n');
    assert.equal(temp, join(f.owned, 'emulator-tmp'));
    assert.equal(runtime, useScope === '1' ? join(f.owned, 'runtime') : f.baseline);
    assert.match(args, /-memory 2560 -cores 4/);
    assert.match(args, /-no-window -no-audio -no-boot-anim -no-snapshot/);
    assert.match(args, /-accel on/);
    assert.match(args, useScope === '1' ? /-gpu lavapipe.*-read-only/ : /-gpu swiftshader_indirect/);
    if (useScope === '1') {
      const scope = readFileSync(join(f.root, 'scope-args'), 'utf8');
      for (const guard of ['OOMPolicy=continue', 'MemoryMax=6G', 'MemorySwapMax=0', 'PartOf=fixture-parent.scope']) assert.ok(scope.includes(guard), guard);
    }
  }));

  test(`startup failure releases owned overlays, scope=${useScope}`, () => fixture(f => {
    resultIs(f.execute(`${traps}\nboot_emulator`, { USE_SCOPE: useScope, STARTUP_FAILURE: '1' }), 1);
    assert.equal(existsSync(f.owned), false);
    assert.equal(existsSync(join(f.root, 'launch')), true, 'the fake emulator allocated temporary data');
  }));

  for (const [signal, status] of [['INT', 130], ['TERM', 143]]) {
    test(`${signal} stops the owned emulator before deletion, scope=${useScope}`, () => fixture(f => {
      resultIs(f.execute(`${traps}\nboot_emulator\nkill -${signal} $$`, { USE_SCOPE: useScope }), status);
      assert.equal(existsSync(f.owned), false);
      assert.equal(existsSync(join(f.root, 'stopped')), true);
    }));
  }
}

test('a failed run keeps its original exit code after cleanup', () => fixture(f => {
  resultIs(f.execute(`${traps}\nboot_emulator\nexit 42`), 42);
  assert.equal(existsSync(f.owned), false);
  assert.equal(existsSync(join(f.root, 'stopped')), true);
}));

test('uncertain scope shutdown fails and retains overlays and provenance', () => fixture(f => {
  const result = f.execute(`${traps}\nboot_emulator`, { STOP_FAILURE: '1', SCOPE_STATE: 'active' });
  resultIs(result, 1);
  assert.match(result.stderr, /could not confirm.*stopped/);
  assert.equal(existsSync(join(f.owned, 'emulator-tmp/overlay.qcow2')), true);
  assert.equal(existsSync(join(f.owned, 'emulator-stopped')), false);
  assert.equal(readFileSync(join(f.owned, 'emulator.scope'), 'utf8'), 'tokkl-emulator-5554-abcd.scope\n');
}));

for (const state of ['inactive', 'active', 'failed', 'query-error', 'no-scope']) {
  test(`orphan recovery respects scope state ${state} and the concurrent slot`, () => fixture(f => {
    const orphan = join(f.root, 'runs', 'orphan');
    mkdirSync(join(orphan, 'emulator-tmp'), { recursive: true });
    writeFileSync(join(orphan, 'emulator.scope'), state === 'no-scope' ? '' : 'tokkl-emulator-5556-dead.scope\n');
    writeFileSync(join(orphan, 'emulator.pid'), `${deadPid()}\n`);
    writeFileSync(join(orphan, 'emulator-tmp/overlay.qcow2'), 'orphan data');
    symlinkSync(f.baseline, join(orphan, 'emulator-tmp/baseline-link'));
    resultIs(f.execute('remove_leftover_runs', { SCOPE_STATE: state }), 0);
    assert.equal(existsSync(orphan), state !== 'inactive');
    assert.equal(existsSync(f.owned), true, 'current run is excluded');
    assert.equal(existsSync(f.concurrent), true, 'held slot is excluded');
  }));
}

for (const ownership of ['live-launcher', 'launch-pending', 'missing-pid', 'invalid-pid']) {
  test(`an inactive scope cannot authorize deletion with ${ownership}`, () => fixture(f => {
    const orphan = join(f.root, 'runs', 'orphan');
    mkdirSync(join(orphan, 'emulator-tmp'), { recursive: true });
    writeFileSync(join(orphan, 'emulator.scope'), 'tokkl-emulator-5556-dead.scope\n');
    if (ownership !== 'missing-pid') writeFileSync(join(orphan, 'emulator.pid'), ownership === 'live-launcher' ? `${process.pid}\n` : ownership === 'invalid-pid' ? 'invalid\n' : `${deadPid()}\n`);
    if (ownership === 'launch-pending') writeFileSync(join(orphan, 'emulator-launching'), '');
    resultIs(f.execute('remove_leftover_runs'), 0);
    assert.equal(existsSync(join(orphan, 'emulator-tmp')), true);
  }));
}

test('a signal before launcher PID assignment retains the allocation', () => fixture(f => {
  mkdirSync(join(f.owned, 'emulator-tmp'));
  writeFileSync(join(f.owned, 'emulator.scope'), 'tokkl-emulator-5554-abcd.scope\n');
  writeFileSync(join(f.owned, 'emulator-launching'), '');
  const result = f.execute(`${traps}\nemulator_unit=tokkl-emulator-5554-abcd.scope\nkill -TERM $$`);
  resultIs(result, 143);
  assert.match(result.stderr, /launch ownership is incomplete/);
  assert.equal(existsSync(join(f.owned, 'emulator-tmp')), true);
  assert.equal(existsSync(join(f.owned, 'emulator-stopped')), false);
}));
