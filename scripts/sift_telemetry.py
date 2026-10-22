#!/usr/bin/env python3
"""Local Sift evidence receipts. Records observations; never runs a workload.

Each check has an exclusive directory and immutable start/result files. Legacy
RUNLOG import is read-only and does not assign identity-less phases to workers.
Only explicitly listed inputs/configuration participate in reuse decisions.
"""
import argparse
from datetime import datetime, timezone
import hashlib
import importlib.util
import json
import math
import os
from pathlib import Path
import re
import sys
import time
import uuid
import xml.etree.ElementTree as ET

SCHEMA = 1
KINDS = {"product", "tooling", "gate", "dependency_wait", "resource_wait"}


def require(condition, message):
    if not condition:
        raise ValueError(message)


def canonical(value):
    return json.dumps(value, sort_keys=True, separators=(",", ":"), allow_nan=False).encode()


def digest(value):
    return hashlib.sha256(canonical(value)).hexdigest()


def load(path):
    return json.loads(Path(path).read_text())


def utc():
    return datetime.now(timezone.utc).isoformat()


def publish(path, value):
    """Atomic publication with no overwrite, including concurrent finish calls."""
    path = Path(path)
    temp = path.with_name(path.name + "." + uuid.uuid4().hex + ".writing")
    try:
        with temp.open("x") as out:
            os.chmod(temp, 0o600)
            out.write(canonical(value).decode() + "\n")
            out.flush()
            os.fsync(out.fileno())
        os.link(temp, path)
        fd = os.open(path.parent, os.O_DIRECTORY)
        try:
            os.fsync(fd)
        finally:
            os.close(fd)
    finally:
        temp.unlink(missing_ok=True)


def files(root, names):
    """Include bytes, names and executable bits; reject missing/symlink inputs."""
    root = Path(root).resolve()
    require(isinstance(names, list) and names, "explicit nonempty input list required")
    rows = []
    for name in sorted(set(names)):
        relative = Path(name)
        require(not relative.is_absolute() and ".." not in relative.parts,
                "inputs must be relative paths inside root")
        path = root / relative
        require(path.resolve().is_relative_to(root), "input escapes root")
        require(path.is_file() and not path.is_symlink(), "missing/nonregular input: " + name)
        rows.append({"path": name, "sha256": hashlib.sha256(path.read_bytes()).hexdigest(),
                     "executable": bool(path.stat().st_mode & 0o111)})
    return rows


def fingerprint(context):
    for key in ("run_id", "wave", "sitting_id", "attempt_id", "check_name"):
        require(str(context.get(key, "")).strip(), "missing identity: " + key)
    require("ticket_ids" in context and isinstance(context["ticket_ids"], list),
            "explicit ticket_ids required; use [] for a gate")
    require(context.get("kind") in KINDS, "unknown work kind")
    require(context.get("stage") in ("development", "compatibility"), "unknown stage")
    config = context["configuration"]
    require(isinstance(config, dict) and isinstance(config.get("command"), list)
            and config["command"] and all(isinstance(x, str) for x in config["command"]),
            "configuration.command must be a nonempty argv list")
    for key in ("environment", "toolchain", "resource_policy", "scope"):
        require(key in config, "configuration missing " + key)
    if config.get("api"):
        require(config["api"] in (31, 36), "unknown API")
        require(context["stage"] != "development" or config["api"] == 36,
                "API31 belongs to the post-development compatibility cycle")
        require(config.get("avd") == "tokkl-api" + str(config["api"]), "AVD/API mismatch")
        for key in ("server_image", "proxy_image"):
            require(re.fullmatch(r"[^\s]+@sha256:[0-9a-f]{64}", config.get(key, "")),
                    "actual digest-pinned " + key + " required")
    inputs = files(context["root"], context["inputs"])
    # Identity and checkout location do not invalidate matching content.
    key = {"inputs": inputs, "configuration": config, "stage": context["stage"],
           "check_name": context["check_name"]}
    return {"inputs": inputs, "content_sha256": digest(inputs),
            "configuration_sha256": digest(config), "reuse_key": digest(key)}


