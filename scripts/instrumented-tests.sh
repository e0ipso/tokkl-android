#!/usr/bin/env bash
# Runs the instrumented tests against a real Tokkl server, never a fake one
# (docs/PRD.md section 7).
#
# 1. Starts ghcr.io/e0ipso/tokkl, pinned by digest, on a free port of
#    127.0.0.1 with its database on a tmpfs, and waits until it answers.
# 2. Creates the first account with a random setup code and password, and
#    reads its API token from GET /api/v9/me.
# 3. Puts a TLS proxy, haproxy pinned by digest, in front of the same server,
#    with a self-signed certificate made for this run. The certificate names
#    10.0.2.2 and 127.0.0.1 as IP addresses, so host name checks pass. Its
#    private key stays in this script's temporary directory. The same proxy
#    serves Tokkl over http under the path prefix /tokkl on a port of its
#    own, as a reverse proxy serves it beside other sites, and answers 404
#    there to every path outside the prefix.
# 4. Copies the password and the API token to each test device, in a file
#    that only the shell user can read. Before that it deletes the
#    credentials files that killed runs left behind, once they are older than
#    any run lasts.
# 5. Runs connectedDebugAndroidTest with the http, https and prefixed server
#    URLs, the proxy certificate's SHA-256 fingerprint, the email and that file's path
#    as instrumentation arguments. The emulator reaches the host's 127.0.0.1
#    as 10.0.2.2. The credentials work only on this container. The run fails
#    when a test device goes away during it, even if Gradle reports success.
# 6. Removes both containers, the certificate's key, the credentials file and
#    the test filter file, if any (see below), also when a step fails.
#
# The setup code, the password and the API token never appear in a process's
# arguments, which every user on the machine can read with ps.
#
# The tests run on the devices adb lists, or on ANDROID_SERIAL. With no device
# attached, the script boots the AVD named by TOKKL_TEST_AVD (default
# tokkl-api36; tokkl-api31 runs Android 12, the oldest version the app
# supports) without a window, with 2.5 GB of guest RAM, 4 cores and the
# lavapipe renderer, or SwiftShader below API 36, turns off error dialogs on
# it, sets its font scale to 1.0, lets its soft keyboard open, makes apps draw
# with Vulkan, and stops it at the end. It refuses an AVD older than Android
# 12, where the app does not install. It changes no setting on a device it did
# not boot. It also stops the adb server if that was not running before.
#
# Runs take turns. Each run locks a file in $XDG_RUNTIME_DIR from its start
# until its cleanup ends. A run that starts meanwhile prints the PID, start
# time and directory of the run holding the lock, and waits for it to end.
#
# qemu peaks at about 3.5 GB in a full run on tokkl-api36 and at about 4 GB on
# tokkl-api31. Its oom_score_adj of 800 makes it the kernel OOM killer's first
# pick. When the user's systemd manager is reachable, the emulator runs in a
# scope of its own with OOMPolicy=continue and at most EMULATOR_MEMORY_MAX of
# memory, so an OOM kill ends only qemu and this script, not the caller's
# scope with everything else in it, such as an agent session. The scope is
# PartOf the caller's unit, so stopping the caller still stops the emulator.
# Without a user manager, as in CI, the emulator starts as a plain child
# process with no memory limit.
#
# Arguments go to Gradle, for example to run one class:
#   scripts/instrumented-tests.sh \
#     -Pandroid.testInstrumentationRunnerArguments.class=com.github.e0ipso.tokkl.api.ServerSmokeTest
#
# The test filters it takes, each as one -Pandroid.testInstrumentationRunnerArguments.<name>=<value>
# argument:
#   class=<class>              one class
#   class=<class>#<method>     one test
#   class=<entry>,<entry>,...  several classes or tests
#   package=<package>          one package and the packages under it
#   package=<package>,...      several packages
# AGP hands the test runner all its arguments in one string of key=value pairs
# joined with commas, and splits that string at every comma. A value with a
# comma lost everything after its first comma, so class=A,B ran only A and
# still passed. So the script writes a class= or package= list to a file on
# each test device, one entry per line, and passes the runner its path as
# testFile instead. It refuses a comma in any other runner argument, and a
# testFile argument of your own beside a list.

