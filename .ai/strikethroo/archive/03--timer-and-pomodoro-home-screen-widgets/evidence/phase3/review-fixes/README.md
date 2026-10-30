# Parent review fixes

All four Opus advisory findings are addressed in implementation. Native acceptance remains task 5.

- Phase-end state redraw is independent of exact-alarm permission. The live sync process waits once for the corrected phase end; replacement/end cancels the wait, idle schedules nothing. The wait rounds fractional milliseconds up so an event cannot occur before ended=true. A denied exact alarm also schedules one best-effort inexact redraw broadcast for process recovery. It redraws only the matching held phase and never starts audio/a foreground service. The exact-alarm permission warning remains. Android can delay the inexact fallback; no exact delivery guarantee is claimed without access.
- The stale progress percentage/bar is replaced with a phase-colour band and phase words. No periodic widget job is added.
- Billable uses a native Chronometer while the current segment is billable, with stoppedBillableSeconds as its accumulation. Otherwise it remains formatted static text.
- Retired favorites row helpers, their obsolete tests, unused widget_start/pomodoro_widget_open/widget_paused strings and unused progress calculation/resource are removed.

RED evidence: red-deadline-behavior.log and red-deadline.xml ran 3 tests with 3 expected missing-redraw timeouts against a no-deadline stub. The first red-deadline.log attempt did not compile because kotlinx-coroutines-test is not installed; the tests were rewritten with standard coroutine channels and injected waiting, adding no dependencies. red-fractional.log/xml then ran one expected failing boundary regression before rounding was corrected.

Final command, under the OOM-safe scope and flock --close /tmp/tokkl-plan3-gradle.lock: ./gradlew --no-daemon :app:testDebugUnitTest --tests com.github.e0ipso.tokkl.widget.* --tests com.github.e0ipso.tokkl.pomodoro.PomodoroPhaseAlarmTest :app:compileDebugAndroidTestKotlin :app:lintDebug. It exited 0. final-green.log, final-junit/ and final-lint.xml preserve the results. Counts: 70 tests, 0 failures/errors/skips; Android test compilation successful; lint has 0 errors/fatal issues.

Pending device proof includes the native denied-alarm scheduling/cancellation path on API36 using an injected exact-access decision, the independent live phase-end transition, a third ticking Billable clock, changed screenshot references and the plan's real-server workflows. Actual API31 permission behavior is deferred to CI per the user's instruction. The existing CI matrix includes API31 and API36.
