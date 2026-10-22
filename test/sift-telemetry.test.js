import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { chmodSync, existsSync, mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { homedir } from 'node:os';
import { join } from 'node:path';
import { test } from 'node:test';
import { fileURLToPath } from 'node:url';

const script = fileURLToPath(new URL('../scripts/sift_telemetry.py', import.meta.url));
const cache = join(homedir(), '.cache', 'tokkl-telemetry-tests');
mkdirSync(cache, { recursive: true });
function fixture(run) {
  const root = mkdtempSync(join(cache, 'owned-'));
  const ledger = join(root, 'ledger');
  const config = {
    command: ['node', '--test', 'test/example.test.js'], scope: ['host/example'],
    environment: { LANG: 'C' }, toolchain: { node: '22' }, resource_policy: 'host-only',
  };
  const context = { root, run_id: 'run-1', wave: 10, sitting_id: 'tools', ticket_ids: ['TKA-0283'],
    attempt_id: 'attempt-1', check_name: 'host', kind: 'tooling', stage: 'development',
    inputs: ['source'], configuration: config };
  writeFileSync(join(root, 'source'), 'checked bytes');
  const json = (name, value) => { const path = join(root, name); writeFileSync(path, JSON.stringify(value)); return path; };
  const cli = (...args) => {
    const r = spawnSync('python3', ['-B', script, ...args], { encoding: 'utf8', timeout: 10000 });
    return { ...r, value: r.stdout ? JSON.parse(r.stdout) : null };
  };
  const invoke = (command, next = context) => cli(command, '--context', json('context.json', next),
    ...(command === 'fingerprint' ? [] : ['--ledger', ledger]));
  const begin = next => { const r = invoke('begin', next); assert.equal(r.status, 0, r.stderr); return r.value.directory; };
  const log = join(root, 'check.log');
  writeFileSync(log, 'final host output\n');
  const result = { exit: 0, outcome: 'passed', finalized: true, required_evidence_complete: true,
    admission_wait_seconds: 0, logs: [log], counts: { tests: 4, failures: 0, errors: 0, skipped: 0 } };
  const finish = (directory, value = result) => cli('finish', '--directory', directory, '--result', json('result-input.json', value));
  try { run({ root, ledger, context, cli, json, invoke, begin, finish, result }); }
  finally { rmSync(root, { recursive: true, force: true }); }
}

test('receipts preserve attempt identities, counts, waits, resource scope and durable evidence', () => fixture(f => {
  const directory = f.begin();
  const r = f.finish(directory, { ...f.result, admission_wait_seconds: 7.5 });
  assert.equal(r.status, 0, r.stderr);
  assert.equal(r.value.counts.tests, 4);
  assert.equal(r.value.result.admission_wait_seconds, 7.5);
  assert.equal(r.value.resource_after.scope, 'telemetry caller');
  assert.ok(r.value.elapsed_seconds >= 0);
  assert.equal(r.value.artifacts.length, 1);
  assert.equal(JSON.parse(readFileSync(join(directory, 'start.json'))).context.attempt_id, 'attempt-1');
}));

test('cherry-picks, checkout paths and unrelated documentation preserve matching evidence', () => fixture(f => {
  assert.equal(f.finish(f.begin()).status, 0);
  writeFileSync(join(f.root, 'notes.md'), 'documentation-only change');
  const next = { ...f.context, run_id: 'another-run', attempt_id: 'attempt-2' };
  const reused = f.invoke('reuse', next);
  assert.equal(reused.value.reusable, true, reused.stderr);
  const other = join(f.root, 'other-checkout');
  mkdirSync(other);
  writeFileSync(join(other, 'source'), 'checked bytes');
  assert.equal(f.invoke('reuse', { ...next, root: other }).value.reusable, true);
}));

test('source bytes, executable mode, scope and toolchain invalidate reuse', () => fixture(f => {
  assert.equal(f.finish(f.begin()).status, 0);
  for (const configuration of [{ ...f.context.configuration, scope: ['different'] },
    { ...f.context.configuration, toolchain: { node: '24' } }]) {
    assert.equal(f.invoke('reuse', { ...f.context, configuration }).value.reusable, false);
  }
  chmodSync(join(f.root, 'source'), 0o755);
  assert.equal(f.invoke('reuse').value.reusable, false);
  chmodSync(join(f.root, 'source'), 0o644);
  writeFileSync(join(f.root, 'source'), 'changed');
  assert.equal(f.invoke('reuse').value.reusable, false);
}));

test('unfinished, failed and missing artifact checks cannot be reused', () => fixture(f => {
  f.begin();
  assert.equal(f.invoke('reuse').value.reusable, false);
  assert.equal(f.finish(f.begin(), { ...f.result, exit: 1, outcome: 'failed' }).status, 0);
  assert.equal(f.invoke('reuse').value.reusable, false);
  assert.equal(f.finish(f.begin()).status, 0);
  rmSync(f.result.logs[0]);
  assert.equal(f.invoke('reuse').value.reusable, false);
}));

test('changed retained logs or start receipts invalidate a previously passing receipt', () => fixture(f => {
  const directory = f.begin();
  assert.equal(f.finish(directory).status, 0);
  writeFileSync(f.result.logs[0], 'different output');
  assert.equal(f.invoke('reuse').value.reusable, false);
  const start = join(directory, 'start.json');
  writeFileSync(start, readFileSync(start, 'utf8') + ' ');
  assert.match(JSON.stringify(f.invoke('reuse').value.rejected), /start changed/);
}));

test('a check cannot finalize twice and overlapping attempts receive distinct directories', () => fixture(f => {
  const a = f.begin();
  const b = f.begin({ ...f.context, attempt_id: 'attempt-2' });
  assert.notEqual(a, b);
  assert.equal(f.finish(a).status, 0);
  const bytes = readFileSync(join(a, 'result.json'), 'utf8');
  assert.equal(f.finish(a).status, 2);
  assert.equal(readFileSync(join(a, 'result.json'), 'utf8'), bytes);
  assert.equal(f.finish(b).status, 0);
}));

test('unknown wait and interrupted outcome are retained without producing passing evidence', () => fixture(f => {
  const r = f.finish(f.begin(), { ...f.result, outcome: 'interrupted', exit: 143, admission_wait_seconds: null });
  assert.equal(r.status, 0, r.stderr);
  assert.equal(f.invoke('reuse').value.reusable, false);
}));

test('pass refuses changed inputs, missing final counts and incomplete measurements', () => fixture(f => {
  for (const change of [{ exit: 1 }, { finalized: false }, { required_evidence_complete: false },
    { admission_wait_seconds: null }, { counts: { tests: 0, failures: 0, errors: 0, skipped: 0 } },
    { counts: { tests: 2, failures: 1, errors: 0, skipped: 0 } }]) {
    assert.equal(f.finish(f.begin(), { ...f.result, ...change }).status, 2);
  }
  const directory = f.begin();
  writeFileSync(join(f.root, 'source'), 'during-check edit');
  assert.equal(f.finish(directory).status, 2);
}));

test('device identity requires actual digests and API31 development is refused', () => fixture(f => {
  const device = { ...f.context, configuration: { ...f.context.configuration, api: 36, avd: 'tokkl-api36',
    server_image: `ghcr.io/e0ipso/tokkl:1.7.0@sha256:${'a'.repeat(64)}`,
    proxy_image: `haproxy@sha256:${'b'.repeat(64)}` } };
  assert.equal(f.invoke('fingerprint', device).status, 0);
  assert.equal(f.finish(f.begin(device)).status, 2, 'manual device counts are insufficient');
  assert.equal(f.invoke('fingerprint', { ...device, configuration: { ...device.configuration, server_image: 'latest' } }).status, 2);
  const api31 = { ...device, configuration: { ...device.configuration, api: 31, avd: 'tokkl-api31' } };
  assert.equal(f.invoke('fingerprint', api31).status, 2);
  assert.equal(f.invoke('fingerprint', { ...api31, stage: 'compatibility' }).status, 0);
}));

test('actual JUnit parser rejects partial, stale, duplicate and inconsistent output', () => fixture(f => {
  const directory = f.begin();
  const path = join(f.root, 'TEST-example.xml');
  const value = { ...f.result, junit: [path] };
  for (const xml of ['<testsuite tests="1">', '<testsuite tests="0"/>',
    '<testsuite tests="2"><testcase classname="C" name="a"/></testsuite>',
    '<testsuite tests="2"><testcase classname="C" name="a"/><testcase classname="C" name="a"/></testsuite>',
    '<testsuite tests="1" failures="0"><testcase classname="C" name="a"><failure/></testcase></testsuite>']) {
    writeFileSync(path, xml);
    assert.equal(f.finish(directory, value).status, 2);
  }
  writeFileSync(path, '<testsuite tests="1"><testcase classname="C" name="a"/></testsuite>');
  assert.equal(f.finish(directory, value).value.counts.tests, 1);
}));

test('read-only RUNLOG import pairs concurrent tickets and labels superseded and orphan data', () => fixture(f => {
  const archive = join(f.root, 'tracker', 'archive'); mkdirSync(archive, { recursive: true });
  for (const [id, type] of [['TKA-0001', 'feature'], ['TKA-0002', 'dx']]) {
    writeFileSync(join(archive, `${id}.md`), `---\nid: ${id}\nstatus: done\ntype: ${type}\n---\n`);
  }
  const lines = [ ['dispatch', 'TKA-0001', 100, '-'], ['dispatch', 'TKA-0002', 110, '-'],
    ['phase', '-', 115, '-'], ['return', 'TKA-0001', 120, 'done'], ['dispatch', 'TKA-0002', 130, '-'],
    ['return', 'TKA-0002', 140, 'done'], ['return', 'TKA-0099', 150, 'done'] ];
  const log = join(f.root, 'RUNLOG.md');
  const text = lines.map(([event, ticket, epoch, status]) => `| ${event} | ${ticket} | - | UTC | ${epoch} | ${status} |`).join('\n');
  writeFileSync(log, text);
  const r = f.cli('report', '--runlog', log, '--tracker', join(f.root, 'tracker'), '--now', '200',
    '--remaining-development', '3', '--compatibility-hours', '8', '24');
  assert.equal(r.status, 0, r.stderr);
  assert.equal(r.value.attempts.length, 2);
  assert.equal(r.value.unattributed_phase_events, 1);
  assert.equal(r.value.elapsed_throughput[0].distinct_done, 2);
  assert.equal(r.value.issues.length, 2);
  assert.equal(r.value.compatibility_eta.source, 'explicit coordinator planning input, not a measured pass');
  assert.equal(readFileSync(log, 'utf8'), text);
}));

test('explicit inputs and identities are mandatory', () => fixture(f => {
  for (const change of [{ inputs: [] }, { inputs: ['missing'] }, { inputs: ['../escape'] },
    { ticket_ids: undefined }, { attempt_id: '' }]) {
    assert.equal(f.invoke('fingerprint', { ...f.context, ...change }).status, 2);
  }
  assert.equal(existsSync(f.ledger), false);
}));
