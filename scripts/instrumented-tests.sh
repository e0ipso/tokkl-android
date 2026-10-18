#!/usr/bin/env bash
# Runs the instrumented tests against a real Tokkl server, never a fake one
# (docs/PRD.md section 7).
#
# 1. Starts ghcr.io/e0ipso/tokkl, pinned by digest, on a free port of
#    127.0.0.1 with its database on a tmpfs, and waits until it answers.
# 2. Creates the first account with a random setup code and password, and
#    reads its API token from GET /api/v9/me.
# 3. Copies the password and the API token to each test device, in a file
#    that only the shell user can read.
# 4. Runs connectedDebugAndroidTest with the server URL, the email and that
#    file's path as instrumentation arguments. The emulator reaches the host's
#    127.0.0.1 as 10.0.2.2. The credentials work only on this container.
#    The run fails when a test device goes away during it, even if Gradle
#    reports success.
# 5. Removes the container and the credentials file, also when a step fails.
#
# The setup code, the password and the API token never appear in a process's
# arguments, which every user on the machine can read with ps.
#
# The tests run on the devices adb lists, or on ANDROID_SERIAL. With no device
# attached, the script boots the AVD named by TOKKL_TEST_AVD (default
# tokkl-api36) without a window and with 2 GB of guest RAM, turns off error
# dialogs on it, and stops it at the end. It changes no setting on a device it
# did not boot. It also stops the adb server if that was not running before.
#
# qemu holds up to 5 GB, and its oom_score_adj of 800 makes it the kernel OOM
# killer's first pick. When the user's systemd manager is reachable, the
# emulator runs in a scope of its own with OOMPolicy=continue, so an OOM kill
# ends only qemu and this script, not the caller's scope with everything else
# in it, such as an agent session. The scope is PartOf the caller's unit, so
# stopping the caller still stops the emulator. Without a user manager, as in
# CI, the emulator starts as a plain child process.
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
emulator_unit=''
credentials_on_device=''
devices_with_credentials=()
adb_was_running=false
if pgrep -x adb >/dev/null; then adb_was_running=true; fi

# Reports why the emulator this script booted exited on its own, and forgets
# it. The OOM killer ends qemu with SIGKILL, which wait reports as 137.
# ShellCheck 0.11 misses that the EXIT trap runs cleanup, which calls this.
# shellcheck disable=SC2329
report_emulator_exit() {
    local status=0
    wait "$emulator_pid" 2>/dev/null || status=$?
    emulator_pid=''
    if [ "$status" -eq 137 ]; then
        echo "$0: the emulator was killed with SIGKILL, most likely by the kernel's OOM killer." >&2
        echo "$0: \`journalctl -k --grep=oom\` shows OOM kills. Free memory before running the tests again." >&2
    else
        echo "$0: the emulator exited with status $status. Last emulator output:" >&2
        tail -n 20 "$work_dir/emulator.log" >&2
    fi
}

