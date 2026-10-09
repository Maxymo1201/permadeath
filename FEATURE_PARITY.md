# Paridad de funcionalidades Fabric → NeoForge

**Probado** solo indica una prueba que se ha **ejecutado** de verdad (ver `PERMADEATH_TEST_REPORT.md`):

* **Sí (unit)**: lo cubren los tests JUnit del núcleo (95/95 OK).
* **Sí (GameTest)**: lo comprueba una aserción de los GameTests, ejecutados en un servidor NeoForge 21.1.256 con
  los perfiles GAME60 y REAL30 (27/27 OK en cada uno).
* **Sí (servidor)**: lo comprueba `tools/server-smoke-test.sh` con los jars de producción (arranque + reinicio,
  OK en GAME60 y REAL30).
* **No**: sin prueba automática ejecutada. Se ha verificado por revisión de código contra el jar original y el
  plugin Permadeath (1.3 y PermaDeathCore).

Las filas marcadas con **(plugin)** cambiaron tras la comparación con el plugin Permadeath 1.3; el detalle está
en `PERMADEATH_AUDIT.md` §5.

Estado del port: ✔ portado · ✚ portado con corrección · ≈ diferencia documentada · ✖ no portado (fuera de alcance).

## Calendario y progresión

| Funcionalidad | Fabric | NeoForge | Estado | Probado |
|---|---|---|---|---|
| Día Permadeath | Fecha real, días de 24 h, `speedrun` | GAME60 (días de Minecraft) y REAL30 (12 h reales), fijado por jar | ✚ | Sí (unit, GameTest, servidor) |
| Hitos D0/10/20/25/30/40/50/60 idempotentes, también con saltos | Por fase | `MilestoneTracker` | ✚ | Sí (unit, servidor: D40 no se repite al reiniciar) |
| D60 final | Hasta D70 | Tope en 60 | ✚ | Sí (unit, GameTest) |
| Protección frente a rollback de `/time` (GAME60) y del reloj del sistema (REAL30) | No | `maxEffectiveDay` / `maxElapsedMillis` | ✚ | Sí (unit) |
| REAL30 cuenta con el servidor apagado e ignora los TPS | — | Epoch UTC con `Clock` inyectable | ✚ | Sí (unit) |
| Migración de los ficheros de Fabric sin borrarlos | — | `LegacyMigration` + `LegacyFabricState` | ✚ | Sí (unit, parser) |
| Persistencia `SavedData` | Mezcla de `.txt` y `PersistentState` | `permadeath_progression` + los mismos `SavedData` de Fabric | ✚ | Sí (servidor: el día sobrevive al reinicio) |
| `/permadeath status/days/info/setday/changeday/reload/reset/resetstorm/BeginningLocation/mikecrack/mensaje/server/maldicion/survival` | Sí (sin OP) | Sí; los administrativos exigen OP 2; añade `debug` | ✚ | Sí (servidor: `status`, `setday`, `debug`); resto No |
| `/permadeath awake`, `storm addHours/removeHours`, `give`, `bendicion`, `event shulkershell/lifeorb` (plugin) | No | Comandos del plugin (los de administración con OP 2) | ✚ | Sí (servidor: `storm addHours`, `event shulkershell`); resto No |
| `/permadeath speedrun` | Sí | Sustituido por las builds GAME60/REAL30 | ✖ | — |

## Muerte, tótems y jugador

