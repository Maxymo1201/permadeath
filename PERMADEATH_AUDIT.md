# Auditoría Permadeath D0–D60

Fuentes comparadas:

* **Fabric**: `permadeath-1.21.1.jar`, decompilado y remapeado a Yarn. Las constantes de registro
  (`field_XXXX`) se resolvieron por orden de declaración contra las listas de registros 1.21.1 de
  `minecraft-data`: sonidos 1611/1611, ítems 1333/1333, bloques 1060/1060, entidades 130/130, y además todos
  los efectos, partículas y enums usados.
* **PermaDeathCore**: el plugin histórico. Tiene prioridad cuando el jar es ambiguo o contradictorio.
* **Permadeath 1.3** (SebazCRC, `Permadeath-1.3.jar` y su código fuente `Permadeath-master`): la edición más
  reciente del mismo plugin. Se comparó subsistema por subsistema con el port; los cambios que salieron de esa
  comparación están en §5. Solo se aplicaron diferencias que **las dos ediciones del plugin** confirman (o que
  corrigen un fallo del port), respetando las correcciones obligatorias de §1.
* **Port**: código de este repositorio.

Leyenda: ✔ igual que Fabric · ✚ corrección · ≈ diferencia menor documentada · ✖ no portado (justificado).

## 1. Correcciones obligatorias

| Requisito | Fabric | Port | Dónde | Test |
|---|---|---|---|---|
| Tótems D0-29: 0 %, 1 tótem | Fallaba un 1 % desde el D0 y recalculaba el daño letal en `ALLOW_DAMAGE` | Vanilla puro antes del D30 | `TotemRules`, `TotemSystem` | `RulesTest` (D0, D29), GameTest `oneTotemSavesBeforeD30` |
| D30-39: 1 %, 1 tótem · D40-49: 3 %, 2 · D50-59: 5 %, 2 · D60: 7 %, 3 | Tabla distinta según la fase | Tabla exacta; con tótems insuficientes se consumen todos y se muere; la medalla de superviviente evita la tirada | `TotemRules`, `TotemSystem` | `RulesTest` (límites y tirada), GameTest `oneTotemIsNotEnoughOnD40` |
| Doble de mobs desde el D10 | `day > 10` (empezaba en el D11) | Límite de MONSTER 70 → 140 desde el D10 | `DayRules.monsterCap`, `MobCapController` | `RulesTest` (D9/D10/D11/D60), GameTest `hostileMobCapDoubledFromD10` |
| Ahogamiento ×5 (D50-59) y ×10 (D60) | Restaba 5 o 19 de aire fijos por tick, incluso con Respiración | `consumo = deltaVanilla × multiplicador`: si vanilla no consume (Respiración, Respiración acuática, Conducto), no se consume. Además, cada golpe de ahogamiento quita 5 (D50-59) o 10 (D60), como en el plugin (§5) | `DayRules.scaledAirConsumption`, `DayRules.drowningDamage`, `WorldRules` | `RulesTest` (×1/×5/×10, golpe 5/10), GameTests `drowningTenTimesFasterOnD60`, `drowningVanillaBeforeD50`, `drowningHitDealsFiveOnD50` y `drowningHitDealsTenOnD60` |
| Ceguera por lluvia 1/5000 desde el D50, sin hueco en el D60 | D40-49 1/10000, D50-59 1/7500, **D60 nada**, >60 1/5000 | D40-49 1/10000, **D50+ 1/5000** (Death Train, Overworld, lloviendo y a cielo abierto, Ceguera 60 s) | `DayRules.rainBlindnessOneIn`, `WorldRules` | `RulesTest` (D39…D60) |
| D60 es el final | Existían D60-69 y D70 | El calendario se detiene en el D60 y no hay contenido de D61+ | `PermadeathCalendar.FINAL_DAY` | Tests de calendario (D60 a las 720 h y a las 1000 h) y GameTest `calendarNeverGoesBeyondD60` |
| Bacalao de la muerte: Sharpness 50 / Knockback 100 | 59 de daño fijos + velocidad bruta 50, ignorando la resistencia al empuje | Golpe calculado con un arma virtual Sharpness L / Knockback C a través de `EnchantmentHelper` (respeta armadura y resistencia al empuje) | `HostileMobConverter` (D50+) | — |
| Death Train en horas reales con marcas absolutas | Ticks restantes (dependía de los TPS, se paraba con el servidor apagado y se descontaba una vez por dimensión y tick) | `fin = max(ahora, fin) + duración`, en epoch ms dentro de `SavedData`. Duraciones de PermaDeathCore | `DeathTrain`, `DayRules.deathTrainDurationMillis` | `RulesTest` (duraciones D0…D60) |
| Wither del D60 cada 60 min reales, persistente | 72000 ticks por jugador (dependía de los TPS) | ms reales por jugador presente en el Overworld; máximo 5 s por tick; guardado | `WitherSpawner` | — |
| Life Orb | Plazo `now + 8 h` al detectar el D60 | Igual en GAME60; en REAL30 el plazo es el instante exacto del D60 + 8 h (cuenta aunque el servidor estuviera apagado). La penalización solo baja 16 de vida **máxima** y se guarda con el jugador (§5) | `LifeOrb` | GameTest `lifeOrbPenaltyLowersOnlyMaxHealthAndIsSaved` |

