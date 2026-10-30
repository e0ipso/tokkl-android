"""Audit terminal receipts without treating discovery or stale output as execution."""
import collections
import json
import pathlib
import re
import sys

base = pathlib.Path(__file__).resolve().parent
run = base / sys.argv[1]
read = lambda path: json.loads(path.read_text())
summary = read(run / "summary.json")
cases = read(run / "cases.json")
prior = read(base / "api36-full-final/cases.json")
key = lambda case: (case["class"], case["name"])
counts = collections.Counter(case["class"] for case in cases)
widget = [case for case in cases if ".widget." in case["class"]]
references = [case for case in widget if "ScreenshotTest" in case["class"]]
receipts = read(run / "process-isolation.json")
case_keys = set(map(key, cases))
raw_pairs = []
actual_starts = []
for receipt in receipts:
    starts = [line for line in receipt["test_runner_lines"] if "started:" in line]
    ends = [line for line in receipt["test_runner_lines"] if "finished:" in line]
    match = re.search(r"started: ([^(]+)\(([^)]+)\)", starts[0]) if len(starts) == 1 else None
    end = re.search(r"finished: ([^(]+)\(([^)]+)\)", ends[0]) if len(ends) == 1 else None
    start_process = re.match(r"(\S+\s+\S+)\s+(\d+)\s+\d+\s+", starts[0]) if len(starts) == 1 else None
    end_process = re.match(r"(\S+\s+\S+)\s+(\d+)\s+\d+\s+", ends[0]) if len(ends) == 1 else None
    is_valid = bool(match and end and match.groups() == end.groups() and start_process and end_process and start_process[2] == end_process[2])
    if match and start_process and (match[2], match[1]) in case_keys:
        actual_starts.append({"class": match[2], "name": match[1], "pid": start_process[2], "started_at": start_process[1], "has_matching_finish": is_valid, "file": receipt["file"]})
    if is_valid:
        raw_pairs.append({"class": match[2], "name": match[1], "pid": start_process[2], "started_at": start_process[1], "finished_at": end_process[1], "file": receipt["file"]})
processes = [process for process in raw_pairs if key(process) in case_keys]
all_keys = list(map(key, processes))
audit = {
    "terminal_summary": summary,
    "expected_cases": len(prior),
    "actual_cases": len(cases),
    "exact_case_multiset_matches_first_full": collections.Counter(map(key, cases)) == collections.Counter(map(key, prior)),
    "class_counts": dict(sorted(counts.items())),
    "widget_methods": len(widget),
    "widget_reference_classes": len(references),
    "reference_matrix_captures": read(base / "visual-review/review-summary.json")["matrix_captures"],
    "raw_per_case_log_files": len(receipts),
    "raw_matching_log_pairs_including_discovery": len(raw_pairs),
    "actual_junit_case_started_receipts": len(actual_starts),
    "actual_junit_case_matching_started_finished_receipts": len(processes),
    "unexecuted_discovery_pairs_excluded": len(raw_pairs) - len(processes),
    "actual_process_case_multiset_matches_junit": collections.Counter(all_keys) == collections.Counter(map(key, cases)),
    "distinct_process_ids": len({process["pid"] for process in processes}),
    "distinct_process_id_start_time_pairs": len({(process["pid"], process["started_at"]) for process in processes}),
    "process_note": "Raw per-case logs can retain discovery pairs for tests never executed. Actual pairs are restricted to fresh terminal JUnit cases. PIDs may be reused later; unique PID alone is not an execution count. An unfinished JUnit case can have a start without a finish.",
    "nonpassing": [case for case in cases if case["failure"]],
}
(run / "terminal-audit.json").write_text(json.dumps(audit, indent=2) + "\n")
(run / "terminal-processes.json").write_text(json.dumps(processes, indent=2) + "\n")
(run / "terminal-started-processes.json").write_text(json.dumps(actual_starts, indent=2) + "\n")
(run / "raw-process-log-pairs.json").write_text(json.dumps(raw_pairs, indent=2) + "\n")
samples = [json.loads(line) for line in (run / "resource-samples.jsonl").read_text().splitlines()]
emulator_samples = [unit for sample in samples for name, unit in sample["units"].items() if name.startswith("tokkl-emulator-")]
numbers = lambda field: [int(unit[field]) for unit in emulator_samples if unit.get(field, "").isdigit()]
resource = {
    "sample_count": len(samples),
    "maximum_observed_memory_current_bytes": max(numbers("MemoryCurrent"), default=None),
    "maximum_recorded_cumulative_memory_peak_bytes": max(numbers("MemoryPeak"), default=None),
    "maximum_observed_swap_bytes": max(numbers("MemorySwapCurrent"), default=None),
    "memory_limits_bytes": sorted(set(numbers("MemoryMax"))),
    "note": "Sampled owned scope values; unloaded scope statistics are not invented. Terminal XML/exit, not progress sampling, determines acceptance.",
}
(run / "resource-summary.json").write_text(json.dumps(resource, indent=2) + "\n")
print(json.dumps({name: value for name, value in audit.items() if name not in ["class_counts", "nonpassing"]}))
print(json.dumps(resource))
if summary != {"exit": 0, "cases": 736, "failures": 0, "skipped": 0, "inputs_unchanged": True}:
    sys.exit("Terminal summary does not satisfy acceptance")
if not (audit["exact_case_multiset_matches_first_full"] and audit["widget_methods"] == 33 and len(references) == 8 and len(receipts) == 736 and len(actual_starts) == 736 and len(processes) == 736 and audit["actual_process_case_multiset_matches_junit"]):
    sys.exit("Terminal case or process inventory differs from acceptance")