| Funcionalidad | Fabric | NeoForge | Estado | Probado |
|---|---|---|---|---|
| Muerte: espectador, títulos, sonidos, monumento, mensajes, reaparición automática y baneo | Sí (hilos) | Sí (planificador del servidor); sonido de Permadeath en todas las muertes (plugin) | ✚ | No |
| Cabezas del monumento con fecha, hora y causa de muerte (plugin) | No | `DeathRecordsData` | ✚ | No |
| Mensaje personal al morir | Sí | Sí | ✔ | No |
| Death Train: duración por día, buffs de mobs, modo UHC desde el D50, barra de acción | Ticks | Tiempo activo restante escalado por perfil (GAME60 /72 mín. 60 s, REAL30 /2), en pausa sin supervivientes; buffs infinitos y Resistencia al fuego D50-59 (plugin); anuncio del modo UHC | ✚ | Sí (unit, duraciones y buffs; GameTest, buffs infinitos D50; servidor, persiste tras reiniciar) |
| Tótems: tabla 0/1/3/5/7 % y 1/1/2/2/3 tótems | 1 % desde D0 | Tabla exacta | ✚ | Sí (unit, GameTest) |
| Medalla de superviviente (D55) | Sí | Sí | ✔ | No |
| −8 de vida máxima (D40) / −16 (D60) | Sí | Modificadores con ID estable | ✔ | Sí (unit, GameTest) |
| Bonus de vida por conjuntos de armadura, élitros blindados y Resistencia del set infernal | Sí | Sí | ✔ | No |
| Hyper Golden Apple+ (límite 1 → 2, +4 de vida persistente) | JSON | `SavedData` (JSON importado); efectos de manzana de oro y mensaje del plugin | ✚ | Sí (unit, límites) |
| Super Golden Apple+ | Sí | Efectos de manzana de oro + Salud aumentada (plugin) | ✚ | Sí (GameTest) |
| Huecos bloqueados D40 / D60 y reliquias | Sí | Sí; los objetos desplazados se sueltan; los bloqueadores no salen del inventario (plugin) | ✚ | Sí (GameTest, hueco 4 en D40); reliquias No |
| Life Orb (−16 de vida máxima, barra de jefe) | Sí, 8 h (quitaba también vida actual en cada reconexión) | 20 min / 4 h de tiempo activo desde el inicio del desafío final; modificador permanente (plugin) | ✚ | Sí (unit, GameTest) |
| Wither periódico (D60) | 72000 ticks | 8 min / 30 min de presencia real por jugador en el Overworld, sin ráfagas | ✚ | Sí (GameTest) |
| Desafío final del D60 (30 min / 6 h, victoria o derrota por jugador) | No | Sí: estados, evaluación única, persistencia | ✚ | Sí (unit, GameTest) |
| PvP desde el D40 | Sí | Sí | ✔ | Sí (unit, GameTest) |
| Ahogamiento ×5 / ×10 | +5 / +19 fijos | delta vanilla × multiplicador; golpes de 5 / 10 (plugin) | ✚ | Sí (unit, GameTest) |
| Ceguera por lluvia en Death Train | 1/7500 en D50-59, hueco en D60 | 1/10000 en D40, 1/5000 desde D50 | ✚ | Sí (unit) |
| Comidas peligrosas, tarta de calabaza, Héroe de la Aldea, fatiga de minería ×2 | Sí (el límite del héroe fallaba) | Tabla del plugin; la leche cura venenos pero no la fatiga | ✚ | Sí (GameTest, pez globo y leche) |
| Perlas de ender D60 | 2 s al lanzar | 6 s al caer (plugin) | ✚ | Sí (GameTest) |
| Levitación aleatoria D50+ | 1/10000 por tick, también de noche | Lluvia a cielo abierto, 1/10000 por segundo (plugin) | ✚ | Sí (unit, constantes) |
| Mensaje de bienvenida | Sí | Sí | ✔ | No |

## Mundo, mobs y fases

| Funcionalidad | Fabric | NeoForge | Estado | Probado |
|---|---|---|---|---|
| Sueño D0-9 (1 jugador) / D10-19 (4 jugadores) / camas que explotan D20+ | Sí | Sí (`TIME_SINCE_REST`); 10 % desde el D50 (plugin) | ✚ | No |
| Límite de monstruos ×2 | Desde D11 | Desde D10 | ✚ | Sí (unit, GameTest) |
| Conversión a hostil de todos los mobs (D20+) | Sí (daño 3) | Daño 8 o vanilla; gólems de aldea hostiles (plugin) | ✚ | Sí (GameTest, gólems) |
| Efectos de arañas, silverfish, endermites, cerdos con nombre, phantoms y vindicators | Se acumulaban | Una vez por mob, tabla del plugin | ✚ | No |
| Clases de esqueleto D20/D30/D40/D50/D60 | Solo jinetes en el D20; sin loot | Esqueletos naturales desde el D20, probabilidades, drops y números del plugin | ✚ | Sí (GameTest, clase D20) |
| Phantoms gigantes | Se duplicaba la vida en cada recarga | Una vez; tamaño 18 desde el D50 (plugin) | ✚ | Sí (unit, constantes) |
| GIGA Slime/MagmaCube, ghasts demoníacos y sus drops (D25) | Sí | Sí | ✔ | No |
| Sustituciones (calamar, murciélago, zombi, lobo, salmón, aldeano, guardián) | Sí | Sí | ✔ | No |
| Creepers cargados, Ender/Quantum Creepers, esquivas por teletransporte | Sí | Esquivan todo salvo cuerpo a cuerpo (plugin); cráter de magma en el Nether | ✚ | Sí (GameTest, esquiva) |
| Ender Ghast / Ghast Definitivo | Perdía la IA al reiniciar | IA restaurada al cargar | ✚ | No |
| Pillagers ametralladora y brujas imposibles | Perdían la IA al reiniciar | Restaurada | ✚ | No |
| Clases de pigman y salto del ghast | 31 %; el salto nunca ocurría | 5/99 y 20/99 (plugin); salto corregido | ✚ | Sí (unit, constantes) |
| Stack Ultra Ravager (Jess y Carlos) y drops | Desde piglins, tótem garantizado | Clase de pigman, vidas y drops del plugin; Ultra Ravager dorado | ✚ | No |
| Araña Abismal (módulo de muerte < D75) | Sí | Sí | ✔ | No |
| Zombie Gigante y Wither Emperador (D50) | 5 %/20 %, re-tirada en cada carga | 1/500 y 1/50 del plugin, una tirada por mob | ✚ | Sí (unit, constantes) |
| Gatos supernova (D40) / galácticos (D50) / osos polares explosivos | Temporizadores rotos | 30 s y potencia 200, cuenta atrás galáctica, osos al golpear (plugin) | ✚ | Sí (unit, constantes) |
| Lluvia de mobs en el Nether, minería sin netherita, alma de arena | Sí | Sí; arena de almas D60 30 s (plugin) | ✚ | No |
| Illagers D50/D60 (evokers, vexes) | Evokers desde zombis | Reglas del plugin | ✚ | No |
| Armadura de netherita personalizada | D25-D60, 10-20 % | D25-29, 10 %, solo con jugador (plugin) | ✚ | Sí (unit, constantes) |
| Cambio de Mikecrack (D60) | Desactivado por defecto | Activo por defecto (plugin) | ✚ | No |
| Golems de nieve salvajes y Shulker Críptico (D60) | Sí | Sí | ✔ | No |
| Flechas TNT del esqueleto demoníaco (D60) | Recorría todas las entidades en cada tick | `ProjectileImpactEvent` | ✔ | No |
| Bacalao de la muerte | 59 de daño fijos | Sharpness 50 / Knockback 100 reales | ✚ | No |
| Límite de magma cubes en los deltas de basalto, Mikecrack | Sí | `SpawnPlacementCheck` | ✔ | No |
| Sin aldeas, templos, monumentos ni naufragios desde el D40 | Mixin | Mixin | ✔ | No |
| Herramientas de netherita irrompibles y doradas | Mixin | Componentes por defecto | ≈ | No |