## 2. Auditoría por fase

### D0-9 (Fase 1)
* ✔ Basta con que duerma un jugador para saltar la noche ("sueño instantáneo"). Durante el día con tormenta
  no se puede dormir.
* ✔ Muerte: espectador; títulos "¡Permadeath!" / "<jugador> ha muerto"; sonidos (muerte de blaze con
  pitch 0.3 y, 5 s después, muerte de caballo esqueleto); monumento (bedrock + valla de roble + cabeza
  orientada); mensajes (sufrimiento eterno, mensaje personal, coordenadas y dimensión); reaparición
  automática; baneo a los 4 s; Death Train.
  * ≈ Al dueño de un servidor integrado (un jugador o LAN) no se le banea, porque cerraría su propia partida.
* ✔ End cerrado hasta el D30.

### D10-19 (Fase 2)
* ✚ Doble de mobs desde el D10 (ver §1).
* ✔ Para saltar la noche hacen falta 4 jugadores durmiendo, con avisos.
* ✚ Arañas con 1-3 efectos aleatorios **una sola vez por araña**. En Fabric se volvían a aplicar en cada tick
  mientras les faltara alguno y los efectos se acumulaban. Se usa la tabla del plugin (Velocidad III,
  Regeneración IV, Fuerza IV, Invisibilidad, Salto V, Caída lenta, Resistencia III y Brillo antes del D50).

### D20-24 (Fase 3)
* ✔ Todo mob pasivo o neutral se vuelve hostil (mismos goals y daños que el `HostileMobConverter` de Fabric).
* ✚ Las camas no dejan dormir: partícula y sonido de explosión y reinicio del contador de phantoms, con
  mensaje (§5). Fabric reiniciaba un temporizador interno que no era el de los phantoms; ahora se reinicia la
  estadística `TIME_SINCE_REST`.
* ✚ Phantoms de tamaño 9 (18 desde el D50, §5) con el doble de vida, una sola vez. En Fabric la vida se
  duplicaba en cada recarga.
* ✔ Arañas con efectos y jinete esqueleto. ✚ Los esqueletos naturales también reciben clase (§5).
* ✔ Muchos mobs dejan de soltar loot. ✚ Ahora solo se elimina el loot propio del mob. Fabric borraba todos
  los ítems en 2 bloques a la redonda, incluidas las piezas de netherita y los objetos que un jugador
  tuviera en el suelo. ✚ Los esqueletos vuelven a soltar loot (§5).
* ✚ Los gólems de hierro de las aldeas atacan a los jugadores (§5).
* ✔ Las abejas conservan el aguijón.

