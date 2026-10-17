#!/usr/bin/env bash
# Runs the instrumented tests against a real Tokkl server, never a fake one
# (docs/PRD.md section 7).
#
# 1. Starts ghcr.io/e0ipso/tokkl, pinned by digest, on a free port of
#    127.0.0.1 with its database on a tmpfs, and waits until it answers.
# 2. Creates the first account with a random setup code and password, and
#    reads its API token from GET /api/v9/me.
# 3. Runs connectedDebugAndroidTest with the server URL and the credentials as
#    instrumentation arguments. The emulator reaches the host's 127.0.0.1 as
#    10.0.2.2. The credentials work only on this container.
# 4. Removes the container, also when a step fails.
#
# The tests run on the devices adb lists, or on ANDROID_SERIAL. With no device
# attached, the script boots the AVD named by TOKKL_TEST_AVD (default
# tokkl-api36) without a window and stops it at the end. It also stops the adb
# server if that was not running before.
#
# Arguments go to Gradle, for example to run one class:
#   scripts/instrumented-tests.sh \
#     -Pandroid.testInstrumentationRunnerArguments.class=com.github.e0ipso.tokkl.api.ServerSmokeTest

set -euo pipefail

# Tokkl 1.3.1. To upgrade, take the index digest from
# `docker buildx imagetools inspect ghcr.io/e0ipso/tokkl:<version>`.
readonly IMAGE='ghcr.io/e0ipso/tokkl:1.3.1@sha256:12591e2b04d17cc6a12a7ff6bd0dffc718079797f73750d00695ef9ad916f9f9'
readonly AVD="${TOKKL_TEST_AVD:-tokkl-api36}"
readonly EMAIL='instrumented-tests@example.com'
readonly RUNNER_ARGS='android.testInstrumentationRunnerArguments'

cd "$(dirname "$0")/.."

for tool in docker adb curl jq; do
    command -v "$tool" >/dev/null || { echo "$0: $tool is not on PATH" >&2; exit 1; }
done

work_dir=$(mktemp -d)
container=''
emulator_pid=''
emulator_serial=''
adb_was_running=false
if pgrep -x adb >/dev/null; then adb_was_running=true; fi

cleanup() {
    local status=$?
    if [ -n "$container" ]; then
        docker rm --force --volumes "$container" >/dev/null 2>&1 ||
            echo "$0: could not remove container $container" >&2
    fi
    if [ -n "$emulator_pid" ]; then
        adb -s "$emulator_serial" emu kill >/dev/null 2>&1 || kill "$emulator_pid" 2>/dev/null || true
        wait "$emulator_pid" 2>/dev/null || true
    fi
    if [ "$adb_was_running" = false ]; then
        adb kill-server >/dev/null 2>&1 || true
    fi
    rm -rf "$work_dir"
    exit "$status"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

random_hex() {
    od -An -N"$1" -tx1 /dev/urandom | tr -d ' \n'
}

attached_devices() {
    adb devices | awk 'NR > 1 && $2 == "device" { print $1 }'
}

boot_emulator() {
    command -v emulator >/dev/null || { echo "$0: no device attached and emulator is not on PATH" >&2; exit 1; }
    local port=5554 log="$work_dir/emulator.log" deadline=$((SECONDS + 300))
    while adb devices | grep -q "^emulator-$port[[:space:]]"; do port=$((port + 2)); done
    emulator_serial="emulator-$port"
    echo "Booting $AVD as $emulator_serial"
    emulator -avd "$AVD" -port "$port" -no-window -no-audio -no-boot-anim -no-snapshot -accel on >"$log" 2>&1 &
    emulator_pid=$!
    export ANDROID_SERIAL="$emulator_serial"
    until [ "$(adb -s "$emulator_serial" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = 1 ]; do
        if ! kill -0 "$emulator_pid" 2>/dev/null || [ "$SECONDS" -ge "$deadline" ]; then
            echo "$0: $AVD did not boot. Last emulator output:" >&2
            tail -n 20 "$log" >&2
            exit 1
        fi
        sleep 2
    done
}

if [ -z "${ANDROID_SERIAL:-}" ] && [ -z "$(attached_devices)" ]; then
    boot_emulator
fi

setup_code=$(random_hex 16)
password=$(random_hex 16)
container="tokkl-android-tests-$(random_hex 4)"

echo "Starting $IMAGE as $container"
# The image runs as an unprivileged user, so the tmpfs must be writable by any
# UID. --env without a value reads the setup code from the environment.
TOKKL_SETUP_CODE="$setup_code" docker run --detach --name "$container" \
    --env TOKKL_SETUP_CODE --tmpfs /data:mode=1777 --publish 127.0.0.1::8080 "$IMAGE" >/dev/null
host_port=$(docker port "$container" 8080/tcp | head -n 1)
host_port=${host_port##*:}
server="http://127.0.0.1:$host_port"

if ! curl --silent --fail --output /dev/null \
    --retry 30 --retry-delay 1 --retry-all-errors "$server/healthz"; then
    echo "$0: Tokkl did not answer on $server/healthz. Last server output:" >&2
    docker logs "$container" 2>&1 | grep -v 'setup code' | tail -n 20 >&2
    exit 1
fi

# printf is a shell builtin, so the secrets reach curl on stdin and never
# appear in a process's arguments.
printf '{"setup_code":"%s","email":"%s","password":"%s","fullname":"Instrumented tests","timezone":"UTC"}' \
    "$setup_code" "$EMAIL" "$password" |
    curl --silent --show-error --fail --output /dev/null \
        --header 'Content-Type: application/json' --header 'X-Tokkl-Csrf: 1' \
        --data-binary @- "$server/api/app/setup"
api_token=$(printf 'user = "%s:%s"\n' "$EMAIL" "$password" |
    curl --silent --show-error --fail --config - "$server/api/v9/me" |
    jq --raw-output --exit-status .api_token)

echo "Tokkl is ready on $server, which the emulator reaches as http://10.0.2.2:$host_port"
./gradlew --no-daemon connectedDebugAndroidTest \
    "-P$RUNNER_ARGS.tokklBaseUrl=http://10.0.2.2:$host_port" \
    "-P$RUNNER_ARGS.tokklEmail=$EMAIL" \
    "-P$RUNNER_ARGS.tokklPassword=$password" \
    "-P$RUNNER_ARGS.tokklApiToken=$api_token" \
    "$@"
