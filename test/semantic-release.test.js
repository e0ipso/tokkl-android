import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { copyFileSync, mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { test } from 'node:test';
import { fileURLToPath, pathToFileURL } from 'node:url';

import { analyzeCommits } from '@semantic-release/commit-analyzer';
import { generateNotes } from '@semantic-release/release-notes-generator';
import load from '@commitlint/load';
import lint from '@commitlint/lint';
import { prepare, verifyRelease } from '../scripts/semantic-release.js';

const root = fileURLToPath(new URL('..', import.meta.url));
const config = JSON.parse(readFileSync(join(root, '.releaserc.json'), 'utf8'));
const logger = { log() {} };

test('release notes render breaking changes and GitHub links with the configured preset', async () => {
  const [, options] = config.plugins.find(plugin => plugin[0] === '@semantic-release/release-notes-generator');
  const repository = 'https://github.com/e0ipso/tokkl-android';
  const hash = '1234567890abcdef1234567890abcdef12345678';
  const notes = await generateNotes(options, {
    cwd: root,
    options: { repositoryUrl: `${repository}.git` },
    lastRelease: { gitTag: 'v0.1.0' },
    nextRelease: { version: '1.0.0', gitTag: 'v1.0.0' },
    commits: [
      { hash, message: 'feat!: change timer behavior\n\nBREAKING CHANGE: new timer contract' },
      { hash, message: 'fix: correct timer rounding\n\nCloses #42\nRefs: TKA-1234' },
      { hash, message: 'docs: explain timer settings' },
    ],
    logger,
  });
  assert.match(notes, /BREAKING CHANGES/);
  assert.match(notes, /new timer contract/);
  assert.match(notes, /Features/);
  assert.match(notes, /change timer behavior/);
  assert.match(notes, /Bug Fixes/);
  assert.match(notes, /correct timer rounding/);
  assert.ok(notes.includes(`${repository}/compare/v0.1.0...v1.0.0`));
  assert.ok(notes.includes(`${repository}/commit/${hash}`));
  assert.ok(notes.includes(`${repository}/issues/42`));
  assert.doesNotMatch(notes, /TKA-1234|explain timer settings/);
});

test('release rules match Tokkl conventional commits', async () => {
  const [, options] = config.plugins.find(plugin => plugin[0] === '@semantic-release/commit-analyzer');
  for (const [message, expected] of [
    ['feat: add a timer', 'minor'],
    ...['fix', 'perf', 'refactor', 'revert'].map(type => [`${type}: update the timer`, 'patch']),
    ...['docs', 'style', 'test', 'build', 'ci', 'chore'].map(type => [`${type}: update the timer`, null]),
    ['feat!: change timer behavior', 'major'],
    ['fix: change timer behavior\n\nBREAKING CHANGE: new timer contract', 'major'],
    ['feat(no-release): add a timer', null],
    ['feat(no-release)!: change timer behavior', 'major'],
  ]) {
    const actual = await analyzeCommits(options, { cwd: root, commits: [{ hash: 'abc', message }], logger });
    assert.equal(actual, expected, message);
  }
});

test('Android versions must fit the Gradle formula, and need a starting tag', () => {
  assert.throws(() => verifyRelease({}, { lastRelease: {}, nextRelease: { version: '1.0.0' } }), /initial v0.1.0 tag/);
  for (const version of ['0.0.1', '0.1.0', '1.0.0', '2147.483.647']) {
    verifyRelease({}, { lastRelease: { version: '0.1.0' }, nextRelease: { version } });
  }
  for (const version of ['0.0.0', '0.1000.0', '1.0.1000', '01.0.0', '1.0.0-beta.1', '2147.483.648', '9999.0.0']) {
    assert.throws(() => verifyRelease({}, { lastRelease: { version: '0.1.0' }, nextRelease: { version } }), /Version/);
  }
});

test('version updates preserve comments and refuse missing or duplicate values', async t => {
  const cwd = mkdtempSync(join(tmpdir(), 'tokkl-release-version-'));
  t.after(() => rmSync(cwd, { recursive: true, force: true }));
  mkdirSync(join(cwd, 'gradle'));
  const file = join(cwd, 'gradle/version.properties');
  const nextRelease = { version: '2.3.4' };
  writeFileSync(file, '# F-Droid reads these\nversionName=0.1.0\nversionCode=1000\n');
  await prepare({}, { cwd, nextRelease, logger });
  assert.equal(readFileSync(file, 'utf8'), '# F-Droid reads these\nversionName=2.3.4\nversionCode=2003004\n');
  for (const source of ['versionCode=1000\n', 'versionName=0.1.0\n', 'versionName=0.1.0\nversionName=0.2.0\nversionCode=1000\n']) {
    writeFileSync(file, source);
    await assert.rejects(prepare({}, { cwd, nextRelease, logger }), /exactly one/);
    assert.equal(readFileSync(file, 'utf8'), source);
  }
});

test('a local release commits the Gradle version and changelog before tagging', async t => {
  const fixture = mkdtempSync(join(tmpdir(), 'tokkl-release-'));
  t.after(() => rmSync(fixture, { recursive: true, force: true }));
  const cwd = join(fixture, 'work');
  const remote = join(fixture, 'remote.git');
  mkdirSync(join(cwd, 'gradle'), { recursive: true });
  // Do not inherit credentials, CI detection or git config from the host.
  const env = {
    PATH: process.env.PATH,
    HOME: fixture,
    GIT_CONFIG_GLOBAL: '/dev/null',
    GIT_CONFIG_NOSYSTEM: '1',
    GIT_AUTHOR_NAME: 'Test',
    GIT_AUTHOR_EMAIL: 'test@example.com',
    GIT_COMMITTER_NAME: 'Test',
    GIT_COMMITTER_EMAIL: 'test@example.com',
    HUSKY: '0',
  };
  const git = (...args) => execFileSync('git', args, { cwd, env, encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'] }).trim();
  git('init', '--bare', '--initial-branch=main', remote);
  git('init', '--initial-branch=main');
  git('config', 'core.hooksPath', '/dev/null');
  git('remote', 'add', 'origin', remote);
  // Keep the starting version fixed even after production releases bump it.
  writeFileSync(join(cwd, 'gradle/version.properties'), 'versionName=0.1.0\nversionCode=1000\n');
  // Include the tooling manifest to prove it receives no app version.
  copyFileSync(join(root, 'package.json'), join(cwd, 'package.json'));
  git('add', 'gradle/version.properties', 'package.json');
  git('commit', '-m', 'chore: start');
  git('tag', 'v0.1.0');
  git('commit', '--allow-empty', '-m', 'feat: add a timer', '-m', 'Refs: TKA-1234');
  git('push', '--tags', 'origin', 'main');
  const sourceCommit = git('rev-parse', 'HEAD');
  // Run every production plugin except GitHub publishing. All pushes go to
  // the disposable local bare remote; plugin resolution stays in this repo.
  const plugins = config.plugins
    .filter(plugin => plugin[0] !== '@semantic-release/github')
    .map(plugin => typeof plugin === 'string' ? join(root, plugin) : plugin);
  const options = { ...config, plugins, repositoryUrl: pathToFileURL(remote).href, ci: false };
  // semantic-release intercepts process stdout. Run it outside the Node test
  // worker so it cannot intercept the test runner's serialized event stream.
  const runRelease = (extra = {}) => JSON.parse(execFileSync(process.execPath, [
    '--input-type=module', '--eval', `
      import semanticRelease from ${JSON.stringify(pathToFileURL(join(root, 'node_modules/semantic-release/index.js')).href)};
      const result = await semanticRelease(JSON.parse(process.argv[1]), { stdout: process.stderr, stderr: process.stderr });
      process.stdout.write(JSON.stringify(result));
    `, JSON.stringify({ ...options, ...extra }),
  ], { cwd, env, encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'] }));
  const result = runRelease();
  assert.equal(result.nextRelease.version, '0.2.0');
  assert.equal(result.nextRelease.gitTag, 'v0.2.0');
  const releaseCommit = git('rev-parse', 'HEAD');
  assert.notEqual(releaseCommit, sourceCommit);
  assert.equal(git('rev-parse', 'v0.2.0'), releaseCommit);
  assert.equal(git('rev-parse', 'HEAD^'), sourceCommit);
  assert.equal(git('ls-remote', 'origin', 'refs/tags/v0.2.0').split(/\s/)[0], releaseCommit);
  assert.equal(git('status', '--porcelain'), '');
  assert.deepEqual(git('diff-tree', '--no-commit-id', '--name-only', '-r', 'HEAD').split('\n'), ['CHANGELOG.md', 'gradle/version.properties']);
  assert.match(git('show', 'v0.2.0:gradle/version.properties'), /^versionName=0\.2\.0\nversionCode=2000$/m);
  assert.match(git('show', 'v0.2.0:CHANGELOG.md'), /add a timer/);
  assert.doesNotMatch(git('show', 'v0.2.0:CHANGELOG.md'), /TKA-1234/);
  assert.equal(JSON.parse(git('show', 'v0.2.0:package.json')).version, undefined);
  const message = git('log', '-1', '--format=%B');
  assert.equal(message, 'chore(release): 0.2.0 [skip ci]');
  const commitlint = await load({}, { cwd: root });
  const report = await lint(message, commitlint.rules, {
    parserOpts: commitlint.parserPreset?.parserOpts,
    plugins: commitlint.plugins,
    ignores: commitlint.ignores,
    defaultIgnores: commitlint.defaultIgnores,
  });
  assert.deepEqual(report.errors, []);
  assert.equal(runRelease(), false, 'a release commit must not release again');

  git('commit', '--allow-empty', '-m', 'fix: correct timer rounding');
  git('push', 'origin', 'main');
  const beforeDryRun = git('rev-parse', 'HEAD');
  const preview = runRelease({ dryRun: true });
  assert.equal(preview.nextRelease.version, '0.2.1');
  assert.equal(git('rev-parse', 'HEAD'), beforeDryRun);
  assert.equal(git('tag', '--list', 'v0.2.1'), '');
  assert.equal(git('status', '--porcelain'), '');
  assert.match(readFileSync(join(cwd, 'gradle/version.properties'), 'utf8'), /^versionName=0\.2\.0$/m);
});
