"""Compare current runtime inputs with every accepted final verification run."""
import hashlib
import json
import pathlib
import time

root = pathlib.Path.cwd()
base = pathlib.Path(__file__).resolve().parent
files = []
for folder in ["app/src", "gradle", "scripts", "test"]:
    files += list((root / folder).rglob("*"))
files += [root / name for name in ["build.gradle.kts", "settings.gradle.kts", "gradle.properties", "gradlew", "app/build.gradle.kts", "package.json", "package-lock.json"]]
current = {str(path.relative_to(root)): hashlib.sha256(path.read_bytes()).hexdigest() for path in sorted(set(files)) if path.is_file()}
read = lambda path: json.loads(path.read_text())
runs = ["final-host-corrected", "final-npm-corrected"] + [f"api36-final-shard-{index}" for index in range(6)]
comparisons = []
for name in runs:
    before = read(base / name / "inputs-before.json")
    after = read(base / name / "inputs-after.json")
    comparisons.append({"run": name, "before_matches_current": before == current, "after_matches_current": after == current, "changed_paths": sorted(path for path in current.keys() | before.keys() | after.keys() if current.get(path) != before.get(path) or current.get(path) != after.get(path))})
union = read(base / "final-shard-union-audit.json")
host = read(base / "final-host-corrected/host-audit.json")
npm = read(base / "final-npm-corrected/npm-summary.json")
audit = {"observed_epoch": time.time(), "current_file_count": len(current), "current_manifest_sha256": hashlib.sha256(json.dumps(current, sort_keys=True).encode()).hexdigest(), "comparisons": comparisons, "all_final_runs_match_current_inputs": all(comparison["before_matches_current"] and comparison["after_matches_current"] for comparison in comparisons), "full_case_union_verified": union["exact_case_multiset_matches"] and union["actual_cases"] == 736 and union["restart_groups_kept_whole"] and union["six_distinct_owned_boots"] and union["widget_methods"] == 33 and union["widget_reference_classes"] == 8, "all_shards_green_with_actual_receipts": all(shard["summary"]["exit"] == shard["summary"]["failures"] == shard["summary"]["skipped"] == 0 and shard["actual_started"] == shard["actual_matching_finished"] == shard["case_count"] and shard["case_multiset_matches_real_pairs"] and shard["ci_shard_arguments"] and shard["no_class_batch_filter"] for shard in union["per_shard"]), "fresh_host_and_npm_verified": host["summary"] == {"exit": 0, "cases": 1069, "failures": 0, "skipped": 0, "inputs_unchanged": True} and host["lint_error_or_fatal"] == 0 and host["android_test_compile_executed"] and host["all47_tasks_executed"] and npm["exit"] == 0 and npm["node_counts"] == {"tests": 11, "pass": 11, "fail": 0, "cancelled": 0, "skipped": 0}}
(base / "final-current-inputs.json").write_text(json.dumps(current, indent=2) + "\n")
(base / "final-current-input-audit.json").write_text(json.dumps(audit, indent=2) + "\n")
print(json.dumps(audit))
assert all(audit[name] for name in ["all_final_runs_match_current_inputs", "full_case_union_verified", "all_shards_green_with_actual_receipts", "fresh_host_and_npm_verified"])
