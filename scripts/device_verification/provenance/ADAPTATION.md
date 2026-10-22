TKA-0280: latest composed operational runtime, cache-only ownership

Cause and limits
The actual controller samples the complete cgroup tree through Session. Its watch
job was a recurring Type=oneshot service: the unchanged script normally returns
immediately for a fresh heartbeat. The journal's last normal completion is18ms
after the failed read. An actual oneshot running that script reproduced ENOENT
between real directory enumeration and stat. Historical final watchdog counters
and the precise kernel removal time remain unavailable; no pressure origin is
inferred. Historical API36 is still unfinalized, and historical product failures
are not cleared by this operational correction.

Correction
watchdog_lifetime.py keeps one finite Type=exec service alive, running the SAME
planned-watchdog.py every60s. The existing90s stale-heartbeat rule and destructive
watchdog body remain byte-identical. Service lifetime is the existing planned
fallback duration plus120s for cleanup, with an independent RuntimeMaxSec bound.
The original forced fallback timer command/deadline stays byte-identical and is
still armed before shared-limit mutation. No monitor path is filtered or omitted.
Unexpected service loss still aborts; after deliberately stopping a verified
service, a new Monitor observation also rejects its missing evidence.

The controller delta is exactly one import, one watch command assignment and the
two verify/cancel suffixes from -watch.timer to -watch.service. Every controller
function and the279 cleanup/conditional rollback/fallback order are unchanged.
The watch service is canceled only AFTER every workload's Session finalization,
279 identity-checked bounded cleanup, conditional rollback and limit readback.
There are no Session observations after that normal cancellation boundary.

Safe composition
1. Use the latest post279/remaining controller, not276/277 or another product WIP.
   Source: /home/e0ipso/.cache/sift-w10/final-matrix/post279-remaining-20261022T063232Z.
   Preserve its historical files, markers, once ledger and carried reclaim budget.
   The supplied copied controller still names that historical base and tail plan;
   it is a REVIEW ARTIFACT, not an executable plan for the next matrix.

2. In a NEW directory owned by the final-matrix worker, apply controller-0280.patch
   to its fresh copy of that exact controller. Add runtime-0280/watchdog_lifetime.py
   beside scope_completion.py/controller_cleanup.py. Preserve the existing278
   Session Path(root) normalizer,279 cleanup module, both shims, holder placement,
   parser contract, monitor, duration policy and all other source. If the candidate
   differs from this proven base, do not overwrite it or blindly refresh hashes;
   compose the small delta and recheck the combined semantics with its owner.

3. Preserve the actual BASE helpers planned-watchdog.py/window-rollback.py unchanged.
   Their original bytes match the helper-directory copies. The actual BASE file
   local-reclaim-finite.py already has its base Path configured to its run directory;
   the unused helper-directory copy still names an earlier directory. This is
   owner configuration, not a new reclaim implementation. Use actual-base-helpers
   as the provenance reference, rebase ONLY its base Path to the NEW directory,
   and carry the existing ledger/reservations. Do not replace it blindly with the
   unused helper copy. This assignment performed no reclaim or budget mutation.

4. Root must review the fresh base/project/plan/nonce and existing carried budget.
   Keep all controller functions, final cleanup, watch/fallback commands, original
   and temporary limits, memory gate and finite window_bounds logic intact.
   Development plans must use tokkl-api36/API36 ONLY. No new API31 diagnostics,
   tests, benchmarks or compatibility fixes are authorized during development.
   Keep API31 supported and its criteria explicitly deferred, never passed. After
   development run the full final-tree API31 compatibility cycle, then affected
   API36 regressions after fixes before full release verification. Preserve every
   deferred obligation separately; the copied latest
   controller contains only the previous API36/repro tail, not a new full plan.
   proposed-checks.json contains exact payloads from the live primary plan/CI,
   plus the68-reference API36 screenshot class set. These are proposals only.

5. Before a new lease/window, run the READ-ONLY combined conformance check:

   python3 -B /home/e0ipso/.cache/sift-w10/watchdog-evidence-0280/preflight_0280.py \
     --runtime "$NEW/helpers" --controller "$NEW/helpers/<fresh-controller>.py"

   It checks immutable helper hashes, actual service proofs, all controller
   functions, admission/setup, finally and watch/fallback AST, protected limit/gate/duration inputs,
   the actual BASE protection scripts and reclaim accounting logic. A configured
   controller must use a fresh base; a historical base is rejected. Default
   preflight on runtime-0280 is explicitly review-only. No controller, systemd,
   SDK tool, timer, memory change or workload is executed by preflight.

6. Preserve original owner authorization: no elevated idle waiting; fresh current
   <=6442450944 bytes and host available>=9663676416 before a temporary window;
   fallback active BEFORE mutation; temporary14G/15G/2G only during authorized work;
   owned cleanup before immediate conditional rollback/readback to10G/12G/2G;
   fallback canceled only after safe cleanup/restoration. Keep8192/5000 cgroup
   floors, physical5000,60s settle/serialization,300s wait, all3/250ms resampling,
   pressure/max/OOM/device/unknown evidence aborts, original private TMP/image/filter
   behavior, --no-daemon and the prohibition on --stop. No device/window permission
   is granted by these artifacts. No such window was opened for0280.

Verification
170 operational host checks (156 unchanged prior checks,9 watchdog,5 preflight),
72/72 repository host tests;9 prior actual Session/launcher scope cases;24 latest
279 controller cases;3 positive watchdog-service cases plus the expected failing
oneshot historical reproducer. All five shell inputs passed Bash syntax/ShellCheck.
The persistent case executes two real60s checks with one unchanged cgroup inode;
RuntimeMax loss aborts, and the independent fallback fires after its launcher exits.
No actual Gradle, Android SDK/emulator/device, shared limit, service restart or
other-work termination occurred. Full API36 development, benchmarks,68 API36 screenshots, JVM/Android
lint/assemble/check and reproducibility remain root lease obligations. API31
compatibility is deferred to the final-tree cycle after development, followed by
affected API36 regressions after fixes and full release verification. Earlier
fixture runs predate this policy; no API31 check was repeated after the handoff.
The independent0282 upstream audit remains untouched. Current policy sources are
state.md (Current verification policy), common-constraints.txt and eta-method.md
in /home/e0ipso/workspace/apps/tokkl-android/.ai/sift/drain; their current hashes
are recorded in proposed-checks.json. This artifact grants no device/build lease.

Cleanup evidence is in cleanup-audit.json. Failed-unit status for only the stopped
owned RuntimeMax/prototype fault fixtures was recorded and cleared with exact-unit
reset-failed after verifying MainPID0 and absent cgroups. No service was restarted,
no live event counter was reset, and all diagnostic failure evidence is retained.