# ShellCheck 0.11 misses that the EXIT trap runs this.
# shellcheck disable=SC2329
cleanup() {
    local status=$?
    if [ -n "$container" ]; then
        docker rm --force --volumes "$container" >/dev/null 2>&1 ||
            echo "$0: could not remove container $container" >&2
    fi
    # sync writes the deletion to disk before an emulator this script booted
    # is killed below. Without it, the file was back on the next boot.
    for device in "${devices_with_credentials[@]}"; do
        adb -s "$device" shell "rm -f $credentials_on_device && sync" >/dev/null 2>&1 ||
            echo "$0: could not remove $credentials_on_device from $device" >&2
    done
    if [ -n "$emulator_pid" ] && ! kill -0 "$emulator_pid" 2>/dev/null; then
        report_emulator_exit
        if [ "$status" -eq 0 ]; then status=1; fi
    fi
    if [ -n "$emulator_pid" ]; then
        adb -s "$emulator_serial" emu kill >/dev/null 2>&1 || kill "$emulator_pid" 2>/dev/null || true
        wait "$emulator_pid" 2>/dev/null || true
    fi
    if [ -n "$emulator_unit" ]; then
        # Stops whatever is left in the scope. It is gone once qemu exits.
        systemctl --user stop "$emulator_unit" >/dev/null 2>&1 || true
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
    local port=5554 log="$work_dir/emulator.log" deadline=$((SECONDS + 300)) caller
    local -a launch=()
    while adb devices | grep -q "^emulator-${port}[[:space:]]"; do port=$((port + 2)); done
    emulator_serial="emulator-$port"
    if command -v systemd-run >/dev/null && systemctl --user show --property=Version >/dev/null 2>&1; then
        emulator_unit="tokkl-emulator-$port-$(random_hex 4).scope"
        launch=(systemd-run --user --scope --quiet --collect --unit="$emulator_unit" -p OOMPolicy=continue)
        if caller=$(systemctl --user whoami 2>/dev/null); then launch+=(-p "PartOf=$caller"); fi
        launch+=(--)
    fi
    echo "Booting $AVD as $emulator_serial${emulator_unit:+ in $emulator_unit}"
    "${launch[@]}" emulator -avd "$AVD" -port "$port" -memory 2048 \
        -no-window -no-audio -no-boot-anim -no-snapshot -accel on >"$log" 2>&1 &
    emulator_pid=$!
    export ANDROID_SERIAL="$emulator_serial"
    until [ "$(adb -s "$emulator_serial" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = 1 ]; do
        if ! kill -0 "$emulator_pid" 2>/dev/null; then
            echo "$0: $AVD did not boot." >&2
            exit 1
        fi
        if [ "$SECONDS" -ge "$deadline" ]; then
            echo "$0: $AVD did not boot in 5 minutes. Last emulator output:" >&2
            tail -n 20 "$log" >&2
            exit 1
        fi
        sleep 2
    done
    # A fresh headless boot often leaves "System UI isn't responding" holding
    # window focus, so keys the tests inject go to that dialog. This emulator
    # is the script's own, so it may change its settings: hide error dialogs
    # from now on, and close the ones already open.
    adb -s "$emulator_serial" shell settings put global hide_error_dialogs 1
    adb -s "$emulator_serial" shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS >/dev/null
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

# printf is a shell builtin, so the secrets reach curl on stdin.
printf '{"setup_code":"%s","email":"%s","password":"%s","fullname":"Instrumented tests","timezone":"UTC"}' \
    "$setup_code" "$EMAIL" "$password" |
    curl --silent --show-error --fail --output /dev/null \
        --header 'Content-Type: application/json' --header 'X-Tokkl-Csrf: 1' \
        --data-binary @- "$server/api/app/setup"
api_token=$(printf 'user = "%s:%s"\n' "$EMAIL" "$password" |
    curl --silent --show-error --fail --config - "$server/api/v9/me" |
    jq --raw-output --exit-status .api_token)

# The password and the token reach the tests in a file on each device, which
# TokklTestServer reads through UiAutomation as the shell user. Instrumentation
# arguments cannot carry them: AGP's test runner passes every one to
# `adb shell am instrument -e`, so they would show in the arguments of a java
# and an adb process on this machine. The file is mode 600 on both sides, and
# adb push gets only its path.
credentials="$work_dir/credentials.properties"
credentials_on_device="/data/local/tmp/tokkl-test-credentials-$(random_hex 4).properties"
(
    umask 077
    printf 'tokklPassword=%s\ntokklApiToken=%s\n' "$password" "$api_token" >"$credentials"
)
if [ -n "${ANDROID_SERIAL:-}" ]; then
    devices=("$ANDROID_SERIAL")
else
    mapfile -t devices < <(attached_devices)
fi
for device in "${devices[@]}"; do
    devices_with_credentials+=("$device")
    adb -s "$device" push "$credentials" "$credentials_on_device" >/dev/null
    adb -s "$device" shell chmod 600 "$credentials_on_device"
done

echo "Tokkl is ready on $server, which the emulator reaches as http://10.0.2.2:$host_port"
gradle_status=0
./gradlew --no-daemon connectedDebugAndroidTest \
    "-P$RUNNER_ARGS.tokklBaseUrl=http://10.0.2.2:$host_port" \
    "-P$RUNNER_ARGS.tokklEmail=$EMAIL" \
    "-P$RUNNER_ARGS.tokklCredentialsFile=$credentials_on_device" \
    "$@" || gradle_status=$?

# AGP's test task can pass when a device dies between two tests. With the
# emulator killed a second into the run, Gradle printed BUILD SUCCESSFUL and
# had run none of the 32 tests. So every test device must still be online.
gone=()
for device in "${devices[@]}"; do
    if [ "$(adb -s "$device" get-state 2>/dev/null)" != device ]; then gone+=("$device"); fi
done
if [ "${#gone[@]}" -gt 0 ]; then
    echo "$0: ${gone[*]} went away during the run. Gradle can pass without running every test, so this run fails." >&2
    exit 1
fi
exit "$gradle_status"
