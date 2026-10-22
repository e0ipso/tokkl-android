#!/bin/bash
set -u
"$@" &
child=$!
timeout 3s tail --pid="$child" -f /dev/null || true
wait "$child"
