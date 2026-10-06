#!/usr/bin/env bash
# Dedicated-server smoke + restart test of BOTH production jars (no dev environment involved):
#   1. installs the NeoForge server of gradle.properties (neo_version) in build/smoke/<PROFILE>
#   2. boots it with permadeath-<PROFILE>-neoforge-1.21.1.jar, runs /permadeath status, setday 40, status, stops
#   3. boots it again and checks that the day survived the restart and that milestone D40 did not run twice
# Requires network access to maven.neoforged.net, piston-meta/piston-data.mojang.com and libraries.minecraft.net.
# Usage: ./gradlew build && tools/server-smoke-test.sh [GAME60|REAL30 ...]
set -euo pipefail

ROOT=$(cd "$(dirname "$0")/.." && pwd)
NEO_VERSION=$(grep '^neo_version=' "$ROOT/gradle.properties" | cut -d= -f2)
MC_VERSION=$(grep '^minecraft_version=' "$ROOT/gradle.properties" | cut -d= -f2)
WORK=${WORK:-$ROOT/build/smoke}
PROFILES=("$@")
[ ${#PROFILES[@]} -eq 0 ] && PROFILES=(GAME60 REAL30)
INSTALLER_URL="https://maven.neoforged.net/releases/net/neoforged/neoforge/${NEO_VERSION}/neoforge-${NEO_VERSION}-installer.jar"

fail() { echo "SMOKE FAIL [$PROFILE]: $*" >&2; exit 1; }

wait_for() { # file pattern timeout_seconds
    local i=0
    until grep -q "$2" "$1" 2>/dev/null; do
        sleep 1; i=$((i + 1))
        [ "$i" -ge "$3" ] && fail "timeout waiting for '$2' in $1"
    done
}

run_server() { # dir log command...
    local dir=$1 log=$2; shift 2
    rm -f "$dir/stdin.fifo"; mkfifo "$dir/stdin.fifo"
    (cd "$dir" && ./run.sh nogui < stdin.fifo > "$log" 2>&1) &
    local pid=$!
    exec 3> "$dir/stdin.fifo"
    wait_for "$log" 'Done (' 600
    for command in "$@"; do
        echo "$command" >&3
        sleep 4
    done
    echo stop >&3
    wait "$pid" || true
    exec 3>&-
    rm -f "$dir/stdin.fifo"
}

for PROFILE in "${PROFILES[@]}"; do
    JAR="$ROOT/build/libs/permadeath-${PROFILE}-neoforge-${MC_VERSION}.jar"
    [ -f "$JAR" ] || fail "missing $JAR (run ./gradlew build first)"
    DIR="$WORK/$PROFILE"
    rm -rf "$DIR"; mkdir -p "$DIR/mods"
    curl -fsSL -o "$DIR/installer.jar" "$INSTALLER_URL"
    (cd "$DIR" && java -jar installer.jar --installServer > install.log 2>&1) || fail "installer failed (see $DIR/install.log)"
    cp "$JAR" "$DIR/mods/"
    echo 'eula=true' > "$DIR/eula.txt"
    printf 'online-mode=false\nlevel-seed=20241\nspawn-protection=0\nmax-tick-time=-1\nsync-chunk-writes=true\n' > "$DIR/server.properties"

    # --- first boot -------------------------------------------------------------------------------------
    run_server "$DIR" "$DIR/boot1.log" "permadeath status" "permadeath setday 40" "permadeath status" "permadeath debug" "save-all flush"
    grep -q "Calendar ${PROFILE} started" "$DIR/boot1.log" || fail "calendar ${PROFILE} not started"
    grep -q 'Milestone D40 executed' "$DIR/boot1.log" || fail "milestone D40 not executed on first boot"
    grep -q 'Día Permadeath: 40/60' "$DIR/boot1.log" || fail "status does not show day 40"
    if grep -Eiq 'mixin.*(apply|inject).*(fail|error)|InvalidInjectionException|InjectionError' "$DIR/boot1.log"; then fail "mixin errors"; fi
    if grep -Eq 'ERROR.*\[(permadeath|com\.serthekiller)' "$DIR/boot1.log"; then fail "errors logged by permadeath"; fi

    # --- restart -----------------------------------------------------------------------------------------
    run_server "$DIR" "$DIR/boot2.log" "permadeath status"
    grep -q "Calendar ${PROFILE} started: PD day 40" "$DIR/boot2.log" || fail "day 40 not persisted across restart"
    if grep -q 'Milestone D40 executed' "$DIR/boot2.log"; then fail "milestone D40 executed twice"; fi
    grep -q 'Día Permadeath: 40/60' "$DIR/boot2.log" || fail "status after restart does not show day 40"
    echo "SMOKE OK [$PROFILE] logs: $DIR/boot1.log $DIR/boot2.log"
done
