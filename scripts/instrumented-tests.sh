#!/usr/bin/env bash
# Runs the instrumented tests against a real Tokkl server, never a fake one
# (docs/PRD.md section 7).
#
# 1. Starts ghcr.io/e0ipso/tokkl, pinned by digest, on a free port of
#    127.0.0.1 with its database on a tmpfs, and waits until it answers.
#    When Docker cannot bind a port it picked, the script removes the
#    container and tries once more.
# 2. Creates the first account with a random setup code and password, and
#    reads its API token from GET /api/v9/me.
# 3. Puts a TLS proxy, haproxy pinned by digest, in front of the same server,
#    with a self-signed certificate made for this run. The certificate names
#    10.0.2.2 and 127.0.0.1 as IP addresses, so host name checks pass. Its
#    private key stays in this script's temporary directory. On a second
#    port the proxy serves a second certificate, with the same names and
#    subject and a key of its own, as after the server's key was regenerated.
#    On a third port it serves a certificate for the same names that a CA
#    made for this run signed; see the user CA below. The same proxy serves
#    Tokkl over http under the path prefix /tokkl on a port of its own, as a
#    reverse proxy serves it beside other sites, and answers 404 there to
#    every path outside the prefix.
# 4. Copies the password and the API token to each test device, in a file
#    that only the shell user can read. Before that it deletes the
#    credentials files that killed runs left behind, once they are older than
#    any run lasts.
# 5. Runs connectedDebugAndroidTest with the http, the three https and the
#    prefixed server URLs, the SHA-256 fingerprints of both self-signed proxy
#    certificates, the email and that file's path as instrumentation
#    arguments. The emulator reaches the host's 127.0.0.1 as 10.0.2.2. The
#    credentials work only on this container. The run fails when a test
#    device goes away during it, even if Gradle reports success.
# 6. Removes both containers, the certificates' keys, the credentials file,
#    the test filter file, if any (see below), and the user CA, if it
#    installed one, also when a step fails.
#
# On an emulator it boots, the script installs this run's CA as a user CA, as
# Settings > Security > Install a certificate would, so a test can check that
# a server whose certificate a user CA signed connects with no prompt. Android
# reads user CAs from /data/misc/user/0/cacerts-added, one file each, named
# after the old OpenSSL hash of the CA's subject and .0, and only root may
# write there. The AOSP images are userdebug builds, whose su runs a command
# as root for the shell user, so the script copies the CA there with
# `adb shell su 0`, readable by every app, and deletes it during cleanup. The
# CA's subject never changes, so a run that died before its cleanup left its
# CA under the same name, and the next run replaces it. On any other device
# the script installs nothing, and the runner's notAnnotation argument leaves
# out the tests marked @UserCaInstalled.
#
# TOKKL_TEST_THROWAWAY_EMULATOR=1 vouches that every attached test device is
# an emulator that is thrown away after the run, as in CI, where
# android-emulator-runner boots it. The script then installs and removes the
# user CA there too, and runs the @UserCaInstalled tests. It refuses the
# variable, before it starts anything, for a device that is not an emulator
# (neither ro.kernel.qemu nor ro.boot.qemu is 1) or whose su does not run a
# command as root. It changes no other setting on such a device.
#
# The setup code, the password and the API token never appear in a process's
# arguments, which every user on the machine can read with ps.
#
# The tests run on the devices adb lists, or on ANDROID_SERIAL. With no device
# attached but the emulators of other runs, the script boots the AVD named by
# TOKKL_TEST_AVD (default tokkl-api36; tokkl-api31 runs Android 12, the oldest
# version the app supports) without a window, with 2.5 GB of guest RAM, 4
# cores and the lavapipe renderer, or SwiftShader below API 36, turns off
# error dialogs on it, sets its font scale to 1.0, lets its soft keyboard
# open, makes apps draw with Vulkan, and stops it at the end. It refuses an
# AVD older than Android 12, where the app does not install. It changes no
# setting on a device it did not boot.
#
# Up to TOKKL_TEST_SLOTS runs (default 2) test at once, each on an emulator
# of its own. Each run locks a slot file in $XDG_RUNTIME_DIR from its start
# until its cleanup ends. A run that finds every slot taken prints the PID,
# start time and directory of the runs holding them, and waits for one to
# end. A run boots its emulator on a free console port and passes its serial
# to Gradle as ANDROID_SERIAL, so Gradle never installs on another run's
# emulator. Runs boot one at a time, and while another run holds a slot, a run
# waits before booting until TOKKL_TEST_BOOT_MEMORY_MB of memory (default
# 9000) is available. A second emulator takes about 3.3 GB at once, and its
# run's Gradle more. On 2026-10-20, with the earlier default of 5000, a second
# emulator booted at 6.1 to 6.4 GB available left 2.9 GB, with under 900 MB of
# swap free. Then the kernel stalled every task for most of the time
# (/proc/pressure/memory "full" above 80%) for minutes, until the emulator's
# hang detector ended qemu (see report_emulator_log). Over that day's logs a
# paired emulator crashed 3.2 times an hour, one alone 0.4 times, and none in a
# run under 5 minutes.
# Two emulators cannot both write to one AVD's disk, so with more than one
# slot every emulator boots with -read-only, and drops what the run wrote to
# the AVD when it exits. Each also gets an XDG_RUNTIME_DIR, and so a netsimd,
# of its own, so that no emulator dies with another run's. With
# TOKKL_TEST_SLOTS=1 runs take turns and keep writing to the AVD. Give every
# run the same slot count: an emulator that writes to an AVD stops a
# -read-only one of the same AVD from booting.
#
# A run that tests on a device it did not boot, an attached phone or the
# emulator CI boots, waits until no other run tests, and then tests alone,
# since two runs would install on the same device. It holds a lock file in
# $XDG_RUNTIME_DIR alone, which every other run holds shared. A copy of this
# script from before the slots takes that file alone, so it takes turns with
# these runs. The last run to end stops the adb server, if a run of this
# script started it.
#
# A run killed with SIGKILL, or ended along with its session, skips its
# cleanup, and its emulator's scope, its two containers and its work
# directory outlive it. Each run keeps its temporary files in a work
# directory of its own under $XDG_RUNTIME_DIR, and its slot file names that
# directory and its server container before either exists. So once a run
# holds its slot, before it looks for attached devices, it stops every
# tokkl-emulator-*.scope, and removes every tokkl-android-tests-* container
# and every work directory, that no run holding a slot names. A copy of the
# script from before the work directories names no container, so while one
# holds a slot the containers stay. With TOKKL_TEST_AVD set, a run fails
# instead of testing on an attached device that is not an emulator of that
# AVD, or that runs another API level than the AVD's image.
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
#
# Every runner argument reaches both test APKs, the app's and the benchmarks'.
# A test APK given a class it does not hold fails the run with an
# initializationError, so a class= list that names a benchmark in
# com.github.e0ipso.tokkl.benchmark gives the benchmark runner a file of its
# own, as tokklBenchmarkTestFile, and the app's runner the other entries. Such
# a list must also name an app test, since a run in which the app's tests did
# not run fails, and must run :macrobenchmark:connectedBenchmarkAndroidTest.
# With that task, a class= list must name a benchmark. A package= list goes to
# both test APKs, since a package an APK does not hold selects nothing there.
# A run with that task fails when no benchmark ran, as it does for the app.
#
# Use either class= or package= in a run, never both, including single values.
# AndroidJUnitRunner 1.7.0 discovers tests in packages only when no explicit
# classes are given. Mixing them in testFile also runs just the named classes.
# The script rejects this combination before checking tools or taking a slot.
# To cover packages and extra classes, use separate runs or name every concrete
# class in one class= list.

