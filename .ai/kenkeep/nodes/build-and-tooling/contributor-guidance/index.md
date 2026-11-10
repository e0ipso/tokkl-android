# kenkeep Index: build-and-tooling / contributor-guidance

↑ Parent: [build-and-tooling](../index.md)

> kenkeep navigation: the injected body above is the root index node, the top-level catalog of branches and root-level leaves. Do not expect the whole knowledge base here; descend on demand. Read the root index node, pick one or more branches whose intent and tags match your task (several branches can be relevant), and read those branch `index.md` nodes. Descend further only where the task needs it, opening only the leaves you have confirmed are relevant. Follow each leaf's `relates_to` and `depends_on` cross edges to reach related leaves in other branches. You decide how deep to go per branch.

> This index only orients you; leaves hold the durable guidance. Open at least one relevant leaf before acting.

## Subfolders
_None._

## Conventions (how we build)
- Open [**Run npm install in every new clone or worktree to turn on the commit hook**](practice-run-npm-install-in-every-clone-and-worktree.md) to learn about: The commit-msg hook lives in git-ignored .husky/_, so a clone or worktree that skipped npm install commits with no checks. #git #commits #tooling
- Open [**Document the current state without change history**](practice-document-the-current-state-without-change-history.md) to learn about: Documentation and source comments describe the current state; change history stays in git. #documentation
- Open [**Keep developer guidance in Kenkeep**](practice-keep-developer-guidance-in-kenkeep.md) to learn about: Store build, test and release guidance in Kenkeep and point documentation and tooling at those nodes. #documentation #kenkeep
- Open [**Keep Strikethroo plans and archives out of Git**](practice-keep-strikethroo-plans-and-archives-out-of-git.md) to learn about: Ignore Strikethroo plans and archives so generated test evidence stays out of the repository. #git #strikethroo #artifacts

## Components (what exists)
_None yet._

## By topic

### #documentation
- Open [**Document the current state without change history**](practice-document-the-current-state-without-change-history.md) — Documentation and source comments describe the current state; change history stays in git.
- Open [**Keep developer guidance in Kenkeep**](practice-keep-developer-guidance-in-kenkeep.md) — Store build, test and release guidance in Kenkeep and point documentation and tooling at those nodes.
### #git
- Open [**Run npm install in every new clone or worktree to turn on the commit hook**](practice-run-npm-install-in-every-clone-and-worktree.md) — The commit-msg hook lives in git-ignored .husky/_, so a clone or worktree that skipped npm install commits with no checks.
- Open [**Keep Strikethroo plans and archives out of Git**](practice-keep-strikethroo-plans-and-archives-out-of-git.md) — Ignore Strikethroo plans and archives so generated test evidence stays out of the repository.
### #artifacts
- Open [**Keep Strikethroo plans and archives out of Git**](practice-keep-strikethroo-plans-and-archives-out-of-git.md) — Ignore Strikethroo plans and archives so generated test evidence stays out of the repository.
### #commits
- Open [**Run npm install in every new clone or worktree to turn on the commit hook**](practice-run-npm-install-in-every-clone-and-worktree.md) — The commit-msg hook lives in git-ignored .husky/_, so a clone or worktree that skipped npm install commits with no checks.
### #kenkeep
- Open [**Keep developer guidance in Kenkeep**](practice-keep-developer-guidance-in-kenkeep.md) — Store build, test and release guidance in Kenkeep and point documentation and tooling at those nodes.
### #strikethroo
- Open [**Keep Strikethroo plans and archives out of Git**](practice-keep-strikethroo-plans-and-archives-out-of-git.md) — Ignore Strikethroo plans and archives so generated test evidence stays out of the repository.
### #tooling
- Open [**JDK and Android SDK on this machine, and how agent shells reach them**](../host-runtime/map-jdk-android-sdk-and-agent-shells.md) — Temurin 21 in ~/.local/share/jdk/21, SDK in ~/Android/Sdk. Agents reach java, adb and the SDK tools through wrappers in ~/.local/bin.
- Open [**T3 restarts daily and ends running agent sessions**](../host-runtime/map-t3-daily-restart-stops-agent-sessions.md) — t3-update.timer fires daily with up to 1h delay and restarts t3.service even without an update, which stops every agent session.
- Open [**Opt the Android SDK tools out of usage metrics with ~/.androidrc**](../host-runtime/practice-opt-sdk-tools-out-of-usage-metrics.md) — sdkmanager 23.0 runs Google's android CLI, which sends usage data unless given --no-metrics. A ~/.androidrc holding --no-metrics opts out.