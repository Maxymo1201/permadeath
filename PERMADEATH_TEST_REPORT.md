# Informe de pruebas

Fecha de ejecución: 2026-10-08 (repetido entero tras la segunda auditoría de fallos, PERMADEATH_AUDIT.md §6). Entorno: contenedor Linux (x86_64), OpenJDK 21.0.12, Gradle 8.14.3 (wrapper),
ModDevGradle 2.0.148, **NeoForge 21.1.256** (la última del canal 21.1 en `maven.neoforged.net` ese día),
Minecraft 1.21.1.

## Resumen

| Prueba | Resultado |
|---|---|
| `./gradlew clean build`: compilación, AT validados, tests del núcleo, los 2 jars y `verifyProductionJars` | **OK** |
| Tests unitarios del núcleo (`./gradlew clean build`) | **96/96 OK** |
| GameTests GAME60 (`./gradlew runGameTestServer -PpermadeathMode=GAME60`) | **34/34 OK** |
| GameTests REAL30 (`./gradlew runGameTestServer -PpermadeathMode=REAL30`) | **34/34 OK** |
| GameTests con mundos limpios (GAME60 y REAL30, mundo `run/world` borrado antes de cada uno) | **34/34 + 34/34 OK** |
| Los 7 GameTests nuevos fallan con sus correcciones desactivadas (comprobación negativa, GAME60) | **7/7 fallan como se espera** |
| Cambio de modo GAME60 → REAL30 sobre el mismo mundo (GameTests REAL30 sobre el mundo de GAME60) | **OK**: conserva el D60 y reancla el calendario |
| Servidor dedicado con el jar de producción, arranque + reinicio + cofres de The Beginning en el D60 (`tools/server-smoke-test.sh`) | **GAME60 OK, REAL30 OK** |
| Inspección de los jars de producción | **OK** |

## 1. Build

`./gradlew clean build` → `BUILD SUCCESSFUL`:

```
[verifyProductionJars] permadeath-GAME60-neoforge-1.21.1.jar: OK (483 entries, profile=GAME60)
[verifyProductionJars] permadeath-REAL30-neoforge-1.21.1.jar: OK (483 entries, profile=REAL30)
```

* `createMinecraftArtifacts` aplica y valida las 7 entradas del access transformer
  (`validateAccessTransformers = true`). Compilación sin errores ni avisos.
* Correcciones necesarias en la primera compilación con red (2026-10-06):
  * una llave sobrante en `ExplodingAnimals`;
  * `Entity#isAddedToWorld` se llama `isAddedToLevel` en NeoForge 21.1.

## 2. Tests unitarios del núcleo

`./gradlew clean build` ejecuta `coreTest` desde cero → 96 tests, 0 fallos. Informes JUnit en `build/test-results/coreTest`.

| Clase | Tests | Casos |
|---|---|---|
| `GameDayProgressionClockTest` | 14 | Límites D9→D10, D19→D20, D24→D25, D29→D30, D39→D40, D49→D50 y D59→D60; los saltos ejecutan cada hito intermedio una vez; un mundo existente en el día 347 empieza en D0; dormir avanza el calendario; un rollback de `/time` nunca retrocede y no congela el calendario (`/time set day` en el D20 → D21 un día de Minecraft después); `setDay` reancla de forma segura; mover un mundo REAL30 a GAME60 conserva el día |
| `RealTimeProgressionClockTest` | 24 | T+0 h → D0; 119 h 59 m → D9; 120 h → D10; 239 h 59 m → D19; 240 h → D20; 299 h 59 m → D24; 300 h → D25; 359 h 59 m → D29; 360 h → D30; 479 h 59 m → D39; 480 h → D40; 599 h 59 m → D49; 600 h → D50; 719 h 59 m → D59; 720 h → D60; 721 h y 1000 h → D60. Independiente de los TPS, de la zona horaria y del horario de verano. El tiempo con el servidor apagado cuenta. Un retroceso del reloj del sistema no hace retroceder el día. Instantes de hito exactos. `setDay` coherente y persistente. Mover un mundo GAME60 a REAL30 conserva el día |
| `LegacyFabricStateTest` | 2 | Lee los ficheros de Fabric (fecha, tormenta, wither, Life Orb, Mikecrack, manzanas) y los aplica en GAME60; en REAL30 conserva el día y lo limita a 60 |
| `RulesTest` | 56 | Tótems D0/D29/D30/D39/D40/D49/D50/D59/D60 (fallo % y número de tótems), límites de la tirada y prueba de humo con RNG; mobs 70/140; ahogamiento ×1/×5/×10 y golpes de 5/10; ceguera D39 = 0, D40-49 = 1/10000, D50-60 = 1/5000; duraciones y buffs del Death Train (coinciden con PermaDeathCore) y Resistencia al fuego solo D50-59; umbrales de PvP, End, manzanas y vida máxima; números del plugin (levitación, perlas, arena de almas, camas, tamaño de phantoms, gigantes, emperadores, clases de pigman, netherita D25-29, supernova, daño 8) |