## End, The Beginning y datos

| Funcionalidad | Fabric | NeoForge | Estado | Probado |
|---|---|---|---|---|
| End cerrado antes del D30 | Mixin | `EntityTravelToDimensionEvent` | ✔ | Sí (unit, GameTest) |
| Pilares de bedrock | Barrido síncrono | Por pilar | ✚ | No |
| Arena: ladrillos, altares y pociones | Altares en memoria | Persistentes, incremental | ✚ | No |
| PERMADEATH DEMON (vida, furia, giro, ataques) | 1000/1600, furia a 1/6 | 2000/2000, furia a 600, rayos, ráfaga de TNT, curación a la mitad (plugin) | ✚ | No |
| Bolas de fuego del dragón | Sí | Sí | ≈ | No |
| Regeneración de cristales | 40-100 min; se congelaba | 30-240 s del plugin; tick de nivel | ✚ | No |
| Endermen y shulkers del End | 11 %/4 %, TNT de 4 s | 1/20 y 1/170, mechas de 1-2 s (plugin) | ✚ | No |
| Portal D40 en el Overworld | Solo al empezar la fase D40 | Hito D40 idempotente | ✚ | Sí (servidor: hito D40 una sola vez) |
| The Beginning (dimensión, islas, corales, estructuras) | Sí | Mismos IDs y algoritmo | ✔ | Sí (servidor: la dimensión carga y se guarda); generación No |
| Gateways (vacío D40-49, ida y vuelta D50+, tormenta) | Mixin | Mixin | ✔ | No |
| Bendición, expulsión por tormenta y maldición | Sí | Sí; maldición y bendición de 10 min / 6 h con los efectos sincronizados con su tiempo | ✚ | Sí (GameTest) |
| Mobs de The Beginning, explosiones y Wither sin destruir bloques | Sí | Tabla de aparición y creepers del plugin, sin cubos, cofres del plugin, cierre durante el Death Train | ✚ | No |
| Recetas por día (`min_day`) | `fabric:load_conditions` | `neoforge:conditions` + recarga en los umbrales | ✔ | No |
| Antorchas sin receta (D40) y lingotes de horno → pepitas (D50) | Mixin | `RecipeFilter` | ✔ | Sí (GameTest, D40) |
| Receta de pepitas de pizarra profunda con hierro | Copia errónea | Corregida | ✚ | No |
| Coste extra de las recetas especiales | Podía duplicar objetos | Corregido | ✚ | No |
| Cofres vacíos en el D60 | `LootTableEvents` + mixin (también vaciaba los de The Beginning) | Global Loot Modifier solo para `minecraft:chests/*`; The Beginning conserva su loot | ✚ | Sí (GameTest; servidor: cofres de The Beginning con loot en el D60) |
| Contenido D61-69 / D70 / logro de 70 días | Sí | Fuera de alcance (D60 final); datos conservados | ✖ | — |