set -euo pipefail

# Tokkl 1.3.1. To upgrade, take the index digest from
# `docker buildx imagetools inspect ghcr.io/e0ipso/tokkl:<version>`.
readonly IMAGE='ghcr.io/e0ipso/tokkl:1.3.1@sha256:12591e2b04d17cc6a12a7ff6bd0dffc718079797f73750d00695ef9ad916f9f9'
# HAProxy 3.2.26 on Alpine, the index digest from
# `docker buildx imagetools inspect haproxy:3.2.26-alpine`.
readonly PROXY_IMAGE='haproxy:3.2.26-alpine@sha256:254e6370f01cd654e0744edf607c67f33777c1b6a9dc0d4b28bcba7171460122'
readonly AVD="${TOKKL_TEST_AVD:-tokkl-api36}"
readonly EMAIL='instrumented-tests@example.com'
readonly RUNNER_ARGS='android.testInstrumentationRunnerArguments'
# Where each run puts its credentials file on a test device, named with this
# prefix, a random part and .properties.
readonly CREDENTIALS_DIR='/data/local/tmp'
readonly CREDENTIALS_PREFIX='tokkl-test-credentials-'
# Where a run puts the class= or package= list it passes as testFile, beside
# the credentials file.
readonly FILTER_PREFIX='tokkl-test-filter-'
# A run takes minutes, so a credentials file this many hours old belongs to a
# run that ended without its cleanup, never to one still reading it.
readonly STALE_CREDENTIALS_HOURS=6
# The most memory the emulator's scope may use, swap included. A full run
# peaks below 4 GB on tokkl-api36.
readonly EMULATOR_MEMORY_MAX=6G
# Where Gradle puts the JUnit XML results of a run, one file for each test
# device.
readonly RESULTS='app/build/outputs/androidTest-results/connected/debug'

cd "$(dirname "$0")/.."

for tool in docker adb curl jq openssl flock; do
    command -v "$tool" >/dev/null || { echo "$0: $tool is not on PATH" >&2; exit 1; }
done

# The arguments for Gradle, and the entries of a class= or package= list,
# which go to the runner in a file (see the header). Checked before the run
# waits for its turn or boots anything.
gradle_args=()
test_filter=()
for arg in "$@"; do
    case $arg in
        "-P$RUNNER_ARGS.class="*,* | "-P$RUNNER_ARGS.package="*,*)
            IFS=, read -ra entries <<<"${arg#*=}"
            for entry in "${entries[@]}"; do
                if [ -n "$entry" ]; then test_filter+=("$entry"); fi
            done
            ;;
        *"$RUNNER_ARGS."*=*,*)
            echo "$0: $arg holds a comma. AGP splits the test runner's arguments at every comma. Only a class= or package= list may hold commas, given as one -P$RUNNER_ARGS.<name>=<list> argument." >&2
            exit 2
            ;;
        *) gradle_args+=("$arg") ;;
    esac
done
if [ "${#test_filter[@]}" -gt 0 ]; then
    for arg in "${gradle_args[@]}"; do
        case $arg in
            *"$RUNNER_ARGS.testFile="*)
                echo "$0: $arg cannot go with a class= or package= list, which the script passes as testFile." >&2
                exit 2
                ;;
        esac
    done