## 3. GameTests (ejecutados en un servidor NeoForge 21.1.256 real)

`gametest/PermadeathGameTests`: 34 tests con la plantilla `permadeath:gametest_empty`. Cada lote fija el día
en `@BeforeBatch` con `ProgressionClock#setDay`, lo mismo que hace `/permadeath setday`. Los jugadores simulados se
conectan con una conexión en memoria configurada para la red de NeoForge.

| Lote | Test | Comprueba | GAME60 | REAL30 |
|---|---|---|---|---|
| d0 | `hostileMobCapVanillaBeforeD10` | Límite de monstruos 70 | OK | OK |
| d0 | `drowningVanillaBeforeD50` | Cerdo sumergido: pierde 1-10 de aire entre los ticks 10 y 20 | OK | OK |
| d0 | `oneTotemSavesBeforeD30` | Un tótem salva y se consume | OK | OK |
| d0 | `endClosedBeforeD30` | Viaje al End cancelado | OK | OK |
| d10 | `calendarPhaseFollowsDay` | Día 10 y fase D10-19 | OK | OK |
| d10 | `hostileMobCapDoubledFromD10` | Límite 140 | OK | OK |
| d10 | `pvpDisabledBeforeD40` | PvP desactivado | OK | OK |
| d40 | `torchRecipeRemovedOnD40` | Sin `minecraft:torch`; el hierro de horno sigue | OK | OK |
| d40 | `chestLootPresentBeforeD60` | El loot de mazmorra no está vacío | OK | OK |
| d40 | `oneTotemIsNotEnoughOnD40` | Con un tótem el jugador muere y el tótem se consume | OK | OK |
| d40 | `maxHealthPenaltyAndLockedSlotsOnD40` | 12 de vida máxima, hueco 4 bloqueado, PvP activo | OK | OK |
| d40 | `numberKeySwapIntoLockedSlotReturnsTheItemToTheChest` | Tecla numérica desde un cofre hacia el hueco bloqueado 4: el hueco vuelve a bloquearse, los 7 diamantes vuelven al cofre y no cae nada al suelo | OK | OK |
| d40 | `zombifiedPiglinKeepsItsAttackWhenTurnedHostile` | Tras la conversión a hostil el ataque sigue ≥ 5 (vanilla o clase de pigman) | OK | OK |
| d40 | `relicOnTheCursorKeepsTheSlotsUnlocked` | Inventario lleno con la Reliquia del Fin en el cursor: el hueco 4 y la mano secundaria conservan sus objetos y no cae nada | OK | OK |
| d40 | `spectatorDeathIsNoNewPermadeath` | Un espectador muerto por `/kill` no genera registro de muerte ni la etiqueta de muerte | OK | OK |
| d40 | `crafterRefusesTheSpecialRecipes` | La Super Golden Apple+ se fabrica a mano, pero el Crafter no la acepta | OK | OK |
| d50 | `enderCreeperReplacementKeepsItsNameOnD50` | 8 Ender Creepers nombrados antes de su entrada diferida conservan el nombre y son invisibles | OK | OK |
| d40tnt | `dragonTntExplodesOnceWithoutBreakingBlocks` | La TNT del dragón explota sin romper los 5 bloques de piedra que la rodean (sin la explosión vanilla de potencia 4) | OK | OK |
| d60 | `calendarNeverGoesBeyondD60` | `setDay(70)` → 60 | OK | OK |
| d60 | `drowningTenTimesFasterOnD60` | Pierde 80-100 de aire entre los ticks 10 y 20 (vanilla 10) | OK | OK |
| d60 | `chestLootEmptyOnD60` | El loot de mazmorra está vacío | OK | OK |
| d60 | `maxHealthPenaltyOnD60` | 4 de vida máxima | OK | OK |
| d20 | `villageIronGolemHuntsPlayersFromD20` | Un gólem de aldea toma al jugador como objetivo | OK | OK |
| d20 | `playerBuiltIronGolemStaysFriendly` | Un gólem construido por un jugador no lo ataca | OK | OK |
| d20 | `naturalSkeletonGetsAClassOnD20` | Un esqueleto natural recibe armadura de clase y 20/40 de vida | OK | OK |
| d50 | `drowningHitDealsFiveOnD50` | Golpe de ahogamiento de 5 | OK | OK |
| d50 | `milkKeepsOnlyMiningFatigueOnD50` | La leche cura el veneno y deja la fatiga de minería | OK | OK |
| d50 | `pufferfishUsesPluginEffectsOnD50` | Pez globo: Veneno IV, Hambre III y Náusea II infinitos | OK | OK |
| d50 | `superGoldenApplePlusIsAGoldenApple` | Absorción, Regeneración y Salud aumentada | OK | OK |
| d50 | `enderCreeperDodgesEverythingButMelee` | Esquiva el daño mágico y recibe el cuerpo a cuerpo | OK | OK |
| d50storm | `deathTrainBuffsArePermanentOnD50` | Death Train: Fuerza II y Resistencia al fuego infinitas | OK | OK |
| d60 | `drowningHitDealsTenOnD60` | Golpe de ahogamiento de 10 | OK | OK |
| d60 | `pearlCooldownSixSecondsAfterLandingOnD60` | La perla sigue en espera 100 ticks después de caer | OK | OK |
| d60orb | `lifeOrbPenaltyLowersOnlyMaxHealthAndIsSaved` | Sin Life Orb: el jugador sigue vivo, vida máxima 1 y modificador guardado | OK | OK |