set -euo pipefail

# Tokkl 1.8.1. To upgrade, take the index digest from
# `docker buildx imagetools inspect ghcr.io/e0ipso/tokkl:<version>`.
readonly IMAGE='ghcr.io/e0ipso/tokkl:1.8.1@sha256:70106ba2a6bc5b23c11f443f33bf2c7fb76d3af75b0335461047c4d27bd52e9b'
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
# The benchmark module's package, and the runner argument that hands the
# benchmark runner its own class= list (see the header and
# macrobenchmark/.../BenchmarkTestRunner.kt).
readonly BENCHMARK_PACKAGE='com.github.e0ipso.tokkl.benchmark'
readonly BENCHMARK_FILTER_ARG='tokklBenchmarkTestFile'
# A run takes minutes, so a credentials file this many hours old belongs to a
# run that ended without its cleanup, never to one still reading it.
readonly STALE_CREDENTIALS_HOURS=6
# The most memory the emulator's scope may use, swap included. A full run
# peaks below 4 GB on tokkl-api36.
readonly EMULATOR_MEMORY_MAX=6G
# Where Gradle puts the JUnit XML results of a run, one file for each test
# device.
readonly RESULTS='app/build/outputs/androidTest-results/connected/debug'
# Where Gradle puts them for :macrobenchmark:connectedBenchmarkAndroidTest.
readonly BENCHMARK_RESULTS='macrobenchmark/build/outputs/androidTest-results/connected/benchmark'
# Where Android keeps the CAs that user 0 installed (see the header).
readonly USER_CA_DIR='/data/misc/user/0/cacerts-added'
# The annotation of the tests that need this run's CA as a user CA.
readonly USER_CA_ANNOTATION='com.github.e0ipso.tokkl.UserCaInstalled'
# Whether the caller vouched that the attached devices are throwaway
# emulators (see the header).
readonly THROWAWAY_EMULATOR="${TOKKL_TEST_THROWAWAY_EMULATOR:-}"
# How many runs may test at once, each on an emulator of its own, and the
# memory, in MB, that must be available before a run boots an emulator while
# another run holds a slot (see the header).
readonly SLOTS="${TOKKL_TEST_SLOTS:-2}"
readonly BOOT_MEMORY_MB="${TOKKL_TEST_BOOT_MEMORY_MB:-9000}"

cd "$(dirname "$0")/.."

case $THROWAWAY_EMULATOR in
    '' | 1) ;;
    *)
        echo "$0: TOKKL_TEST_THROWAWAY_EMULATOR is \"$THROWAWAY_EMULATOR\". Set it to 1 to vouch that the attached devices are throwaway emulators, or leave it unset." >&2
        exit 2
        ;;
esac
if ! [[ $SLOTS =~ ^[1-9][0-9]*$ ]]; then
    echo "$0: TOKKL_TEST_SLOTS is \"$SLOTS\". Set it to the number of runs that may test at once, 1 or more." >&2
    exit 2
fi
if ! [[ $BOOT_MEMORY_MB =~ ^[0-9]+$ ]]; then
    echo "$0: TOKKL_TEST_BOOT_MEMORY_MB is \"$BOOT_MEMORY_MB\". Set it to a number of MB." >&2
    exit 2
fi

# The arguments for Gradle, and the entries of a class= or package= list,
# which go to the runner in a file (see the header). Checked before the run
# waits for its turn or boots anything.
gradle_args=()
test_filter=()
class_requested=''
package_requested=''
for arg in "$@"; do
    case $arg in
        "-P$RUNNER_ARGS.class="*) class_requested=1 ;;
        "-P$RUNNER_ARGS.package="*) package_requested=1 ;;
    esac
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
if [ -n "$class_requested" ] && [ -n "$package_requested" ]; then
    echo "$0: cannot combine class= and package= filters. AndroidJUnitRunner can omit package tests when explicit classes are given, also in testFile. Use separate runs or list every concrete class in class=. No test device has been allocated." >&2
    exit 2
fi
benchmark_task=''
for arg in "${gradle_args[@]}"; do
    case $arg in
        *connectedBenchmarkAndroidTest) benchmark_task=1 ;;
    esac
done
if [ "${#test_filter[@]}" -gt 0 ]; then
    for arg in "${gradle_args[@]}"; do
        case $arg in
            *"$RUNNER_ARGS.testFile="* | *"$RUNNER_ARGS.$BENCHMARK_FILTER_ARG="*)
                echo "$0: $arg cannot go with a class= or package= list, which the script passes as testFile." >&2
                exit 2
                ;;
        esac
    done
fi
# A class= list's benchmarks go to the benchmark runner alone (see the header).
benchmark_filter=()
if [ -n "$class_requested" ] && [ "${#test_filter[@]}" -gt 0 ]; then
    app_filter=()
    for entry in "${test_filter[@]}"; do
        case $entry in
            "$BENCHMARK_PACKAGE".*) benchmark_filter+=("$entry") ;;
            *) app_filter+=("$entry") ;;
        esac
    done
    if [ "${#benchmark_filter[@]}" -gt 0 ] && [ -z "$benchmark_task" ]; then
        echo "$0: the class= list names benchmarks, but the run has no :macrobenchmark:connectedBenchmarkAndroidTest to run them. No test device has been allocated." >&2
        exit 2
    fi
    if [ -n "$benchmark_task" ] && [ "${#benchmark_filter[@]}" -eq 0 ]; then
        echo "$0: the class= list names no benchmark in $BENCHMARK_PACKAGE, but the run has :macrobenchmark:connectedBenchmarkAndroidTest, whose APK cannot load the app's tests. No test device has been allocated." >&2
        exit 2
    fi
    if [ "${#benchmark_filter[@]}" -gt 0 ] && [ "${#app_filter[@]}" -eq 0 ]; then
        echo "$0: the class= list names only benchmarks. Add an app test, such as com.github.e0ipso.tokkl.api.ServerSmokeTest#apiTokenAuthenticatesWithGetMe, since a run in which the app's tests did not run fails. No test device has been allocated." >&2
        exit 2
    fi
    test_filter=("${app_filter[@]}")
