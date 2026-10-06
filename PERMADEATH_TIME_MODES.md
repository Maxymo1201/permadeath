# Modos de tiempo: GAME60 y REAL30

El mismo código produce dos jars. El modo lo fija la build (`permadeath_profile.properties` dentro del jar)
y **no es configurable** en el servidor:

| Jar | Modo | 1 día Permadeath | D60 (final) se alcanza a los… |
|---|---|---|---|
| `permadeath-GAME60-neoforge-1.21.1.jar` | GAME60 | 1 día de Minecraft (24 000 ticks del Overworld) | 60 días de Minecraft desde el inicio |
| `permadeath-REAL30-neoforge-1.21.1.jar` | REAL30 | 12 horas reales | 720 horas reales (30 días) |

Los dos modos comparten los hitos **D0, D10, D20, D25, D30, D40, D50 y D60**. El D60 es el último: el día
nunca pasa de 60 (`PermadeathCalendar.FINAL_DAY`) y no existe contenido de D70.

## Reloj central

`core/ProgressionClock` es la única fuente del día. La interfaz ofrece:
`getDay()`, `getPhase()`, `hasReached(d)`, `getElapsed()`, `getNextMilestone()`, `timeUntilDay(d)`,
`update()` (devuelve `DayChange`), `setDay(d)` (solo para administración) y `describe()` (texto de
`/permadeath status`).
Todas las mecánicas consultan `Permadeath.day()`, que delega en ese reloj. El reloj se actualiza una vez por
tick de servidor (`DayController`) y, justo después:

1. `MilestoneTracker.pending(state, day)` ejecuta **una sola vez** cada hito alcanzado que falte,
   aunque se salte del D9 al D41. Los hitos ejecutados se guardan en `executedMilestones`.
2. `MobCapController` aplica el límite de monstruos del día.
3. `PhaseManager.ensurePhase` cambia de handler si ha cambiado la fase (llama a `onPhaseEnd` del handler
   anterior y a `onPhaseStart` del nuevo).
4. Si se cruza un umbral de recetas (D40, D50, D60), se recargan los datapacks para reevaluar las
   condiciones `permadeath:min_day`.

## GAME60

* **Fórmula:** `díaPD = floor(dayTime del Overworld / 24000) − baseWorldDay`.
* **`baseWorldDay`** se fija en el primer arranque con el mod (día de mundo actual) y se guarda en
  `permadeath_progression`. Un mundo que ya va por el día 347 empieza en el D0 (test
  `existingWorldBaseDay347`).
* **Dormir** adelanta el `dayTime` y por tanto el calendario, igual que en vanilla (test
  `sleepingAdvancesTheCalendar`).
* **Protección frente a retrocesos:** se guarda `maxEffectiveDay`. Si alguien hace `/time set 0` o retrocede
  el reloj del mundo, el día Permadeath **no baja**; se avisa una vez en el log (test
  `timeRollbackNeverMovesBack`).
* **No usa el reloj del sistema** para el calendario. Los TPS solo afectan a la velocidad con que avanza el
  `dayTime`, como en vanilla.
* `/permadeath setday N` reancla `baseWorldDay = díaMundo − N`, fija `maxEffectiveDay = N` y borra los hitos
  posteriores a N para que vuelvan a ejecutarse al alcanzarlos (test `setDayAdjustsBaseSafely`).

## REAL30

* **Fórmula:** `díaPD = floor((ahora − startEpochMillis) / 12 h)`, con tope en 60. Usa
  `java.time.Clock.systemUTC()` y, en los tests, se inyecta un `Clock` fijo.
* **Cuenta con el servidor apagado:** el tiempo es epoch UTC (test `serverOfflineTimeKeepsCounting`).
* **Independiente de los TPS** (test `tpsHasNoInfluence`) y de la zona horaria o el horario de verano (test
  `timezoneAndDstCannotChangeProgress`).
* **Protección frente a retrocesos del reloj del sistema:** se guarda `maxElapsedMillis`. Si el reloj vuelve
  atrás, el progreso no retrocede y se avisa una vez (test `clockRegressionNeverMovesBack`). El máximo se
  guarda en disco cada minuto de progreso.
