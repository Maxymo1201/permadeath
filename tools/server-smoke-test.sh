#!/usr/bin/env bash
# Dedicated-server smoke + restart test of the PRODUCTION jars (build/libs/permadeath-<PROFILE>-neoforge-1.21.1.jar).
#   1. boots a dedicated NeoForge server whose mods/ folder only contains the production jar,
#      runs /permadeath status, setday 40, status, storm addHours 2, event shulkershell, status, tiempos, debug, then
#      stops it. Nobody is online, so the active-time timers must stay paused at exactly 2h 00m / the event duration.
#   2. boots it again and checks that day 40 and the remaining time of the Death Train and of the X2 Shulker Shells
#      event survived the restart unchanged (stored as remaining active time, nothing consumed while stopped or
#      empty) and that milestone D40 did not run twice; then sets day 60 (the final challenge waits for a survivor,
#      the calendar stays on D60) and checks that every chest of the nearest Ytic city of The Beginning (and of the
#      islands around it) still rolls its loot and that only the two containers with fixed contents have no loot
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
    case "$PROFILE" in
        GAME60) SHULKER='10m 00s'; WITHER='8m 00s'; FINAL='30m 00s' ;;
        REAL30) SHULKER='2h 00m'; WITHER='30m 00s'; FINAL='6h 00m' ;;
        *) fail "unknown profile" ;;
    esac
    run_server "$DIR" "$DIR/boot1.log" "permadeath status" "permadeath setday 40" "permadeath status" \
        "permadeath storm addHours 2" "permadeath event shulkershell" "permadeath status" "permadeath tiempos" \
        "permadeath debug" "save-all flush"
    grep -q "Calendar ${PROFILE} started" "$DIR/boot1.log" || fail "calendar ${PROFILE} not started"
    grep -q 'Milestone D40 executed' "$DIR/boot1.log" || fail "milestone D40 not executed on first boot"
    grep -q 'Día Permadeath: 40/60' "$DIR/boot1.log" || fail "status does not show day 40"
    grep -q 'Operación completada exitosamente' "$DIR/boot1.log" || fail "storm addHours failed"
    grep -q 'Se ha iniciado el evento correctamente' "$DIR/boot1.log" || fail "event shulkershell failed"
    grep -q 'Death Train activo: quedan 2h 00m' "$DIR/boot1.log" || fail "status does not show the 2h Death Train (paused without players)"
    grep -q "X2 Shulker Shells: quedan ${SHULKER}" "$DIR/boot1.log" || fail "the X2 Shulker Shells event does not last ${SHULKER}"
    grep -q 'en pausa: ningún superviviente conectado' "$DIR/boot1.log" || fail "status does not say that the timers are paused"
    grep -q "Tiempos de Permadeath (${PROFILE})" "$DIR/boot1.log" || fail "/permadeath tiempos failed"
    sed 's/§.//g' "$DIR/boot1.log" | grep -q "Wither periódico D60: ${WITHER} .* Desafío final: ${FINAL}" \
        || fail "wrong ${PROFILE} durations in /permadeath tiempos"
    if grep -Eiq 'mixin.*(apply|inject).*(fail|error)|InvalidInjectionException|InjectionError' "$DIR/boot1.log"; then fail "mixin errors"; fi
    if grep -Eq 'ERROR.*\[(permadeath|com\.serthekiller)' "$DIR/boot1.log"; then fail "errors logged by permadeath"; fi

    # --- restart -----------------------------------------------------------------------------------------
    run_server "$DIR" "$DIR/boot2.log" "permadeath status" "permadeath setday 60" "permadeath status" "permadeath debug beginningloot"
    grep -q "Calendar ${PROFILE} started: PD day 40" "$DIR/boot2.log" || fail "day 40 not persisted across restart"
    if grep -q 'Milestone D40 executed' "$DIR/boot2.log"; then fail "milestone D40 executed twice"; fi
    grep -q 'Día Permadeath: 40/60' "$DIR/boot2.log" || fail "status after restart does not show day 40"
    grep -q 'Death Train activo: quedan 2h 00m' "$DIR/boot2.log" || fail "the Death Train did not keep its remaining time across the restart"
    grep -q "X2 Shulker Shells: quedan ${SHULKER}" "$DIR/boot2.log" || fail "the X2 Shulker Shells event did not keep its remaining time"
    grep -q 'Día Permadeath: 60/60' "$DIR/boot2.log" || fail "status does not show day 60"
    grep -q 'el desafío final empieza cuando haya un superviviente conectado' "$DIR/boot2.log" || fail "the final challenge must wait for a survivor"
    if grep -Eq 'Día Permadeath: 6[1-9]|D61' "$DIR/boot2.log"; then fail "the calendar went beyond D60"; fi
    if grep -Eq 'ERROR.*\[(permadeath|com\.serthekiller)' "$DIR/boot2.log"; then fail "errors logged by permadeath after the restart"; fi
    # D60: the chests of the Ytic city and of the islands of The Beginning still have their loot.
    grep -Eq 'Cofres de The Beginning .*con tabla de loot: [1-9][0-9]*, vacíos: 0\)' "$DIR/boot2.log" || fail "empty chests in The Beginning on D60"
    # Only the two containers with fixed contents (trapped chest with tools, shulker box with gold) have no table.
    [ "$(grep -c ' sin tabla objetos: ' "$DIR/boot2.log")" -le 2 ] || fail "a chest of The Beginning has no loot table"
    echo "SMOKE OK [$PROFILE] ($RUNTIME runtime) logs: $DIR/boot1.log $DIR/boot2.log"
done