### D25-29
* ✔ GIGA Slime, GIGA MagmaCube, Ghast Demoníaco y Demonio Flotante.
* ✚ Armadura de netherita: 10 % por pieza solo del D25 al D29 y solo si mata un jugador (§5).
* ✔ Límite de 20 magma cubes en 128 bloques en los deltas de basalto.
* ✚ Death Train: los mobs reciben Fuerza, Resistencia y Velocidad I para siempre (§5). Ravagers con
  Velocidad II y Fuerza I.
* ✚ Tótem del ravager: 20 % desde el D25. Fabric usaba `day + 1 >= 25` y `<= 20` sobre 0-99, es decir un
  21 % desde el D24.
* ✚ Bolas de fuego de los ghasts especiales: el "Demonio Flotante" tenía potencia 1 por la errata
  "Demonio Flotate". Ahora tiene potencia 0 + levitación (5 s) + wither, como pretendía el código (§5).

### D30-39 (Fase 4)
* ✔ El End se abre; las columnas de obsidiana pasan a bedrock. ✚ Se convierte por pilar (`SpikeFeature`),
  uno por tick, en lugar de recorrer 401×401×256 bloques de forma síncrona.
* ✔ Creepers cargados (invisibles en el End). Los creepers del End esquivan proyectiles teletransportándose.
* ✔ Calamares → guardianes y murciélagos → blazes (máximo 5 cerca). Clases de esqueleto D30. Pillagers
  invisibles con ametralladora. Gólems, endermen y piglins reforzados. ✚ Endermen nuevos del End → Ender
  Creeper (1/20) o Ender Ghast (1/170, solo con el dragón muerto), como en el plugin (§5).
  * ✚ La ametralladora de los pillagers y el teletransporte de los Ender Ghast se restauran al cargar el
    mundo. En Fabric se perdían al reiniciar.
* ✔ Fallo de tótems 1 %. Al acabarse la visión nocturna en el End con el dragón vivo, nube de corazones
  oscuros (✚ también del D40 al D60, §5).
* ✚ Dragón "PERMADEATH DEMON": 2000 de vida máxima y llena, se enfurece a 600 de vida (§5), gira tras 200
  ticks posado y lanza ataques en vuelo cada 1200/800 ticks (visión nocturna, círculo de TNT, tormenta
  eléctrica, esqueleto wither, endermite). Tabla de bolas de fuego normal y enfurecida. En el End: un rayo
  por segundo junto al portal, ráfaga de TNT cada 30-90 s y curación de cristales a la mitad (§5).
  * ≈ El dragón deja de atacar mientras se muere. Fabric podía soltar un círculo de TNT durante la animación
    de muerte.
  * ≈ Las bolas de fuego extra al estar posado dependen de `Mob#getTarget()`, que vanilla nunca asigna al
    dragón. Siguen inactivas, igual que en Fabric.
* ✔ Arena del End: 35 % de ladrillos de piedra del End en un radio de 120, cuatro altares rojos con farol
  marino y botellas en marcos, y una poción roja cada 3 s. ✚ Los altares se guardan en el mundo (en Fabric
  se perdían al reiniciar) y la conversión se reparte en 256 ticks.
* ✔ Regeneración de cristales: Ender Ghast y sonido de wither al destruir uno; el cristal vuelve a salir.
  * ✚ El retardo real es el del plugin: 30, 60, 90, 120, 150 o 240 s. Fabric descontaba el contador una vez
    cada 20 ticks, lo que daba 40-100 min. El Ender Ghast aparece 10 bloques por encima (§5).
  * ✚ Destruir el último cristal congelaba en Fabric todas las regeneraciones pendientes; ahora se procesan
    desde el tick del nivel.
  * ≈ Solo cuentan los cristales de pilar (con base visible). Los cuatro que pone un jugador para revivir al
    dragón ya no generan ghasts ni reaparecen sobre el portal de salida.

### D40-49 (Fase 5)
* ✔ Hito D40 (una vez por mundo): portal a The Beginning a ±2500 bloques, 40 por encima de la superficie.
  Mismo `permadeath_portal_state_day40` que Fabric. ✚ Se ejecuta también si el mundo salta del D3x al D5x.
