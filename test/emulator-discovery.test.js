import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import test from 'node:test';

test('emulator discovery preserves authentication and resource ownership', () => {
  const suite = fileURLToPath(new URL('./emulator-discovery.test.py', import.meta.url));
  const result = spawnSync('python3', [suite], { encoding: 'utf8' });
  assert.equal(result.status, 0, result.stderr || result.error?.message);
});