def resource_snapshot():
    """Read caller ancestors only; controller samples can be supplied at finish.

    This snapshot is diagnostic, never an admission grant. A workload in another
    scope must provide that scope's complete before/after samples as artifacts.
    """
    try:
        path = Path(__file__).with_name("memory-capacity.py")
        spec = importlib.util.spec_from_file_location("sift_capacity", path)
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        capacity = module.capacity(Path("/proc"), 8192, 5000)
        headroom = [capacity["host_available_bytes"]]
        for row in capacity["ancestors"]:
            headroom.extend(v for k, v in row.items() if k.endswith(".headroom"))
            try:
                row["memory.events.local"] = (Path(row["path"]) / "memory.events.local").read_text().strip()
            except OSError:
                row["memory.events.local"] = "unavailable"
        return {"scope": "telemetry caller", "at": utc(), "capacity": capacity,
                "effective_headroom_bytes": min(headroom),
                "host_pressure": Path("/proc/pressure/memory").read_text().strip()}
    except (OSError, ValueError, IndexError, ImportError) as error:
        return {"scope": "telemetry caller", "at": utc(), "unavailable": str(error)}


def resource_delta(before, after):
    rows, unknown = [], []
    old = {x["path"]: x for x in before.get("capacity", {}).get("ancestors", [])}
    new = {x["path"]: x for x in after.get("capacity", {}).get("ancestors", [])}
    for path in sorted(old.keys() | new.keys()):
        if path not in old or path not in new:
            unknown.append(path)
            continue
        for field in ("memory.events", "memory.events.local"):
            try:
                a = dict(x.split() for x in old[path][field].splitlines())
                b = dict(x.split() for x in new[path][field].splitlines())
                require(a.keys() == b.keys(), "event keys changed")
                delta = {k: int(b[k]) - int(a[k]) for k in a}
                require(all(v >= 0 for v in delta.values()), "counter reset")
                rows.append({"path": path, "counter": field, "delta": delta})
            except (KeyError, ValueError):
                unknown.append(path + "/" + field)
    return {"rows": rows, "unavailable": unknown,
            "attribution": "diagnostic only; no event origin or clean certificate inferred"}


def begin(ledger, context):
    fp = fingerprint(context)
    ledger = Path(ledger).resolve()
    ledger.mkdir(parents=True, exist_ok=True)
    directory = ledger / uuid.uuid4().hex
    directory.mkdir(mode=0o700)
    row = {"schema_version": SCHEMA, "check_id": directory.name, "started_at": utc(),
           "started_epoch": time.time(), "started_monotonic": time.monotonic(),
           "boot_id": Path("/proc/sys/kernel/random/boot_id").read_text().strip(),
           "context": context, "fingerprint": fp, "resource_before": resource_snapshot()}
    publish(directory / "start.json", row)
    return {"check_id": directory.name, "directory": str(directory)}


def junit_counts(paths, started_epoch):
    require(paths, "no finalized JUnit files")
    counts = dict(tests=0, failures=0, errors=0, skipped=0)
    identities = set()
    for name in paths:
        path = Path(name)
        require(path.stat().st_mtime >= started_epoch, "stale JUnit: " + str(path))
        root = ET.parse(path).getroot()
        suites = [root] if root.tag == "testsuite" else root.findall("testsuite")
        require(suites, "JUnit testsuite missing")
        for suite in suites:
            cases = suite.findall("testcase")
            require(int(suite.get("tests", -1)) == len(cases) and cases,
                    "JUnit final count missing, empty or inconsistent")
            actual = {"failures": 0, "errors": 0, "skipped": 0}
            for case in cases:
                identity = (case.get("classname"), case.get("name"))
                require(all(identity) and identity not in identities, "missing/duplicate testcase identity")
                identities.add(identity)
                for tag, field in (("failure", "failures"), ("error", "errors"), ("skipped", "skipped")):
                    actual[field] += int(case.find(tag) is not None)
            for field, value in actual.items():
                require(int(suite.get(field, 0)) == value, "JUnit outcome count inconsistent")
                counts[field] += value
            counts["tests"] += len(cases)
    return counts


