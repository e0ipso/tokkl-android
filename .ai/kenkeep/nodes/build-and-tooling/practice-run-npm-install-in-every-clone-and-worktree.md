---
type: practice
title: Run npm install in every new clone or worktree to turn on the commit hook
description: >-
  The commit-msg hook lives in git-ignored .husky/_, so a clone or worktree that
  skipped npm install commits with no checks.
tags:
  - git
  - commits
  - tooling
kk_schema_version: 3
kk_id: practice-run-npm-install-in-every-clone-and-worktree
kk_derived_from: []
kk_relates_to: []
kk_depends_on: []
kk_confidence: high
---
A husky commit-msg hook runs commitlint with `commitlint.config.js`, but only in a tree where `npm install` or `npm ci` has run. The `prepare` script in `package.json` runs `husky`, which generates `.husky/_` and sets `core.hooksPath=.husky/_` in `.git/config`. git ignores `.husky/_` through the `.gitignore` that husky writes inside it, so a fresh clone has no hook. A new worktree inherits `core.hooksPath` from the shared `.git/config` but has no `.husky/_` of its own. Both commit with no check at all, and nothing warns about it. Run `npm install` in each new clone or worktree before the first commit. commitlint 21 needs Node 22.12 or later.

Drain worktrees get this from `.ai/sift/config/worktree-setup`, which the sift-drain skill runs once in each new worktree. git ignores all of `.ai/sift`, so the hook exists only in the main checkout. When the worktree's `package-lock.json` matches the main checkout's and that install is current, it copies the main checkout's `node_modules` and runs `husky`. Otherwise it runs `npm ci`. It fails when `.husky/_/commit-msg` is still missing. It also copies the git-ignored `.ai/kenkeep/hooks`, without which `kenkeep doctor` fails, and it copies no `.env`. A worktree made any other way still needs `npm install` by hand.

On top of the Conventional Commits rules, two custom rules reject a `TKA-` or `TT-` ticket ID in the header (it belongs in a `Refs:` trailer) and any `Co-authored-by:` line or AI tool credit, such as `Generated with Claude Code` or `Assisted-by: <tool>`.

commitlint's own default ignores would skip every rule for `Revert ...`, `fixup!` and `squash!` subjects and for merges, so those messages could carry a ticket ID or a credit line. `commitlint.config.js` sets `defaultIgnores: false` and supplies an `ignores` function that skips a message only when `@commitlint/is-ignored` (a declared dependency in `package.json`) matches its first line and the message passes both custom rules. A revert or fixup with a ticket ID in the header or a credit line goes through the full rule set and is rejected. Because only the header goes to `isIgnored`, a body line such as `Merge branch y` no longer lets a non-Conventional message skip the format rules. `npm test` (`node --test "test/**/*.test.js"`) checks the rules through `@commitlint/load` and `@commitlint/lint`, and drives the real hook in a throwaway repository.

The hook never sees the message that `git revert` or `git cherry-pick` writes (checked on git 2.43.0 with `GIT_TRACE`), a commit made with `--no-verify`, or one from a tree without `npm install`. The `commitlint` job in `.github/workflows/ci.yml` catches those. It lints the pull request's commits, or the pushed range on `main`, and falls back to `commitlint --last` when a force push removed the old tip. Then it runs `npm test`.
