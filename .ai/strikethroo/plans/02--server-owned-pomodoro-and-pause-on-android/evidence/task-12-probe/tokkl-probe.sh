#!/usr/bin/env bash
# Plan 02 task 12 platform probes: one disposable Tokkl from the runner's pinned image, and the
# other device's API calls against it. The setup code, password and API token stay in a 0600 file
# under $XDG_RUNTIME_DIR and reach curl on stdin, never in arguments or output.
#
#   tokkl-probe.sh up                 start the server and its account
#   tokkl-probe.sh url                the server's URL as the emulator sees it
#   tokkl-probe.sh state              the timer state, without the running entry's fields
#   tokkl-probe.sh session [label] [work minutes]   start a session, one-minute phases by default
#   tokkl-probe.sh end                end the session at the current revision
#   tokkl-probe.sh type-token SERIAL  type the token into the focused field on the device
#   tokkl-probe.sh down               remove the container and the secrets
set -euo pipefail
umask 077
dir=${XDG_RUNTIME_DIR:?}/t12-probe
mkdir -p "$dir"
repo=$(git -C "$(dirname "$0")" rev-parse --show-toplevel)
image=$(sed -n "s/^readonly IMAGE='\(.*\)'$/\1/p" "$repo/scripts/instrumented-tests.sh")
email='probe@example.com'

rand() { od -An -N"$1" -tx1 /dev/urandom | tr -d ' \n'; }
server() { echo "http://127.0.0.1:$(cat "$dir/port")"; }
auth() { printf 'user = "%s:api_token"\n' "$(cat "$dir/token")"; }

api() { # method path [json]
    local body=${3:-}
    if [ -n "$body" ]; then
        { auth; printf 'data = "%s"\n' "$(printf '%s' "$body" | sed 's/"/\\"/g')"; } |
            curl --silent --show-error --config - -X "$1" --header 'Content-Type: application/json' \
                --write-out '\nHTTP %{http_code}\n' "$(server)$2"
    else
        auth | curl --silent --show-error --config - -X "$1" --write-out '\nHTTP %{http_code}\n' "$(server)$2"
    fi
}

case "$1" in
up)
    code=$(rand 16); pass=$(rand 16); name="tokkl-t12-probe-$(rand 4)"
    TOKKL_SETUP_CODE="$code" docker run --detach --name "$name" --env TOKKL_SETUP_CODE \
        --tmpfs /data:mode=1777 --publish 127.0.0.1::8080 "$image" >/dev/null
    echo "$name" >"$dir/container"
    port=$(docker port "$name" 8080/tcp | head -n 1); echo "${port##*:}" >"$dir/port"
    curl --silent --fail --output /dev/null --retry 30 --retry-delay 1 --retry-all-errors "$(server)/healthz"
    printf '{"setup_code":"%s","email":"%s","password":"%s","fullname":"Probe","timezone":"UTC"}' "$code" "$email" "$pass" |
        curl --silent --show-error --fail --output /dev/null --header 'Content-Type: application/json' \
            --header 'X-Tokkl-Csrf: 1' --data-binary @- "$(server)/api/app/setup"
    me=$(printf 'user = "%s:%s"\n' "$email" "$pass" | curl --silent --show-error --fail --config - "$(server)/api/v9/me")
    jq --raw-output --exit-status .api_token <<<"$me" >"$dir/token"
    jq --raw-output --exit-status .default_workspace_id <<<"$me" >"$dir/workspace"
    echo "started $name from $image on port $(cat "$dir/port")"
    ;;
url) echo "http://10.0.2.2:$(cat "$dir/port")" ;;
state)
    api GET /api/app/timer | head -n -1 | jq -c '{revision, server_time, running: .running_entry.id, pause: (.pause != null), pomodoro: (if .pomodoro then {kind: .pomodoro.phase.kind, start: .pomodoro.phase.start, planned_end: .pomodoro.phase.planned_end, number: .pomodoro.number} else null end)}'
    ;;
session)
    ws=$(cat "$dir/workspace")
    api POST /api/app/pomodoro "{\"workspace_id\":$ws,\"created_with\":\"t12 probe, another device\",\"description\":\"${2:-Probe session}\",\"tags\":[],\"billable\":false,\"work_minutes\":${3:-1},\"short_rest_minutes\":1,\"long_rest_minutes\":1,\"long_rest_every\":4}" |
        head -n -1 | jq -c '{revision, pomodoro: {kind: .pomodoro.phase.kind, start: .pomodoro.phase.start, planned_end: .pomodoro.phase.planned_end, number: .pomodoro.number}}'
    ;;
end)
    rev=$(api GET /api/app/timer | head -n -1 | jq .revision)
    api POST /api/app/pomodoro/end "{\"revision\":$rev}" | tail -n 1
    ;;
type-token)
    adb -s "$2" shell 'cat > /data/local/tmp/t12-token' <"$dir/token"
    adb -s "$2" shell 'input text "$(cat /data/local/tmp/t12-token)"; rm -f /data/local/tmp/t12-token'
    ;;
down)
    docker rm --force --volumes "$(cat "$dir/container")" >/dev/null && echo "removed $(cat "$dir/container")"
    rm -rf "$dir"
    ;;
esac