def artifacts(paths):
    require(isinstance(paths, list), "artifact paths must be a list")
    result = []
    for name in paths:
        path = Path(name).resolve()
        require(path.is_file(), "missing artifact: " + str(path))
        result.append({"path": str(path), "sha256": hashlib.sha256(path.read_bytes()).hexdigest(),
                       "bytes": path.stat().st_size})
    return result


def finish(directory, result):
    directory = Path(directory)
    start = load(directory / "start.json")
    require(not (directory / "result.json").exists(), "check already finalized")
    require(type(result.get("exit")) is int, "integer exit required")
    wait = result.get("admission_wait_seconds")
    require((wait is None and result.get("outcome") != "passed")
            or (type(wait) in (int, float) and math.isfinite(wait) and wait >= 0),
            "measured admission wait required for a pass; null means unmeasured")
    require(result.get("outcome") in ("passed", "failed", "unknown", "interrupted", "deadline", "observed"),
            "explicit controller/check outcome required")
    require(isinstance(result.get("logs"), list) and result["logs"], "durable logs required")
    try:
        current = fingerprint(start["context"])
        input_error = None
    except (OSError, ValueError) as error:
        current, input_error = None, str(error)
    same = current == start["fingerprint"]
    counts = None
    if result.get("junit"):
        counts = junit_counts(result["junit"], start["started_epoch"])
    elif "counts" in result:
        counts = result["counts"]
        require(set(counts) == {"tests", "failures", "errors", "skipped"}
                and all(type(v) is int and v >= 0 for v in counts.values()), "invalid finalized counts")
        require(sum(counts[k] for k in ("failures", "errors", "skipped")) <= counts["tests"],
                "finalized counts inconsistent")
    passed = result["outcome"] == "passed"
    if passed:
        require(not start["context"]["configuration"].get("api") or result.get("junit"),
                "device pass requires finalized JUnit, not manually reported counts")
        require(result["exit"] == 0 and same, "pass requires exit zero and unchanged checked inputs")
        require(result.get("finalized") is True and counts and counts["tests"] > 0
                and not any(counts[k] for k in ("failures", "errors")), "pass requires finalized positive counts")
        require(result.get("required_evidence_complete") is True,
                "pass requires explicit completion of required metrics, guards and assertions")
    retained = artifacts(result["logs"] + result.get("junit", []) + result.get("artifacts", []))
    end = time.time()
    boot = Path("/proc/sys/kernel/random/boot_id").read_text().strip()
    elapsed = time.monotonic() - start["started_monotonic"] if boot == start["boot_id"] else None
    after = resource_snapshot()
    row = {"schema_version": SCHEMA, "check_id": start["check_id"], "finished_at": utc(),
           "finished_epoch": end, "elapsed_seconds": elapsed,
           "wall_elapsed_seconds": end - start["started_epoch"], "counts": counts,
           "input_match": same, "input_error": input_error, "result": result, "artifacts": retained,
           "resource_after": after, "resource_delta": resource_delta(start["resource_before"], after),
           "start_sha256": hashlib.sha256((directory / "start.json").read_bytes()).hexdigest()}
    publish(directory / "result.json", row)
    return row


