// Checks commitlint.config.js two ways. The first block lints messages through commitlint's
// own API with the options the CLI builds. The second installs the commit-msg hook in a
// throwaway repository and runs real `git commit` and `git merge` against it.
import assert from 'node:assert/strict';
import { execFileSync, spawnSync } from 'node:child_process';
import {
  copyFileSync,
  lstatSync,
  mkdirSync,
  mkdtempSync,
  readFileSync,
  rmSync,
  symlinkSync,
  unlinkSync,
} from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { after, before, describe, test } from 'node:test';
import { fileURLToPath } from 'node:url';

import load from '@commitlint/load';
import lint from '@commitlint/lint';

const repoRoot = fileURLToPath(new URL('..', import.meta.url));

// The options @commitlint/cli passes to lint() for a loaded config. The hook runs
// `commitlint --edit`, which also sets the comment character to git's default.
const lintOptions = config => ({
  parserOpts: { ...config.parserPreset?.parserOpts, commentChar: '#' },
  plugins: config.plugins ?? {},
  ignores: config.ignores ?? [],
  defaultIgnores: config.defaultIgnores !== false,
});

const failedRules = report => report.errors.map(error => error.name).sort();

describe('commitlint.config.js', () => {
  let config;
  const lintMessage = message => lint(message, config.rules, lintOptions(config));

  before(async () => {
    config = await load({}, { cwd: repoRoot });
  });

  test('loads with both custom rules set to error', () => {
    assert.deepEqual(config.rules['no-ticket-id-in-header'], [2, 'always']);
    assert.deepEqual(config.rules['no-ai-generated'], [2, 'always']);
  });

  test('package.json declares every package the config imports (TKA-0083)', () => {
    const source = readFileSync(join(repoRoot, 'commitlint.config.js'), 'utf8');
    const manifest = JSON.parse(readFileSync(join(repoRoot, 'package.json'), 'utf8'));
    const declared = new Set(Object.keys({ ...manifest.dependencies, ...manifest.devDependencies }));
    const specifiers = [...source.matchAll(/^import\s[^'"]*['"]([^'"]+)['"]/gm)].map(match => match[1]);
    const packages = specifiers
      .filter(specifier => !specifier.startsWith('.') && !specifier.startsWith('node:'))
      .map(specifier => specifier.split('/').slice(0, specifier.startsWith('@') ? 2 : 1).join('/'));
    assert.ok(packages.length > 0, `no package import found in: ${specifiers}`);
    for (const name of packages) {
      assert.ok(declared.has(name), `${name} is imported by commitlint.config.js but not in package.json`);
    }
  });

  describe('ordinary commits (TKA-0003)', () => {
    test('rejects a ticket ID in the header', async () => {
      for (const message of ['feat: implement TKA-0001', 'feat(TKA-0001): add x', 'fix: close TT-12']) {
        const report = await lintMessage(message);
        assert.deepEqual(failedRules(report), ['no-ticket-id-in-header'], message);
      }
    });

    test('rejects a co-author trailer, human or AI', async () => {
      for (const message of [
        'feat: add x\n\nCo-Authored-By: Jane <jane@example.com>',
        'feat: add x\n\nCo-authored-by: Claude <noreply@anthropic.com>',
      ]) {
        const report = await lintMessage(message);
        assert.deepEqual(failedRules(report), ['no-ai-generated'], message);
      }
    });

    test('rejects AI credit lines', async () => {
      for (const message of [
        'feat: add x\n\n🤖 Generated with [Claude Code](https://claude.com/claude-code)',
        'feat: add x\n\nMade-with: Cursor',
        'feat: add x\n\nAssisted-by: Claude:claude-opus-5-5',
      ]) {
        const report = await lintMessage(message);
        assert.deepEqual(failedRules(report), ['no-ai-generated'], message);
      }
    });

    test('accepts a Conventional Commit with ticket IDs in a Refs trailer', async () => {
      for (const message of [
        'feat: add x\n\nRefs: TKA-0001',
        'docs: record x\n\nExplain why x matters.\n\nRefs: TKA-0001, TKA-0002',
      ]) {
        const report = await lintMessage(message);
        assert.deepEqual(report.errors, [], message);
        assert.equal(report.valid, true, message);
      }
    });
  });

  describe("messages commitlint ignores by default (TKA-0078)", () => {
    test('rejects a revert or fixup with a ticket ID in the header and a credit trailer', async () => {
      for (const message of [
        'Revert "feat: implement TKA-0001"\n\nCo-Authored-By: Jane <jane@example.com>',
        'fixup! feat: implement TKA-0001\n\nGenerated-by: Claude',
      ]) {
        const report = await lintMessage(message);
        assert.equal(report.valid, false, message);
        const rules = failedRules(report);
        assert.ok(rules.includes('no-ticket-id-in-header'), `${message}: ${rules}`);
        assert.ok(rules.includes('no-ai-generated'), `${message}: ${rules}`);
      }
    });

    test('rejects a revert, squash or merge that fails only one custom check', async () => {
      for (const [message, rule] of [
        ['Revert "feat: implement TKA-0001"', 'no-ticket-id-in-header'],
        ['squash! feat: add x\n\nCo-Authored-By: Jane <jane@example.com>', 'no-ai-generated'],
        ["Merge branch 'TKA-0001-x'", 'no-ticket-id-in-header'],
        ["Merge branch 'topic'\n\n🤖 Generated with [Claude Code](https://claude.com/claude-code)", 'no-ai-generated'],
      ]) {
        const report = await lintMessage(message);
        assert.equal(report.valid, false, message);
        assert.ok(failedRules(report).includes(rule), `${message}: ${failedRules(report)}`);
      }
    });

    test('accepts a clean revert with a Refs trailer', async () => {
      for (const message of [
        'Revert "feat: add x"\n\nRefs: TKA-0001',
        'Revert "feat: add x"\n\nThis reverts commit 0123456789abcdef0123456789abcdef01234567.\n\nRefs: TKA-0001',
      ]) {
        const report = await lintMessage(message);
        assert.deepEqual(report.errors, [], message);
        assert.equal(report.valid, true, message);
      }
    });

    test('accepts clean fixup! and squash! messages', async () => {
      for (const message of ['fixup! feat: add x', 'squash! feat: add x\n\nFold the retry into the first commit.']) {
        const report = await lintMessage(message);
        assert.deepEqual(report.errors, [], message);
        assert.equal(report.valid, true, message);
      }
    });
  });
});

describe('commit-msg hook in a throwaway repository', () => {
  let fixture;

  // Drop every GIT_* variable so a run from inside a git hook cannot reach this checkout,
  // and HUSKY so a HUSKY=0 in the caller's shell cannot turn the hook off.
  const env = {
    ...Object.fromEntries(
      Object.entries(process.env).filter(([name]) => !name.startsWith('GIT_') && name !== 'HUSKY'),
    ),
    GIT_CONFIG_GLOBAL: '/dev/null',
    GIT_CONFIG_NOSYSTEM: '1',
    GIT_AUTHOR_NAME: 'Test',
    GIT_AUTHOR_EMAIL: 'test@example.com',
    GIT_COMMITTER_NAME: 'Test',
    GIT_COMMITTER_EMAIL: 'test@example.com',
  };

  const git = (...args) => execFileSync('git', args, { cwd: fixture, env, encoding: 'utf8' }).trim();
  const tryGit = (...args) => spawnSync('git', args, { cwd: fixture, env, encoding: 'utf8' });
  const head = () => git('rev-parse', 'HEAD');
  // Setup commits skip the hook; only the commit or merge under test runs it.
  const setupCommit = message => git('commit', '-q', '--allow-empty', '--no-verify', '-m', message);

  before(() => {
    fixture = mkdtempSync(join(tmpdir(), 'tokkl-commitlint-'));
    mkdirSync(join(fixture, '.husky'));
    for (const file of ['package.json', 'commitlint.config.js', join('.husky', 'commit-msg')]) {
      copyFileSync(join(repoRoot, file), join(fixture, file));
    }
    symlinkSync(join(repoRoot, 'node_modules'), join(fixture, 'node_modules'));
    git('init', '-q', '-b', 'main');
    // npm install runs this script; it is what turns the hook on.
    execFileSync('npm', ['run', '--silent', 'prepare'], { cwd: fixture, env });
    assert.equal(git('config', 'core.hooksPath'), '.husky/_');
    setupCommit('chore: start');
  });

  after(() => {
    if (!fixture) {
      return;
    }
    // Remove the link first, so the recursive delete cannot touch the real node_modules.
    const link = join(fixture, 'node_modules');
    if (lstatSync(link, { throwIfNoEntry: false })?.isSymbolicLink()) {
      unlinkSync(link);
    }
    rmSync(fixture, { recursive: true, force: true });
  });

  test('rejects a commit with a ticket ID in the subject', () => {
    const start = head();
    const result = tryGit('commit', '--allow-empty', '-m', 'feat: implement TKA-0001');
    assert.notEqual(result.status, 0, result.stdout + result.stderr);
    assert.match(result.stdout + result.stderr, /no-ticket-id-in-header/);
    assert.equal(head(), start);
  });

  test('accepts a commit with a Refs trailer', () => {
    const start = head();
    const result = tryGit('commit', '--allow-empty', '-m', 'feat: add x', '-m', 'Refs: TKA-0001');
    assert.equal(result.status, 0, result.stdout + result.stderr);
    assert.notEqual(head(), start);
  });

  test('runs on merges and rejects one whose header has a ticket ID', () => {
    git('switch', '-q', '-c', 'TKA-0001-x');
    setupCommit('feat: add y');
    git('switch', '-q', 'main');
    const start = head();
    const result = tryGit('merge', '--no-ff', '--no-edit', 'TKA-0001-x');
    // Abort before asserting, so a failure here leaves no merge in progress for the next test.
    tryGit('merge', '--abort');
    assert.notEqual(result.status, 0, result.stdout + result.stderr);
    assert.match(result.stdout + result.stderr, /no-ticket-id-in-header/);
    assert.equal(head(), start);
  });

  test('accepts the messages git merge --no-ff and git merge --log write', () => {
    for (const [branch, flags] of [
      ['topic', ['--no-ff']],
      ['topic-log', ['--no-ff', '--log']],
    ]) {
      git('switch', '-q', '-c', branch, 'main');
      setupCommit('feat: add z');
      setupCommit('fix: handle z');
      git('switch', '-q', 'main');
      const result = tryGit('merge', ...flags, '--no-edit', branch);
      assert.equal(result.status, 0, `${branch}: ${result.stdout}${result.stderr}`);
      const message = git('log', '-1', '--format=%B');
      assert.match(message, new RegExp(`^Merge branch '${branch}'`));
      if (flags.includes('--log')) {
        assert.match(message, new RegExp(`^\\* ${branch}:$`, 'm'));
      }
    }
  });
});
