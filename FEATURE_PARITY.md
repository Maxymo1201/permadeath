# Paridad de funcionalidades Fabric → NeoForge

**Probado** solo indica una prueba que se ha **ejecutado** de verdad (ver `PERMADEATH_TEST_REPORT.md`):

* **Sí (unit)**: la regla o el cálculo lo cubren los tests JUnit del núcleo, que se ejecutaron (82/82 OK).
* **No (GameTest escrito)**: existe un GameTest, pero no se ha podido ejecutar porque la compilación del mod
  necesita hosts bloqueados en este entorno.
* **No**: sin prueba ejecutada. Se ha verificado por revisión de código contra el jar original.

Estado del port: ✔ portado · ✚ portado con corrección · ≈ diferencia documentada · ✖ no portado (fuera de alcance).

## Calendario y progresión

| Funcionalidad | Fabric | NeoForge | Estado | Probado |
|---|---|---|---|---|
| Día Permadeath | Fecha real, días de 24 h, `speedrun` | GAME60 (días de Minecraft) y REAL30 (12 h reales), fijado por jar | ✚ | Sí (unit) |
| Hitos D0/10/20/25/30/40/50/60 idempotentes, también con saltos | Por fase | `MilestoneTracker` | ✚ | Sí (unit) |
| D60 final | Hasta D70 | Tope en 60 | ✚ | Sí (unit) |
| Protección frente a rollback de `/time` (GAME60) y del reloj del sistema (REAL30) | No | `maxEffectiveDay` / `maxElapsedMillis` | ✚ | Sí (unit) |
| REAL30 cuenta con el servidor apagado e ignora los TPS | — | Epoch UTC con `Clock` inyectable | ✚ | Sí (unit) |
| Migración de los ficheros de Fabric sin borrarlos | — | `LegacyMigration` + `LegacyFabricState` | ✚ | Sí (unit, parser) |
| Persistencia `SavedData` | Mezcla de `.txt` y `PersistentState` | `permadeath_progression` + los mismos `SavedData` de Fabric | ✚ | No |
| `/permadeath status/days/info/setday/changeday/reload/reset/resetstorm/BeginningLocation/mikecrack/mensaje/server/maldicion/survival` | Sí (sin OP) | Sí; los administrativos exigen OP 2; añade `debug` | ✚ | No |
| `/permadeath speedrun` | Sí | Sustituido por las builds GAME60/REAL30 | ✖ | — |

## Muerte, tótems y jugador

| Funcionalidad | Fabric | NeoForge | Estado | Probado |
|---|---|---|---|---|
| Muerte: espectador, títulos, sonidos, monumento, mensajes, reaparición automática y baneo | Sí (hilos) | Sí (planificador del servidor) | ✔ | No |
| Mensaje personal al morir | Sí | Sí | ✔ | No |
| Death Train: duración por día, buffs de mobs, modo UHC desde el D50, barra de acción | Ticks | Epoch absoluto | ✚ | Sí (unit, duraciones y buffs) |
| Tótems: tabla 0/1/3/5/7 % y 1/1/2/2/3 tótems | 1 % desde D0 | Tabla exacta | ✚ | Sí (unit) · GameTests escritos |
| Medalla de superviviente (D55) | Sí | Sí | ✔ | No |
| −8 de vida máxima (D40) / −16 (D60) | Sí | Modificadores con ID estable | ✔ | Sí (unit, umbral) · GameTests escritos |
| Bonus de vida por conjuntos de armadura, élitros blindados y Resistencia del set infernal | Sí | Sí | ✔ | No |
| Hyper Golden Apple+ (límite 1 → 2, +4 de vida persistente) | JSON | `SavedData` (JSON importado) | ✔ | Sí (unit, límites) |
| Super Golden Apple+ | Sí | Sí | ✔ | No |
| Huecos bloqueados D40 / D60 y reliquias | Sí | Sí; los objetos desplazados se sueltan | ✔ | No (GameTest escrito) |
| Life Orb (8 h, −16, barra de jefe) | Sí | Plazo absoluto | ✚ | No |
| Wither cada 60 min reales (D60) | Ticks | ms reales | ✚ | No |
| PvP desde el D40 | Sí | Sí | ✔ | Sí (unit) · GameTests escritos |
| Ahogamiento ×5 / ×10 | +5 / +19 fijos | delta vanilla × multiplicador | ✚ | Sí (unit) · GameTests escritos |
| Ceguera por lluvia en Death Train | 1/7500 en D50-59, hueco en D60 | 1/10000 en D40, 1/5000 desde D50 | ✚ | Sí (unit) |
| Comidas peligrosas, tarta de calabaza, Héroe de la Aldea, fatiga de minería ×2 | Sí (el límite del héroe fallaba) | Sí | ✚ | No |
| Mensaje de bienvenida | Sí | Sí | ✔ | No |

## Mundo, mobs y fases