* ✔ −8 de vida máxima; 5 huecos de inventario bloqueados (columna central + mano secundaria) salvo con la
  Reliquia del Fin. Los objetos desplazados se sueltan, nunca se borran.
* ✔ PvP activado desde el D40 (antes del D40 está desactivado, como en Fabric).
* ✔ Recetas desde el D40: antorchas eliminadas, aliento de dragón, Reliquia del Fin, des-crafteo de shulker
  box y Hyper Golden Apple+.
* ✔ Zombis → vindicators; lobos → gatos; arañas → arañas de cueva (11 % Módulo de Muerte: Araña Abismal +
  Shulker Explosivo + vagoneta de pociones); animales de granja → ravagers; brujas imposibles; aparición de
  mobs en champiñonales; los endermen del Nether pasan a ser Ender Creepers (✚ su explosión deja magma, §5).
* ✚ Clases de pigman con la probabilidad del plugin (5/99 en el D40-49, 20/99 en el D50-59) y el stack Ultra
  Ravager como una de las clases, en vez de 31 % de pigmen y 21 % de piglins (§5).
* ✔ Láser del guardián al doble de velocidad. Élitros del End casi rotos. No se generan aldeas, templos,
  monumentos ni naufragios.
* ✚ El gato supernova explota a los 30 s con potencia 200 (plugin; en Fabric a los 2 s por un decremento de
  10 por tick), se llama "Gato Supernova", incluye ocelotes y como mucho hay 2 pendientes (§5).
* ✚ La montura "ghast pig" del pigman salta al recibir daño (20 %). En Fabric nunca se etiquetaba y
  comparaba con el nombre equivocado.
* ✔ Gateway del Overworld en el D40-49: lleva a y = −100 (el vacío), ✚ con el aviso del plugin. The
  Beginning abre en el D50.

### D50-59 (Fase 6)
* ✔ The Beginning: gateway del Overworld ↔ plataforma (1.5, 238, 1.5); bendición del primero en entrar;
  expulsión durante la tormenta (✚ comprobada cada segundo y anunciada, §5); Wither Skeleton Rosáceo, Ghast
  Definitivo y Vex Definitivo; las explosiones y el Wither no rompen bloques. ✚ No se pueden vaciar cubos;
  tabla de aparición y de cofres del plugin (§5).
* ✔ Quantum Creeper (radio 20) / Ender Creeper (20 %). ✚ Zombie Gigante en llanuras (1/500, ataque 2000) y
  Wither Skeleton Emperador en el Nether (1/50), como en el plugin (§5). ✚ Ambas tiradas se hacen una vez por
  mob. En Fabric se repetían en cada carga y el emperador se curaba hasta 80.
* ✔ Gatos galácticos (tabla de 42 casos, ✚ con aviso y cuenta atrás de 5 s, §5). ✚ Osos polares: explotan
  al golpear a un jugador (§5). ✚ El gato supernova galáctico explota (en Fabric nunca). ✚ Los casos 27 y 28
  del módulo de muerte aparecían en (0, 0, 0); ahora aparecen donde muere el gato.
* ✔ Lluvia de mobs en el Nether por encima de y = 117; minar sin herramienta de netherita quita 1 de vida;
  alma de arena ralentiza; la invisibilidad no funciona en The Beginning; ✚ levitación aleatoria solo bajo la
  lluvia a cielo abierto (§5); el cubo vacío no recoge líquidos.
* ✚ Comida peligrosa con la tabla del plugin; la leche cura venenos y hambre pero no la fatiga de minería
  (§5). ✚ "Héroe de la Aldea" limitado a 5 min. En Fabric el `equals` fallaba y el límite nunca se aplicaba.
  Fatiga de minería ×2.
* ✔ Mordisco de araña de cueva: veneno III y náusea. Escupitajo de llama: veneno III, náusea y empuje.
* ✔ Ahogamiento ×5 (✚ golpes de 5); ceguera por lluvia 1/5000; Death Train desactiva la regeneración natural
  ("modo UHC", ✚ anunciado) y da Resistencia al fuego a los mobs (§5).
