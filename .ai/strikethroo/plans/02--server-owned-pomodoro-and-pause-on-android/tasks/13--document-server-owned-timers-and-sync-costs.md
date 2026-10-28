---
id: 13
group: "documentation"
dependencies: [12]
status: "pending"
created: 2026-10-28
skills:
  - technical-writing
complexity_score: 4
execution_profile: "docs-and-config"
---
# Document server-owned timers and sync costs

## Objective

Update product and agent documentation to the implemented server-owned session/pause behavior and measured Android background limitations.

## Skills Required

Technical writing grounded in the completed implementation and acceptance evidence.

## Acceptance Criteria

- [ ] `docs/PRD.md` updates T1, N1/N3/N4/N7, W2/W5/W7/W9/W10/W12, G3, sections 6.3/7/8.3 and the Built paragraph as specified by this plan. It states online-only session/pause actions, read-only paused fields, server rules/state, capability hiding, the second foreground service and the accepted silent ongoing notification/25-second keep-alive cost.
- [ ] The PRD records actual API 31/API 36 start restrictions and Doze observations from task 12, including any measured delay. Ordinary queued Start/Stop/edit behavior and the kept defaults, alarms, sounds and permission flows remain accurately described.
- [ ] README Timer/Pomodoro bullets explain cross-device sessions/pauses and their requirement for a supporting Tokkl server. The `CLAUDE.md` introduction describes server-owned state and the ongoing sync notification, following any existing link/shared-file arrangement.
- [ ] Update/remove the plan-named alarm, change-queue/handoff, capability, Start/Stop, RunningCheck and real-server knowledge nodes; add durable holder/service documentation and wire it into the relevant indexes. Use live paths and the verified image digest, not stale planning snapshots.
- [ ] Do not manually edit `CHANGELOG.md`; release tooling owns it. No documentation promises offline Pause/Resume or a device-local Pomodoro fallback.
- [ ] Run `npx kenkeep doctor`; expect exit 0 with no new node/schema/link problems. Run `npx kenkeep freshness --verbose`, inspect flags relevant to this plan and reconcile changed nodes; unrelated pre-existing flags are reported accurately rather than rewritten without need.
- [ ] Run `git diff --check` and `rg -n 'device only|device-local|only the phone|offline Pause|no push|no wakeups' docs/PRD.md README.md CLAUDE.md .ai/kenkeep/nodes`. The diff check exits 0; each search hit is reviewed and either removed if superseded or recorded as valid context rather than treated as an automatic failure.

Use your internal Todo tool to track these and keep on track.

## Technical Requirements

The plan's Documentation section is the checklist of required rows/files. Follow the knowledge base's current deterministic editing/indexing conventions and validate them. Only update nodes describing the changed implementation; the generation-time freshness check flagged 37 nodes across unrelated areas as well.

## Input Dependencies

Hard dependency on task 12's final behavior/evidence matrix, measured platform behavior and release contract. No soft dependencies.

## Output Artifacts

Updated PRD, README and agent introduction; reconciled knowledge nodes/indexes; validation output and accurate release-note inputs through Conventional Commits.

## Implementation Notes

<details>
<summary>Documentation reconciliation</summary>

1. Use the final evidence matrix and task 1's verified API contract. Document observed behavior rather than copying the plan's proposed implementation literally.
2. Update every named PRD row and the surrounding request/battery/navigation/capability text. Keep Start/Stop's immediate offline queue separate from the six online session/pause actions. Mention that sessions/pauses are hidden on Toggl Track and old Tokkl.
3. Describe the silent always-on sync service, separate ringing service, clock correction, background start fallback and measured Doze limit. Preserve the four defaults and per-session overrides, exact alarms, sounds, full-screen permissions and running-entry edits.
4. Check whether `CLAUDE.md` and agent guidance share a file or link before editing the intro. Change the canonical file once, preserving repository instruction structure.
5. Open `.ai/kenkeep/ENTRY.md`, descend to the named nodes and follow relevant edges. Update stale local-session/handoff claims, add the holder/service node and regenerate/check indexes through the project's installed kenkeep commands as required by its current workflow. Preserve node metadata and stable identities where possible.
6. Run the listed documentation/knowledge checks, review every relevant freshness flag and record unrelated pre-existing ones. Do not mark nodes fresh merely to clear a warning, and do not hand-edit the changelog.

</details>
