---
schema_version: 3
nodes_hash: 'sha256:3d73ca401fd96b7c6e306ab78877bffc37009667897762802185132d6ba1f5ce'
node_count: 87
---
# kenkeep

> kenkeep navigation: the injected body above is the root index node, the top-level catalog of branches and root-level leaves. Do not expect the whole knowledge base here; descend on demand. Read the root index node, pick one or more branches whose intent and tags match your task (several branches can be relevant), and read those branch `index.md` nodes. Descend further only where the task needs it, opening only the leaves you have confirmed are relevant. Follow each leaf's `relates_to` and `depends_on` cross edges to reach related leaves in other branches. You decide how deep to go per branch.

## Branches
- Load [`api-and-security/`](nodes/api-and-security/index.md) for more information on the Tokkl and Toggl APIs, the shared OkHttp client, certificate trust, token storage and request rules; read before adding a request or endpoint, or changing trust, credentials or privacy.
- Load [`build-and-tooling/`](nodes/build-and-tooling/index.md) for more information on the JDK, Android SDK, Gradle build, Android stack, commit hook and agent host limits on this machine; read before building, committing, adding a dependency or running a long job.
- Load [`data-and-offline/`](nodes/data-and-offline/index.md) for more information on account settings and time, the Room cache, the change queue, the running timer's notification and the TypeScript ports; read before changing what the phone stores or sends, or how it reads and writes times.
- Load [`design/`](nodes/design/index.md) for more information on Tokkl's theme, palette, brand rules and shared UI components; read before changing how anything looks or reads to TalkBack.
- Load [`release/`](nodes/release/index.md) for more information on the app ID, versioning, reproducible signed builds, CI jobs and F-Droid metadata; read before touching versions, signing, CI or store text.
- Load [`screens/`](nodes/screens/index.md) for more information on sign-in and sign-out, the navigation host with its bar, rail and window sizes, the Timer page, the timer and entry form, Reports, Trash and the catalog pages; read before changing a screen, its model or navigation.
- Load [`testing/`](nodes/testing/index.md) for more information on instrumented, screenshot and real-server tests on the plain AOSP emulators; read before writing or running any test on a device or emulator.

## Conventions (how we build)
- Open [**Isolate all known failing tests before full-suite validation**](nodes/practice-isolate-all-known-failing-tests-before-full-suite-validation.md) to learn about: Inventory every failure, reproduce it in a focused run, and iterate there before validating the full suite. #testing #ci #agents
