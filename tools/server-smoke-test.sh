#!/usr/bin/env bash
# Dedicated-server smoke + restart test of the PRODUCTION jars (build/libs/permadeath-<PROFILE>-neoforge-1.21.1.jar).
#   1. boots a dedicated NeoForge server whose mods/ folder only contains the production jar,
#      runs /permadeath status, setday 40, status, storm addHours 2, event shulkershell, status, debug, then stops it
#   2. boots it again and checks that day 40 and the Death Train survived the restart and that milestone D40 did
#      not run twice; then sets day 60 and checks that every chest of the nearest Ytic city of The Beginning (and of
#      the islands around it) still rolls its loot and that only the two containers with fixed contents have no loot
#      table (/permadeath debug beginningloot)
#
# Runtimes (SMOKE_RUNTIME):
#   moddev    (default) dedicated NeoForge server of gradle.properties prepared by ModDevGradle ("smokeServer" run,
#             launched with the command written by `gradlew writeSmokeServerCommand`). The run contains no mod classes:
#             the mod is loaded only from the production jar copied into <dir>/mods, like on a real server.
#   installer official NeoForge installer (--installServer) + run.sh. Needs launchermeta.mojang.com in addition to
#             maven.neoforged.net / piston-meta / piston-data / libraries.minecraft.net.
# Usage: ./gradlew build && tools/server-smoke-test.sh [GAME60|REAL30 ...]
set -euo pipefail

