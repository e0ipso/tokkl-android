"""Read-only runtime sampling; these observations never replace terminal JUnit."""
from pathlib import Path
import json
import re
import subprocess
import sys
import time

base = Path(__file__).resolve().parent / (sys.argv[1] if len(sys.argv) > 1 else "api36-full-final")
log = (base / "run.log").read_text(errors="replace")
match = re.search(r"Booting \S+ as (emulator-\d+) in (tokkl-emulator-[\w-]+\.scope)", log)
launcher = json.loads((base / "launcher.json").read_text())
units = [launcher["service"], launcher["scope"]] + ([match[2]] if match else [])
sample = {"at": time.time(), "progress_only": True, "units": {}}
memory = dict(line.split(":", 1) for line in Path("/proc/meminfo").read_text().splitlines())
sample["host_available_mb"] = int(memory["MemAvailable"].split()[0]) // 1024
sample["host_swap_free_mb"] = int(memory["SwapFree"].split()[0]) // 1024
sample["host_memory_pressure"] = Path("/proc/pressure/memory").read_text().strip()
for unit in units:
    result = subprocess.run(["systemctl", "--user", "show", unit, "-p", "ActiveState", "-p", "SubState", "-p", "Result", "-p", "ExecMainStatus", "-p", "MemoryCurrent", "-p", "MemoryPeak", "-p", "MemoryMax", "-p", "MemorySwapCurrent", "-p", "MemorySwapMax"], capture_output=True, text=True, timeout=5)
    sample["units"][unit] = dict(line.split("=", 1) for line in result.stdout.splitlines() if "=" in line)
state = subprocess.run(["adb", "-s", match[1], "get-state"], capture_output=True, text=True, timeout=5) if match else None
sample["device_state"] = state.stdout.strip() if state else "not-yet-allocated"
if state and state.returncode == 0:
    result = subprocess.run(["adb", "-s", match[1], "logcat", "-d", "-v", "brief", "-s", "TestRunner:I"], capture_output=True, text=True, timeout=10)
    starts = re.findall(r"TestRunner\(\s*\d+\): started: (.+)", result.stdout)
    sample["last_started"] = starts[-1] if starts else None
    sample["visible_failures"] = re.findall(r"TestRunner\(\s*\d+\): failed: (.+)", result.stdout)
with (base / "resource-samples.jsonl").open("a") as output:
    output.write(json.dumps(sample) + "\n")
print(json.dumps(sample))
