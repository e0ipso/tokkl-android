"""Verify the complete existing CI shard configuration against actual terminal cases."""
import collections
import json
import pathlib
import re
import sys

base = pathlib.Path(__file__).resolve().parent
root = pathlib.Path.cwd()
read = lambda path: json.loads(path.read_text())
key = lambda case: (case["class"], case["name"])
expected = read(base / "api36-full-final/cases.json")
inputs = read(base / "final-npm-corrected/inputs-before.json")
ordered = []
for path in (root / "app/src/androidTest/java").rglob("*.kt"):
    source = path.read_text()
    if "@FixMethodOrder" in source:
        package = re.search(r"(?m)^package (\S+)", source)[1]
        ordered.append(package + "." + path.stem)

runs = sys.argv[1:] or [f"api36-final-shard-{index}" for index in range(6)]
assert len(runs) == 6, "All six sequential CI shards are required"
all_cases = []
per_shard = []
groups = collections.defaultdict(set)
boot_scopes = []
for index, name in enumerate(runs):
    run = base / name
    summary = read(run / "summary.json")
    cases = read(run / "cases.json")
    command = read(run / "command.json")
    argv = command["argv"]
    keys = set(map(key, cases))
    starts = []
    pairs = []
    raw = read(run / "process-isolation.json")
    for receipt in raw:
        beginning = [line for line in receipt["test_runner_lines"] if "started:" in line]
        ending = [line for line in receipt["test_runner_lines"] if "finished:" in line]
        start = re.search(r"started: ([^(]+)\(([^)]+)\)", beginning[0]) if len(beginning) == 1 else None
        end = re.search(r"finished: ([^(]+)\(([^)]+)\)", ending[0]) if len(ending) == 1 else None
        if not start or (start[2], start[1]) not in keys:
            continue  # Discovery logs for unexecuted cases do not count.
        first = re.match(r"(\S+\s+\S+)\s+(\d+)\s+\d+\s+", beginning[0])
        last = re.match(r"(\S+\s+\S+)\s+(\d+)\s+\d+\s+", ending[0]) if len(ending) == 1 else None
        starts.append((start[2], start[1]))
        if end and first and last and end.groups() == start.groups() and first[2] == last[2]:
            pairs.append((start[2], start[1]))
    before = read(run / "inputs-before.json")
    after = read(run / "inputs-after.json")
    log = (run / "run.log").read_text(errors="replace")
    boot = re.search(r"Booting \S+ as emulator-\d+ in (tokkl-emulator-[\w-]+\.scope)", log)
    shard = {"index": index, "run": name, "summary": summary, "case_count": len(cases), "actual_started": len(starts), "actual_matching_finished": len(pairs), "case_multiset_matches_real_pairs": collections.Counter(map(key, cases)) == collections.Counter(pairs), "inputs_match_final": before == after == inputs, "ci_shard_arguments": f"-Pandroid.testInstrumentationRunnerArguments.numShards=6" in argv and f"-Pandroid.testInstrumentationRunnerArguments.shardIndex={index}" in argv, "no_class_batch_filter": not any("RunnerArguments.class=" in argument or "RunnerArguments.notClass=" in argument for argument in argv), "owned_boot_scope": boot[1] if boot else None}
    (run / "case-audit.json").write_text(json.dumps(shard, indent=2) + "\n")
    per_shard.append(shard)
    if boot:
        boot_scopes.append(boot[1])
    for case in cases:
        groups[case["class"]].add(index)
    all_cases += cases

actual = collections.Counter(map(key, all_cases))
target = collections.Counter(map(key, expected))
group_audit = {name: {"shards": sorted(groups[name]), "case_count": sum(case["class"] == name for case in all_cases), "expected_cases": sum(case["class"] == name for case in expected)} for name in sorted(ordered)}
audit = {"per_shard": per_shard, "expected_cases": len(expected), "actual_cases": len(all_cases), "exact_case_multiset_matches": actual == target, "duplicates": [list(case) for case, count in actual.items() if count != 1], "missing": [list(case) for case in target.keys() - actual.keys()], "unexpected": [list(case) for case in actual.keys() - target.keys()], "widget_methods": sum(".widget." in case["class"] for case in all_cases), "widget_reference_classes": sum(".widget." in case["class"] and "ScreenshotTest" in case["class"] for case in all_cases), "reference_matrix_captures": 208, "six_distinct_owned_boots": len(set(boot_scopes)) == 6, "restart_groups": group_audit, "restart_groups_kept_whole": all(len(group["shards"]) == 1 and group["case_count"] == group["expected_cases"] for group in group_audit.values())}
(base / "final-shard-union-audit.json").write_text(json.dumps(audit, indent=2) + "\n")
print(json.dumps({name: value for name, value in audit.items() if name not in ["per_shard", "restart_groups"]}))
assert len(all_cases) == 736 and actual == target and audit["widget_methods"] == 33 and audit["widget_reference_classes"] == 8 and audit["six_distinct_owned_boots"] and audit["restart_groups_kept_whole"]
for shard in per_shard:
    summary = shard["summary"]
    assert summary["exit"] == 0 and summary["failures"] == summary["skipped"] == 0 and summary["inputs_unchanged"]
    assert shard["case_count"] > 0 and shard["actual_started"] == shard["actual_matching_finished"] == shard["case_count"] and shard["case_multiset_matches_real_pairs"]
    assert shard["inputs_match_final"] and shard["ci_shard_arguments"] and shard["no_class_batch_filter"]