def reuse(ledger, context):
    key = fingerprint(context)["reuse_key"]
    rejected = []
    for path in sorted(Path(ledger).glob("*/start.json")):
        try:
            start = load(path)
            if start["fingerprint"]["reuse_key"] != key:
                continue
            result = load(path.with_name("result.json"))
            require(result["start_sha256"] == hashlib.sha256(path.read_bytes()).hexdigest(), "start changed")
            require(result["result"]["outcome"] == "passed" and result["input_match"], "no complete pass")
            for row in result["artifacts"]:
                require(hashlib.sha256(Path(row["path"]).read_bytes()).hexdigest() == row["sha256"],
                        "retained artifact changed")
            return {"reusable": True, "check_id": start["check_id"], "receipt": str(path.parent),
                    "reuse_key": key, "reason": "matching explicit inputs/configuration and retained passing evidence"}
        except (OSError, ValueError, KeyError) as error:
            rejected.append({"receipt": str(path.parent), "reason": str(error)})
    return {"reusable": False, "reuse_key": key, "rejected": rejected,
            "reason": "no complete matching pass; deferred or missing evidence is not a pass"}


def legacy_report(runlog, tracker, now):
    """Pair by ticket; a second dispatch censors the previous attempt explicitly."""
    pending, attempts, issues, completions = {}, [], [], {}
    phase_count = 0
    metadata = {}
    for path in Path(tracker).glob("archive/**/*.md"):
        text = path.read_text()
        fields = dict(re.findall(r"^(id|type|status):\s*(.+)$", text, re.M))
        if fields.get("id"):
            metadata[fields["id"]] = fields
    runlog_bytes = Path(runlog).read_bytes()
    for line, raw in enumerate(runlog_bytes.decode().splitlines(), 1):
        cells = [x.strip() for x in raw.split("|")][1:-1]
        if len(cells) != 6 or cells[0] not in ("dispatch", "return", "phase"):
            continue
        event, ticket, _, _, epoch, status = cells
        if not epoch.isdigit():
            issues.append({"line": line, "reason": "malformed epoch"})
            continue
        epoch = int(epoch)
        if epoch > now:
            continue
        if event == "phase":
            phase_count += 1
            continue
        if event == "dispatch":
            if ticket in pending:
                issues.append({"ticket": ticket, "start": pending[ticket], "reason": "superseded dispatch"})
            pending[ticket] = epoch
        else:
            if status == "done" and metadata.get(ticket, {}).get("status") == "done":
                completions.setdefault(ticket, epoch)
            start = pending.pop(ticket, None)
            if start is None or start > epoch:
                issues.append({"ticket": ticket, "end": epoch, "reason": "unmatched/invalid return"})
            else:
                attempts.append({"ticket": ticket, "start": start, "end": epoch,
                                 "status": status, "seconds": epoch - start})
    windows = []
    for size in (20, 40):
        chosen = sorted(completions, key=completions.get)[-size:]
        starts = [a["start"] for a in attempts if a["ticket"] in chosen]
        if not starts:
            continue
        # Use the earliest dispatch of selected tickets, including retries.
        start = min(starts)
        hours = (now - start) / 3600
        kinds = {}
        for ticket in chosen:
            kind = "tooling" if metadata[ticket].get("type") == "dx" else "product_or_other"
            kinds[kind] = kinds.get(kind, 0) + 1
        windows.append({"requested_completions": size, "distinct_done": len(chosen),
                        "start_epoch": start, "end_epoch": now, "elapsed_hours": hours,
                        "done_per_hour": len(chosen) / hours if hours > 0 else None, "kinds": kinds})
    return {"schema_version": SCHEMA, "source_sha256": hashlib.sha256(runlog_bytes).hexdigest(),
            "now_epoch": now, "attempts": attempts, "issues": issues, "open_dispatches": pending,
            "unattributed_phase_events": phase_count, "elapsed_throughput": windows,
            "model_generation_tool_wait_split": "unmeasured; legacy phases have no worker identity",
            "duration_warning": "attempt durations overlap; never sum them as elapsed delivery time"}


