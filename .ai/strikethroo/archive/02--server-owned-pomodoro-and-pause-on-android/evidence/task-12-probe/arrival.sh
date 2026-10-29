#!/usr/bin/env bash
# Starts a session as the other device and times until the phone posts its session notification
# (id 3): from the server's answer to the notification's posttimeElapsedMs, mapped to the host
# clock through dumpsys alarm's nowRTC/nowELAPSED and the device's clock skew, and by polling.
# Usage: arrival.sh <serial> <label> [timeout s]
set -uo pipefail
serial=$1 label=$2 pkg=com.canpicasoft.tokkl
here=$(dirname "$0")
sh() { adb -s "$serial" shell "$@" | tr -d '\r'; }
session_record() {
    sh dumpsys notification --noredact | awk -v p="pkg=$pkg" '
        /^  Notification List:/ {list=1; next}
        list && /^  [^ ]/ {list=0}
        list && /NotificationRecord\(/ {f=(index($0,p)>0 && index($0," id=3 ")>0)}
        list && f && /posttimeElapsedMs/ {print}'
}
if [ -n "$(session_record)" ]; then echo "a session notification is already posted; end the session first" >&2; exit 2; fi
h0=$(date +%s%3N); dev=$(sh date +%s%3N); h1=$(date +%s%3N)
skew=$((dev - (h0 + h1) / 2))
clocks=$(sh dumpsys alarm | grep -m1 nowELAPSED)
rtc=$(sed -n 's/.*nowRTC=\([0-9]*\).*/\1/p' <<<"$clocks"); el=$(sed -n 's/.*nowELAPSED=\([0-9]*\).*/\1/p' <<<"$clocks")
boot_host=$((rtc - el - skew))
before=$(date +%s%3N)
answer=$("$here/tokkl-probe.sh" session "$label")
after=$(date +%s%3N)
seen='' rec=''
deadline=$((after + ${3:-600} * 1000))
while [ "$(date +%s%3N)" -lt "$deadline" ]; do
    rec=$(session_record)
    if [ -n "$rec" ]; then seen=$(date +%s%3N); break; fi
    sleep 0.05
done
echo "server answer: $answer"
echo "request took $((after - before)) ms; device clock skew ${skew} ms (rtt $((h1 - h0)) ms); nowRTC=$rtc nowELAPSED=$el"
if [ -z "$seen" ]; then echo "NOT ARRIVED within ${3:-600} s"; exit 1; fi
post=$(sed -n 's/.*posttimeElapsedMs=\([0-9]*\).*/\1/p' <<<"$rec" | head -n 1)
echo "session notification posted at elapsed $post: $((boot_host + post - after)) ms after the answer (device clock); first poll hit $((seen - after)) ms after"
