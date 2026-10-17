# Tokkl for Android

Native Android client for the Tokkl server and for Toggl Track. The repo is in the requirements stage; [docs/PRD.md](docs/PRD.md) is the source of truth for scope and decisions.

- The server lives in the sibling repo `../tokkl` (github.com/e0ipso/tokkl). Its `docs/PRD.md` section 6 lists the Toggl API v9 and Reports v3 endpoints Tokkl implements, and section 4 has the visual direction this app borrows from.
- Commits follow Conventional Commits, as in tokkl. Ticket IDs go in a `Refs:` trailer, never in the subject. Commits carry no AI or co-author credit lines.
- `.env` holds `GITHUB_TOKEN` for push and `gh`. It is ignored by git; never print or commit it.
