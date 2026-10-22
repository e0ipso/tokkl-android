// Android's version belongs in Gradle, rather than the npm tooling manifest.
// @semantic-release/git commits this file and the changelog before tagging.
import { readFile, writeFile } from 'node:fs/promises';
import { join } from 'node:path';

function versionCode(version) {
  const match = /^(0|[1-9][0-9]{0,3})\.(0|[1-9][0-9]{0,2})\.(0|[1-9][0-9]{0,2})$/.exec(version);
  if (!match) {
    throw new Error(`Version ${version} must be MAJOR.MINOR.PATCH with MINOR and PATCH below 1000.`);
  }
  const [major, minor, patch] = match.slice(1).map(Number);
  const code = major * 1_000_000 + minor * 1_000 + patch;
  if (code < 1 || code > 2_147_483_647) {
    throw new Error(`Version ${version} gives versionCode ${code}, outside 1..2147483647.`);
  }
  return code;
}

export function verifyRelease(pluginConfig, { lastRelease, nextRelease }) {
  if (!lastRelease.version) {
    throw new Error('Create and push the initial v0.1.0 tag before enabling releases. See README.md, "Automatic releases".');
  }
  versionCode(nextRelease.version);
}

export async function prepare(pluginConfig, { cwd, nextRelease, logger }) {
  const code = versionCode(nextRelease.version);
  const path = join(cwd, 'gradle/version.properties');
  const source = await readFile(path, 'utf8');
  for (const key of ['versionName', 'versionCode']) {
    if ([...source.matchAll(new RegExp(`^${key}=.*$`, 'gm'))].length !== 1) {
      throw new Error(`gradle/version.properties must contain exactly one ${key}= line.`);
    }
  }
  await writeFile(path, source
    .replace(/^versionName=.*$/m, `versionName=${nextRelease.version}`)
    .replace(/^versionCode=.*$/m, `versionCode=${code}`));
  logger.log('Android versionName %s, versionCode %s', nextRelease.version, code);
}
