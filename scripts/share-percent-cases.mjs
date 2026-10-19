// Writes app/src/test/resources/reports/share-percent-cases.tsv, the cases ReportFormatTest checks
// sharePercent against: formatPercent(percent(part, whole)) from the web app in ../tokkl, run in
// Node for a fixed set of part and whole pairs.
//
//   LANG=en_US.UTF-8 node scripts/share-percent-cases.mjs > app/src/test/resources/reports/share-percent-cases.tsv
//
// formatPercent formats in the default locale, so the script refuses to run in any other than en-US.
import { execFileSync } from 'node:child_process';
import { dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const formatTs = fileURLToPath(new URL('../../tokkl/packages/web/src/pages/reports/format.ts', import.meta.url));
const locale = new Intl.NumberFormat().resolvedOptions().locale;
if (locale !== 'en-US') {
  console.error(`The default locale is ${locale}. Run with LANG=en_US.UTF-8.`);
  process.exit(1);
}
const { percent, formatPercent } = await import(formatTs);
const tokklCommit = execFileSync('git', ['-C', dirname(formatTs), 'rev-parse', 'HEAD'], { encoding: 'utf8' }).trim();

// mulberry32, so the same pairs come out on every run.
let seed = 0x7a1e0148;
function random() {
  seed = (seed + 0x6d2b79f5) | 0;
  let t = Math.imul(seed ^ (seed >>> 15), 1 | seed);
  t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
  return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
}
const below = (n) => Math.floor(random() * n);

const pairs = [
  [15300, 600000],
  [0, 0],
  [5, 0],
  [0, 3600],
  [7, 7],
  [1, 3],
  [2, 3],
  [1, 8],
  [29, 200],
];
// Every share of a small whole.
for (let whole = 1; whole <= 30; whole++) {
  for (let part = 0; part <= whole; part++) pairs.push([part, whole]);
}
// Exact halves of the last digit, 0.05% to 99.95%, where toFixed and Intl.NumberFormat can differ.
for (let i = 0; i < 400; i++) {
  const unit = 1 + below(3000);
  pairs.push([unit * (2 * below(1000) + 1), unit * 2000]);
}
// Wholes from a second to about 30 years of seconds, spread evenly in magnitude.
for (let i = 0; i < 700; i++) {
  const whole = Math.floor(Math.exp(random() * Math.log(1e9))) + 1;
  pairs.push([below(whole + 1), whole]);
}

const rows = pairs.map(([part, whole]) => [part, whole, formatPercent(percent(part, whole))]);
const toFixedDiffers = pairs.filter(([part, whole], i) => `${percent(part, whole).toFixed(1)}%` !== rows[i][2]).length;
console.log(`# formatPercent(percent(part, whole)) from tokkl's packages/web/src/pages/reports/format.ts`);
console.log(`# at tokkl commit ${tokklCommit}, in Node ${process.version} (ICU ${process.versions.icu}), locale ${locale}.`);
console.log(`# Written by scripts/share-percent-cases.mjs: ${rows.length} cases, ${toFixedDiffers} of which toFixed(1) rounds otherwise.`);
console.log('# part\twhole\tformatPercent');
for (const row of rows) console.log(row.join('\t'));