def check_summary(ledger):
    groups, incomplete = {}, []
    for path in sorted(Path(ledger).glob("*/start.json")):
        start = load(path)
        terminal = path.with_name("result.json")
        if not terminal.exists():
            incomplete.append(start["check_id"])
            continue
        end = load(terminal)
        context = start["context"]
        key = context["stage"] + "/" + context["kind"]
        group = groups.setdefault(key, {"checks": 0, "outcomes": {}, "intervals": [],
                                        "measured_admission_wait_seconds": 0, "unmeasured_waits": 0})
        group["checks"] += 1
        outcome = end["result"]["outcome"]
        group["outcomes"][outcome] = group["outcomes"].get(outcome, 0) + 1
        group["intervals"].append((start["started_epoch"], end["finished_epoch"]))
        wait = end["result"]["admission_wait_seconds"]
        if wait is None:
            group["unmeasured_waits"] += 1
        else:
            group["measured_admission_wait_seconds"] += wait
    for group in groups.values():
        merged = []
        for start, end in sorted(group.pop("intervals")):
            require(end >= start, "negative check interval")
            if merged and start <= merged[-1][1]:
                merged[-1][1] = max(merged[-1][1], end)
            else:
                merged.append([start, end])
        group["elapsed_union_seconds"] = sum(b - a for a, b in merged)
    return {"groups": groups, "incomplete_check_ids": incomplete,
            "warning": "group intervals and per-check wait durations can overlap across workers; do not sum as wall time"}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    for name in ("begin", "reuse", "fingerprint"):
        cmd = commands.add_parser(name)
        cmd.add_argument("--context", required=True, type=Path)
        if name != "fingerprint":
            cmd.add_argument("--ledger", required=True, type=Path)
    cmd = commands.add_parser("finish")
    cmd.add_argument("--directory", required=True, type=Path)
    cmd.add_argument("--result", required=True, type=Path)
    cmd = commands.add_parser("report")
    cmd.add_argument("--runlog", required=True, type=Path)
    cmd.add_argument("--tracker", required=True, type=Path)
    cmd.add_argument("--now", type=float, default=None, help="UTC epoch; defaults to current time")
    cmd.add_argument("--ledger", type=Path)
    cmd.add_argument("--remaining-development", type=int)
    cmd.add_argument("--compatibility-hours", type=float, nargs=2, metavar=("LOW", "HIGH"))
    args = parser.parse_args()
    try:
        if args.command == "begin":
            value = begin(args.ledger, load(args.context))
        elif args.command == "finish":
            value = finish(args.directory, load(args.result))
        elif args.command == "reuse":
            value = reuse(args.ledger, load(args.context))
        elif args.command == "fingerprint":
            value = fingerprint(load(args.context))
        else:
            value = legacy_report(args.runlog, args.tracker, args.now if args.now is not None else time.time())
            if args.ledger:
                value["checks"] = check_summary(args.ledger)
            if args.remaining_development is not None:
                require(args.remaining_development >= 0, "negative remaining work")
                rates = [w["done_per_hour"] for w in value["elapsed_throughput"] if w["done_per_hour"]]
                value["development_eta_hours"] = ([args.remaining_development / max(rates),
                                                   args.remaining_development / min(rates)] if rates else None)
                value["eta_caveat"] = "low confidence rate projection; gate critical path, ticket mix and external blockers need coordinator review"
            if args.compatibility_hours:
                low, high = args.compatibility_hours
                require(math.isfinite(low) and math.isfinite(high) and 0 <= low <= high,
                        "invalid compatibility planning range")
                value["compatibility_eta"] = {"hours_after_development": [low, high],
                                              "source": "explicit coordinator planning input, not a measured pass"}
        print(json.dumps(value, sort_keys=True, allow_nan=False))
        return 0
    except (OSError, ValueError, KeyError, TypeError, ET.ParseError) as error:
        print(json.dumps({"error": str(error), "outcome": "unknown"}), file=sys.stderr)
        return 2


if __name__ == "__main__":
    sys.exit(main())
