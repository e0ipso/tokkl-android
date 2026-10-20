#!/usr/bin/env bash
# Compares the macrobenchmark results in CURRENT_DIR with those in BASELINE_DIR
# and fails when a median is more than 10% above the baseline's
# (docs/PRD.md, Speed). CI passes the results of the last green run on main
# as the baseline.
#
#   scripts/compare-benchmarks.sh BASELINE_DIR CURRENT_DIR
#
# Each directory holds the *-benchmarkData.json files of one device, as
# Macrobenchmark writes them, in any subdirectory. The script compares the
# median of every metric in milliseconds, for each benchmark by class and
# method. It fails when a metric is slower by more than the limit, and when a
# metric of the baseline is missing from the current run, since a benchmark
# that stopped measuring hides any slowdown. A metric new in the current run
# has nothing to compare with and passes. With no baseline results, as on the
# first run or once the baseline's artifact has expired, it prints the
# current medians and passes.
#
# Exit status: 0 when nothing is slower, 1 when something is, 2 on a usage
# error or when CURRENT_DIR holds no results.

set -euo pipefail
# A point, not the locale's decimal comma, in the medians awk prints.
export LC_ALL=C

readonly LIMIT_PERCENT=10

if [ "$#" -ne 2 ]; then
    echo "usage: $0 BASELINE_DIR CURRENT_DIR" >&2
    exit 2
fi
command -v jq >/dev/null || { echo "$0: jq is not on PATH" >&2; exit 2; }

# Prints "<class>#<method> <metric><TAB><median>" for each metric in
# milliseconds in the results under $1, sorted. Fails when a benchmark shows
# up twice, as with the results of two devices.
medians() {
    local lines duplicates
    # The $ names are jq's variables, not the shell's.
    # shellcheck disable=SC2016
    lines=$(find "$1" -name '*-benchmarkData.json' -print0 |
        xargs -0 -r jq -r '
            .benchmarks[]
            | "\(.className)#\(.name)" as $benchmark
            | .metrics | to_entries[]
            | select(.key | endswith("Ms"))
            | "\($benchmark) \(.key)\t\(.value.median)"' |
        sort)
    duplicates=$(cut -f1 <<<"$lines" | uniq -d)
    if [ -n "$duplicates" ]; then
        echo "$0: $1 holds these metrics more than once, so it holds results of more than one device:" >&2
        echo "$duplicates" >&2
        return 2
    fi
    printf '%s' "$lines"
}

if [ ! -d "$2" ]; then
    echo "$0: $2 is not a directory" >&2
    exit 2
fi
current=$(medians "$2")
if [ -z "$current" ]; then
    echo "$0: $2 holds no macrobenchmark results (*-benchmarkData.json)" >&2
    exit 2
fi
baseline=''
if [ -d "$1" ]; then baseline=$(medians "$1"); fi
if [ -z "$baseline" ]; then
    echo "No baseline results in $1, so nothing to compare with. The medians of this run:"
    awk -F'\t' '{ printf "  %-90s %10.1f\n", $1, $2 }' <<<"$current"
    exit 0
fi

echo "Medians in ms, baseline from $1, this run from $2:"
awk -F'\t' -v limit="$LIMIT_PERCENT" '
    FNR == 1 && NR == 1 { printf "  %-90s %10s %10s %8s\n", "benchmark and metric", "baseline", "this run", "change" }
    NR == FNR { base[$1] = $2; next }
    {
        seen[$1] = 1
        if (!($1 in base)) {
            printf "  %-90s %10s %10.1f   new, no baseline\n", $1, "-", $2
            next
        }
        change = base[$1] > 0 ? ($2 - base[$1]) * 100 / base[$1] : 0
        verdict = change > limit ? "SLOWER" : "ok"
        if (change > limit) slower++
        printf "  %-90s %10.1f %10.1f %+7.1f%%  %s\n", $1, base[$1], $2, change, verdict
    }
    END {
        for (metric in base) if (!(metric in seen)) {
            printf "  %-90s %10.1f %10s   MISSING from this run\n", metric, base[metric], "-"
            missing++
        }
        if (slower || missing) {
            printf "More than %d%% slower than the baseline: %d. Missing from this run: %d.\n", limit, slower, missing
            exit 1
        }
        printf "No median is more than %d%% slower than the baseline.\n", limit
    }
' <(printf '%s\n' "$baseline") <(printf '%s\n' "$current")
