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
  el reloj del mundo, el día Permadeath **no baja**; se avisa una vez en el log y `baseWorldDay` se reancla al
  día de mundo actual, así que el siguiente día de Minecraft es el siguiente día Permadeath. Antes el calendario
  se congelaba hasta que el mundo volvía a alcanzar el día perdido: con un simple `/time set day` (que pone el
  contador de días a 0), tantos días de Minecraft como tuviera el mundo (tests `timeRollbackNeverMovesBack` y
  `timeSetDayDoesNotFreezeTheCalendar`).
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

## Tres tipos de tiempo

| Tipo | Qué mide | Quién lo usa | ¿Corre con el servidor apagado? | ¿Corre sin supervivientes conectados? |
|---|---|---|---|---|
| **Calendario** | El día Permadeath (D0-D60) | Hitos, fases, reglas de cada día | GAME60: no (es el `dayTime` del mundo). REAL30: sí (epoch UTC) | Sí (GAME60 mientras el mundo avance; REAL30 siempre) |
| **Tiempo activo global** | Tiempo real efectivo de la campaña | Death Train, X2 Shulker Shells, plazo de la Life Orb, desafío final | No | No: se pausa |
| **Tiempo individual** | Tiempo real efectivo de UN jugador | Wither periódico del D60, maldición y bendición de The Beginning | No | Solo corre para ese jugador mientras está conectado y es elegible |

* **Superviviente elegible** (`mechanics/Participants`): jugador conectado, vivo, en supervivencia o aventura y
  sin la marca de Permadeath. Los espectadores (muertos u observadores), los jugadores en creativo y los
  eliminados no hacen correr nada.
* **Tiempo real efectivo** (`core/time/EventClock`): diferencia de `System.nanoTime()` entre ticks de servidor.
  Es monotónico (un cambio de hora del sistema no le afecta), no depende de los TPS (un tick lento descuenta lo que
  realmente duró), el primer tick tras arrancar descuenta 0, cada tick descuenta como máximo 10 s (un servidor
  congelado o un salto del reloj no gastan los temporizadores de golpe) y **nunca se guarda** `nanoTime`: en disco
  solo hay tiempo restante.
* Todos los temporizadores avanzan en un único punto (`progression/CampaignTicker`, una vez por tick y con un
  único paso de reloj), en este orden: desafío final (inicio/participantes) → temporizadores globales → reacciones
  (fin de tormenta, fin de evento, plazo de la Life Orb, evaluación final) → temporizadores individuales.

## Duraciones de cada perfil

Única fuente: `core/time/PermadeathTimings` (`Permadeath.timings()`).

| Temporizador | Tipo de tiempo | GAME60 | REAL30 |
|---|---|---|---|
| Wither periódico del D60 | Individual (en el Overworld) | 8 min | 30 min |
| Plazo de la Life Orb (D60) | Activo global | 20 min | 4 h |
| Evento X2 Shulker Shells | Activo global | 10 min | 2 h |
| Maldición de The Beginning (sin leche + Lentitud I + Debilidad I) | Individual | 10 min | 6 h |
| Bendición de The Beginning (Resistencia II) | Individual | 10 min | 6 h |
| Death Train (por muerte) | Activo global | `max(60 s, original / 72)` | `original / 2` |
| Desafío final del D60 | Activo global | 30 min | 6 h |

### Death Train de referencia

`original` es la duración histórica de `DayRules.deathTrainDurationMillis` (plugin y jar Fabric): D1-24 = día h;
D25 = 1 h; D26-49 = (día−24) h; D50 = 30 min; D51-60 = (día−49) × 30 min; D0 = 1 h. Todas son múltiplos de 30 min,
así que las dos divisiones son exactas. Se escala **una sola vez**, al sumar la muerte.

| Día | Original | GAME60 | REAL30 |
|---|---|---|---|
| D0 | 1 h | 1m 00s (mínimo) | 30m 00s |
| D1 | 1 h | 1m 00s (mínimo) | 30m 00s |
| D10 | 10 h | 8m 20s | 5h 00m |
| D24 | 24 h | 20m 00s | 12h 00m |
| D25 | 1 h | 1m 00s (mínimo) | 30m 00s |
| D30 | 6 h | 5m 00s | 3h 00m |
| D40 | 16 h | 13m 20s | 8h 00m |
| D49 | 25 h | 20m 50s | 12h 30m |
| D50 | 30 min | 1m 00s (mínimo) | 15m 00s |
| D55 | 3 h | 2m 30s | 1h 30m |
| D60 | 5 h 30 min | 4m 35s | 2h 45m |