fi
# A native permission probe runs alone, with permission reset before its test process starts.
# Keep this validation before device allocation, like the selector checks above.
fresh_notification_permission=${TOKKL_TEST_FRESH_NOTIFICATION_PERMISSION:-0}
case $fresh_notification_permission in
    0) ;;
    1)
        if [ "$#" -ne 1 ] || { [ "$1" != "-P$RUNNER_ARGS.class=com.github.e0ipso.tokkl.notification.NativeNotificationPermissionTest#allowingTheNativeDialogPostsTheAlreadyRunningTimer" ] &&
            [ "$1" != "-P$RUNNER_ARGS.class=com.github.e0ipso.tokkl.notification.NativeNotificationPermissionTest#denyingTheNativeDialogKeepsTheTimerAndOpensItsSettings" ] &&
            [ "$1" != "-P$RUNNER_ARGS.class=com.github.e0ipso.tokkl.pomodoro.NativePomodoroPermissionTest#grantingTheFirstSessionDialogKeepsTheSameStartedSession" ] &&
            [ "$1" != "-P$RUNNER_ARGS.class=com.github.e0ipso.tokkl.pomodoro.NativePomodoroPermissionTest#denyingThenRecreatingKeepsPromptsAndLaterStartsAskOnlyOnExplicitRetry" ]; }; then
            echo "$0: a fresh notification permission requires exactly one supported native notification-permission method, selected with class=, before startup." >&2
            exit 2
        fi
        gradle_args+=("-PtokklFreshNotificationPermission=true")
        ;;
    *) echo "$0: TOKKL_TEST_FRESH_NOTIFICATION_PERMISSION must be 0 or 1." >&2; exit 2 ;;
esac

for tool in docker adb curl jq openssl flock; do
    command -v "$tool" >/dev/null || { echo "$0: $tool is not on PATH" >&2; exit 1; }
done
# The lock files (see the header). A run holds a slot from here until it exits,
# after its cleanup. Its slot file names the run, and the emulator it boots, so
# a waiting run can say who holds the slots and no run tests on another one's
# emulator. Every run holds the device lock too: shared while it tests on an
# emulator it boots, alone when it tests on a device it did not boot. Before
# slots, each run held the device lock alone, so a checkout with an older copy
# of this script still takes turns with these runs. One run boots an emulator
# at a time, under the boot lock. The marker says that a run of this script
# started the adb server, which the last run to end then stops.
lock_prefix="${XDG_RUNTIME_DIR:-${TMPDIR:-/tmp}}/tokkl-instrumented-tests-$(id -u)"
# Each run keeps its temporary files in a directory of its own in here, which
# its slot file names, so a later run can delete what a killed run left.
runs_dir="$lock_prefix-runs"
device_lock="$lock_prefix.lock"
boot_lock="$lock_prefix-boot.lock"
adb_marker="$lock_prefix-adb-started"
slot_file() { echo "$lock_prefix-slot-$1.lock"; }
exec {device_fd}<>"$device_lock" {boot_fd}<>"$boot_lock"
slot=''
slot_fd=''
run_testing=''
work_dir=''
container=''
emulator_tmp=''

random_hex() {
    od -An -N"$1" -tx1 /dev/urandom | tr -d ' \n'
}

# Prints the slot files that runs other than this one hold.
held_slot_files() {
    local file
    for file in "$lock_prefix"-slot-*.lock; do
        if [ -e "$file" ] && [ "$file" != "$(slot_file "$slot")" ] &&
            ! flock --nonblock --shared "$file" true; then
            echo "$file"
        fi
    done
}

