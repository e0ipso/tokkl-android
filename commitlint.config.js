import isIgnored from '@commitlint/is-ignored';

const aiTools = [
  'aider',
  'amp',
  'anthropic',
  'chatgpt',
  'claude',
  'codex',
  'copilot',
  'cursor',
  'devin',
  'gemini',
  'jules',
  'openai',
  'opencode',
  'qwen',
  'windsurf',
].join('|');

// Unicode-aware word boundaries, so "Ampère" or "OpenCoder" don't match.
const tool = `(?<![\\p{L}\\p{N}])(?:${aiTools})(?![\\p{L}\\p{N}])`;

const forbiddenPatterns = [
  // Any co-author, human or not.
  /^Co-authored-by:.*/im,
  // Made-with: Cursor, Assisted-by: Claude:claude-opus-5-5, ...
  new RegExp(`^(?:Assisted-by|Generated-by|Made-with|Signed-off-by):.*${tool}`, 'imu'),
  // Tool-specific trailers: Copilot-Session: ..., Amp-Thread: ...
  new RegExp(`^${tool}-[\\w-]+:`, 'imu'),
  // 🤖 Generated with [Claude Code](https://claude.com/claude-code)
  new RegExp(`(?:Generated|Made) (?:with|by|using)\\b.*${tool}`, 'iu'),
];

// TKA is this repo's prefix. TT is Tokkl's, kept from the copied rule.
const ticketIdInHeader = /\b(?:TKA|TT)-\d+\b/;

const findForbiddenContent = raw => {
  for (const pattern of forbiddenPatterns) {
    const match = raw.match(pattern);
    if (match) {
      return match[0];
    }
  }

  return null;
};

const findTicketId = header => (header ?? '').match(ticketIdInHeader)?.[0] ?? null;

// commitlint skips every rule for a message that matches one of its default ignores: merge
// commits, `Revert "..."`, `fixup!`/`squash!`, a bare version number and a few more. That
// also skipped the two custom rules below, so a revert or fixup could carry a ticket ID or a
// credit trailer. Turn the defaults off and apply them again only to messages that pass both
// custom checks. A message that fails one goes through the full rule set and is rejected.
const passesCustomChecks = message =>
  findForbiddenContent(message) === null && findTicketId(message.trimStart().split(/\r?\n/, 1)[0]) === null;

// The merge defaults match a "Merge branch ..." line anywhere in a message, so `added a thing`
// with that line in its body skipped the format rules. Hand the defaults the first line only.
// The other defaults already look at the start of the message, so they see no change.
const firstLine = message => message.split(/\r?\n/, 1)[0];

const ignoreCleanDefaults = (message = '') => isIgnored(firstLine(message)) && passesCustomChecks(message);

export default {
  extends: ['@commitlint/config-conventional'],
  defaultIgnores: false,
  ignores: [ignoreCleanDefaults],
  plugins: [
    {
      rules: {
        'no-ai-generated': parsed => {
          const match = findForbiddenContent(parsed.raw);
          if (match) {
            return [false, `Commit message contains forbidden AI-generated content: "${match}"`];
          }

          return [true, ''];
        },
        'no-ticket-id-in-header': parsed => {
          const match = findTicketId(parsed.header);
          if (match) {
            return [
              false,
              `Commit header contains ticket ID "${match}". Move it to a "Refs: ${match}" trailer in the body.`,
            ];
          }

          return [true, ''];
        },
      },
    },
  ],
  rules: {
    // Ensure the commit type is lowercase
    'type-case': [2, 'always', 'lower-case'],
    // Ensure the commit type is not empty
    'type-empty': [2, 'never'],
    // Ensure the commit subject is not empty
    'subject-empty': [2, 'never'],
    // Ensure the commit subject doesn't end with a period
    'subject-full-stop': [2, 'never', '.'],
    // Limit the subject line to 72 characters
    'subject-max-length': [2, 'always', 72],
    // Ensure the commit body has a maximum line length
    'body-max-line-length': [2, 'always', 100],
    // Ensure the commit footer has a maximum line length
    'footer-max-line-length': [2, 'always', 100],
    // Ensure valid commit types
    'type-enum': [
      2,
      'always',
      [
        'feat', // A new feature
        'fix', // A bug fix
        'docs', // Documentation only changes
        'style', // Changes that do not affect the meaning of the code (white-space, formatting, missing semi-colons, etc)
        'refactor', // A code change that neither fixes a bug nor adds a feature
        'perf', // A code change that improves performance
        'test', // Adding missing tests or correcting existing tests
        'build', // Changes that affect the build system or external dependencies (example scopes: gulp, broccoli, npm)
        'ci', // Changes to our CI configuration files and scripts (example scopes: Travis, Circle, BrowserStack, SauceLabs)
        'chore', // Other changes that don't modify src or test files
        'revert', // Reverts a previous commit
      ],
    ],
    // Custom rule to block AI-generated strings
    'no-ai-generated': [2, 'always'],
    // Custom rule to keep internal ticket IDs out of release notes and the CHANGELOG
    'no-ticket-id-in-header': [2, 'always'],
  },
  helpUrl: 'https://github.com/conventional-changelog/commitlint/#what-is-commitlint',
};
