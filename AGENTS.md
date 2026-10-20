# Tokkl for Android

Native Android client for the Tokkl server and for Toggl Track, for Android 12 (API 31) and later. The repo holds a Gradle project. After sign-in, a bottom navigation bar leads to pages that each show their title in a top bar. The Timer page starts, stops and edits the running entry. It lists past entries by day, grouping similar ones as the web app does, or shows them on a week calendar, and it duplicates a listed entry or deletes it with Undo. An entry screen edits every field of a past entry or adds one by hand. Reports has the summary, detailed and weekly reports, with the web app's filters. The Projects and Clients pages create, edit, archive and restore projects and clients on the server, and a project's form manages its tasks. The Tags page creates, renames and deletes tags. Trash is still a placeholder. Settings signs out, which clears the account's data from the phone, and opens a Licences screen. Under the screens sit an offline cache of the account's data and a queue that sends time entry changes made on the phone in order, also in the background when the network returns. [docs/PRD.md](docs/PRD.md) is the source of truth for scope and decisions.

- The server lives in the sibling repo `../tokkl` (github.com/e0ipso/tokkl). Its `docs/PRD.md` section 6 lists the Toggl API v9 and Reports v3 endpoints Tokkl implements, and section 4 has the visual direction this app borrows from.
- Commits follow Conventional Commits, as in tokkl. Ticket IDs (`TKA-NNNN`, tracked with sift in `.ai/sift`) go in a `Refs:` trailer, never in the subject. Commits carry no AI or co-author credit lines.
- `.env` holds `GITHUB_TOKEN` for push and `gh`. It is ignored by git; never print or commit it.

<!-- >>> kenkeep:kk-index >>> -->
You are required to load [.ai/kenkeep/ENTRY.md](.ai/kenkeep/ENTRY.md), the small curated entry catalog for this repo. Enter there and descend using progressive disclosure principles.

> kenkeep navigation: the injected body above is the root index node, the top-level catalog of branches and root-level leaves. Do not expect the whole knowledge base here; descend on demand. Read the root index node, pick one or more branches whose intent and tags match your task (several branches can be relevant), and read those branch `index.md` nodes. Descend further only where the task needs it, opening only the leaves you have confirmed are relevant. Follow each leaf's `relates_to` and `depends_on` cross edges to reach related leaves in other branches. You decide how deep to go per branch.
<!-- <<< kenkeep:kk-index <<< -->