* ✔ Recetas desde el D50: los minerales se funden en pepitas. ✚ `iron_ingot_from_blasting_deepslate_iron_ore`
  era una copia de la receta de oro crudo en horno; ahora es pizarra profunda con hierro → pepita de hierro
  en alto horno.
* ✔ Maldición de The Beginning (`/permadeath maldicion`, 12 h, la leche mata). Medalla de superviviente
  desde el D55.
* ✚ Blazes con 200 de vida máxima desde el D50, como en el plugin. Fabric ponía `setHealth(200)` sobre 20 de
  máximo (se quedaba en 20) y solo en los que venían de murciélagos.

### D60 (Fase 7, final)
* ✔ Otros −8 de vida máxima (−16 en total). Sin reliquias se bloquean 30 huecos; con la Reliquia del Fin, 25
  más la mano secundaria; con la del Comienzo, ninguno.
* ✔ Todos los creepers son Ender Quantum Creepers (mecha de 15 ticks); ✚ sin pigmen en ninguna dimensión;
  ✚ aldeanos → vindicators o vexes, y la mitad de los vindicators desaparece (a veces en su lugar sale un
  evoker reforzado) (§5); guardianes → guardianes ancianos; armas de los monstruos con Aspecto Ígneo II o
  Fuego; ✚ 26/101 phantoms traen 4 Ender Ghasts; flechas TNT del esqueleto demoníaco; golems de nieve salvajes;
  Shulker Críptico en el Nether.
* ✚ Cofres vacíos desde el D60 mediante un loot modifier global: tablas `chests/*` integradas y cualquier
  contenedor de The Beginning.
* ✔ Life Orb (8 h, después −16 de vida máxima para quien no lo tenga), Wither cada 60 min reales,
  ahogamiento ×10 (✚ golpes de 10), 7 % de fallo de tótems con 3 tótems, Wither que ignora escudos, minar sin
  netherita quita 16. ✚ Perlas: 6 s de espera al caer. ✚ Cambio de Mikecrack activo por defecto (§5).
* ✖ Contenido D61-69 y D70 (bolas de nieve de 50 de daño, módulo de muerte D75, logro de 70 días): fuera de
  alcance porque el D60 es el último día. Los datos del logro se conservan.

## 3. Otros fallos de Fabric corregidos

| Fallo | Corrección |
|---|---|
| Cualquier jugador podía usar `changeday`, `reset`, `resetstorm`, `mikecrack`… salvo con el modo restringido | Todos los subcomandos administrativos exigen OP 2 |
| `permadeath:max_day` estaba invertida (`day >= max_day`) | Semántica inclusiva correcta (ningún fichero la usaba) |
| Recetas especiales: duplicaban objetos cuando la cuadrícula dejaba de cumplir la receta tras el coste extra | El sobrante pasa al inventario |
| `data/minecraft/recipe/dragon_breath.json` no se podía leer en 1.21.1 | Se elimina (`permadeath:dragon_breath` cubre la receta) |
| La arena del End convertía 120² columnas de forma síncrona al visitarla | Trabajo incremental de un chunk por tick |
| Modo de días `speedrun` (cambiaba el calendario en caliente) | No se porta: la identidad GAME60/REAL30 la fija la build |
| `MixinCaveSpider#getVehicleAttachmentPos` | Código muerto (la araña nunca va montada sobre un shulker); no se porta |
| Goal `applyEndermanBehavior` del Ender Creeper | Código muerto (capturaba un objetivo nulo); no se porta. Se mantiene el esquive por teletransporte |

## 4. Diferencias dudosas (documentadas, no inventadas)

* **Tabla de efectos D20+**: Fabric usaba Fuerza I, Velocidad IV y Brillo III, lo que contradice su propia
  tabla del D10 y la del plugin. Se usa la tabla histórica en todos los días.