# Prints the run that holds each slot, as its slot file names it.
print_slot_holders() {
    local file n
    while read -r file; do
        n=${file##*-slot-}
        printf '  slot %s: %s\n' "${n%.lock}" "$(cat "$file")"
    done < <(held_slot_files)
}

# Prints the serials of the emulators that other runs booted.
owned_serials() {
    local file
    while read -r file; do
        sed -n 's/.*, testing on \(emulator-[0-9]*\).*/\1/p' "$file"
    done < <(held_slot_files)
}

# Takes the first free of the SLOTS slots, or waits for one.
take_slot() {
    local n fd waited_from=''
    while :; do
        for ((n = 1; n <= SLOTS; n++)); do
            exec {fd}<>"$(slot_file "$n")"
            if flock --nonblock "$fd"; then
                slot=$n
                slot_fd=$fd
                break 2
            fi
            exec {fd}>&-
        done
        if [ -z "$waited_from" ]; then
            waited_from=$SECONDS
            echo "Waiting for one of the $SLOTS instrumented tests slots, held by:"
            print_slot_holders
        fi
        sleep 5
    done
    if [ -n "$waited_from" ]; then echo "Got slot $slot after $((SECONDS - waited_from)) s"; fi
}

# Writes what this run is to its slot file: what it tests on, from $1 if
# given, its work directory and its server container. Each one is named here
# before it exists, so no other run takes it for a killed run's leftover.
describe_run() {
    if [ $# -gt 0 ]; then run_testing=$1; fi
    printf 'PID %s, started %s in %s%s%s%s%s\n' "$$" "$run_started" "$PWD" "${run_testing:+, $run_testing}" \
        "${work_dir:+, work directory $work_dir}" "${container:+, server $container}" \
        "${emulator_tmp:+, temporary emulator data $emulator_tmp}" >"$(slot_file "$slot")"
}

# Takes the device lock shared ($1 is --shared) or alone, saying who holds it
# while it waits.
take_device_lock() {
    local waited_from=$SECONDS
    if flock --nonblock "$@" "$device_fd"; then return; fi
    if [ "${1:-}" = --shared ]; then
        echo "Waiting for the run that holds $device_lock alone: $(cat "$device_lock")"
    else
        echo "This run tests on a device it did not boot, so it waits until no other run tests. Other runs:"
        print_slot_holders
    fi
    flock "$@" "$device_fd"
    echo "Got $device_lock after $((SECONDS - waited_from)) s"
}

run_started=$(date '+%F %T')
# Named before the run takes a slot, so every slot file of this script names a
# work directory. One that names none comes from an older copy of the script.
work_dir="$runs_dir/$(random_hex 4)"
take_slot
describe_run
take_device_lock --shared
# An adb server or emulator that this script starts can outlive it. Neither
# gets the lock files' descriptors, or the next run would wait for them too.
without_locks() { "$@" {slot_fd}>&- {device_fd}>&- {boot_fd}>&-; }
adb() { without_locks command adb "$@"; }
if ! pgrep -x adb >/dev/null; then : >"$adb_marker"; fi

mkdir -m 700 "$runs_dir" 2>/dev/null || true
if [ -L "$runs_dir" ] || [ ! -d "$runs_dir" ] || [ ! -O "$runs_dir" ]; then
    echo "$0: $runs_dir is not a directory of your own" >&2
    exit 1
fi
mkdir -m 700 "$work_dir"
proxy=''
emulator_pid=''
emulator_serial=''
emulator_unit=''
credentials_on_device=''
filter_on_device=''
benchmark_filter_on_device=''
user_ca_on_device=''
devices_with_credentials=()
# The devices this run installed its CA on as a user CA.
devices_with_user_ca=()

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
        echo "$0: the emulator exited with status $status." >&2
        report_emulator_log
    fi
}

# Prints why the emulator ended, from its log. When a thread of qemu makes no
# progress for about 15 s, the emulator's hang detector logs "detected a
# hanging thread" and ends qemu with a crash dump, which the shell reports as
# a segmentation fault. In the cores of 2026-10-20 the threads it waited for
# held a lock while freeing memory or reading a file, so qemu had stalled as a
# whole, as it does when the machine runs out of memory. Then crashpad fills
# the end of the log with ptrace errors, which this leaves out. It also prints
# the memory available and the memory pressure at that moment.
report_emulator_log() {
    local log="$work_dir/emulator.log" hangs
    hangs=$(grep -E 'detected a hanging thread|FATAL' "$log" 2>/dev/null | tail -n 10) || true
    if [ -n "$hangs" ]; then
        echo "$0: qemu stopped responding, and its hang detector ended it:" >&2
        echo "$hangs" >&2
    fi
    echo "$0: last emulator output:" >&2
    grep -v 'scoped_ptrace_attach' "$log" 2>/dev/null | tail -n 20 >&2 || true
    echo "$0: $(available_memory_mb) MB of memory available, $(awk '/^SwapFree:/ { print int($2 / 1024) }' /proc/meminfo) MB of swap free. Memory pressure:" >&2
    cat /proc/pressure/memory >&2 2>/dev/null || true
}

# Deletes the credentials file, and the test list if any, from each device
# they went to. sync writes the deletion to disk before an emulator this script
# booted is killed. Without it, the file was back on the next boot.
remove_device_files() {
    for device in "${devices_with_credentials[@]}"; do
        adb -s "$device" shell "rm -f $credentials_on_device $filter_on_device $benchmark_filter_on_device && sync" >/dev/null 2>&1 ||
            echo "$0: could not remove $credentials_on_device${filter_on_device:+ or $filter_on_device}${benchmark_filter_on_device:+ or $benchmark_filter_on_device} from $device" >&2
    done
    devices_with_credentials=()
}

# Deletes the user CA that install_user_ca put on each device, and the copy it
# staged beside the credentials file, then syncs, as remove_device_files does.
remove_user_ca() {
    for device in "${devices_with_user_ca[@]}"; do
        adb -s "$device" shell "su 0 rm -f $user_ca_on_device $CREDENTIALS_DIR/tokkl-test-user-ca.pem && sync" >/dev/null 2>&1 ||
            echo "$0: could not remove the user CA $user_ca_on_device from $device" >&2
    done
    devices_with_user_ca=()
}

# Stops the emulator this script booted, if any, and its scope. Fails, after
# saying why, when the emulator had already exited on its own.
stop_emulator() {
    local status=0 stopped=1
    # A signal can arrive between forking the launcher and recording $!.
    # An inactive scope then does not prove that launch cannot still happen.
    if [ -f "$work_dir/emulator-launching" ] && [ -z "$emulator_pid" ]; then
        echo "$0: emulator launch ownership is incomplete; retaining $work_dir" >&2
        stopped=0
        status=1
    fi
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
        if systemctl --user stop "$emulator_unit" >/dev/null 2>&1 || emulator_scope_stopped "$emulator_unit"; then
            emulator_unit=''
        else
            echo "$0: could not confirm $emulator_unit stopped; retaining $work_dir" >&2
            stopped=0
            status=1
        fi
    fi
    if [ "$stopped" -eq 1 ] && [ -d "$work_dir/emulator-tmp" ]; then
        : >"$work_dir/emulator-stopped"
    fi
    return "$status"
}

# A collected scope reports inactive too. A failed query or any other state
# cannot prove that qemu and its helpers released their files.
emulator_scope_stopped() {
    local state
    state=$(systemctl --user show --property=ActiveState --value "$1" 2>/dev/null) && [ "$state" = inactive ]
}

# Only allocations named before launch belong to this run. A SIGKILL can
# skip shutdown, so orphan recovery must prove the recorded scope stopped
# before deleting its directory. Without a scope, retain an orphan rather
# than guess whether its emulator is still alive from a reused PID.
remove_run_directory() {
    local dir=$1 unit pid
    if [ -d "$dir/emulator-tmp" ] && [ ! -f "$dir/emulator-stopped" ]; then
        unit=$(cat "$dir/emulator.scope" 2>/dev/null) || unit=''
        pid=$(cat "$dir/emulator.pid" 2>/dev/null) || pid=''
        # A reused PID only retains data unnecessarily; it never authorizes
        # deletion. A live launcher can still be registering its scope.
        if [ -f "$dir/emulator-launching" ] || ! [[ $pid =~ ^[1-9][0-9]*$ ]] || kill -0 "$pid" 2>/dev/null ||
            ! [[ $unit =~ ^tokkl-emulator-[0-9]+-[0-9a-f]+\.scope$ ]] || ! emulator_scope_stopped "$unit"; then
            echo "$0: retaining $dir; temporary emulator data may still be in use" >&2
            return 1
        fi
    fi
    rm -rf -- "$dir"
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
    remove_user_ca
    if ! stop_emulator && [ "$status" -eq 0 ]; then status=1; fi
    # The last run to end stops the adb server that a run of this script
    # started. A run that holds the device lock alone is the only one left.
    if [ -e "$adb_marker" ] && flock --nonblock "$device_fd"; then
        adb kill-server >/dev/null 2>&1 || true
        rm -f "$adb_marker"
    fi
    if ! remove_run_directory "$work_dir" && [ "$status" -eq 0 ]; then status=1; fi
    exit "$status"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

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

# Prints the memory available, in MB.
available_memory_mb() {
    awk '/^MemAvailable:/ { print int($2 / 1024) }' /proc/meminfo
}

# Waits until BOOT_MEMORY_MB of memory is available, or no other run holds a
# slot, so that another run's emulator and this one never push the machine
# into an OOM.
wait_for_memory() {
    local available waited_from=''
    available=$(available_memory_mb)
    while [ "$available" -lt "$BOOT_MEMORY_MB" ] && [ -n "$(held_slot_files)" ]; do
        if [ -z "$waited_from" ]; then
            waited_from=$SECONDS
            echo "Waiting for $BOOT_MEMORY_MB MB of available memory before booting a second emulator. $available MB are available."
        fi
        sleep 5
        available=$(available_memory_mb)
    done
    if [ -n "$waited_from" ]; then
        echo "Booting after waiting $((SECONDS - waited_from)) s for memory, with $available MB available"
    fi
}

boot_emulator() {
    command -v emulator >/dev/null || { echo "$0: no device attached and emulator is not on PATH" >&2; exit 1; }
    local port=5554 log="$work_dir/emulator.log" deadline caller emulator_sdk gpu=lavapipe level waited_from=$SECONDS
    local -a launch=() instance_args=() emulator_env=()
    # One run boots at a time, so the next one sees this one's emulator in adb
    # and in the memory it takes.
    if ! flock --nonblock "$boot_fd"; then
        echo "Waiting for another run to boot its emulator"
        flock "$boot_fd"
        echo "Got $boot_lock after $((SECONDS - waited_from)) s"
    fi
    wait_for_memory
    deadline=$((SECONDS + 300))
    while adb devices | grep -q "^emulator-${port}[[:space:]]" || owned_serials | grep -qx "emulator-$port"; do
        port=$((port + 2))
    done
    emulator_serial="emulator-$port"
    # Two emulators cannot both write to one AVD's disk. With -read-only each
    # keeps what it writes to itself, and drops it when it exits. An emulator
    # also starts netsimd, which serves Wi-Fi and Bluetooth, unless it finds
    # one in $XDG_RUNTIME_DIR/netsim.ini. A second emulator used the first
    # one's, inside the first one's scope, and aborted when that scope
    # stopped. So each emulator gets a runtime directory, and a netsimd, of
    # its own.
    if [ "$SLOTS" -gt 1 ]; then
        instance_args=(-read-only)
        mkdir -m 700 "$work_dir/runtime"
        emulator_env=("XDG_RUNTIME_DIR=$work_dir/runtime")
    fi
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
    # System::getTempDir uses ANDROID_TMP on Linux, otherwise /tmp/android-$USER.
    # Record ownership before launch, including if startup or a signal fails.
    # Keep AVD images and other runs' temporary files outside this allocation.
    emulator_tmp="$work_dir/emulator-tmp"
    # The slot file names the emulator before it starts, so another run never
    # takes it for an attached device.
    describe_run "testing on $emulator_serial${emulator_unit:+ in $emulator_unit}"
    printf '%s\n' "$emulator_unit" >"$work_dir/emulator.scope"
    mkdir -m 700 "$emulator_tmp"
    emulator_env=(env "ANDROID_TMP=$emulator_tmp" "${emulator_env[@]}")
    echo "Booting $AVD as $emulator_serial${emulator_unit:+ in $emulator_unit} with the $gpu renderer${instance_args[*]:+, ${instance_args[*]}}"
    # The emulator raises any smaller -memory to 2560 MB for an API 34 or later
    # image.
    : >"$work_dir/emulator-launching"
    "${launch[@]}" "${emulator_env[@]}" emulator -avd "$AVD" -port "$port" -memory 2560 -cores 4 -gpu "$gpu" \
        -no-window -no-audio -no-boot-anim -no-snapshot "${instance_args[@]}" -accel on \
        {slot_fd}>&- {device_fd}>&- {boot_fd}>&- >"$log" 2>&1 &
    emulator_pid=$!
    printf '%s\n' "$emulator_pid" >"$work_dir/emulator.pid"
    rm -f "$work_dir/emulator-launching"
    # Gradle tests on this emulator alone, never on another run's.
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
    # Gradle tests on whatever answers as this serial, so it must be the
    # emulator this run booted.
    read_device "$emulator_serial"
    if [ "$device_avd" != "$AVD" ]; then
        echo "$0: $emulator_serial runs ${device_avd:-no AVD that it names}, not $AVD." >&2
        exit 1
    fi
    echo "$emulator_serial runs $device_avd on API $emulator_sdk"
    # qemu kept about 100 MB each time an app process that drew with OpenGL
    # ended, with lavapipe and with swiftshader, and the test orchestrator
    # starts a process for every test, so one full run took qemu to 11 GB. A
    # process that draws with Vulkan leaves about 5 MB. HWUI reads this when a
    # process starts, so it applies to every test. With lavapipe on API 31,
    # HWUI aborted every app at its first frame, missing
    # VK_KHR_external_semaphore_fd.
    adb -s "$emulator_serial" shell setprop debug.hwui.renderer skiavk
    flock --unlock "$boot_fd"
}

# Refuses TOKKL_TEST_THROWAWAY_EMULATOR for device $1 unless it is an emulator
# whose su runs a command as root (see the header).
check_throwaway_emulator() {
    local device=$1 qemu uid
    qemu=$(adb -s "$device" shell 'getprop ro.kernel.qemu; getprop ro.boot.qemu' | tr -d '\r') || qemu=''
    if ! grep -qx 1 <<<"$qemu"; then
        echo "$0: TOKKL_TEST_THROWAWAY_EMULATOR is 1, but $device is not an emulator: neither ro.kernel.qemu nor ro.boot.qemu is 1. The script installs a user CA only on an emulator." >&2
        exit 2
    fi
    uid=$(adb -s "$device" shell 'su 0 id -u' 2>/dev/null | tr -d '\r') || uid=''
    if [ "$uid" != 0 ]; then
        echo "$0: TOKKL_TEST_THROWAWAY_EMULATOR is 1, but su on $device does not run a command as root, so the script cannot install a user CA there. Use a userdebug image, such as the default system images." >&2
        exit 2
    fi
}

# Prints the attached devices that no other run booted.
foreign_devices() {
    attached_devices | grep -vxF -f <(owned_serials) || true
}

# Stops the emulators that runs left behind: every tokkl-emulator-*.scope that
# no run holding a slot names. A run killed with SIGKILL, or ended along with
# its session, skips its cleanup, and its emulator's scope outlives it. Every
# other run would then take that emulator for a device the user attached. The
# scopes are listed before the slot files are read, so an emulator that
# another run starts meanwhile is named in its slot file by then.
stop_leftover_emulators() {
    local units unit owned serial deadline file
    if ! command -v systemctl >/dev/null || ! systemctl --user show --property=Version >/dev/null 2>&1; then
        return
    fi
    units=$(systemctl --user list-units --type=scope --plain --no-legend 'tokkl-emulator-*.scope' | awk '{ print $1 }')
    owned=$(while read -r file; do grep -o 'tokkl-emulator-[0-9]*-[0-9a-f]*\.scope' "$file" || true; done < <(held_slot_files))
    while read -r unit; do
        if [ -z "$unit" ] || grep -qxF "$unit" <<<"$owned"; then continue; fi
        echo "Stopping $unit, an emulator that no running test run booted"
        systemctl --user stop "$unit" >/dev/null 2>&1 || echo "$0: could not stop $unit" >&2
        serial=${unit#tokkl-emulator-}
        serial="emulator-${serial%%-*}"
        deadline=$((SECONDS + 30))
        while [ "$(adb -s "$serial" get-state 2>/dev/null)" = device ] && [ "$SECONDS" -lt "$deadline" ]; do
            sleep 1
        done
    done <<<"$units"
}

# Sets device_avd to the name of the AVD that device $1 runs, or to nothing
# when it is not an emulator, and device_sdk to its API level.
read_device() {
    device_avd=$(adb -s "$1" emu avd name 2>/dev/null | sed -n 1p | tr -d '\r') || device_avd=''
    device_sdk=$(adb -s "$1" shell getprop ro.build.version.sdk 2>/dev/null | tr -d '\r') || device_sdk=''
}

# Fails the run when TOKKL_TEST_AVD names an AVD and test device $1 is not an
# emulator of that AVD, or runs another API level than the AVD's image.
check_requested_avd() {
    local device=$1 level what
    if [ -z "${TOKKL_TEST_AVD:-}" ]; then return; fi
    read_device "$device"
    level=$(avd_api_level)
    if [ "$device_avd" = "$AVD" ] && { [ -z "$level" ] || [ "$device_sdk" = "$level" ]; }; then return; fi
    what=${device_avd:+an emulator of $device_avd}
    echo "$0: TOKKL_TEST_AVD is $AVD${level:+, API $level}, but the attached $device is ${what:-not an emulator of an AVD} on API ${device_sdk:-unknown}. Stop that device, or unset TOKKL_TEST_AVD to test on it." >&2
    exit 2
}

# Checks the requested AVD on each device this run would test on.
check_test_devices() {
    local device
    local -a list=()
    if [ -n "${ANDROID_SERIAL:-}" ]; then list=("$ANDROID_SERIAL"); else mapfile -t list < <("$@"); fi
    for device in "${list[@]}"; do check_requested_avd "$device"; done
}

# Removes the server containers and the work directories that runs left
# behind: every tokkl-android-tests-* container and every directory in
# runs_dir that no run holding a slot names, but this run's own directory. A
# killed run skips its cleanup, so its containers keep their ports and memory,
# and its directory keeps the proxy's keys. Both are listed before the slot
# files are read, as in stop_leftover_emulators.
remove_leftover_runs() {
    local containers dirs owned_servers owned_dirs name dir file
    containers=$(docker ps --all --format '{{.Names}}' | grep -x 'tokkl-android-tests-[0-9a-f]*\(-tls\)\?' || true)
    dirs=$(find "$runs_dir" -mindepth 1 -maxdepth 1 -type d)
    owned_servers=$(while read -r file; do
        sed -n 's/.*, server \(tokkl-android-tests-[0-9a-f]*\).*/\1/p' "$file"
    done < <(held_slot_files))
    owned_dirs=$(while read -r file; do
        sed -n 's/.*, work directory \([^,]*\).*/\1/p' "$file"
    done < <(held_slot_files))
    # A copy of the script from before this names no work directory or
    # server, so while one holds a slot no container counts as left behind.
    while read -r file; do
        if ! grep -qF ', work directory ' "$file"; then
            echo "Leaving the server containers alone while an older copy of this script runs: $(cat "$file")"
            containers=''
        fi
    done < <(held_slot_files)
    while read -r name; do
        if [ -z "$name" ] || grep -qxF -- "${name%-tls}" <<<"$owned_servers"; then continue; fi
        echo "Removing container $name, which no running test run started"
        docker rm --force --volumes "$name" >/dev/null 2>&1 || echo "$0: could not remove container $name" >&2
    done <<<"$containers"
    while read -r dir; do
        if [ -z "$dir" ] || [ "$dir" = "$work_dir" ] || grep -qxF -- "$dir" <<<"$owned_dirs"; then
            continue
        fi
        echo "Removing $dir, which no running test run uses"
        remove_run_directory "$dir" || true
    done <<<"$dirs"
}

stop_leftover_emulators
remove_leftover_runs
if [ -z "${ANDROID_SERIAL:-}" ] && [ -z "$(foreign_devices)" ]; then
    boot_emulator
else
    if [ -n "${ANDROID_SERIAL:-}" ] && owned_serials | grep -qxF "$ANDROID_SERIAL"; then
        echo "$0: ANDROID_SERIAL is $ANDROID_SERIAL, an emulator that another run booted and stops when it ends. Unset ANDROID_SERIAL to let this run boot an emulator of its own." >&2
        exit 2
    fi
    # Fails before waiting for a device that waiting does not change.
    check_test_devices foreign_devices
    # Other runs would install on the same device, so this run tests alone.
    take_device_lock
    printf 'PID %s, started %s in %s\n' "$$" "$run_started" "$PWD" >"$device_lock"
    describe_run 'testing on devices it did not boot'
    # The attached devices may have gone while this run waited.
    if [ -z "${ANDROID_SERIAL:-}" ] && [ -z "$(attached_devices)" ]; then
        boot_emulator
    else
        check_test_devices attached_devices
    fi
fi
# The devices that get this run's CA as a user CA: the emulator this script
# booted, or every test device the caller vouched for.
user_ca_devices=()
if [ -n "$emulator_serial" ]; then
    user_ca_devices=("$emulator_serial")
elif [ "$THROWAWAY_EMULATOR" = 1 ]; then
    if [ -n "${ANDROID_SERIAL:-}" ]; then
        user_ca_devices=("$ANDROID_SERIAL")
    else
        mapfile -t user_ca_devices < <(attached_devices)
    fi
    for device in "${user_ca_devices[@]}"; do check_throwaway_emulator "$device"; done
fi

setup_code=$(random_hex 16)
password=$(random_hex 16)
container="tokkl-android-tests-$(random_hex 4)"
describe_run

echo "Starting $IMAGE as $container"
# The image runs as an unprivileged user, so the tmpfs must be writable by any
# UID. --env without a value reads the setup code from the environment. The
# proxy joins this container's network namespace, so its ports 8443, 8444,
# 8445 and 8081 are published here too.
# Docker picks the host ports, and another container on the machine can take
# one it picked before it binds it. So a bind error gets one more try, after
# removing the container that the failed try created.
start_server() {
    local attempt errors="$work_dir/docker-run.log"
    for attempt in 1 2; do
        if TOKKL_SETUP_CODE="$setup_code" docker run --detach --name "$container" \
            --env TOKKL_SETUP_CODE --tmpfs /data:mode=1777 \
            --publish 127.0.0.1::8080 --publish 127.0.0.1::8443 --publish 127.0.0.1::8444 \
            --publish 127.0.0.1::8445 --publish 127.0.0.1::8081 "$IMAGE" >/dev/null 2>"$errors"; then
            return 0
        fi
        cat "$errors" >&2
        if [ "$attempt" -eq 2 ] || ! grep -q -e 'address already in use' -e 'port is already allocated' "$errors"; then
            return 1
        fi
        echo "Docker could not bind a host port it picked. Removing $container and starting it again."
        docker rm --force --volumes "$container" >/dev/null 2>&1 || true
    done
}
if ! start_server; then
    echo "$0: could not start $container" >&2
    exit 1
fi
host_port=$(docker port "$container" 8080/tcp | head -n 1)
host_port=${host_port##*:}
tls_port=$(docker port "$container" 8443/tcp | head -n 1)
tls_port=${tls_port##*:}
new_key_port=$(docker port "$container" 8444/tcp | head -n 1)
new_key_port=${new_key_port##*:}
user_ca_port=$(docker port "$container" 8445/tcp | head -n 1)
user_ca_port=${user_ca_port##*:}
prefix_port=$(docker port "$container" 8081/tcp | head -n 1)
prefix_port=${prefix_port##*:}
server="http://127.0.0.1:$host_port"

if ! curl --silent --fail --output /dev/null \
    --retry 30 --retry-delay 1 --retry-all-errors "$server/healthz"; then
    echo "$0: Tokkl did not answer on $server/healthz. Last server output:" >&2
    docker logs "$container" 2>&1 | grep -v 'setup code' | tail -n 20 >&2
    exit 1
fi

# The proxy's certificates and keys, made for this run. HAProxy reads each
# certificate and its key from one file, as an unprivileged user in its
# container, so the file is readable by every user. Only this user can enter
# $work_dir, which holds it, and the cleanup deletes it. The tests get the
# certificates' fingerprints, never the keys.
proxy_dir="$work_dir/proxy"
mkdir -m 755 "$proxy_dir"

# Makes a self-signed certificate with a new key in $proxy_dir/$1-cert.pem,
# and the file HAProxy reads, the certificate followed by its key, in
# $proxy_dir/$1.pem.
make_proxy_certificate() {
    local name=$1
    (
        umask 077
        openssl req -x509 -newkey ec -pkeyopt ec_paramgen_curve:prime256v1 -nodes -days 2 \
            -subj '/CN=Tokkl instrumented tests' -addext 'subjectAltName=IP:10.0.2.2,IP:127.0.0.1' \
            -keyout "$work_dir/$name-key.pem" -out "$proxy_dir/$name-cert.pem" 2>/dev/null
    )
    cat "$proxy_dir/$name-cert.pem" "$work_dir/$name-key.pem" >"$proxy_dir/$name.pem"
    rm -f "$work_dir/$name-key.pem"
    chmod 644 "$proxy_dir/$name-cert.pem" "$proxy_dir/$name.pem"
}

# Prints the SHA-256 fingerprint of the certificate in file $1, as AB:CD:...
fingerprint() {
    local line
    line=$(openssl x509 -in "$1" -noout -fingerprint -sha256)
    echo "${line#*=}"
}

# The certificate the TLS proxy serves, and a second one for the same names,
# with a key of its own, as the server would serve after its key was
# regenerated. The second one has a port of its own. To the app a port is a
# server of its own, so a test that swaps the key relays one host and port to
# the first port, then to the second.
make_proxy_certificate tls
make_proxy_certificate new-key
tls_fingerprint=$(fingerprint "$proxy_dir/tls-cert.pem")
new_key_fingerprint=$(fingerprint "$proxy_dir/new-key-cert.pem")

# A CA made for this run, which the script installs as a user CA (see the
# header), and a certificate for the same names that it signs, which the
# proxy serves on a third port. The CA's key signs that one certificate and is
# deleted at once.
(
    umask 077
    openssl req -x509 -newkey ec -pkeyopt ec_paramgen_curve:prime256v1 -nodes -days 2 \
        -subj '/CN=Tokkl instrumented tests CA' \
        -addext 'basicConstraints=critical,CA:TRUE' -addext 'keyUsage=critical,keyCertSign,cRLSign' \
        -keyout "$work_dir/user-ca-key.pem" -out "$work_dir/user-ca.pem" 2>/dev/null
    openssl req -new -newkey ec -pkeyopt ec_paramgen_curve:prime256v1 -nodes \
        -subj '/CN=Tokkl instrumented tests' \
        -keyout "$work_dir/user-ca-leaf-key.pem" -out "$work_dir/user-ca-leaf.csr" 2>/dev/null
    printf '%s\n' 'basicConstraints=critical,CA:FALSE' 'keyUsage=critical,digitalSignature' \
        'extendedKeyUsage=serverAuth' 'subjectAltName=IP:10.0.2.2,IP:127.0.0.1' >"$work_dir/user-ca-leaf.ext"
    openssl x509 -req -in "$work_dir/user-ca-leaf.csr" -days 2 -set_serial "0x$(random_hex 16)" \
        -CA "$work_dir/user-ca.pem" -CAkey "$work_dir/user-ca-key.pem" -extfile "$work_dir/user-ca-leaf.ext" \
        -out "$proxy_dir/user-ca-leaf-cert.pem" 2>/dev/null
)
rm -f "$work_dir/user-ca-key.pem" "$work_dir/user-ca-leaf.csr" "$work_dir/user-ca-leaf.ext"
cat "$proxy_dir/user-ca-leaf-cert.pem" "$work_dir/user-ca-leaf-key.pem" >"$proxy_dir/user-ca-leaf.pem"
rm -f "$work_dir/user-ca-leaf-key.pem"
chmod 644 "$proxy_dir/user-ca-leaf-cert.pem" "$proxy_dir/user-ca-leaf.pem"
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
    bind :8443 ssl crt /usr/local/etc/haproxy/tls.pem
    default_backend tokkl

# The same server with the second certificate, as after its key was
# regenerated.
frontend new_key
    bind :8444 ssl crt /usr/local/etc/haproxy/new-key.pem
    default_backend tokkl

# The same server with the certificate that this run's CA signed.
frontend user_ca
    bind :8445 ssl crt /usr/local/etc/haproxy/user-ca-leaf.pem
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
# --cacert trusts one of this run's certificates alone, so curl checks it as
# the app does.
if ! curl --silent --fail --output /dev/null --cacert "$proxy_dir/tls-cert.pem" \
    --retry 30 --retry-delay 1 --retry-all-errors "https://127.0.0.1:$tls_port/healthz"; then
    echo "$0: the TLS proxy did not answer on https://127.0.0.1:$tls_port/healthz. Last proxy output:" >&2
    docker logs "$proxy" 2>&1 | tail -n 20 >&2
    exit 1
fi
# The proxy is up, so the second certificate's port answers at once.
if ! curl --silent --fail --output /dev/null --cacert "$proxy_dir/new-key-cert.pem" \
    "https://127.0.0.1:$new_key_port/healthz"; then
    echo "$0: the TLS proxy did not answer with the second certificate on https://127.0.0.1:$new_key_port/healthz" >&2
    exit 1
fi
# Here curl trusts the CA alone, so the certificate it signed must pass as
# one from a CA does.
if ! curl --silent --fail --output /dev/null --cacert "$work_dir/user-ca.pem" \
    "https://127.0.0.1:$user_ca_port/healthz"; then
    echo "$0: the TLS proxy did not answer with the certificate this run's CA signed on https://127.0.0.1:$user_ca_port/healthz" >&2
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
benchmark_filter_file="$work_dir/benchmark-test-filter.txt"

# Copies the credentials file to each test device, and the test list in
# $filter too once filter_on_device names its place there, and the benchmarks'
# list once benchmark_filter_on_device does.
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
        if [ -n "$benchmark_filter_on_device" ]; then
            adb -s "$device" push "$benchmark_filter_file" "$benchmark_filter_on_device" >/dev/null
            adb -s "$device" shell "chmod 644 $benchmark_filter_on_device"
        fi
    done
}

# Installs this run's CA as a user CA on device $1, an emulator this script
# booted or one the caller vouched for, as the header says, in the place and
# under the name Android looks it up by, readable by every app. Fails the run
# when su cannot.
install_user_ca() {
    local device=$1 staged="$CREDENTIALS_DIR/tokkl-test-user-ca.pem" install
    user_ca_on_device="$USER_CA_DIR/$(openssl x509 -in "$work_dir/user-ca.pem" -noout -subject_hash_old).0"
    install="mkdir -p $USER_CA_DIR && cp $staged $user_ca_on_device && rm $staged"
    install+=" && chown system:system $USER_CA_DIR $user_ca_on_device"
    install+=" && chmod 755 $USER_CA_DIR && chmod 644 $user_ca_on_device"
    # restorecon lists the file context files it read, whatever it does.
    install+=" && restorecon -R $USER_CA_DIR >/dev/null 2>&1 && sync"
    devices_with_user_ca+=("$device")
    adb -s "$device" push "$work_dir/user-ca.pem" "$staged" >/dev/null
    if ! adb -s "$device" shell "su 0 sh -c '$install'"; then
        echo "$0: could not install this run's CA as a user CA on $device with su" >&2
        exit 1
    fi
}

# Emits build properties from this run's owned emulator, immediately before
# instrumentation. Attached devices never produce this owned-device record.
emit_owned_build() {
    local properties
    properties=$(adb -s "$emulator_serial" shell 'getprop ro.product.brand; getprop ro.product.device; getprop ro.build.fingerprint; getprop ro.build.id; getprop ro.product.model; getprop ro.build.type; getprop ro.build.version.sdk; getprop ro.build.version.codename' | tr -d '\r')
    python3 - "$emulator_serial" "$AVD" "$properties" <<'PY'
import json, sys, time
values = sys.argv[3].splitlines()
if len(sys.argv[3].encode()) > 8192 or len(values) != 8 or any(not v.strip() for v in values) or not values[6].isascii() or not values[6].isdigit():
    raise SystemExit('owned emulator did not report complete build properties')
build = dict(zip(('brand', 'device', 'fingerprint', 'id', 'model', 'type'), values[:6]))
build['version'] = {'sdk': int(values[6]), 'codename': values[7]}
print('TOKKL_DEVICE_BUILD '+json.dumps(dict(serial=sys.argv[1], avd=sys.argv[2],
      observed_at_ns=time.time_ns(), build=build), sort_keys=True, separators=(',', ':')))
PY
}

# Runs the tests once on every test device. Fails when Gradle fails, when a
# test device went away during the run, and when Gradle passed without running
# the tests on every device, the benchmarks too when the run has their task.
run_tests() {
    local status=0 device xml tests dir
    local -a gone=() results=() result_dirs=()
    if [ -n "$emulator_serial" ]; then emit_owned_build; fi
    # A run that stops before its tests must not count the last run's results.
    result_dirs=("$RESULTS")
    if [ -n "$benchmark_task" ]; then result_dirs+=("$BENCHMARK_RESULTS"); fi
    rm -rf "${result_dirs[@]}"
    ./gradlew --no-daemon connectedDebugAndroidTest \
        "-P$RUNNER_ARGS.tokklBaseUrl=http://10.0.2.2:$host_port" \
        "-P$RUNNER_ARGS.tokklTlsBaseUrl=https://10.0.2.2:$tls_port" \
        "-P$RUNNER_ARGS.tokklPrefixedBaseUrl=http://10.0.2.2:$prefix_port/tokkl" \
        "-P$RUNNER_ARGS.tokklTlsFingerprint=$tls_fingerprint" \
        "-P$RUNNER_ARGS.tokklNewKeyTlsBaseUrl=https://10.0.2.2:$new_key_port" \
        "-P$RUNNER_ARGS.tokklNewKeyTlsFingerprint=$new_key_fingerprint" \
        "-P$RUNNER_ARGS.tokklUserCaTlsBaseUrl=https://10.0.2.2:$user_ca_port" \
        "-P$RUNNER_ARGS.tokklEmail=$EMAIL" \
        "-P$RUNNER_ARGS.tokklCredentialsFile=$credentials_on_device" \
        "${gradle_args[@]}" {slot_fd}>&- {device_fd}>&- {boot_fd}>&- 2>&1 | tee "$work_dir/gradle.log" || status=$?
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
    # A class= list entry that names no test, such as a mistyped benchmark
    # method, runs nothing in its APK and Gradle passes.
    for dir in "${result_dirs[@]}"; do
        mapfile -t results < <(find "$dir" -maxdepth 1 -name 'TEST-*.xml' 2>/dev/null)
        if [ "${#results[@]}" -lt "${#devices[@]}" ]; then
            echo "$0: Gradle passed, but $dir holds results for ${#results[@]} of the ${#devices[@]} test devices, so this run fails." >&2
            return 1
        fi
        for xml in "${results[@]}"; do
            tests=$(sed -n 's/^<testsuites [^>]*\btests="\([0-9]*\)".*/\1/p;T;q' "$xml")
            if [ "${tests:-0}" -eq 0 ]; then
                echo "$0: Gradle passed, but $xml reports no test run, so this run fails." >&2
                return 1
            fi
        done
    done
}

echo "Tokkl is ready on $server, which the emulator reaches as http://10.0.2.2:$host_port"
echo "The TLS proxy is ready on https://127.0.0.1:$tls_port, with certificate SHA-256 $tls_fingerprint"
echo "It serves its second certificate on https://127.0.0.1:$new_key_port, with SHA-256 $new_key_fingerprint"
echo "It serves the certificate this run's CA signed on https://127.0.0.1:$user_ca_port"
echo "The proxy serves Tokkl under $prefixed, which the emulator reaches as http://10.0.2.2:$prefix_port/tokkl"
if [ "${#test_filter[@]}" -gt 0 ]; then
    printf '%s\n' "${test_filter[@]}" >"$filter"
    filter_on_device="$CREDENTIALS_DIR/$FILTER_PREFIX$(random_hex 4).txt"
    gradle_args+=("-P$RUNNER_ARGS.testFile=$filter_on_device")
fi
if [ "${#benchmark_filter[@]}" -gt 0 ]; then
    printf '%s\n' "${benchmark_filter[@]}" >"$benchmark_filter_file"
    benchmark_filter_on_device="$CREDENTIALS_DIR/$FILTER_PREFIX$(random_hex 4).txt"
    gradle_args+=("-P$RUNNER_ARGS.$BENCHMARK_FILTER_ARG=$benchmark_filter_on_device")
fi
if [ "${#user_ca_devices[@]}" -gt 0 ]; then
    for device in "${user_ca_devices[@]}"; do
        install_user_ca "$device"
        echo "Installed this run's CA as a user CA on $device"
    done
else
    for arg in "${gradle_args[@]}"; do
        case $arg in
            *"$RUNNER_ARGS.notAnnotation="*)
                echo "$0: $arg cannot go with the notAnnotation argument that leaves out the user CA tests on a device this script did not boot." >&2
                exit 2
                ;;
        esac
    done
    gradle_args+=("-P$RUNNER_ARGS.notAnnotation=$USER_CA_ANNOTATION")
    echo "This script did not boot the test devices and TOKKL_TEST_THROWAWAY_EMULATOR is not 1, so it installs no user CA and leaves out the tests marked @UserCaInstalled"
fi
push_to_devices
if [ "$fresh_notification_permission" = 1 ]; then
    # Never alter permissions on an attached device, even one vouched for as throwaway.
    if [ -z "$emulator_serial" ] || [ "$(adb -s "$emulator_serial" shell getprop ro.build.version.sdk | tr -d '\r')" -lt 33 ]; then
        echo "$0: a fresh notification permission requires an API 33+ emulator booted by this script." >&2
        exit 2
    fi
    ./gradlew --no-daemon installDebug -PtokklFreshNotificationPermission=true
    adb -s "$emulator_serial" shell pm revoke com.github.e0ipso.tokkl android.permission.POST_NOTIFICATIONS
    adb -s "$emulator_serial" shell pm clear-permission-flags com.github.e0ipso.tokkl android.permission.POST_NOTIFICATIONS user-set user-fixed
    echo "Reset notification permission before instrumentation on $emulator_serial"
fi
run_tests