* **Límites exactos** (test parametrizado): D10 a las 120 h, D20 a las 240 h, D25 a las 300 h, D30 a las
  360 h, D40 a las 480 h, D50 a las 600 h y D60 a las 720 h. A las 1000 h sigue en D60.
* `/permadeath status` muestra la fecha UTC de inicio, el tiempo real transcurrido y la fecha UTC exacta del
  siguiente hito.

## Temporizadores en tiempo real (en ambos modos)

| Sistema | Tiempo | Persistencia |
|---|---|---|
| Death Train | Horas reales: D1-24 = día h; D25-49 = (día−24) h; D50 = 30 min; D51-60 = (día−49)×30 min. Cada muerte suma su duración al final actual | `deathTrainEndEpochMillis` (instante absoluto: si el servidor se apaga, la tormenta sigue contando) |
| Wither del D60 | 60 min reales de presencia en el Overworld por jugador | `witherRemainingMillis` por UUID; descuenta ms reales, con un máximo de 5 s por tick (un servidor colgado no gasta el temporizador de golpe) |
| Life Orb | 8 h reales desde el inicio del D60 (REAL30: instante exacto del D60 + 8 h; GAME60: primer momento observado en D60 + 8 h, como en Fabric) | `lifeOrbDeadlineEpochMillis`, `lifeOrbActive` |
| Maldición de The Beginning | 12 h reales | `permadeath_beginning_curse` |

En Fabric la tormenta y el Wither contaban ticks, así que iban más lentos con TPS bajos y se paraban con el
servidor apagado. La tormenta, además, se descontaba una vez por cada dimensión cargada en cada tick.

## Persistencia

`data/permadeath_progression.dat` (en el Overworld) guarda: `formatVersion`, `mode`, `initialized`,
`baseWorldDay`, `startEpochMillis`, `maxElapsedMillis`, `maxEffectiveDay`, `executedMilestones`, Death Train,
Life Orb, temporizadores del Wither, Mikecrack, contador de Hyper Golden Apple+ por jugador, estado de la arena
del End y la marca `legacyMigrated`.

### Cambiar un mundo de modo

Si un mundo creado con un jar se abre con el otro, **se conserva el día Permadeath** y se reancla el modo
nuevo: GAME60 calcula `baseWorldDay = díaMundo − día` y REAL30 calcula `start = ahora − día × 12 h`. Se avisa
en el log (tests `movingWorldFromReal30PreservesDay` y `movingWorldFromGame60PreservesDay`).

### Migración desde el mod Fabric

En el primer arranque se importan, si existen, los ficheros sueltos del mod Fabric y **nunca se borran**:

| Fichero Fabric | Importación |
|---|---|
| `permadeath_date.txt` (`dd/MM/yyyy`) | Día Fabric = días desde la fecha + 1, con tope en 60. Se conserva ese día en el modo del jar |
| `permadeath_storm.txt` | Ticks restantes × 50 ms → fin absoluto del Death Train |
| `permadeath_wither.txt` | Ticks por jugador × 50 ms |
| `permadeath_lifeorb.txt` | Plazo y estado del Life Orb |
| `permadeath_mikecrack.txt` | Modo Mikecrack |
| `data/hyper_apple_consumed.json` | Manzanas consumidas por jugador |
| `permadeath_boost.txt` | Ignorado (el nivel de buffs del Death Train se deriva ahora del día) |

Al terminar se registra `[Permadeath] Legacy state migrated successfully (original files kept in …)`. Los
`SavedData` de Fabric con el mismo nombre (portal, mensajes, modo servidor, maldición, logro) se leen
directamente.

## Comandos de calendario

| Comando | Permiso | Efecto |
|---|---|---|
| `/permadeath status` (alias `days`) | Todos (salvo con el modo restringido activo) | Texto de `ProgressionClock#describe` + tormenta activa |
| `/permadeath setday <0-60>` (alias `changeday`) | OP 2 | Reancla el calendario, ejecuta los hitos pendientes y cambia de fase en el acto |
| `/permadeath reset` | OP 2 | Día 0, borra las marcas de muerte, termina la tormenta y reinicia la fase |
| `/permadeath debug` | OP 2 | Todos los campos persistidos |