Además de las aserciones, el log de los GameTests confirma en vivo:
* ejecución idempotente de los hitos D10→D60 al saltar de día;
* generación del portal a The Beginning en el D40;
* recarga de datapacks en los umbrales D40/D50/D60, con 3 recetas desactivadas en D40 y 17 en D60;
* plazo del Life Orb al entrar en D60;
* muerte del jugador → Death Train de 16 h en D40, mensajes, espectador y baneo.

Ajustes que hizo falta en los propios tests:
* ids de plantilla sin espacio de nombres, que NeoForge ya añade;
* golpear al jugador simulado después de sus 60 ticks de protección de aparición;
* el test del Death Train usa una bruja: desde el D40 los zombis se sustituyen por vindicators;
* los lotes del D60 desactivan el cambio de Mikecrack (ahora activo por defecto) para que ningún creeper se
  acerque a los jugadores simulados de 1-4 de vida.

**Fallo real del mod encontrado y corregido:** `DeathHandler` leía el día al final del tick y no en el
momento de la muerte. Ahora la duración del Death Train usa el día en que se murió.

**Comprobación negativa de los tests nuevos:** con su corrección desactivada, cada uno de los 7 tests nuevos
falla y con el código final pasa:
* TNT del dragón: `Expected Stone, got Air at … (relative: 3,1,3)`;
* intercambio con tecla numérica: `the diamonds must go back to the chest, found 1 minecraft:structure_void`;
* zombified piglin: `zombified piglin attack lowered to 3.0`;
* Ender Creepers del D50: `an Ender Creeper was renamed to Quantum Creeper`;
* Crafter: `the Crafter must refuse the Super Golden Apple+ (it skipped the extra cost)`;
* espectador: `a spectator killed by /kill or the void was recorded as a new Permadeath`;
* reliquia en el cursor: `slot 4 was locked while the relic was on the cursor`.

**Test corregido:** los tests de ahogamiento medían el aire absoluto a los 10 ticks con un cerdo sin nombre.
Desde el D40 el pase periódico de mobs convierte los cerdos en ravagers y, al correr ahora cada 60 ticks de
tiempo de juego en cada mundo, a veces caía dentro de la medida. El cerdo de prueba lleva nombre (como el
cerdo del Pigman Rosa, que el pase respeta) y se mide la pérdida entre los ticks 10 y 20.

## 4. Servidor dedicado con los jars de producción

`tools/server-smoke-test.sh GAME60 REAL30` → `SMOKE OK [GAME60]` y `SMOKE OK [REAL30]` (semilla 20241).

* **Servidor usado:** un servidor dedicado NeoForge 21.1.256 (`--launchTarget forgeserverdev`, el mismo
  NeoForge que instala el instalador). Lo prepara la run `smokeServer` de ModDevGradle y no contiene ninguna
  clase del mod: el mod se carga solo desde el jar de producción copiado en `mods/` (en el log aparece
  `Loading Permadeath 2.0.0`, versión del manifiesto del jar).
* **Primer arranque:**
  * `Calendar <PERFIL> started: PD day 0`;
  * `/permadeath status`, `setday 40` (hitos D10-D40, portal, recarga de recetas), `status` → `Día Permadeath: 40/60`;
  * `storm addHours 2` → `Operación completada exitosamente. Quedan 01:59:59 de tormenta.`;
    `event shulkershell` → `Se ha iniciado el evento correctamente.`; `status` → `Death Train activo`;
  * `debug`; `save-all flush`; `stop`;
  * la dimensión `permadeath:the_beginning` se carga y se guarda;
  * ningún error de mixin ni del mod.
