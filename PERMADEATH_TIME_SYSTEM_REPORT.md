# Informe técnico: sistema temporal definitivo y final de Permadeath

Fecha: 2026-10-09. NeoForge 21.1.256, Minecraft 1.21.1, Java 21. Rama `claude/permadeath-fabric-neoforge-port-h2w8ie`.
Referencia funcional completa: `PERMADEATH_TIME_MODES.md`. Resultados detallados: `PERMADEATH_TEST_REPORT.md`.

## 1. Entregables

| Fichero | Perfil comprobado dentro del jar | SHA-256 |
|---|---|---|
| `build/libs/permadeath-GAME60-neoforge-1.21.1.jar` (835 158 bytes) | `mode=GAME60` | `9ef7b8f7ea4e3d619e68c51afc2dd484bb2ddbd1468367a52bc063362576a741` |
| `build/libs/permadeath-REAL30-neoforge-1.21.1.jar` (835 168 bytes) | `mode=REAL30` | `e01bb2264c68ddb6742f614320745011b388d9ad7b780c997816a9545c813e32` |

* Los perfiles no están intercambiados: `verifyProductionJars` lee `permadeath_profile.properties` de cada jar, el
  smoke test arranca cada jar en un servidor dedicado y comprueba `Calendar GAME60 started` / `Calendar REAL30
  started` y las duraciones de su perfil (`/permadeath tiempos`).
* Descomprimidos y comparados, los dos jars solo difieren en `permadeath_profile.properties` y
  `META-INF/MANIFEST.MF` (un núcleo, dos builds).
* Código fuente: ZIP generado con `git archive` del commit entregado.

## 2. Problemas encontrados

| # | Problema | Consecuencia |
|---|---|---|
| 1 | Death Train, X2 Shulker Shells y la maldición guardaban instantes absolutos del reloj del sistema | Corrían con el servidor apagado o vacío; una tormenta podía acabar sin que nadie la viviera |
| 2 | Duraciones fijas iguales en GAME60 y REAL30 (Death Train histórico completo, Wither 60 min, Life Orb 8 h, Shulker 4 h, maldición 12 h) | En GAME60 una sola muerte del D40 (16 h) superaba varias sesiones de juego |
| 3 | Plazo de la Life Orb de REAL30 anclado al instante teórico del D60 | Un servidor que alcanzaba el D60 apagado arrancaba con el plazo vencido y penalización inmediata |
| 4 | Efectos de maldición y bendición de 864 000 ticks, en un reloj distinto del de la prohibición de la leche | Con TPS bajos o reconexiones, efectos y prohibición terminaban en momentos distintos |
| 5 | No existía una fase final | El D60 no terminaba nunca y no había victoria ni derrota |
| 6 | El Wither se reiniciaba aunque el Wither no hubiera entrado en el mundo y no había protección configurable | Withers perdidos; servidores sin control de acumulación |

## 3. Cambios realizados

### Núcleo (`src/core`, sin dependencias de Minecraft, probado con JUnit)

* `core/time/PermadeathTimings`: **única** fuente de duraciones por perfil, incluida la fórmula del Death Train
  (`max(60 s, original / 72)` en GAME60, `original / 2` en REAL30) sobre `DayRules.deathTrainDurationMillis`.
* `core/time/EventClock`: tiempo real efectivo con `System.nanoTime()` (monotónico, independiente de los TPS,
  primer paso 0, tope de 10 s por paso, restos sub-milisegundo acumulados, nunca persistido).
* `core/time/CampaignTimers`: temporizadores globales en tiempo activo (Death Train acumulativo, tiempo de
  administrador sin escalar, evento Shulker sin duplicados, plazo de la Life Orb, temporizador final); cada
  transición se informa una sola vez.
* `core/time/FinalChallenge` + `FinalPhaseState` + `FinalParticipant`: desafío final con estados
  `NOT_STARTED/ACTIVE/COMPLETED/FAILED`, observación de participantes, eliminación, evaluación única, reinicio y
  sincronización con el calendario en la campaña estricta.
* `core/time/TimerMigration`: migración versionada formato 1 → 2.
* `ProgressionState` (formato 2): tiempos restantes en lugar de instantes; estado del desafío final;
  datos de migración. `RealTimeProgressionClock`: campaña estricta opcional (D60 a la hora 714).
  `TimeFormat.compact`: `8m 20s`, `4h 00m`, `2h 45m`, nunca negativo.