Los tests (`PermadeathTimingsTest` y la GameTest `deathTrainDurationOfEveryDayFollowsTheProfile`) comprueban
los 61 días en los dos perfiles.

### Temporizadores tácticos (sin cambios)

No son temporizadores de progresión y siguen en ticks donde se usan: mecha de la Supernova, cooldowns (perla del
D60, tótems), ataques y fases del dragón, efectos cortos de pociones, Mikecrack, mecánicas vanilla.

## Death Train

* Cada muerte **suma** su duración escalada a lo que queda (dos muertes en el mismo tick suman las dos; GameTest
  `simultaneousDeathsAddBothStorms`). Nunca reinicia la tormenta.
* Se guarda el **tiempo restante** (`DeathTrainRemaining`), no un instante de fin. Solo se consume con el servidor
  en marcha y al menos un superviviente elegible conectado; con el servidor vacío o apagado queda congelada (smoke
  test: `Death Train activo: quedan 2h 00m` antes y después del reinicio, sin jugadores).
* Mientras dura: tormenta permanente en el Overworld, contador en la barra de acción (`(en pausa: ningún
  superviviente conectado)` cuando corresponde), buffs permanentes de mobs desde el D25 y, desde el D50, modo UHC
  (`naturalRegeneration=false`). La regla se restaura al terminar **solo si la tormenta la cambió**
  (`DeathTrainUhc` guardado); un servidor que la tenía desactivada la conserva así.
* `/permadeath storm addHours|removeHours|addMinutes|removeMinutes <n>`: tiempo real efectivo elegido por el
  administrador, **sin escalar**; quitar deja al menos 1 s. `/permadeath resetstorm` la termina.
* GAME60 no inventa muertes falsas para alargarla.

## Wither periódico del D60

* Un contador por UUID (`WitherTimers`), guardado en el mundo: sobrevive a relogs y reinicios.
* Solo corre mientras ese jugador está conectado, es elegible y está en el Overworld.
* El contador se reinicia a un intervalo completo **solo cuando el Wither ha entrado de verdad en el mundo**; si
  `addFreshEntity` falla se reintenta. Como máximo un Wither por jugador y tick: un servidor congelado no provoca
  una ráfaga (GameTest `witherCounterIsPerPlayerAndOnlyRunsForEligiblePlayers`).
* Contadores guardados con más tiempo que el intervalo del perfil (mundos antiguos con 60 min) se limitan al
  intervalo actual.
* Opción `witherAccumulationLimit` (desactivada, 0): si hay al menos N Withers a 128 bloques, el del jugador
  espera y se anota en el log.
* `/permadeath wither [jugador]` muestra el tiempo restante de cada jugador (conectados y desconectados).

## Life Orb

* Solo en el D60. El plazo empieza cuando empieza el desafío final (primer superviviente elegible conectado en el
  D60), no en el instante teórico del D60: un servidor REAL30 que llega al D60 apagado recibe las 4 h completas al
  arrancar, sin penalización instantánea.
* Barra de jefe con el tiempo restante; se pausa como el resto del tiempo activo.
* Al vencer, cada jugador en supervivencia sin Life Orb en el inventario recibe el modificador persistente e
  idempotente `permadeath:life_orb_penalty` (−16 de vida máxima, solo el máximo). No se acumula consigo mismo ni con
  las penalizaciones del D40/D60 (modificadores independientes). Con las tres activas la vida máxima queda en 1
  (mínimo de vanilla); el jugador no muere por ello.
* Los jugadores que se conectan después del plazo conservan la consecuencia original tras una breve espera de
  sincronización de 60 ticks (para no penalizar antes de cargar el inventario); la espera no les da un plazo nuevo.
* `/permadeath event lifeorb` reinicia el plazo completo del perfil (plugin `/pdc event lifeorb`).

## Otros temporizadores

* **X2 Shulker Shells** (`/permadeath event shulkershell`): tiempo activo global, barra de jefe, guardado como
  restante (`ShulkerEventRemaining`); un evento en marcha no se reinicia.
* **Maldición de The Beginning** (`/permadeath maldicion <jugador>`): por jugador, solo mientras ese jugador está
  conectado y no es espectador. La prohibición de la leche, la Lentitud I y la Debilidad I usan **el mismo
  reloj**: cada segundo se ajusta la duración de los efectos al tiempo restante (no pueden terminar antes ni después
  que la prohibición, aunque bajen los TPS o el jugador se reconecte). Beber leche maldito sigue matando.