| Funcionalidad | Fabric | NeoForge | Estado | Probado |
|---|---|---|---|---|
| Sueño D0-9 (1 jugador) / D10-19 (4 jugadores) / camas que explotan D20+ | Sí | Sí (`TIME_SINCE_REST`) | ✚ | No |
| Límite de monstruos ×2 | Desde D11 | Desde D10 | ✚ | Sí (unit) · GameTests escritos |
| Conversión a hostil de todos los mobs (D20+) | Sí | Sí | ✔ | No |
| Efectos de arañas, silverfish, endermites, cerdos con nombre, phantoms y vindicators | Se acumulaban | Una vez por mob, tabla del plugin | ✚ | No |
| Clases de esqueleto D20/D30/D40/D50/D60 | Sí | Sí (mismos números) | ✔ | No |
| Phantoms gigantes | Se duplicaba la vida en cada recarga | Una vez | ✚ | No |
| GIGA Slime/MagmaCube, ghasts demoníacos y sus drops (D25) | Sí | Sí | ✔ | No |
| Sustituciones (calamar, murciélago, zombi, lobo, salmón, aldeano, guardián) | Sí | Sí | ✔ | No |
| Creepers cargados, Ender/Quantum Creepers, esquivas por teletransporte | Sí | Sí | ✔ | No |
| Ender Ghast / Ghast Definitivo | Perdía la IA al reiniciar | IA restaurada al cargar | ✚ | No |
| Pillagers ametralladora y brujas imposibles | Perdían la IA al reiniciar | Restaurada | ✚ | No |
| Pigmen con montura (31 %) y salto del ghast | El salto nunca ocurría | Corregido | ✚ | No |
| Stack Ultra Ravager (Jess y Carlos) y drops | Sí | Sí | ✔ | No |
| Araña Abismal (módulo de muerte < D75) | Sí | Sí | ✔ | No |
| Zombie Gigante y Wither Emperador (D50) | Se re-tiraba en cada carga | Una tirada por mob | ✚ | No |
| Gatos supernova (D40) / galácticos (D50) / osos polares explosivos | Temporizadores rotos | Corregidos | ✚ | No |
| Lluvia de mobs en el Nether, minería sin netherita, alma de arena, levitación aleatoria | Sí | Sí | ✔ | No |
| Golems de nieve salvajes y Shulker Críptico (D60) | Sí | Sí | ✔ | No |
| Flechas TNT del esqueleto demoníaco (D60) | Recorría todas las entidades en cada tick | `ProjectileImpactEvent` | ✔ | No |
| Bacalao de la muerte | 59 de daño fijos | Sharpness 50 / Knockback 100 reales | ✚ | No |
| Límite de magma cubes en los deltas de basalto, Mikecrack | Sí | `SpawnPlacementCheck` | ✔ | No |
| Sin aldeas, templos, monumentos ni naufragios desde el D40 | Mixin | Mixin | ✔ | No |
| Herramientas de netherita irrompibles y doradas | Mixin | Componentes por defecto | ≈ | No |

## End, The Beginning y datos

| Funcionalidad | Fabric | NeoForge | Estado | Probado |
|---|---|---|---|---|
| End cerrado antes del D30 | Mixin | `EntityTravelToDimensionEvent` | ✔ | Sí (unit, umbral) · GameTest escrito |
| Pilares de bedrock | Barrido síncrono | Por pilar | ✚ | No |
| Arena: ladrillos, altares y pociones | Altares en memoria | Persistentes, incremental | ✚ | No |
| PERMADEATH DEMON (vida, furia, giro, ataques) | Sí | Sí | ≈ | No |
| Bolas de fuego del dragón | Sí | Sí | ≈ | No |
| Regeneración de cristales | 40-100 min; se congelaba | 2-5 min; tick de nivel | ✚ | No |
| Portal D40 en el Overworld | Solo al empezar la fase D40 | Hito D40 idempotente | ✚ | No |
| The Beginning (dimensión, islas, corales, estructuras) | Sí | Mismos IDs y algoritmo | ✔ | No |
| Gateways (vacío D40-49, ida y vuelta D50+, tormenta) | Mixin | Mixin | ✔ | No |
| Bendición, expulsión por tormenta y maldición | Sí | Sí | ✔ | No |
| Mobs de The Beginning, explosiones y Wither sin destruir bloques | Sí | Sí | ✔ | No |
| Recetas por día (`min_day`) | `fabric:load_conditions` | `neoforge:conditions` + recarga en los umbrales | ✔ | No |
| Antorchas sin receta (D40) y lingotes de horno → pepitas (D50) | Mixin | `RecipeFilter` | ✔ | No (GameTest escrito) |
| Receta de pepitas de pizarra profunda con hierro | Copia errónea | Corregida | ✚ | No |
| Coste extra de las recetas especiales | Podía duplicar objetos | Corregido | ✚ | No |
| Cofres vacíos en el D60 | `LootTableEvents` + mixin | Global Loot Modifier | ✔ | No (GameTest escrito) |
| Contenido D61-69 / D70 / logro de 70 días | Sí | Fuera de alcance (D60 final); datos conservados | ✖ | — |