fi
# Runs take turns. A second run would test on the first one's emulator, which
# the first one's cleanup then stops, along with an adb server it started. So
# each run locks this file until it exits, after its cleanup, and a run that
# starts meanwhile waits. The file names the run that holds the lock.
lock_file="${XDG_RUNTIME_DIR:-${TMPDIR:-/tmp}}/tokkl-instrumented-tests-$(id -u).lock"
exec {lock_fd}<>"$lock_file"
if ! flock --nonblock "$lock_fd"; then
    holder=$(cat "$lock_file")
    echo "Waiting for the instrumented tests run that holds $lock_file: ${holder:-unknown}"
    waited_from=$SECONDS
    flock "$lock_fd"
    echo "Got $lock_file after $((SECONDS - waited_from)) s"
fi
printf 'PID %s, started %s in %s\n' "$$" "$(date '+%F %T')" "$PWD" >"$lock_file"
# An adb server or emulator that this script starts can outlive it. Neither
# gets the lock's descriptor, or the next run would wait for them too.
adb() { command adb "$@" {lock_fd}>&-; }

work_dir=$(mktemp -d)
container=''
proxy=''
emulator_pid=''
emulator_serial=''
emulator_unit=''
credentials_on_device=''
filter_on_device=''
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
        if [ -n "$emulator_unit" ]; then
            echo "$0: \"Memory cgroup out of memory\" there means qemu outgrew the $EMULATOR_MEMORY_MAX its scope may use, whatever memory was free." >&2
        fi
    else
        echo "$0: the emulator exited with status $status. Last emulator output:" >&2
        tail -n 20 "$work_dir/emulator.log" >&2
    fi
}

# Deletes the credentials file, and the test list if any, from each device
# they went to. sync writes the deletion to disk before an emulator this script
# booted is killed. Without it, the file was back on the next boot.
remove_device_files() {
    for device in "${devices_with_credentials[@]}"; do
        adb -s "$device" shell "rm -f $credentials_on_device $filter_on_device && sync" >/dev/null 2>&1 ||
            echo "$0: could not remove $credentials_on_device${filter_on_device:+ or $filter_on_device} from $device" >&2
    done
    devices_with_credentials=()
}

# Stops the emulator this script booted, if any, and its scope. Fails, after
# saying why, when the emulator had already exited on its own.
stop_emulator() {
    local status=0
    if [ -n "$emulator_pid" ] && ! kill -0 "$emulator_pid" 2>/dev/null; then
        report_emulator_exit
        status=1
    fi
    if [ -n "$emulator_pid" ]; then
        adb -s "$emulator_serial" emu kill >/dev/null 2>&1 || kill "$emulator_pid" 2>/dev/null || true
        wait "$emulator_pid" 2>/dev/null || true
        emulator_pid=''
    fi
    if [ -n "$emulator_unit" ]; then
        # Stops whatever is left in the scope. It is gone once qemu exits.
        systemctl --user stop "$emulator_unit" >/dev/null 2>&1 || true
        emulator_unit=''
    fi
    return "$status"
}