* **Tótem insuficiente**: si quedan menos tótems de los necesarios se consumen todos, como en PermaDeathCore
  (`TotemFail.*`).
* **Bolas de fuego del dragón**: cuando sale un resultado personalizado, el impacto se cancela y no se llama
  a `Projectile#onHitBlock` (reacciones de bloques como la diana o la campana). El 13 % de bolas vanilla no
  cambia.
* **Registro de cristales del End**: en memoria, como en Fabric. Un reinicio en mitad del combate olvida las
  regeneraciones pendientes.
* **Herramientas de netherita con nombre personalizado**: el nombre dorado va en `ITEM_NAME`, así que un
  objeto renombrado muestra su nombre propio. En Fabric también se teñía de dorado.

## 5. Cambios tras comparar con el plugin Permadeath 1.3

La comparación se hizo leyendo el código del plugin (`tech.sebazcrc.permadeath`, sus entidades NMS y su
`config.yml`) y el de PermaDeathCore. Cada fila cita la regla del plugin y lo que hacían Fabric y la versión
anterior del port. Las correcciones obligatorias de §1 no se tocan: la ceguera sigue siendo 1/5000 por tick y
el doble de mobs empieza en el D10, aunque el plugin use otros valores.

### Jugador

| Regla | Plugin (ambas ediciones) | Antes (Fabric / port) | Ahora | Test |
|---|---|---|---|---|
| Golpe de ahogamiento | 5 (D50-59), 10 (D60) | 2 (vanilla) | `LivingDrownEvent` con 5/10 | `RulesTest`, GameTests `drowningHitDealsFiveOnD50` / `TenOnD60` |
| Levitación aleatoria D50+ | 1/10000 cada segundo, solo bajo la lluvia a cielo abierto, 3-19 s | 1/10000 por tick también de noche, bajo techo y bajo tierra | Regla del plugin | `RulesTest` |
| Super/Hyper Golden Apple+ | Son manzanas de oro (Regeneración II, Absorción) | Comida sin efectos | `Foods.GOLDEN_APPLE` | GameTest `superGoldenApplePlusIsAGoldenApple` |
| Comida dañina D50+ | Ojo de araña Veneno I, carne podrida Hambre II, patata Veneno I, pez globo Veneno IV + Hambre III + Náusea II; tarta Saturación (y Daño IV en el D60) | Otra tabla; leche y tótem no curaban veneno ni hambre (veneno infinito incurable) | Tabla del plugin; la leche solo respeta la fatiga de minería | GameTests `pufferfishUsesPluginEffectsOnD50`, `milkKeepsOnlyMiningFatigueOnD50` |
| Perlas D60 | 6 s de espera al caer la perla | 2 s al lanzarla | `EntityTeleportEvent.EnderPearl` | GameTest `pearlCooldownSixSecondsAfterLandingOnD60` |
| Arena de almas D60 | Lentitud III 30 s | 3 s | 30 s (alma de arena) | `RulesTest` |
| Death Train | Fuerza/Resistencia/Velocidad infinitas para los mobs vivos en cada muerte y los que aparecen; Resistencia al fuego en D50-59 | Efectos de 3 s renovados mientras duraba la tormenta | Efectos infinitos | GameTest `deathTrainBuffsArePermanentOnD50` |
| Huecos bloqueados | Los bloqueadores no salen del inventario | F (cambio de mano) tiraba el objeto al suelo; se podían guardar en cofres | Cambio de mano cancelado, el cursor se deshace, se borran de los contenedores al cerrarlos | — |
| Mensajes | "¡Ha comenzado el modo UHC!", "entró a TheBeginning antes de tiempo", "¡Has obtenido contenedores de vida extra!" | Sin mensajes o con erratas | Textos del plugin | — |
| Camas D20+ | Reinicio con mensaje; 10 % desde el D50 | Silencioso en D20-29; 11 % | 10 % y mensaje | `RulesTest` |
| Sonido de muerte | En cada muerte | Solo desde el D30 y dos veces | En cada muerte, una vez | — |
| Cabezas del monumento | "HA SIDO PERMABANEADO", fecha, hora y causa al recogerlas | Sin datos | `DeathRecordsData` (`permadeath_death_records`) | — |
| Life Orb | Solo baja la vida máxima | Quitaba además 16 de vida actual y volvía a aplicarse en cada reconexión (normalmente mortal) | Modificador permanente guardado con el jugador | GameTest `lifeOrbPenaltyLowersOnlyMaxHealthAndIsSaved` |

