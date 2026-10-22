// Only a release created by this invocation should start the APK jobs. A tag
// already on HEAD, or a dry run, must not publish another APK.
import { appendFileSync } from 'node:fs';
import semanticRelease from 'semantic-release';

const dryRun = process.argv.includes('--dry-run');
const result = await semanticRelease({ dryRun });
if (result && !dryRun && process.env.GITHUB_OUTPUT) {
  appendFileSync(process.env.GITHUB_OUTPUT, `tag=${result.nextRelease.gitTag}\n`);
}