# ShellCheck 0.11 misses that the EXIT trap runs this.
# shellcheck disable=SC2329
cleanup() {
    local status=$?
    if [ -n "$proxy" ]; then
        docker rm --force --volumes "$proxy" >/dev/null 2>&1 ||
            echo "$0: could not remove container $proxy" >&2
    fi
    if [ -n "$container" ]; then
        docker rm --force --volumes "$container" >/dev/null 2>&1 ||
            echo "$0: could not remove container $container" >&2
    fi
    remove_device_files
    if ! stop_emulator && [ "$status" -eq 0 ]; then status=1; fi
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

# A run killed with SIGKILL, or ended along with the session that started it,
# skips the EXIT trap and leaves its credentials file on the device. This
# deletes those files on device $1 once they are STALE_CREDENTIALS_HOURS old,
# so a file that another run on the same device still reads stays, then syncs
# as the cleanup does. A device whose find rejects toybox's +6h syntax keeps
# its files and gets a warning.
remove_stale_credentials() {
    local device=$1 removed
    if removed=$(adb -s "$device" shell \
        "find $CREDENTIALS_DIR -maxdepth 1 -name '$CREDENTIALS_PREFIX*.properties' -mtime +${STALE_CREDENTIALS_HOURS}h -print -delete && sync"); then
        if [ -n "$removed" ]; then
            echo "Removed stale credentials files from $device: ${removed//$'\n'/ }"
        fi
    else
        echo "$0: could not look for stale credentials files on $device" >&2
    fi
}

# Prints the API level of the system image of the AVD named AVD, from its
# config.ini, or nothing when that file does not say.
avd_api_level() {
    local avd_home=${ANDROID_AVD_HOME:-${ANDROID_USER_HOME:-$HOME/.android}/avd} dir
    dir=$(sed -n 's/^path=//p' "$avd_home/$AVD.ini" 2>/dev/null)
    sed -n 's|^image\.sysdir\.1=system-images/android-\([0-9]*\)/.*|\1|p' "${dir:-$avd_home/$AVD.avd}/config.ini" 2>/dev/null
}

attached_devices() {
    adb devices | awk 'NR > 1 && $2 == "device" { print $1 }'
}

boot_emulator() {
    command -v emulator >/dev/null || { echo "$0: no device attached and emulator is not on PATH" >&2; exit 1; }
    local port=5554 log="$work_dir/emulator.log" deadline=$((SECONDS + 300)) caller emulator_sdk gpu=lavapipe level
    local -a launch=()
    while adb devices | grep -q "^emulator-${port}[[:space:]]"; do port=$((port + 2)); done
    emulator_serial="emulator-$port"
    if command -v systemd-run >/dev/null && systemctl --user show --property=Version >/dev/null 2>&1; then
        emulator_unit="tokkl-emulator-$port-$(random_hex 4).scope"
        launch=(systemd-run --user --scope --quiet --collect --unit="$emulator_unit"
            -p OOMPolicy=continue -p "MemoryMax=$EMULATOR_MEMORY_MAX" -p MemorySwapMax=0)
        if caller=$(systemctl --user whoami 2>/dev/null); then launch+=(-p "PartOf=$caller"); fi
        launch+=(--)
    fi
    # lavapipe is the renderer the emulator picked on its own for API 36, which
    # drew the screenshot references. On the API 31 image its Vulkan lacks
    # VK_KHR_external_semaphore_fd, which HWUI needs to draw with Vulkan (see
    # below), and SwiftShader's Vulkan has it. So images older than the
    # references' use SwiftShader.
    level=$(avd_api_level)
    if [[ $level =~ ^[0-9]+$ ]] && [ "$level" -lt 36 ]; then gpu=swiftshader_indirect; fi
    echo "Booting $AVD as $emulator_serial${emulator_unit:+ in $emulator_unit} with the $gpu renderer"
    # The emulator raises any smaller -memory to 2560 MB for an API 34 or later
    # image.
    "${launch[@]}" emulator -avd "$AVD" -port "$port" -memory 2560 -cores 4 -gpu "$gpu" \
        -no-window -no-audio -no-boot-anim -no-snapshot -accel on {lock_fd}>&- >"$log" 2>&1 &
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
    # The AVD keeps its font scale across boots, and SystemFontScale puts back
    # whatever it read before a test. Start every run at the default size, so a
    # test that sets no scale of its own does not run at a scale left behind.
    adb -s "$emulator_serial" shell settings put system font_scale 1.0
    # Focusing a text field never opened the soft keyboard on the headless
    # emulator, so no test saw a screen with the keyboard open. With this
    # setting, which shows it alongside a hardware keyboard, it opens.
    adb -s "$emulator_serial" shell settings put secure show_ime_with_hard_keyboard 1
    # The app needs Android 12 (API 31). With pipefail, one failed adb call
    # here would end the run without a message, so this asks again until it
    # gets a number, within the boot's deadline.
    emulator_sdk=$(adb -s "$emulator_serial" shell getprop ro.build.version.sdk | tr -d '\r') || true
    until [[ $emulator_sdk =~ ^[0-9]+$ ]]; do
        if [ "$SECONDS" -ge "$deadline" ]; then
            echo "$0: $AVD did not report its API level in 5 minutes." >&2
            exit 1
        fi
        sleep 2
        emulator_sdk=$(adb -s "$emulator_serial" shell getprop ro.build.version.sdk | tr -d '\r') || true
    done
    if [ "$emulator_sdk" -lt 31 ]; then
        echo "$0: $AVD runs API $emulator_sdk, but the app needs Android 12 (API 31) or later." >&2
        exit 1
    fi
    # qemu kept about 100 MB each time an app process that drew with OpenGL
    # ended, with lavapipe and with swiftshader, and the test orchestrator
    # starts a process for every test, so one full run took qemu to 11 GB. A
    # process that draws with Vulkan leaves about 5 MB. HWUI reads this when a
    # process starts, so it applies to every test. With lavapipe on API 31,
    # HWUI aborted every app at its first frame, missing
    # VK_KHR_external_semaphore_fd.
    adb -s "$emulator_serial" shell setprop debug.hwui.renderer skiavk
}

if [ -z "${ANDROID_SERIAL:-}" ] && [ -z "$(attached_devices)" ]; then
    boot_emulator
fi

setup_code=$(random_hex 16)
password=$(random_hex 16)
container="tokkl-android-tests-$(random_hex 4)"

echo "Starting $IMAGE as $container"
# The image runs as an unprivileged user, so the tmpfs must be writable by any
# UID. --env without a value reads the setup code from the environment. The
# proxy joins this container's network namespace, so its ports 8443 and 8081
# are published here too.
TOKKL_SETUP_CODE="$setup_code" docker run --detach --name "$container" \
    --env TOKKL_SETUP_CODE --tmpfs /data:mode=1777 \
    --publish 127.0.0.1::8080 --publish 127.0.0.1::8443 --publish 127.0.0.1::8081 "$IMAGE" >/dev/null
host_port=$(docker port "$container" 8080/tcp | head -n 1)
host_port=${host_port##*:}
tls_port=$(docker port "$container" 8443/tcp | head -n 1)
tls_port=${tls_port##*:}
prefix_port=$(docker port "$container" 8081/tcp | head -n 1)
prefix_port=${prefix_port##*:}
server="http://127.0.0.1:$host_port"

if ! curl --silent --fail --output /dev/null \
    --retry 30 --retry-delay 1 --retry-all-errors "$server/healthz"; then
    echo "$0: Tokkl did not answer on $server/healthz. Last server output:" >&2
    docker logs "$container" 2>&1 | grep -v 'setup code' | tail -n 20 >&2
    exit 1
fi

# The proxy's certificate and key, made for this run. HAProxy reads both from
# one file, as an unprivileged user in its container, so the file is readable
# by every user. Only this user can enter $work_dir, which holds it, and the
# cleanup deletes it. The tests get the certificate's fingerprint, never the
# key.
proxy_dir="$work_dir/proxy"
mkdir -m 755 "$proxy_dir"
(
    umask 077
    openssl req -x509 -newkey ec -pkeyopt ec_paramgen_curve:prime256v1 -nodes -days 2 \
        -subj '/CN=Tokkl instrumented tests' -addext 'subjectAltName=IP:10.0.2.2,IP:127.0.0.1' \
        -keyout "$work_dir/proxy-key.pem" -out "$proxy_dir/cert.pem" 2>/dev/null
)
cat "$proxy_dir/cert.pem" "$work_dir/proxy-key.pem" >"$proxy_dir/server.pem"
rm -f "$work_dir/proxy-key.pem"
chmod 644 "$proxy_dir/cert.pem" "$proxy_dir/server.pem"
tls_fingerprint=$(openssl x509 -in "$proxy_dir/cert.pem" -noout -fingerprint -sha256)
tls_fingerprint=${tls_fingerprint#*=}
cat >"$proxy_dir/haproxy.cfg" <<'CONFIG'
global
    log stdout format raw local0 warning

defaults
    mode http
    log global
    timeout connect 5s
    timeout client 30s
    timeout server 30s

frontend tls
    bind :8443 ssl crt /usr/local/etc/haproxy/server.pem
    default_backend tokkl

# Tokkl under /tokkl, with the prefix stripped on the way in. Every other path
# gets 404, so a client that drops the prefix fails instead of reaching Tokkl
# at the root.
frontend prefix
    bind :8081
    acl prefixed path /tokkl
    acl prefixed path_beg /tokkl/
    http-request deny deny_status 404 unless prefixed
    http-request replace-path ^/tokkl/?(.*)$ /\1
    default_backend tokkl

backend tokkl
    server tokkl 127.0.0.1:8080
CONFIG
chmod 644 "$proxy_dir/haproxy.cfg"
proxy="$container-tls"
echo "Starting $PROXY_IMAGE as $proxy"
docker run --detach --name "$proxy" --network "container:$container" \
    --volume "$proxy_dir:/usr/local/etc/haproxy:ro" "$PROXY_IMAGE" >/dev/null
# --cacert trusts this run's certificate alone, so curl checks it as the app does.
if ! curl --silent --fail --output /dev/null --cacert "$proxy_dir/cert.pem" \
    --retry 30 --retry-delay 1 --retry-all-errors "https://127.0.0.1:$tls_port/healthz"; then
    echo "$0: the TLS proxy did not answer on https://127.0.0.1:$tls_port/healthz. Last proxy output:" >&2
    docker logs "$proxy" 2>&1 | tail -n 20 >&2
    exit 1
fi
# The proxy is up, so the prefix answers at once, and the root of its port
# must not.
prefixed="http://127.0.0.1:$prefix_port/tokkl"
if ! curl --silent --fail --output /dev/null "$prefixed/healthz"; then
    echo "$0: the proxy did not serve Tokkl under $prefixed" >&2
    exit 1
fi
root_status=$(curl --silent --output /dev/null --write-out '%{http_code}' "http://127.0.0.1:$prefix_port/healthz")
if [ "$root_status" != 404 ]; then
    echo "$0: http://127.0.0.1:$prefix_port/healthz answered $root_status, not 404, so a client that drops the prefix would pass" >&2
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
credentials_on_device="$CREDENTIALS_DIR/$CREDENTIALS_PREFIX$(random_hex 4).properties"
(
    umask 077
    printf 'tokklPassword=%s\ntokklApiToken=%s\n' "$password" "$api_token" >"$credentials"
)
if [ -n "${ANDROID_SERIAL:-}" ]; then
    devices=("$ANDROID_SERIAL")
else
    mapfile -t devices < <(attached_devices)
fi
filter="$work_dir/test-filter.txt"

# Copies the credentials file to each test device, and the test list in
# $filter too once filter_on_device names its place there.
push_to_devices() {
    for device in "${devices[@]}"; do
        remove_stale_credentials "$device"
        devices_with_credentials+=("$device")
        adb -s "$device" push "$credentials" "$credentials_on_device" >/dev/null
        # adb push keeps this machine's modification time. touch sets the
        # device's own, the clock its stale file check reads.
        adb -s "$device" shell "chmod 600 $credentials_on_device && touch $credentials_on_device"
        # The test runner reads the list in the app's process, so every user
        # may read it. It names test classes and nothing secret.
        if [ -n "$filter_on_device" ]; then
            adb -s "$device" push "$filter" "$filter_on_device" >/dev/null
            adb -s "$device" shell "chmod 644 $filter_on_device"
        fi
    done
}

# Runs the tests once on every test device. Fails when Gradle fails, when a
# test device went away during the run, and when Gradle passed without running
# the tests on every device.
run_tests() {
    local status=0 device xml tests
    local -a gone=() results=()
    # A run that stops before its tests must not count the last run's results.
    rm -rf "$RESULTS"
    ./gradlew --no-daemon connectedDebugAndroidTest \
        "-P$RUNNER_ARGS.tokklBaseUrl=http://10.0.2.2:$host_port" \
        "-P$RUNNER_ARGS.tokklTlsBaseUrl=https://10.0.2.2:$tls_port" \
        "-P$RUNNER_ARGS.tokklPrefixedBaseUrl=http://10.0.2.2:$prefix_port/tokkl" \
        "-P$RUNNER_ARGS.tokklTlsFingerprint=$tls_fingerprint" \
        "-P$RUNNER_ARGS.tokklEmail=$EMAIL" \
        "-P$RUNNER_ARGS.tokklCredentialsFile=$credentials_on_device" \
        "${gradle_args[@]}" 2>&1 | tee "$work_dir/gradle.log" || status=$?
    # AGP's test task can pass when a device dies between two tests. With the
    # emulator killed a second into the run, Gradle printed BUILD SUCCESSFUL
    # and had run none of the 32 tests. So every test device must still be
    # online.
    for device in "${devices[@]}"; do
        if [ "$(adb -s "$device" get-state 2>/dev/null)" != device ]; then gone+=("$device"); fi
    done
    if [ "${#gone[@]}" -gt 0 ]; then
        echo "$0: ${gone[*]} went away during the run. Gradle can pass without running every test, so this run fails." >&2
        return 1
    fi
    if [ "$status" -ne 0 ]; then return "$status"; fi
    # AGP's test task also passes when it cannot start the tests. With another
    # copy of the app on the device, signed with another key, it logged
    # "AndroidTestRunner failed" and INSTALL_FAILED_UPDATE_INCOMPATIBLE, ran no
    # test, wrote no JUnit XML and printed BUILD SUCCESSFUL. On API 26 a copy
    # left by a killed run did the same with INSTALL_FAILED_ALREADY_EXISTS.
    if grep -q 'AndroidTestRunner failed' "$work_dir/gradle.log"; then
        echo "$0: Gradle passed, but the test runner failed on a device (\"AndroidTestRunner failed\" above), so this run fails." >&2
        return 1
    fi
    mapfile -t results < <(find "$RESULTS" -maxdepth 1 -name 'TEST-*.xml' 2>/dev/null)
    if [ "${#results[@]}" -lt "${#devices[@]}" ]; then
        echo "$0: Gradle passed, but $RESULTS holds results for ${#results[@]} of the ${#devices[@]} test devices, so this run fails." >&2
        return 1
    fi
    for xml in "${results[@]}"; do
        tests=$(sed -n 's/^<testsuites [^>]*\btests="\([0-9]*\)".*/\1/p;T;q' "$xml")
        if [ "${tests:-0}" -eq 0 ]; then
            echo "$0: Gradle passed, but $xml reports no test run, so this run fails." >&2
            return 1
        fi
    done
}

echo "Tokkl is ready on $server, which the emulator reaches as http://10.0.2.2:$host_port"
echo "The TLS proxy is ready on https://127.0.0.1:$tls_port, with certificate SHA-256 $tls_fingerprint"
echo "The proxy serves Tokkl under $prefixed, which the emulator reaches as http://10.0.2.2:$prefix_port/tokkl"
if [ "${#test_filter[@]}" -gt 0 ]; then
    printf '%s\n' "${test_filter[@]}" >"$filter"
    filter_on_device="$CREDENTIALS_DIR/$FILTER_PREFIX$(random_hex 4).txt"
    gradle_args+=("-P$RUNNER_ARGS.testFile=$filter_on_device")
fi
push_to_devices
run_tests