* **Bendición de The Beginning** (primer jugador que entra): Resistencia II con la duración exacta del perfil en
  tiempo real del jugador, sincronizada igual que la maldición. Si el jugador pierde el efecto (leche, muerte) la
  bendición termina.

## Desafío final del D60

Estados (`FinalPhaseState`): `NOT_STARTED`, `ACTIVE`, `COMPLETED`, `FAILED`. El calendario se queda en el D60
antes, durante y después: no existe D61.

| Momento (tiempo activo desde T0) | GAME60 | REAL30 |
|---|---|---|
| T0: primer superviviente elegible en el D60 → anuncio, plazo de la Life Orb y contadores de Wither | 0 | 0 |
| Withers periódicos (por jugador en el Overworld) | 8, 16, 24 min | cada 30 min |
| Fin del plazo de la Life Orb | 20 min | 4 h |
| Fin del desafío y evaluación | 30 min | 6 h |

* **REAL30 `strictCampaignDuration=true`** (opcional, desactivado): el D60 empieza a la hora 714 y el desafío
  termina exactamente a la hora 720 del calendario (el desafío y el plazo de la Life Orb siguen el reloj de
  pared, como el calendario REAL30). Por defecto el D60 llega a las 720 h y el desafío dura 6 h efectivas más.
* **Victoria** de un participante: sigue vivo (no eliminado, no espectador) al terminar, tuvo la Life Orb antes de
  que venciera su plazo y la conserva al final. La campaña es `COMPLETED` si gana al menos uno y `FAILED` si no gana
  nadie o si todos los participantes son eliminados antes del final.
* El resultado se evalúa y guarda **una sola vez** (participantes, motivo, hora de fin) y se anuncia con títulos y
  un resumen en el chat. El mundo no se borra ni se modifica.
* Después, con `freezeAfterCampaign=true` (por defecto) se detienen los temporizadores periódicos (Withers); con
  `false` sigue la supervivencia libre con todas las mecánicas del D60.
* Un `setday` por debajo del D60 o `/permadeath reset` devuelven el desafío a `NOT_STARTED`.

## Pausas y reinicios

* Servidor apagado: ningún temporizador de tiempo activo o individual se consume. Al arrancar se continúa con el
  tiempo restante guardado (GameTests `…SurvivesARestart` y smoke test con reinicio real).
* Servidor sin supervivientes elegibles: se pausan los temporizadores globales; los individuales solo corren para
  quien está conectado.
* Lag o tick congelado: como mucho 10 s por tick y nunca más de un Wither por jugador y tick.
* Cambio de hora del sistema: no afecta al tiempo activo (monotónico). El calendario REAL30 sigue protegido con
  `maxElapsedMillis`.

## Opciones del servidor

`config/permadeath-server.toml` (una copia en `<mundo>/serverconfig/` lo sustituye solo para ese mundo):

| Opción | Por defecto | Efecto |
|---|---|---|
| `campaign.strictCampaignDuration` | `false` | Solo REAL30: D60 a la hora 714 y fin del desafío a la hora 720 |
| `campaign.freezeAfterCampaign` | `true` | Tras el desafío final se detienen los Withers periódicos; `false` = supervivencia libre del D60 |
| `wither.witherAccumulationLimit` | `0` | Protección anti-acumulación de Withers (0 = desactivada) |

El perfil GAME60/REAL30 **no** es una opción: lo fija el jar.

## Persistencia

`data/permadeath_progression.dat` (en el Overworld, formato 2) guarda: `FormatVersion`, `Mode`, `Initialized`,
`BaseWorldDay`, `StartEpochMillis`, `MaxElapsedMillis`, `MaxEffectiveDay`, `ExecutedMilestones`, el tiempo
restante del Death Train (`DeathTrainRemaining`, `DeathTrainUhc`), de la Life Orb (`LifeOrbRemaining`, −1 = sin
plazo; `LifeOrbActive`) y del evento X2 Shulker Shells (`ShulkerEventRemaining`), el desafío final (`FinalPhase`,
`FinalPhaseRemaining`, `FinalPhaseStarted`, `FinalPhaseEnded`, `FinalParticipants` con nombre, eliminado, Life Orb a
tiempo, la conserva, superviviente y resultado), los contadores del Wither por UUID (`WitherTimers`), la migración
(`MigratedFrom`, `MigrationTime`, `MigrationSummary`, `FormatV1`), Mikecrack, contador de Hyper Golden Apple+ por
jugador, estado de la arena del End y la marca `legacyMigrated`.

`data/permadeath_beginning_curse.dat` (formato 2) guarda el tiempo restante de la maldición y de la bendición de
cada jugador.

### Migración de mundos anteriores (formato 1 → 2)

