import assert from 'node:assert/strict';
import { mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { test } from 'node:test';

import { prepare } from '../scripts/semantic-release.js';

const recipe = readFileSync(new URL('../fdroid/metadata/com.github.e0ipso.tokkl.yml', import.meta.url), 'utf8');
const repository = 'https://github.com/e0ipso/tokkl-android';
const logger = { log() {} };

// Read this draft's simple scalars, not arbitrary YAML. fdroid lint owns schema
// validation. Build-list entries can start with '- key: value'. A duplicate
// field must not hide a conflicting recipe value.
function value(key, source = recipe) {
  const matches = [...source.matchAll(new RegExp(`^ *(?:- +)?${key}: (.+)$`, 'gm'))];
  assert.equal(matches.length, 1, `one ${key} field`);
  return matches[0][1];
}

// Model check_tags' documented first-capture and '.' reuse semantics with
// the expressions from the actual recipe, not expressions copied into tests.
function versionAtTag(files) {
  const fields = value('UpdateCheckData').split('|');
  assert.equal(fields.length, 4);
  const [codePath, codeRegex, namePath, nameRegex] = fields;
  const codeSource = files[codePath];
  const nameSource = files[namePath === '.' ? codePath : namePath];
  if (codeSource === undefined || nameSource === undefined) return null;
  const code = new RegExp(codeRegex).exec(codeSource)?.[1];
  const name = new RegExp(nameRegex).exec(nameSource)?.[1];
  return code && name ? { name, code: Number(code) } : null;
}

function binaryFor(version) {
  return value('Binaries').replaceAll('%v', version.name).replaceAll('%c', String(version.code));
}

test('draft scalar reader handles build-list keys and rejects missing or duplicate fields', () => {
  for (const fields of [
    '  - versionName: 0.2.0\n    versionCode: 2000',
    '  - versionCode: 2000\n    versionName: 0.2.0',
  ]) {
    const source = `License: MIT\nBuilds:\n${fields}\n`;
    assert.equal(value('License', source), 'MIT');
    assert.equal(value('versionName', source), '0.2.0');
    assert.equal(value('versionCode', source), '2000');
    assert.throws(() => value('commit', source), {
      code: 'ERR_ASSERTION', message: /^one commit field\b/, actual: 0, expected: 1,
    });
    for (const duplicate of [
      'versionName: 9.9.9', '    versionName: 9.9.9', '  - versionName: 9.9.9',
    ]) {
      assert.throws(() => value('versionName', `${source}${duplicate}\n`), {
        code: 'ERR_ASSERTION', message: /^one versionName field\b/, actual: 2, expected: 1,
      });
    }
  }
});

test('F-Droid draft identifies the MIT source and the proven release only', () => {
  assert.equal(value('License'), 'MIT');
  assert.equal(value('SourceCode'), repository);
  assert.equal(value('Repo'), `${repository}.git`);
  assert.equal(value('RepoType'), 'git');
  assert.equal(value('versionName'), '0.2.0');
  assert.equal(value('versionCode'), '2000');
  assert.equal(value('commit'), 'v0.2.0');
  assert.equal(value('CurrentVersion'), value('versionName'));
  assert.equal(value('CurrentVersionCode'), value('versionCode'));
  assert.equal(value('subdir'), 'app');
  assert.equal([...recipe.matchAll(/^  - versionName:/gm)].length, 1);
});

test('update checks use repository-root properties and preserve regex field order', () => {
  assert.equal(value('UpdateCheckMode'), 'Tags');
  assert.equal(value('AutoUpdateMode'), 'Version');
  assert.equal(value('UpdateCheckData'), String.raw`gradle/version.properties|versionCode=(\d+)|.|versionName=(\S+)`);
  for (const source of [
    '# Release properties\nversionName=0.2.0\nversionCode=2000\n',
    'versionCode=2000\r\nversionName=0.2.0\r\n',
  ]) {
    assert.deepEqual(versionAtTag({
      'gradle/version.properties': source,
      // A wrong app-relative path must not accidentally satisfy this check.
      'app/gradle/version.properties': 'versionName=9.9.9\nversionCode=9009009\n',
    }), { name: '0.2.0', code: 2000 });
  }
});

test('update regexes skip tags without both stored version fields', () => {
  for (const source of ['', 'versionName=0.2.0\n', 'versionCode=2000\n']) {
    assert.equal(versionAtTag({ 'gradle/version.properties': source }), null);
  }
  assert.equal(versionAtTag({ 'app/gradle/version.properties': 'versionName=0.2.0\nversionCode=2000\n' }), null);
});

test('numeric version codes choose the newest tag independently of tag order', () => {
  const tags = [
    ['v0.9.9', 'versionName=0.9.9\nversionCode=9009\n'],
    ['v0.10.0', 'versionName=0.10.0\nversionCode=10000\n'],
    ['v0.2.0', 'versionName=0.2.0\nversionCode=2000\n'],
  ];
  for (const ordered of [tags, [...tags].reverse()]) {
    const found = ordered.map(([tag, source]) => ({
      tag, ...versionAtTag({ 'gradle/version.properties': source }),
    })).sort((a, b) => b.code - a.code)[0];
    assert.deepEqual(found, { tag: 'v0.10.0', name: '0.10.0', code: 10000 });
    // Tags + Version uses the selected tag directly, including its v prefix.
    assert.equal(value('AutoUpdateMode'), 'Version');
    assert.equal(binaryFor(found), `${repository}/releases/download/v0.10.0/tokkl-android-0.10.0.apk`);
  }
});

test('release preparation writes formula values that the F-Droid regexes extract', async t => {
  const cwd = mkdtempSync(join(tmpdir(), 'tokkl-fdroid-version-'));
  t.after(() => rmSync(cwd, { recursive: true, force: true }));
  mkdirSync(join(cwd, 'gradle'));
  const file = join(cwd, 'gradle/version.properties');
  writeFileSync(file, '# Release properties\nversionName=0.2.0\nversionCode=2000\n');
  for (const [name, code] of [
    ['0.2.0', 2000], ['0.10.0', 10000], ['1.0.0', 1000000],
    ['2.3.4', 2003004], ['2147.483.647', 2147483647],
  ]) {
    await prepare({}, { cwd, nextRelease: { version: name }, logger });
    const actual = versionAtTag({ 'gradle/version.properties': readFileSync(file, 'utf8') });
    assert.deepEqual(actual, { name, code });
    assert.equal(binaryFor(actual), `${repository}/releases/download/v${name}/tokkl-android-${name}.apk`);
  }
});

test('binary mapping and allowed certificate match the public v0.2.0 evidence', () => {
  const version = { name: value('versionName'), code: Number(value('versionCode')) };
  assert.equal(binaryFor(version), `${repository}/releases/download/v0.2.0/tokkl-android-0.2.0.apk`);
  const keys = /^AllowedAPKSigningKeys:\n((?:  - [^\n]+\n)+)/m.exec(recipe);
  assert.ok(keys, 'public signing certificate list');
  assert.equal(keys[1].trim(), '- a136818136320223ea0dcccf4735e62a63badaf69be6b14e2b154f484f4c6196');
});

test('recipe selects the released build toolchain without rewriting dependencies', () => {
  assert.match(recipe, /apt-get install -y openjdk-21-jdk-headless/);
  assert.match(recipe, /update-java-alternatives --set java-1\.21\.0-openjdk-amd64/);
  assert.match(recipe, /^    gradle:\n      - yes$/m);
  assert.match(recipe, /sdkmanager 'platforms;android-37' 'build-tools;36\.0\.0'/);
  assert.doesNotMatch(recipe, /forceversion:|forcevercode:|scanignore:|scandelete:|sed /);
});
