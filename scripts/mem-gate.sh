#!/usr/bin/env bash
# Local Linux admission wrapper. Retains the shared drain lock and startup ramp.
set -u
script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
min=${MEM_GATE_MIN_MB:-5000}
required=${MEM_GATE_CGROUP_MB:-5000}
settle=${MEM_GATE_SETTLE_S:-60}
wait_s=${MEM_GATE_WAIT_S:-300}
for value in "$min" "$required" "$settle" "$wait_s"; do
    if ! [[ $value =~ ^[0-9]{1,8}$ ]]; then
        echo 'mem-gate: settings must be nonnegative integers of at most 8 digits' >&2
        exit 2
    fi
done
min=$((10#$min)); required=$((10#$required))
settle=$((10#$settle)); wait_s=$((10#$wait_s))
if (( min < 5000 || required < 5000 || settle < 60 )); then
    echo 'mem-gate: floors are 5000 MiB; startup settling must be at least 60 s' >&2
    exit 2
fi
if (( $# == 0 )); then echo 'usage: mem-gate.sh CMD [ARGS...]' >&2; exit 2; fi
# An emulator and the in-process Gradle compiler share app.slice. This budget
# includes the measured 4.16 GiB qemu peak, about 3.25 GiB Gradle, and margin.
for arg in "$@"; do
    if [[ ${arg##*/} == instrumented-tests.sh ]] && (( required < 8192 )); then required=8192; fi
done
lock=${XDG_RUNTIME_DIR:-/run/user/$(id -u)}/tokkl-android-mem-gate.lock
exec 9>"$lock" || exit 2
if ! flock -w "$wait_s" 9; then
    echo "mem-gate: startup lock deadline reached: $lock; workload was not started" >&2
    exit 75
fi
deadline=$((SECONDS + wait_s))
while :; do
    python3 "$script_dir/memory-capacity.py" --host-mb "$min" --required-mb "$required" >&2
    status=$?
    if (( status == 0 )); then break; fi
    if (( status != 75 )); then exit "$status"; fi
    if (( SECONDS >= deadline )); then
        echo 'mem-gate: capacity deadline reached; workload was not started' >&2
        exit 75
    fi
    remaining=$((deadline - SECONDS))
    if (( remaining > 15 )); then remaining=15; fi
    sleep "$remaining"
done
"$@" 9>&- &
pid=$!
timeout "$settle" tail --pid="$pid" -f /dev/null 9>&- || true
flock -u 9
exec 9>&-
wait "$pid"
