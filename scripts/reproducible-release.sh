#!/usr/bin/env bash
# Checks that the unsigned release APK is reproducible: builds it twice from
# clean and compares the two APKs byte for byte.
#
#   scripts/reproducible-release.sh [REVISION]
#
# Each build runs in a new clone of REVISION (default HEAD), the way F-Droid
# builds a release tag, so uncommitted changes are not built. The two clones
# sit at paths of different lengths, so an embedded build path shows up as a
# difference. The second build sees at most two CPU cores and gets its own
# Gradle user home, which reads downloaded dependencies from yours but redoes
# every transform, such as dexing the libraries. So output that depends on the
# core count or on a cached transform shows up too. Both builds run without the
# release signing variables, with --no-daemon so they share no daemon, and
# with --no-build-cache, because gradle.properties turns on the local build
# cache and the first build would otherwise take task outputs from your earlier
# builds instead of building from clean, as F-Droid does.
#
# The clones and the second Gradle home live in build/reproducible-release.*
# of this checkout, up to about 700 MB, and go away at the end.
#
# Exits 0 when the APKs match and 1 when they differ. On a difference it lists
# the zip entries that differ and keeps both APKs for inspection.

set -euo pipefail

readonly APK='app/build/outputs/apk/release/app-release-unsigned.apk'
readonly SIGNING_VARIABLES=(
    TOKKL_RELEASE_KEYSTORE
    TOKKL_RELEASE_KEYSTORE_PASSWORD
    TOKKL_RELEASE_KEY_ALIAS
    TOKKL_RELEASE_KEY_PASSWORD
)

repo=$(git -C "$(dirname "$0")" rev-parse --show-toplevel)
git_dir=$(git -C "$repo" rev-parse --path-format=absolute --git-common-dir)
revision=$(git -C "$repo" rev-parse --verify --quiet "${1:-HEAD}^{commit}") || {
    echo "$0: ${1:-HEAD} is not a commit" >&2
    exit 2
}

# Keep clones and build logs with the caller's temporary artifacts.
work_dir=$(mktemp -d "${TMPDIR:-/tmp}/tokkl-reproducible-release.XXXXXX")
trap 'rm -rf "$work_dir"' EXIT

unset_signing=()
for variable in "${SIGNING_VARIABLES[@]}"; do
    unset_signing+=(-u "$variable")
done

# Builds the release APK of $revision in a new clone at $1 and copies it to $2.
# The remaining arguments go in front of the Gradle command, through env.
build() {
    local tree=$1 out=$2
    shift 2
    git clone --quiet --no-checkout "$git_dir" "$tree"
    git -C "$tree" -c advice.detachedHead=false checkout --quiet --detach "$revision"
    # local.properties is ignored by git; it may hold sdk.dir.
    if [ -f "$repo/local.properties" ]; then
        cp "$repo/local.properties" "$tree/"
    fi
    echo "Building $(git -C "$tree" rev-parse --short HEAD) in $tree"
    if ! (cd "$tree" && env "${unset_signing[@]}" "$@" ./gradlew --no-daemon --no-build-cache assembleRelease) \
        >"$tree.log" 2>&1; then
        tail -n 40 "$tree.log" >&2
        echo "$0: the build in $tree failed" >&2
        exit 1
    fi
    cp "$tree/$APK" "$out"
}

build "$work_dir/a/tokkl-android" "$work_dir/a.apk"

# The first build has downloaded the Gradle distribution and the dependencies.
# The second Gradle home links the distribution and reads the dependencies
# through Gradle's read-only cache, so it downloads nothing.
gradle_home=${GRADLE_USER_HOME:-$HOME/.gradle}
second_home="$work_dir/gradle-home"
mkdir -p "$second_home/wrapper"
ln -s "$gradle_home/wrapper/dists" "$second_home/wrapper/dists"
second=(GRADLE_USER_HOME="$second_home" GRADLE_RO_DEP_CACHE="$gradle_home/caches")
if command -v taskset >/dev/null && [ "$(nproc)" -gt 2 ]; then
    second+=(taskset -c 0,1)
fi
build "$work_dir/second-build/tokkl-android" "$work_dir/b.apk" "${second[@]}"

sha256sum "$work_dir/a.apk" "$work_dir/b.apk" | sed "s|$work_dir/||"
if cmp -s "$work_dir/a.apk" "$work_dir/b.apk"; then
    echo "The two unsigned release APKs of $revision are identical."
    exit 0
fi

echo "The two unsigned release APKs of $revision differ." >&2
# One line per entry: size, compressed size, CRC-32 and name, in zip order.
entries() {
    unzip -v "$1" | awk 'NR > 3 && $1 ~ /^[0-9]+$/ && NF >= 8 { print $1, $3, $7, $8 }'
}
if diff <(entries "$work_dir/a.apk") <(entries "$work_dir/b.apk") >&2; then
    echo "Every entry has the same size and CRC-32; the difference is in the zip metadata." >&2
fi
keep=$(mktemp -d "${TMPDIR:-/tmp}/reproducible-release.XXXXXX")
cp "$work_dir/a.apk" "$work_dir/b.apk" "$keep/"
echo "Both APKs are in $keep" >&2
exit 1
