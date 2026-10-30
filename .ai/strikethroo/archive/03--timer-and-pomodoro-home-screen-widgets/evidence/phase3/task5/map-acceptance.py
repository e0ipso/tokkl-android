"""Map authorized task-5 scope to exact accepted receipts, keeping downstream scope explicit."""
import json
import pathlib

base = pathlib.Path(__file__).resolve().parent
read = lambda path: json.loads(path.read_text())
guard = read(base / "final-current-input-audit.json")
assert all(guard[name] for name in ["all_final_runs_match_current_inputs", "full_case_union_verified", "all_shards_green_with_actual_receipts", "fresh_host_and_npm_verified"])
assert read(base / "final-shard-union-audit.json")["exact_case_multiset_matches"]
records = []
for index in range(6):
    run = f"api36-final-shard-{index}"
    for case in read(base / run / "cases.json"):
        assert not case["failure"]
        records.append({"selector": case["class"] + "#" + case["name"], "class": case["class"], "method": case["name"], "shard": index, "cases_receipt": run + "/cases.json", "terminal_audit": run + "/terminal-audit.json"})
select = lambda predicate: [record for record in records if predicate(record)]
screens = select(lambda record: ".widget." in record["class"] and "ScreenshotTest" in record["class"])
large = select(lambda record: record["class"].endswith(".WidgetLargeTextTest"))
midnight = select(lambda record: record["class"].endswith(".WidgetMidnightServerTest"))
controls = select(lambda record: record["class"].endswith(".WidgetServerTest") and record["method"] in {"startAndStopWorkOfflineThroughTheNativeControls", "favoritesResizeAndEachNativeTapQueuesAllItsFieldsOffline", "pauseResumeAndStopWhilePausedGoToTheServerFromTheNativeControls", "anOfflinePauseSaysWhyAndKeepsTheTimerWhileStopStillQueues", "todayReportTapOpensReportsOnToday"})
today = select(lambda record: record["class"].endswith(".WidgetNavigationTest") and record["method"] == "todayOpensOnColdStartAndReplacesRetainedWeeklySelection")
pomodoro = select(lambda record: any(record["class"].endswith("." + name) for name in ["PomodoroWidgetServerTest", "PomodoroWidgetClockTest", "PomodoroWidgetRedrawTest", "PomodoroDeniedAlarmWidgetTest", "PomodoroAlarmActivityTest", "PomodoroRingingServiceTest"]) or record["method"] == "aSessionFromAnotherDeviceReachesTheBackgroundedPhoneAndItsNextAndEndSilenceTheRing")
billable = select(lambda record: record["class"].endswith(".WidgetServerTest") and record["method"] == "billableDayClockAddsOnlyStoppedBillableTimeAndTicksNatively")
assert len(screens) == 8 and len(large) == 2 and len(midnight) == len(today) == len(billable) == 1 and len(controls) == 5
assert sum(record["class"].endswith(".PomodoroWidgetServerTest") for record in pomodoro) == 6
host = ["final-host-corrected/host-audit.json", "final-npm-corrected/npm-summary.json", "final-shard-union-audit.json", "final-current-input-audit.json"]
audit7 = {"status": "audit_performed; documentation_match_owned_by_task6_and_parent", "receipt": "README.md#clock-permission-and-documentation-scope", "findings": "PRD N3/built/Battery and old widget knowledge are stale; separate Pomodoro widget node missing at task5 audit.", "task6_edits_performed_by_this_worker": False}
self_validation = {
    "1": {"proof": "Maintained runner builds/installs API36 and signs disposable accounts into pinned Tokkl 1.10.0", "receipts": [f"api36-final-shard-{index}/command.json" for index in range(6)], "workflows": select(lambda record: record["class"].endswith((".WidgetServerTest", ".PomodoroWidgetServerTest")))},
    "2": {"proof": "Both providers, four host sizes, all states, normal/largest text, light/dark, accessibility; 208 comparisons", "cases": screens + large, "review": "visual-review/manifest.json", "copy_audit": "visual-review/reference-copy-audit.json"},
    "3": {"cases": midnight, "valid_red": "midnight-clock-red-isolated/", "focused_green": "api36-acceptance-focused/", "adaptation": "Only account-day event-read Clock advanced; OS/network/alarm time unchanged. Same-cache native base and actual TimerTotals before/after; real RunningCheck and unchanged Room response."},
    "4": {"cases": controls + today, "scope_override": "Ordinary Start/Stop/templates offline; Pause/Resume online under current PRD/completed plan 02."},
    "5": {"cases": pomodoro, "permission_adaptation": "API36 exact-access denial injected into native scheduler; actual native inexact pending intents/receiver effects checked. Existing real ringing/full-screen integration retained."},
    "6": {"receipts": host, "focused": ["api36-discovery-auth-focused/", "api36-acceptance-focused/", "api36-stale-tap-green-focused/", "api36-full-failures-green/", "api36-affected-surrounding/"], "api31": "deferred_to_existing_CI_by_user"},
    "7": audit7,
}
success = {
    "1": {"local_runtime_verified": True, "cases": screens + large + midnight + billable, "api31": "CI_deferred"},
    "2": {"local_runtime_verified": True, "cases": controls + today + midnight, "offline_pause_wording": "superseded_by_current_PRD_and_completed_plan02"},
    "3": {"local_runtime_verified": True, "cases": pomodoro},
    "4": {"local_runtime_verified": True, "receipts": host, "references": "visual-review/manifest.json", "whole_plan_complete": False, "downstream": ["API31_existing_CI", "task6_documentation_updates_and_parent_doc_match"]},
}
(base / "acceptance-map.json").write_text(json.dumps({"self_validation": self_validation, "plan_success_criteria": success}, indent=2) + "\n")
print(json.dumps({"mapped_self_validation_steps": len(self_validation), "mapped_success_criteria": len(success), "widget_screenshot_classes": len(screens), "pomodoro_server_workflows": 6, "whole_plan_documentation_claim": False}))
