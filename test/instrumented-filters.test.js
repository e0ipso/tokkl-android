import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { existsSync, mkdirSync, mkdtempSync, readFileSync, readdirSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { test } from 'node:test';
import { fileURLToPath } from 'node:url';

const script = fileURLToPath(new URL('../scripts/instrumented-tests.sh', import.meta.url));
const runnerArg = (name, value) => `-Pandroid.testInstrumentationRunnerArguments.${name}=${value}`;
const prefix = 'com.github.e0ipso.tokkl';

// Run the complete entry point. Any dependency invocation fails harmlessly,
// including `id`, which is the first command used to name the slot files.
// No Gradle, adb, emulator, Docker or real runtime directory can be reached.
function checkPreflight(args, message, environment = {}, reachesStartup = false) {
  const root = mkdtempSync(join(tmpdir(), 'tokkl-filter-preflight-'));
  try {
    const bin = join(root, 'bin');
    const runtime = join(root, 'runtime');
    const calls = join(root, 'calls');
    mkdirSync(bin);
    mkdirSync(runtime);
    for (const tool of ['id', 'docker', 'adb', 'curl', 'jq', 'openssl', 'flock', 'emulator']) {
      writeFileSync(join(bin, tool), '#!/bin/bash\nprintf "%s\\n" "$0" >>"$TOKKL_FILTER_TEST_CALLS"\nexit 86\n', { mode: 0o755 });
    }
    const result = spawnSync('/bin/bash', [script, ...args], {
      encoding: 'utf8',
      timeout: 5000,
      env: {
        ...process.env,
        PATH: `${bin}:${process.env.PATH}`,
        XDG_RUNTIME_DIR: runtime,
        TOKKL_FILTER_TEST_CALLS: calls,
        TOKKL_TEST_SLOTS: '2',
        TOKKL_TEST_BOOT_MEMORY_MB: '9000',
        TOKKL_TEST_THROWAWAY_EMULATOR: '',
        TOKKL_TEST_FRESH_NOTIFICATION_PERMISSION: '0',
        ...environment,
      },
    });
    assert.ifError(result.error);
    if (reachesStartup) {
      assert.equal(result.status, 86, `${result.stdout}\n${result.stderr}`);
      assert.equal(readFileSync(calls, 'utf8'), `${join(bin, 'id')}\n`, 'only the startup sentinel ran');
    } else {
      assert.equal(result.status, 2, `${result.stdout}\n${result.stderr}`);
      assert.match(result.stderr, message);
      assert.equal(existsSync(calls), false, 'preflight invoked an external dependency');
    }
    assert.deepEqual(readdirSync(runtime), [], 'preflight created device or slot files');
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
}

test('rejects the mixed lists that passed with only 27 named-class tests', () => {
  checkPreflight([
    runnerArg('package', `${prefix}.ui.screenshot,${prefix}.navigation`),
    runnerArg('class', `${prefix}.ui.TopBarTest,${prefix}.ui.StickerCardTest,${prefix}.ui.theme.LargestFontScaleTest`),
  ], /cannot combine class= and package= filters/);
});

for (const [classForm, classes] of [
  ['single class', `${prefix}.LauncherIconTest`],
  ['class list', `${prefix}.BackupRefusedTest,${prefix}.LauncherIconTest`],
  ['method', `${prefix}.LauncherIconTest#launcherGetsAnAdaptiveIconForMainActivity`],
]) {
  for (const [packageForm, packages] of [
    ['single package', `${prefix}.capabilities`],
    ['package list', `${prefix}.capabilities,${prefix}.licences`],
  ]) {
    for (const reversed of [false, true]) {
      test(`rejects ${classForm} with ${packageForm}, ${reversed ? 'package' : 'class'} first`, () => {
        const args = [runnerArg('class', classes), runnerArg('package', packages)];
        checkPreflight(reversed ? args.reverse() : args, /cannot combine class= and package= filters/);
      });
    }
  }
}

for (const selector of ['class', 'package']) {
  test(`still refuses a caller testFile beside a ${selector} list`, () => {
    const values = selector === 'class'
      ? `${prefix}.BackupRefusedTest,${prefix}.LauncherIconTest`
      : `${prefix}.capabilities,${prefix}.licences`;
    checkPreflight([
      runnerArg(selector, values), runnerArg('testFile', '/data/local/tmp/custom-tests.txt'),
    ], /cannot go with a class= or package= list/);
  });
}

test('still refuses other comma-separated runner arguments', () => {
  checkPreflight([runnerArg('annotation', 'example.First,example.Second')], /holds a comma/);
});

const permissionTest = `${prefix}.notification.NativeNotificationPermissionTest`;
const pomodoroPermissionTest = `${prefix}.pomodoro.NativePomodoroPermissionTest`;
const allow = `${permissionTest}#allowingTheNativeDialogPostsTheAlreadyRunningTimer`;
const deny = `${permissionTest}#denyingTheNativeDialogKeepsTheTimerAndOpensItsSettings`;
const pomodoroAllow = `${pomodoroPermissionTest}#grantingTheFirstSessionDialogKeepsTheSameStartedSession`;
const pomodoroDeny = `${pomodoroPermissionTest}#denyingThenRecreatingKeepsPromptsAndLaterStartsAskOnlyOnExplicitRetry`;
const freshPermission = { TOKKL_TEST_FRESH_NOTIFICATION_PERMISSION: '1' };
// Both timer and Pomodoro probes now share this refusal diagnostic.
const isolatedPermissionRequired = /requires exactly one supported native notification-permission method/;

for (const args of [
  [],
  [runnerArg('package', `${prefix}.notification`)],
  [runnerArg('package', `${prefix}.pomodoro`)],
  [runnerArg('class', permissionTest)],
  [runnerArg('class', pomodoroPermissionTest)],
]) {
  test(`fresh notification permission refuses a non-isolated selection: ${JSON.stringify(args)}`, () => {
    checkPreflight(args, isolatedPermissionRequired, freshPermission);
  });
}

test('fresh notification permission refuses an invalid switch before startup', () => {
  checkPreflight([], /must be 0 or 1/, { TOKKL_TEST_FRESH_NOTIFICATION_PERMISSION: '2' });
});

for (const method of [allow, deny, pomodoroAllow, pomodoroDeny]) {
  test(`fresh notification permission admits the isolated native probe: ${method}`, () => {
    // Stop at the fake id command, before creating locks or reaching a device.
    checkPreflight([runnerArg('class', method)], null, freshPermission, true);
  });
}

for (const [selection, args] of [
  ['both native responses in one process', [runnerArg('class', `${allow},${deny}`)]],
  ['both Pomodoro responses in one process', [runnerArg('class', `${pomodoroAllow},${pomodoroDeny}`)]],
  ['timer and Pomodoro responses in one process', [runnerArg('class', `${allow},${pomodoroAllow}`)]],
  ['an unknown native method', [runnerArg('class', `${permissionTest}#anotherResponse`)]],
  ['an unknown Pomodoro method', [runnerArg('class', `${pomodoroPermissionTest}#anotherResponse`)]],
  ['an extra argument beside a valid probe', [runnerArg('class', allow), '--stacktrace']],
  ['an extra argument beside the Pomodoro allow probe', [runnerArg('class', pomodoroAllow), '--stacktrace']],
  ['an extra argument beside the Pomodoro deny probe', [runnerArg('class', pomodoroDeny), '--stacktrace']],
]) {
  test(`fresh notification permission refuses ${selection} before startup`, () => {
    checkPreflight(args, isolatedPermissionRequired, freshPermission);
  });
}