* **Reinicio:** `Calendar <PERFIL> started: PD day 40` (el día persiste), `Día Permadeath: 40/60`,
  `Death Train activo: quedan 01:59:19` (la tormenta persiste con su marca absoluta) y el hito D40 **no** se
  vuelve a ejecutar.
* **Cofres de The Beginning en el D60** (`setday 60` y `/permadeath debug beginningloot`, que tira el loot de
  cada contenedor alrededor de la ciudad Ytic más cercana sin abrirlo):
  `Cofres de The Beginning junto a la ciudad Ytic de -1536, 0, 912: 17 (con tabla de loot: 15, vacíos: 0)`.
  Los 15 cofres con tabla dan entre 10 y 148 objetos; los dos sin tabla son el cofre trampa de herramientas
  (4 objetos) y la caja de shulker de oro (64), contenidos fijos como en el plugin. El cofre central de la
  ciudad (`-1509, 179, 920`), que antes tenía 21 objetos sueltos fijos y ninguna tabla, da ahora 19 objetos de
  la tabla. Antes de corregir el loot modifier, el mismo comando daba 14/14 cofres vacíos en el D60 (y 14/14
  con loot en el D50). Igual en GAME60 y REAL30.
* **REAL30:** `setday 40` reancla el inicio 20 días atrás (`start=2026-09-18 23:13:49 UTC`,
  `maxElapsed=20d 00h 00m 24s`).
* Los únicos errores del log son de red (`api.minecraftservices.com` no está permitido: clave de Yggdrasil).

### Hosts que siguen bloqueados en este entorno

La política de red rechaza todavía dos hosts que no hacen falta para compilar:

| Host | Quién lo usa | Cómo se ha resuelto |
|---|---|---|
| `resources.download.minecraft.net` | Assets del cliente (sonidos, idiomas). ModDevGradle los descarga para todas las runs | Las runs de servidor (`server`, `gameTestServer`, `smokeServer`) usan un descriptor de assets vacío: un servidor no los lee. `runClient` sigue necesitando el host |
| `launchermeta.mojang.com` | Instalador oficial de NeoForge (paso `DOWNLOAD_MOJMAPS`) | `tools/server-smoke-test.sh` usa por defecto el runtime de ModDevGradle descrito arriba. Con el host permitido, `SMOKE_RUNTIME=installer tools/server-smoke-test.sh` repite la prueba con un servidor instalado por el instalador oficial |

## 5. Inspección de los jars de producción

| | GAME60 | REAL30 |
|---|---|---|
| Fichero | `permadeath-GAME60-neoforge-1.21.1.jar` (786 007 bytes) | `permadeath-REAL30-neoforge-1.21.1.jar` (786 017 bytes) |
| Entradas | 483 | 483 |
| `permadeath_profile.properties` | `mode=GAME60` | `mode=REAL30` |
| Manifiesto | `Implementation-Version: 2.0.0`, `Permadeath-Profile: GAME60`, `Built-Against-NeoForge: 21.1.256` | igual con `REAL30` |

* Descomprimidos y comparados con `diff -r`, los dos jars **solo** difieren en
  `permadeath_profile.properties` y `META-INF/MANIFEST.MF`. Clases y recursos son idénticos byte a byte (un
  núcleo, dos builds).
* `META-INF/neoforge.mods.toml` está expandido: `modId="permadeath"`, `version="2.0.0"`,
  `loaderVersion="[4,)"`, NeoForge `[21.1.0,)` y Minecraft `[1.21.1]`; declara `permadeath.mixins.json` y
  `META-INF/accesstransformer.cfg`.
* Sin `fabric.mod.json`, sin `net/fabricmc/*`, sin `.accesswidener`, y ninguna clase referencia `net.fabricmc`
  (`verifyProductionJars`).

### Jars entregados (build final, probados con `tools/server-smoke-test.sh`)

| Jar | SHA-256 |
|---|---|
| `build/libs/permadeath-GAME60-neoforge-1.21.1.jar` | `c9fed3ed858c10e66a7b180a356bdb6c4f109e64817337499bdb75958b3756e8` |
| `build/libs/permadeath-REAL30-neoforge-1.21.1.jar` | `69bd24f3d28e23814ea856263dcf3ae3cb21a565c087833df57ad7e5a4a178c2` |

## Cómo repetir todo

```
./gradlew clean build
./gradlew runGameTestServer -PpermadeathMode=GAME60
./gradlew runGameTestServer -PpermadeathMode=REAL30
tools/server-smoke-test.sh GAME60 REAL30
```
