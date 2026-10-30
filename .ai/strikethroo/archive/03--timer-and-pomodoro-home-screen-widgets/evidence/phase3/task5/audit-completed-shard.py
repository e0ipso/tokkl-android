"""Audit one completed CI shard; the six-shard union remains a separate gate."""
import collections
import json
import pathlib
import re
import subprocess
import sys

base = pathlib.Path(__file__).resolve().parent
index = int(sys.argv[1])
run = base / f"api36-final-shard-{index}"
read = lambda path: json.loads(path.read_text())
summary = read(run / "summary.json")
cases = read(run / "cases.json")
key = lambda case: (case["class"], case["name"])
keys = collections.Counter(map(key, cases))
prior = collections.Counter(map(key, read(base / "api36-full-final/cases.json")))
raw = read(run / "process-isolation.json")
starts = []
pairs = []
for receipt in raw:
    first_lines = [line for line in receipt["test_runner_lines"] if "started:" in line]
    last_lines = [line for line in receipt["test_runner_lines"] if "finished:" in line]
    start = re.search(r"started: ([^(]+)\(([^)]+)\)", first_lines[0]) if len(first_lines) == 1 else None
    end = re.search(r"finished: ([^(]+)\(([^)]+)\)", last_lines[0]) if len(last_lines) == 1 else None
    if not start or (start[2], start[1]) not in keys:
        continue  # Exclude discovery-only logs for cases absent from terminal JUnit.
    starts.append((start[2], start[1]))
    first = re.match(r"\S+\s+\S+\s+(\d+)\s+\d+\s+", first_lines[0])
    last = re.match(r"\S+\s+\S+\s+(\d+)\s+\d+\s+", last_lines[0]) if len(last_lines) == 1 else None
    if end and first and last and start.groups() == end.groups() and first[1] == last[1]:
        pairs.append((start[2], start[1]))
before = read(run / "inputs-before.json")
argv = read(run / "command.json")["argv"]
audit = {"shard": index, "summary": summary, "actual_junit_cases": len(cases), "raw_case_logs": len(raw), "actual_started": len(starts), "actual_matching_finished": len(pairs), "case_multiset_matches_real_process_pairs": keys == collections.Counter(pairs), "cases_are_subset_of_complete_first_full": not bool(keys - prior), "no_duplicate_cases": all(count == 1 for count in keys.values()), "inputs_match_final_host_npm": before == read(run / "inputs-after.json") == read(base / "final-host-corrected/inputs-before.json") == read(base / "final-npm-corrected/inputs-before.json"), "exact_ci_arguments": "-Pandroid.testInstrumentationRunnerArguments.numShards=6" in argv and f"-Pandroid.testInstrumentationRunnerArguments.shardIndex={index}" in argv, "no_class_batch_filter": not any("RunnerArguments.class=" in arg or "RunnerArguments.notClass=" in arg for arg in argv)}
(run / "terminal-audit.json").write_text(json.dumps(audit, indent=2) + "\n")
samples = [json.loads(line) for line in (run / "resource-samples.jsonl").read_text().splitlines()]
emulators = [unit for sample in samples for name, unit in sample["units"].items() if name.startswith("tokkl-emulator-")]
numbers = lambda field: [int(unit[field]) for unit in emulators if unit.get(field, "").isdigit()]
resource = {"sample_count": len(samples), "maximum_recorded_emulator_peak_bytes": max(numbers("MemoryPeak")), "maximum_observed_emulator_current_bytes": max(numbers("MemoryCurrent")), "maximum_observed_emulator_swap_bytes": max(numbers("MemorySwapCurrent")), "minimum_sampled_host_available_mb": min(sample["host_available_mb"] for sample in samples), "note": "Measured samples; final scope journal retained separately. No invented unloaded statistics."}
(run / "resource-summary.json").write_text(json.dumps(resource, indent=2) + "\n")
scope = re.search(r"in (tokkl-emulator-[\w-]+\.scope)", (run / "run.log").read_text())[1]
journal = subprocess.run(["journalctl", "--user", "-u", scope, "--no-pager", "-o", "cat", "-n", "3"], capture_output=True, text=True, timeout=10)
(run / "scope-terminal-journal.txt").write_text(journal.stdout)
print(json.dumps(audit))
print(json.dumps(resource))
assert summary == {"exit": 0, "cases": len(cases), "failures": 0, "skipped": 0, "inputs_unchanged": True} and len(cases) > 0
assert len(starts) == len(pairs) == len(cases)
assert all(audit[name] for name in ["case_multiset_matches_real_process_pairs", "cases_are_subset_of_complete_first_full", "no_duplicate_cases", "inputs_match_final_host_npm", "exact_ci_arguments", "no_class_batch_filter"])
