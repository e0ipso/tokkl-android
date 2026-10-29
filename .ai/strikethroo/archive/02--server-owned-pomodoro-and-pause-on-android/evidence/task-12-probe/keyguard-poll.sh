#!/usr/bin/env bash
# Read-only: logs the keyguard and screen state of the emulator a runner run boots, once a second,
# until that emulator goes away. Usage: keyguard-poll.sh <out file>
set -uo pipefail
out=$1
serial=''
until [ -n "$serial" ]; do serial=$(adb devices | awk '/^emulator-[0-9]+\tdevice$/ {print $1; exit}'); sleep 1; done
until [ "$(adb -s "$serial" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = 1 ]; do sleep 1; done
echo "$(date +%T.%3N) screen_off_timeout=$(adb -s "$serial" shell settings get system screen_off_timeout | tr -d "\r")" >>"$out"
while adb -s "$serial" get-state >/dev/null 2>&1; do
    state=$(adb -s "$serial" shell 'dumpsys activity activities | grep -m1 -oE "mKeyguardShowing=[a-z]+"; dumpsys power | grep -m1 -oE "mWakefulness=[A-Za-z]+"' 2>/dev/null | tr -d '\r' | tr '\n' ' ')
    echo "$(date +%T.%3N) $state" >>"$out"
    sleep 1
done