### Juego (`src/main`)

* `progression/CampaignTicker`: un único punto que avanza todos los temporizadores por tick con un solo paso de
  `EventClock`; también lo usan las GameTests para simular horas en un tick.
* `mechanics/Participants`: definición única de superviviente elegible.
* `mechanics/DeathTrain`: suma escalada por muerte, pausa sin supervivientes (aviso en la barra de acción y en el
  log), UHC restaurado solo si la tormenta lo cambió, `storm add/remove` en tiempo real efectivo sin escalar.
* `mechanics/WitherSpawner`: contador por UUID con el intervalo del perfil, solo en el Overworld y para elegibles,
  como mucho un Wither por jugador y tick, reinicio solo con el Wither en el mundo, límite anti-acumulación opcional.
* `mechanics/LifeOrb`: plazo de tiempo activo que empieza con el desafío final, barra de jefe, penalización
  persistente e idempotente, espera de sincronización para los que se conectan tarde.
* `mechanics/FinalChallengeManager`: inicio, barra de jefe, títulos, eliminación por Permadeath, evaluación y
  anuncio del resultado. `DeathHandler` le notifica cada Permadeath.
* `mechanics/ShulkerShellEvent`: tiempo activo y barra de jefe con el restante.
* `beginning/BeginningEffects` + `data/BeginningCurseData` (formato 2): maldición y bendición por jugador con los
  efectos sincronizados con su tiempo; `MilkCurse` consulta el mismo temporizador.
* `progression/PermadeathConfig`: `strictCampaignDuration`, `freezeAfterCampaign`, `witherAccumulationLimit`.
* `command/PermadeathCommands`: `status` ampliado, `tiempos`, `wither [jugador]`, `storm` con minutos, textos en
  español; `debug` muestra la migración.
* `data/PermadeathData`: lectura/escritura del formato 2, lectura del formato 1 como temporizadores heredados.

No se ha tocado: estadísticas de mobs y jefes, recetas, porcentajes de tótems, drops, armaduras, generación de
dimensiones, contenido de The Beginning, reglas de los hitos ni ids `permadeath:*`. Los temporizadores tácticos
(mecha de la Supernova, cooldowns, dragón, efectos cortos, vanilla) siguen igual.

## 4. Duraciones

| Temporizador | Tipo de tiempo | GAME60 | REAL30 |
|---|---|---|---|
| Wither periódico del D60 | Individual (Overworld) | 8 min | 30 min |
| Plazo de la Life Orb | Activo global | 20 min | 4 h |
| X2 Shulker Shells | Activo global | 10 min | 2 h |
| Maldición de The Beginning | Individual | 10 min | 6 h |
| Bendición de The Beginning | Individual | 10 min | 6 h |
| Death Train por muerte | Activo global | `max(60 s, original / 72)` | `original / 2` |
| Desafío final del D60 | Activo global | 30 min | 6 h |

| Death Train | D1 | D10 | D24 | D25 | D30 | D40 | D49 | D50 | D55 | D60 |
|---|---|---|---|---|---|---|---|---|---|---|
| Original | 1 h | 10 h | 24 h | 1 h | 6 h | 16 h | 25 h | 30 min | 3 h | 5 h 30 min |
| GAME60 | 1m 00s | 8m 20s | 20m 00s | 1m 00s | 5m 00s | 13m 20s | 20m 50s | 1m 00s | 2m 30s | 4m 35s |
| REAL30 | 30m 00s | 5h 00m | 12h 00m | 30m 00s | 3h 00m | 8h 00m | 12h 30m | 15m 00s | 1h 30m | 2h 45m |

## 5. Tipos de tiempo

* **Calendario**: el día D0-D60. GAME60 = días de Minecraft; REAL30 = 12 h reales por día (epoch UTC, sigue con
  el servidor apagado). Nunca pasa del D60.
* **Tiempo activo global**: solo con el servidor en marcha y al menos un superviviente elegible conectado
  (Death Train, Shulker, plazo de la Life Orb, desafío final).
* **Tiempo individual**: solo mientras ese jugador está conectado y es elegible (Wither en el Overworld,
  maldición, bendición).

## 6. Desafío final y victoria

* Empieza cuando hay un superviviente elegible en el D60 (T0: anuncio, plazo de la Life Orb, contadores de Wither).
* GAME60: Withers a los 8/16/24 min, plazo de la Life Orb a los 20 min, fin y evaluación a los 30 min.
  REAL30: Wither cada 30 min, plazo a las 4 h, fin a las 6 h. Con `strictCampaignDuration=true` (REAL30) el D60
  empieza a la hora 714 y el desafío acaba a la hora 720 del calendario.
