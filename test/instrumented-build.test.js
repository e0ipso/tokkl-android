import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { existsSync, mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { test } from 'node:test';
import { fileURLToPath } from 'node:url';

const script = readFileSync(fileURLToPath(new URL('../scripts/instrumented-tests.sh', import.meta.url)), 'utf8');
const emitter = script.match(/^emit_owned_build\(\) \{[^]*?^\}/m)?.[0];
assert.ok(emitter, 'production owned build emitter');
const prefix = script.slice(script.indexOf('run_tests() {'), script.indexOf("    # A run that stops before its tests"));
assert.ok(prefix.startsWith('run_tests() {'), 'production instrumentation entry');

// Execute the production emitter with a PATH stand-in. No SDK, device or
// launcher entry point runs; every property is visibly host model data.
function fixture(body, properties, status = 0) {
  const root = mkdtempSync(join(tmpdir(), 'tokkl-build-metadata-'));
  try {
    const bin = join(root, 'bin');
    mkdirSync(bin);
    writeFileSync(join(bin, 'adb'), '#!/bin/bash\nprintf "%s\\0" "$@" >"$BUILD_FIXTURE_CALLS"\nprintf "%s" "$BUILD_FIXTURE_PROPERTIES"\nexit "$BUILD_FIXTURE_STATUS"\n', { mode: 0o755 });
    const result = spawnSync('/bin/bash', ['-c', `set -euo pipefail\n${emitter}\n${prefix}\n}\n${body}`], {
      encoding: 'utf8', timeout: 5000,
      env: { ...process.env, PATH: `${bin}:${process.env.PATH}`, BUILD_FIXTURE_PROPERTIES: properties,
        BUILD_FIXTURE_CALLS: join(root, 'calls'), BUILD_FIXTURE_STATUS: String(status) },
    });
    assert.ifError(result.error);
    return { ...result, calls: existsSync(join(root, 'calls')) ? readFileSync(join(root, 'calls'), 'utf8').split('\0').slice(0, -1) : [] };
  } finally {
    rmSync(root, { recursive: true, force: true });
    assert.equal(existsSync(root), false);
  }
}

for (const api of [31, 36]) {
  test(`owned API${api} metadata comes from the selected serial's actual properties`, () => {
    const values = ['HOST-MODEL Android', 'fixture-device', 'HOST-MODEL/quote"/fingerprint',
      'fixture-id', 'fixture model', 'userdebug', String(api), 'REL'];
    const before = BigInt(Date.now()) * 1000000n;
    const result = fixture(`emulator_serial=emulator-5554; AVD=tokkl-api${api}; run_tests`, values.join('\r\n') + '\r\n');
    assert.equal(result.status, 0, result.stderr);
    const lines = result.stdout.trim().split('\n');
    assert.equal(lines.length, 1);
    assert.ok(lines[0].startsWith('TOKKL_DEVICE_BUILD '));
    const row = JSON.parse(lines[0].slice('TOKKL_DEVICE_BUILD '.length));
    assert.equal(row.serial, 'emulator-5554');
    assert.equal(row.avd, `tokkl-api${api}`);
    assert.ok(BigInt(row.observed_at_ns) >= before);
    assert.ok(BigInt(row.observed_at_ns) <= BigInt(Date.now() + 1) * 1000000n);
    assert.deepEqual(row.build, { brand: values[0], device: values[1], fingerprint: values[2],
      id: values[3], model: values[4], type: values[5], version: { sdk: api, codename: 'REL' } });
    assert.deepEqual(result.calls.slice(0, 3), ['-s', 'emulator-5554', 'shell']);
    assert.equal(result.calls.length, 4);
    assert.equal(result.calls[3], 'getprop ro.product.brand; getprop ro.product.device; getprop ro.build.fingerprint; getprop ro.build.id; getprop ro.product.model; getprop ro.build.type; getprop ro.build.version.sdk; getprop ro.build.version.codename');
  });
}

test('attached devices emit no owned-device metadata and invoke no adb', () => {
  const result = fixture("emulator_serial=''; AVD=tokkl-api36; run_tests", 'unused');
  assert.equal(result.status, 0, result.stderr);
  assert.equal(result.stdout, '');
  assert.deepEqual(result.calls, []);
});

test('missing, malformed and oversized build properties fail without a record', () => {
  for (const properties of ['brand\ndevice\n', 'brand\ndevice\nfp\nid\nmodel\ntype\nwrong\nREL\n',
    'brand\ndevice\n\nid\nmodel\ntype\n36\nREL\n', 'x'.repeat(8193)]) {
    const result = fixture('emulator_serial=emulator-5554; AVD=tokkl-api36; run_tests', properties);
    assert.equal(result.status, 1);
    assert.equal(result.stdout, '');
    assert.match(result.stderr, /did not report complete build properties/);
  }
});

test('a failed property request cannot emit a successful record', () => {
  const result = fixture('emulator_serial=emulator-5554; AVD=tokkl-api36; run_tests', 'partial', 23);
  assert.equal(result.status, 23);
  assert.equal(result.stdout, '');
});