ROOT=$(cd "$(dirname "$0")/.." && pwd)
NEO_VERSION=$(grep '^neo_version=' "$ROOT/gradle.properties" | cut -d= -f2)
MC_VERSION=$(grep '^minecraft_version=' "$ROOT/gradle.properties" | cut -d= -f2)
WORK=${WORK:-$ROOT/build/smoke}
RUNTIME=${SMOKE_RUNTIME:-moddev}
PROFILES=("$@")
[ ${#PROFILES[@]} -eq 0 ] && PROFILES=(GAME60 REAL30)
INSTALLER_URL="https://maven.neoforged.net/releases/net/neoforged/neoforge/${NEO_VERSION}/neoforge-${NEO_VERSION}-installer.jar"

fail() { echo "SMOKE FAIL [$PROFILE]: $*" >&2; exit 1; }

wait_for() { # file pattern timeout_seconds pid
    local i=0
    until grep -q "$2" "$1" 2>/dev/null; do
        if [ -n "${4:-}" ] && ! kill -0 "$4" 2>/dev/null; then fail "server exited before '$2' (see $1)"; fi
        sleep 1; i=$((i + 1))
        if [ "$i" -ge "$3" ]; then fail "timeout waiting for '$2' in $1"; fi
    done
    return 0
}

start_server() { # dir log -> runs the server reading commands from <dir>/stdin.fifo
    local dir=$1 log=$2
    if [ "$RUNTIME" = installer ]; then
        (cd "$dir" && ./run.sh nogui < stdin.fifo > "$log" 2>&1) &
    else
        # JVM of the ModDevGradle "smokeServer" run (see writeSmokeServerCommand in build.gradle), started from the
        # server directory so that --gameDir . points at it and the console reads the FIFO directly.
        (cd "$dir" && mapfile -t command < "$ROOT/build/moddev/smokeServerCommand.txt" && "${command[@]}" < stdin.fifo > "$log" 2>&1) &
    fi
    SERVER_PID=$!
}

run_server() { # dir log command...
    local dir=$1 log=$2; shift 2
    rm -f "$dir/stdin.fifo"; mkfifo "$dir/stdin.fifo"
    start_server "$dir" "$log"
    exec 3> "$dir/stdin.fifo"
    wait_for "$log" 'Done (' 900 "$SERVER_PID"
    for command in "$@"; do
        echo "$command" >&3
        sleep 5
    done
    echo stop >&3
    wait "$SERVER_PID" || true
    exec 3>&-
    rm -f "$dir/stdin.fifo"
}

prepare_dir() { # dir jar
    local dir=$1 jar=$2
    rm -rf "$dir"; mkdir -p "$dir/mods"
    if [ "$RUNTIME" = installer ]; then
        curl -fsSL -o "$dir/installer.jar" "$INSTALLER_URL"
        (cd "$dir" && java -jar installer.jar --installServer > install.log 2>&1) || fail "installer failed (see $dir/install.log)"
    fi
    cp "$jar" "$dir/mods/"
    echo 'eula=true' > "$dir/eula.txt"
    printf 'online-mode=false\nlevel-seed=20241\nspawn-protection=0\nmax-tick-time=-1\nsync-chunk-writes=true\nserver-port=25599\n' > "$dir/server.properties"
}

if [ "$RUNTIME" != installer ]; then
    (cd "$ROOT" && ./gradlew --console=plain -q writeSmokeServerCommand) || { echo "SMOKE FAIL: cannot prepare the smokeServer run" >&2; exit 1; }
fi

for PROFILE in "${PROFILES[@]}"; do
    JAR="$ROOT/build/libs/permadeath-${PROFILE}-neoforge-${MC_VERSION}.jar"
    [ -f "$JAR" ] || fail "missing $JAR (run ./gradlew build first)"
    DIR="$WORK/$PROFILE"
    prepare_dir "$DIR" "$JAR"

    # --- first boot -------------------------------------------------------------------------------------
    run_server "$DIR" "$DIR/boot1.log" "permadeath status" "permadeath setday 40" "permadeath status" \
        "permadeath storm addHours 2" "permadeath event shulkershell" "permadeath status" "permadeath debug" "save-all flush"
    grep -q "Calendar ${PROFILE} started" "$DIR/boot1.log" || fail "calendar ${PROFILE} not started"
    grep -q 'Milestone D40 executed' "$DIR/boot1.log" || fail "milestone D40 not executed on first boot"
    grep -q 'Día Permadeath: 40/60' "$DIR/boot1.log" || fail "status does not show day 40"
    grep -q 'Operación completada exitosamente' "$DIR/boot1.log" || fail "storm addHours failed"
    grep -q 'Se ha iniciado el evento correctamente' "$DIR/boot1.log" || fail "event shulkershell failed"
    grep -q 'Death Train activo' "$DIR/boot1.log" || fail "status does not show the Death Train"
    if grep -Eiq 'mixin.*(apply|inject).*(fail|error)|InvalidInjectionException|InjectionError' "$DIR/boot1.log"; then fail "mixin errors"; fi
    if grep -Eq 'ERROR.*\[(permadeath|com\.serthekiller)' "$DIR/boot1.log"; then fail "errors logged by permadeath"; fi

    # --- restart -----------------------------------------------------------------------------------------
    run_server "$DIR" "$DIR/boot2.log" "permadeath status" "permadeath setday 60" "permadeath debug beginningloot"
    grep -q "Calendar ${PROFILE} started: PD day 40" "$DIR/boot2.log" || fail "day 40 not persisted across restart"
    if grep -q 'Milestone D40 executed' "$DIR/boot2.log"; then fail "milestone D40 executed twice"; fi
    grep -q 'Día Permadeath: 40/60' "$DIR/boot2.log" || fail "status after restart does not show day 40"
    grep -q 'Death Train activo' "$DIR/boot2.log" || fail "the Death Train did not survive the restart"
    # D60: the chests of the Ytic city and of the islands of The Beginning still have their loot.
    grep -Eq 'Cofres de The Beginning .*con tabla de loot: [1-9][0-9]*, vacíos: 0\)' "$DIR/boot2.log" || fail "empty chests in The Beginning on D60"
    # Only the two containers with fixed contents (trapped chest with tools, shulker box with gold) have no table.
    [ "$(grep -c ' sin tabla objetos: ' "$DIR/boot2.log")" -le 2 ] || fail "a chest of The Beginning has no loot table"
    echo "SMOKE OK [$PROFILE] ($RUNTIME runtime) logs: $DIR/boot1.log $DIR/boot2.log"
done