* Victoria de un participante: vivo y no espectador al final, tuvo la Life Orb antes del plazo y la conserva.
  `COMPLETED` si gana alguien, `FAILED` si no o si todos son eliminados antes. Se registra una vez y se guarda.
* El mundo no se borra. `freezeAfterCampaign=true` (por defecto) detiene los Withers periódicos; `false` deja la
  supervivencia libre del D60.

## 7. Comandos

| Comando | Quién | Muestra / hace |
|---|---|---|
| `/permadeath status` | Todos | Perfil, día, fase, siguiente hito, Death Train, X2 Shulker, Life Orb y estado de la campaña (con "en pausa" cuando corresponde) |
| `/permadeath tiempos` | OP | Duraciones del perfil, opciones, temporizadores globales, participantes, Wither por jugador, maldiciones y bendiciones |
| `/permadeath wither [jugador]` | OP | Próximo Wither por UUID (también desconectados) |
| `/permadeath storm addHours\|removeHours\|addMinutes\|removeMinutes <n>` | OP | Death Train en tiempo real efectivo, sin escalar |
| `/permadeath event shulkershell\|lifeorb` | OP | Evento X2 Shulker Shells / reinicio del plazo de la Life Orb |
| `/permadeath maldicion <jugador>`, `/permadeath bendicion <jugador>` | OP | Maldición / bendición con la duración del perfil |
| `/permadeath debug` | OP | Estado persistido y migración |

## 8. Persistencia, migración e instrucciones de actualización

1. Haz una copia de seguridad del mundo (el formato 2 no lo entienden las versiones anteriores).
2. Para el servidor y sustituye el jar de `mods/` por el **del mismo perfil** (GAME60 para mundos GAME60, REAL30
   para mundos REAL30).
3. Arranca. En el log aparece una sola vez `[Permadeath] Progression data migrated from format 1 to 2 (…)` con lo
   convertido; `/permadeath debug` muestra `migration: from=1 at=… (…)`.
   * Death Train y X2 Shulker conservan el tiempo que quedaba (sin volver a escalar); lo vencido queda en 0.
   * Life Orb: un plazo vencido sigue vencido (sin doble penalización); uno en marcha conserva su tiempo con tope en
     el plazo del perfil. Un mundo ya en el D60 recibe un desafío final activo alineado con ese plazo.
   * Maldiciones: tiempo restante con tope en la duración del perfil. Contadores del Wither con tope en el intervalo.
   * Hitos ejecutados y el resto del estado se conservan; los valores antiguos quedan en `FormatV1`.
4. Opcional: edita `config/permadeath-server.toml` (o una copia en `<mundo>/serverconfig/`) para
   `strictCampaignDuration`, `freezeAfterCampaign` o `witherAccumulationLimit`.

## 9. Pruebas ejecutadas

| Prueba | Resultado |
|---|---|
| `./gradlew clean build buildGame60 buildReal30` (compilación, tests del núcleo, 2 jars, `verifyProductionJars`) | OK |
| Tests unitarios del núcleo con relojes simulados | 140/140 OK |
| GameTests en servidor NeoForge real, GAME60, mundo limpio | 45/45 OK |
| GameTests en servidor NeoForge real, REAL30, mundo limpio | 45/45 OK |
| Comprobación negativa: 4 fallos introducidos a propósito | Detectados los 4 (8 GameTests fallan) |
| Servidor dedicado con cada jar de producción: arranque, comandos, parada, reinicio, D60 (`tools/server-smoke-test.sh`) | GAME60 OK, REAL30 OK |

Cobertura de las 11 GameTests nuevas: Death Train de los 61 días y valores de referencia, acumulación, dos muertes
reales en el mismo tick, pausa sin supervivientes / espectador / creativo, `storm add/remove`, persistencia y fin
único, modo UHC restaurado solo si lo cambió la tormenta; Wither por UUID, espectadores, sin ráfagas y
persistencia; desafío final con victoria (línea temporal completa, Life Orb, resultado guardado y único, sin Withers
después) y con derrota por eliminación; penalización de la Life Orb tras la espera; evento Shulker; maldición y
bendición; migración del formato 1 de los dos ficheros. El smoke test demuestra con un servidor real que con el
servidor vacío y tras un reinicio el Death Train sigue en exactamente `2h 00m` y el evento Shulker en su duración
completa, y que en el D60 el desafío espera a un superviviente sin pasar al D61.