### Mobs

| Regla | Plugin | Antes | Ahora | Test |
|---|---|---|---|---|
| Gólems de aldea D20+ | Atacan a los jugadores (los construidos por jugadores no) | Pacíficos | Objetivo jugador | GameTests `villageIronGolemHuntsPlayersFromD20`, `playerBuiltIronGolemStaysFriendly` |
| Esqueletos D20-29 | Todos con clase (nextInt(6)) | Solo los jinetes de araña | Clase D20 también para los naturales | GameTest `naturalSkeletonGetsAClassOnD20` |
| Loot de esqueletos | Huesos y flechas; armadura de clase 80 %; armas nunca | Nada desde el D20 | Regla del plugin | — |
| Probabilidad de clases | Guerrero 2/6 (2/8 en el D60), Definitivo 1/101 | Equiprobable, 1/99 | Regla del plugin | — |
| Clases D60 | Científico 3 min, Pesadilla 60 de vida, Definitivo con Velocidad II y persistente | 18 s, 40, sin velocidad | Regla del plugin | — |
| Mobs pasivos hostiles | 8 de daño; los que ya atacan conservan el suyo | 3 para todos (hoglins y pandas incluidos) | 8 / vanilla (el bacalao conserva su golpe auditado) | `RulesTest` |
| Osos polares D50+ | Explotan 0,5 s después de golpear a un jugador (1,5, fuego, sin romper bloques) | Cualquier oso a 20 bloques explotaba con potencia 10 rompiendo bloques | Regla del plugin | — |
| Stack Ultra Ravager | Ravager 240, Carlos 150, Jess 500; oro y manzanas 33 % en el D60; sin tótem; rompe netherrack | Ravager 500 / Jess 240; tótem garantizado | Regla del plugin | — |
| Ultra Ravager dorado D50+ | Ravagers del Overworld; tótem solo si muere en el Nether | Solo animales de granja; sin tótem | Regla del plugin | — |
| Clases de pigman | 5/99 (D40-49), 20/99 (D50-59); el stack es una de las clases; ningún pigman en el D60 | 31 %; stack desde el 21 % de piglins; pigmen del D60 solo fuera del Nether | Regla del plugin | `RulesTest` |
| Phantoms D50+ | Tamaño 18; con 2/101 (26/101 en el D60) traen 4 Ender Ghasts y siguen | Tamaño 9; el phantom desaparecía | Regla del plugin | `RulesTest` |
| Zombie Gigante / Emperador | 1/500 (1/125 D60), ataque 2000 / 1/50 (1/13 D60), "Wither Skeleton Emperador" | 5 % / 20 %, ataque 30 | Regla del plugin | `RulesTest` |
| Ender Creepers | Esquivan todo salvo cuerpo a cuerpo y el vacío | Solo proyectiles/explosiones (y fuego desde el D50) | Regla del plugin | GameTest `enderCreeperDodgesEverythingButMelee` |
| Ender Creeper del Nether | Su cráter se convierte en magma | Explosión normal | Regla del plugin | — |
| Ghasts | Demoníaco potencia 6 desde el D50; Flotante 40-60 de vida, levitación 5 s a cualquier mob | 3-5; 60 fijo; 20 s solo a jugadores | Regla del plugin | — |
| Gatos | Supernova 30 s, potencia 200, nombre, ocelotes, máx. 2; galáctico con aviso y cuenta atrás | 20 s, 250; galáctico inmediato y silencioso | Regla del plugin (la tabla de invocaciones de Fabric se conserva) | `RulesTest` |
| Illagers D50+/D60 | 1 % de pillagers → evoker; aldeanos → vindicator o vex; 50 % de vindicators → evoker ×2 vida, Resistencia III; vex ataque 7 | Evokers desde zombis; vex 14 | Regla del plugin | — |
| Netherita | 10 % por pieza, solo D25-29, solo con jugador | 10/20 %, D25-60, granjas | Regla del plugin | `RulesTest` |
| Mikecrack D60 | Activo por defecto: Ender Quantum Creepers cerca del jugador (1/30 por segundo, máx. 10) | Desactivado; 5 creepers cada 5 s al activarlo | Activo por defecto; `/permadeath mikecrack disable` lo apaga | — |
| Endermen D40+ | 1 % hostiles fuera del End | 20 % en todas partes | Regla del plugin | — |
| Nombres | Bruja Imposible, Bacalao de la Muerte, Pufferfish invulnerable, Arco de Gigante (aqua); mensajes del tótem del ravager | Sin nombre | Regla del plugin | — |