Las versiones anteriores de este port guardaban instantes absolutos (`DeathTrainEnd`, `LifeOrbDeadline`,
`ShulkerEventEnd`, `Until` de la maldición). Al arrancar con esta versión (`core/time/TimerMigration`, una sola vez):

* Death Train y X2 Shulker Shells conservan exactamente el tiempo observable (`fin − ahora`), **sin volver a
  aplicar el factor del perfil**. Lo ya vencido queda en 0 (nunca negativo).
* Life Orb: un plazo vencido sigue vencido (la penalización no se aplica dos veces: el modificador es idempotente);
  uno en marcha conserva su tiempo con tope en el plazo del perfil.
* Un mundo que ya estaba en el D60 con el plazo iniciado recibe un desafío final `ACTIVE` alineado con el plazo de
  la Life Orb.
* Maldición: tiempo observable con tope en la duración del perfil; las vencidas se descartan.
* Hitos ejecutados, contadores del Wither (con tope en el intervalo nuevo) y el resto de valores se conservan.
* Los valores originales quedan en `FormatV1` y la conversión se anota en el log (`Progression data migrated from
  format 1 to 2 …`) y en `/permadeath debug`. Un mundo ya migrado nunca vuelve a migrarse.
* No se puede reconstruir una Resistencia II de bendición concedida por una versión anterior (no se guardaba su
  temporizador): ese efecto sigue con la duración que ya tenía.

### Cambiar un mundo de modo

Si un mundo creado con un jar se abre con el otro, **se conserva el día Permadeath** y se reancla el modo
nuevo: GAME60 calcula `baseWorldDay = díaMundo − día` y REAL30 calcula `start = ahora − día × 12 h`. Se avisa
en el log (tests `movingWorldFromReal30PreservesDay` y `movingWorldFromGame60PreservesDay`).

### Migración desde el mod Fabric

En el primer arranque se importan, si existen, los ficheros sueltos del mod Fabric y **nunca se borran**:

| Fichero Fabric | Importación |
|---|---|
| `permadeath_date.txt` (`dd/MM/yyyy`) | Día Fabric = días desde la fecha + 1, con tope en 60. Se conserva ese día en el modo del jar |
| `permadeath_storm.txt` | Ticks restantes × 50 ms → tiempo restante del Death Train |
| `permadeath_wither.txt` | Ticks por jugador × 50 ms (con tope en el intervalo del perfil) |
| `permadeath_lifeorb.txt` | Estado del Life Orb; un plazo en marcha pasa a tiempo restante (con tope en el plazo del perfil) |
| `permadeath_mikecrack.txt` | Modo Mikecrack |
| `data/hyper_apple_consumed.json` | Manzanas consumidas por jugador |
| `permadeath_boost.txt` | Ignorado (el nivel de buffs del Death Train se deriva ahora del día) |

Al terminar se registra `[Permadeath] Legacy state migrated successfully (original files kept in …)`. Los
`SavedData` de Fabric con el mismo nombre (portal, mensajes, modo servidor, maldición, logro) se leen
directamente.

## Comandos de calendario

| Comando | Permiso | Efecto |
|---|---|---|
| `/permadeath status` (alias `days`) | Todos (salvo con el modo restringido activo) | Perfil, calendario (día, fase, siguiente hito), Death Train, X2 Shulker Shells, plazo de la Life Orb y estado de la campaña / desafío final, indicando si están en pausa |
| `/permadeath tiempos` | OP 2 | Duraciones del perfil, opciones, temporizadores globales, participantes del desafío final, Wither de cada jugador, maldiciones y bendiciones |
| `/permadeath wither [jugador]` | OP 2 | Tiempo restante hasta el próximo Wither (por UUID, también desconectados) |
| `/permadeath storm addHours\|removeHours\|addMinutes\|removeMinutes <n>` | OP 2 | Tiempo real efectivo del Death Train, sin escalar |
| `/permadeath event shulkershell\|lifeorb` | OP 2 | Evento X2 Shulker Shells / reinicio del plazo de la Life Orb |
| `/permadeath setday <0-60>` (alias `changeday`) | OP 2 | Reancla el calendario, ejecuta los hitos pendientes y cambia de fase en el acto |
| `/permadeath reset` | OP 2 | Día 0, borra las marcas de muerte, termina la tormenta y reinicia la fase |
| `/permadeath debug` | OP 2 | Todos los campos persistidos, incluida la migración de formato |

Formato de los tiempos: `8m 20s`, `4h 00m`, `2h 45m` (`TimeFormat.compact`); nunca negativos.
