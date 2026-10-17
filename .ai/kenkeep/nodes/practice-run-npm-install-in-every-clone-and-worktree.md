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

On top of the Conventional Commits rules, two custom rules reject a `TKA-` or `TT-` ticket ID in the header (it belongs in a `Refs:` trailer) and any `Co-authored-by:` line or AI tool credit, such as `Generated with Claude Code` or `Assisted-by: <tool>`.

commitlint skips every rule for messages that match its default ignores, including `Revert ...`, `fixup!` and `squash!` subjects. Those messages can still carry a ticket ID in the header or a credit line. TKA-0078 tracks the gap.