### End y The Beginning

| Regla | Plugin | Antes | Ahora | Test |
|---|---|---|---|---|
| PERMADEATH DEMON | 2000/2000, furia a 600, ataques cada 60/40 s | 1000/1600, furia a 1/6, 60/45 s | Regla del plugin (los dragones guardados de 1600 se suben a 2000) | — |
| Combate | Un rayo por segundo junto al portal; 6 TNT de potencia 15 cada 30-90 s que no rompen la isla; curación de cristales a la mitad | No existía | Regla del plugin (la TNT no daña al dragón) | — |
| Cristales | 30-240 s; ghast 10 bloques más arriba | 2-5 min; ghast en el pilar | Regla del plugin | — |
| Endermen del End | 1/20 Ender Creeper, 1/170 Ender Ghast, solo al aparecer | 11 % / 4 %, repetido en cada carga | Regla del plugin | — |
| Shulkers del End | TNT de 1 s en la entidad, 2 s en el bloque (≥ 4 bloques); sin TNT junto a otra | 4 s siempre | Regla del plugin (fuera del End siguen los 4 s de Fabric) | — |
| Nube de corazones oscuros | Todos los días del combate | Solo D30-39 | D30-60 | — |
| The Beginning | Sin cubos; creepers de radio 7 y 100 de vida; Ghast Definitivo 150; aparición 61/15/4/21 (Rosáceo/Vex/Ghast/Creeper); cofres con la tabla del plugin; cerrado y anunciado en cada Death Train | Cubos permitidos; radio 20; 240; 50/40/20 sin vex; tabla inventada; expulsión solo al empezar la tormenta | Regla del plugin | — |

### Comandos nuevos del plugin

`/permadeath awake` (tiempo despierto), y para OP: `storm addHours|removeHours <horas>`, `give <objeto>`,
`bendicion <jugador>`, `event shulkershell` (X2 Shulker Shells durante 4 h reales, con barra de jefe) y
`event lifeorb` (reinicia la cuenta atrás del Life Orb). `storm` y `event shulkershell` se prueban en el
servidor dedicado (`tools/server-smoke-test.sh`), incluida la persistencia del Death Train tras el reinicio.

### Diferencias del plugin que no se aplican

* **Ceguera y doble de mobs**: el plugin usa 300/10000 cada 1,5 s y un límite configurable. Son correcciones
  obligatorias (§1) y se mantienen.
* **Buffs del Death Train sin tormenta**: el plugin llama a `deathTrainEffects` en cada aparición aunque no
  haya tormenta. Parece una comprobación olvidada en un método llamado "death train"; solo se aplican durante
  la tormenta.
* **Wither del D60**: el plugin cuenta iteraciones de su bucle (90 min reales en la 1.3, 60 en PermaDeathCore).
  Se mantienen los 60 min reales obligatorios.
* **GIGA Slime/MagmaCube y lluvia de mobs del Nether**: los números del plugin dependen de la vida del mob
  antes de cambiar su tamaño y de su bucle de 1,5 s; se conservan los de Fabric.

