# Local test capacity

Build isolation, memory admission settings, diagnostics and host checks live in
[Kenkeep's process isolation and memory guide](../.ai/kenkeep/nodes/build-and-tooling/host-runtime/map-oom-kill-ends-agent-session.md).

[Drain runs use the isolated scope directly](../.ai/kenkeep/nodes/build-and-tooling/host-runtime/practice-drain-sittings-gate-on-memory-not-a-sitting-cap.md).
The optional `scripts/mem-gate.sh` wrapper and its ignored drain shim share one startup
lock and must not be nested.