Fallo encontrado durante las pruebas: la espera de sincronización de la Life Orb en milisegundos fallaba en las
GameTests porque el servidor recupera ticks atrasados más rápido que el tiempo real; se dejó en 60 ticks.

## 10. Limitaciones conocidas

* Una Resistencia II de bendición concedida por una versión anterior no se puede migrar (no se guardaba su
  temporizador): conserva la duración que ya tenía.
* La migración limita al perfil los plazos antiguos más largos (Life Orb, maldición, Wither); las tormentas y el
  evento Shulker conservan exactamente su tiempo observable.
* No se implementa el modo experimental opcional de "muertes simuladas" del Death Train para partidas en solitario.
* La espera de sincronización de la penalización de la Life Orb se mide en ticks (60), no en tiempo real.
* Si todos los participantes son eliminados antes del plazo de la Life Orb, el plazo sigue
  su curso (solo afecta a jugadores en supervivencia que se conecten después).
* El smoke test usa el servidor dedicado de ModDevGradle (mismo NeoForge que el instalador) porque el instalador
  oficial necesita `launchermeta.mojang.com`, bloqueado en este entorno.

## 11. Archivos

Nuevos:
`src/core/java/com/serthekiller/permadeath/core/FinalParticipant.java`,
`src/core/java/com/serthekiller/permadeath/core/FinalPhaseState.java`,
`src/core/java/com/serthekiller/permadeath/core/time/{PermadeathTimings,EventClock,CampaignTimers,FinalChallenge,TimerMigration}.java`,
`src/coreTest/java/com/serthekiller/permadeath/core/time/{PermadeathTimingsTest,EventClockTest,CampaignTimersTest,FinalChallengeTest,TimerMigrationTest}.java`,
`src/main/java/com/serthekiller/permadeath/beginning/BeginningEffects.java`,
`src/main/java/com/serthekiller/permadeath/mechanics/{FinalChallengeManager,Participants}.java`,
`src/main/java/com/serthekiller/permadeath/progression/{CampaignTicker,PermadeathConfig}.java`,
`PERMADEATH_TIME_SYSTEM_REPORT.md`.

Modificados:
`src/core/java/com/serthekiller/permadeath/core/{ProgressionState,RealTimeProgressionClock,TimeFormat}.java`,
`src/core/java/com/serthekiller/permadeath/core/legacy/LegacyFabricState.java`,
`src/core/java/com/serthekiller/permadeath/core/rules/DayRules.java`,
`src/coreTest/java/com/serthekiller/permadeath/core/RealTimeProgressionClockTest.java`,
`src/coreTest/java/com/serthekiller/permadeath/core/legacy/LegacyFabricStateTest.java`,
`src/main/java/com/serthekiller/permadeath/PermadeathMod.java`,
`src/main/java/com/serthekiller/permadeath/beginning/BeginningEvents.java`,
`src/main/java/com/serthekiller/permadeath/command/PermadeathCommands.java`,
`src/main/java/com/serthekiller/permadeath/data/{BeginningCurseData,PermadeathData,SurvivalAchievementData}.java`,
`src/main/java/com/serthekiller/permadeath/event/PermadeathEvents.java`,
`src/main/java/com/serthekiller/permadeath/gametest/PermadeathGameTests.java`,
`src/main/java/com/serthekiller/permadeath/mechanics/{DeathHandler,DeathTrain,GameplayRules,LifeOrb,MilkCurse,ShulkerShellEvent,WitherSpawner}.java`,
`src/main/java/com/serthekiller/permadeath/progression/Permadeath.java`,
`tools/server-smoke-test.sh`,
`PERMADEATH_TIME_MODES.md`, `PERMADEATH_TEST_REPORT.md`, `PERMADEATH_AUDIT.md`, `FABRIC_TO_NEOFORGE_PORT.md`,
`FEATURE_PARITY.md`, `PORT_NOTES.md`.

## Cómo repetir las pruebas

```
./gradlew clean build buildGame60 buildReal30
rm -rf run/world && ./gradlew runGameTestServer -PpermadeathMode=GAME60
rm -rf run/world && ./gradlew runGameTestServer -PpermadeathMode=REAL30
tools/server-smoke-test.sh GAME60 REAL30
```
