# Auditoría Permadeath D0–D60

Fuentes comparadas:

* **Fabric**: `permadeath-1.21.1.jar`, decompilado y remapeado a Yarn. Las constantes de registro
  (`field_XXXX`) se resolvieron por orden de declaración contra las listas de registros 1.21.1 de
  `minecraft-data`: sonidos 1611/1611, ítems 1333/1333, bloques 1060/1060, entidades 130/130, y además todos
  los efectos, partículas y enums usados.
* **PermaDeathCore**: el plugin histórico. Tiene prioridad cuando el jar es ambiguo o contradictorio.
* **Port**: código de este repositorio.

Leyenda: ✔ igual que Fabric · ✚ corrección · ≈ diferencia menor documentada · ✖ no portado (justificado).

## 1. Correcciones obligatorias

| Requisito | Fabric | Port | Dónde | Test |
|---|---|---|---|---|
| Tótems D0-29: 0 %, 1 tótem | Fallaba un 1 % desde el D0 y recalculaba el daño letal en `ALLOW_DAMAGE` | Vanilla puro antes del D30 | `TotemRules`, `TotemSystem` | `RulesTest` (D0, D29), GameTest `oneTotemSavesBeforeD30` |
| D30-39: 1 %, 1 tótem · D40-49: 3 %, 2 · D50-59: 5 %, 2 · D60: 7 %, 3 | Tabla distinta según la fase | Tabla exacta; con tótems insuficientes se consumen todos y se muere; la medalla de superviviente evita la tirada | `TotemRules`, `TotemSystem` | `RulesTest` (límites y tirada), GameTest `oneTotemIsNotEnoughOnD40` |
| Doble de mobs desde el D10 | `day > 10` (empezaba en el D11) | Límite de MONSTER 70 → 140 desde el D10 | `DayRules.monsterCap`, `MobCapController` | `RulesTest` (D9/D10/D11/D60), GameTest `hostileMobCapDoubledFromD10` |
| Ahogamiento ×5 (D50-59) y ×10 (D60) | Restaba 5 o 19 de aire fijos por tick, incluso con Respiración | `consumo = deltaVanilla × multiplicador`: si vanilla no consume (Respiración, Respiración acuática, Conducto), no se consume | `DayRules.scaledAirConsumption`, `WorldRules.onBreathe` | `RulesTest` (×1/×5/×10), GameTests `drowningTenTimesFasterOnD60` y `drowningVanillaBeforeD50` |
| Ceguera por lluvia 1/5000 desde el D50, sin hueco en el D60 | D40-49 1/10000, D50-59 1/7500, **D60 nada**, >60 1/5000 | D40-49 1/10000, **D50+ 1/5000** (Death Train, Overworld, lloviendo y a cielo abierto, Ceguera 60 s) | `DayRules.rainBlindnessOneIn`, `WorldRules` | `RulesTest` (D39…D60) |
| D60 es el final | Existían D60-69 y D70 | El calendario se detiene en el D60 y no hay contenido de D61+ | `PermadeathCalendar.FINAL_DAY` | Tests de calendario (D60 a las 720 h y a las 1000 h) y GameTest `calendarNeverGoesBeyondD60` |
| Bacalao de la muerte: Sharpness 50 / Knockback 100 | 59 de daño fijos + velocidad bruta 50, ignorando la resistencia al empuje | Golpe calculado con un arma virtual Sharpness L / Knockback C a través de `EnchantmentHelper` (respeta armadura y resistencia al empuje) | `HostileMobConverter` (D50+) | — |
| Death Train en horas reales con marcas absolutas | Ticks restantes (dependía de los TPS, se paraba con el servidor apagado y se descontaba una vez por dimensión y tick) | `fin = max(ahora, fin) + duración`, en epoch ms dentro de `SavedData`. Duraciones de PermaDeathCore | `DeathTrain`, `DayRules.deathTrainDurationMillis` | `RulesTest` (duraciones D0…D60) |
| Wither del D60 cada 60 min reales, persistente | 72000 ticks por jugador (dependía de los TPS) | ms reales por jugador presente en el Overworld; máximo 5 s por tick; guardado | `WitherSpawner` | — |
| Life Orb | Plazo `now + 8 h` al detectar el D60 | Igual en GAME60; en REAL30 el plazo es el instante exacto del D60 + 8 h (cuenta aunque el servidor estuviera apagado) | `LifeOrb` | — |

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
* ✚ Las camas no dejan dormir: partícula y sonido de explosión y reinicio silencioso del contador de
  phantoms. Fabric reiniciaba un temporizador interno que no era el de los phantoms; ahora se reinicia la
  estadística `TIME_SINCE_REST`.
* ✚ Phantoms de tamaño 9 con el doble de vida, una sola vez. En Fabric la vida se duplicaba en cada recarga.
* ✔ Arañas con efectos y jinete esqueleto.
* ✔ Muchos mobs dejan de soltar loot. ✚ Ahora solo se elimina el loot propio del mob. Fabric borraba todos
  los ítems en 2 bloques a la redonda, incluidas las piezas de netherita y los objetos que un jugador
  tuviera en el suelo.
* ✔ Las abejas conservan el aguijón.

### D25-29
* ✔ GIGA Slime, GIGA MagmaCube, Ghast Demoníaco y Demonio Flotante, con sus drops de armadura de netherita.
* ✔ Las arañas de cueva sueltan el casco de netherita (20 %).
* ✔ Límite de 20 magma cubes en 128 bloques en los deltas de basalto.
* ✔ Death Train: los mobs reciben Fuerza, Resistencia y Velocidad I.
* ✚ Tótem del ravager: 20 % desde el D25. Fabric usaba `day + 1 >= 25` y `<= 20` sobre 0-99, es decir un
  21 % desde el D24.
* ✚ Bolas de fuego de los ghasts especiales: el "Demonio Flotante" tenía potencia 1 por la errata
  "Demonio Flotate". Ahora tiene potencia 0 + levitación + wither, como pretendía el código.

### D30-39 (Fase 4)
* ✔ El End se abre; las columnas de obsidiana pasan a bedrock. ✚ Se convierte por pilar (`SpikeFeature`),
  uno por tick, en lugar de recorrer 401×401×256 bloques de forma síncrona.
* ✔ Creepers cargados (invisibles en el End). Los creepers del End esquivan proyectiles teletransportándose.
* ✔ Calamares → guardianes y murciélagos → blazes (máximo 5 cerca). Clases de esqueleto D30. Pillagers
  invisibles con ametralladora. Gólems, endermen y piglins reforzados. Endermen del End → Ender Creeper
  (11 %) o Ender Ghast (4 %, solo con el dragón muerto).
  * ✚ La ametralladora de los pillagers y el teletransporte de los Ender Ghast se restauran al cargar el
    mundo. En Fabric se perdían al reiniciar.
* ✔ Fallo de tótems 1 %. Al acabarse la visión nocturna en el End con el dragón vivo, nube de corazones
  oscuros.
* ✔ Dragón "PERMADEATH DEMON": 1600 de vida máxima, empieza con 1000, se enfurece por debajo de 1/6, gira
  tras 200 ticks posado y lanza ataques en vuelo cada 1200/900 ticks (visión nocturna, círculo de TNT,
  tormenta eléctrica, esqueleto wither, endermite). Tabla de bolas de fuego normal y enfurecida.
  * ≈ El dragón deja de atacar mientras se muere. Fabric podía soltar un círculo de TNT durante la animación
    de muerte.
  * ≈ Las bolas de fuego extra al estar posado dependen de `Mob#getTarget()`, que vanilla nunca asigna al
    dragón. Siguen inactivas, igual que en Fabric.
* ✔ Arena del End: 35 % de ladrillos de piedra del End en un radio de 120, cuatro altares rojos con farol
  marino y botellas en marcos, y una poción roja cada 3 s. ✚ Los altares se guardan en el mundo (en Fabric
  se perdían al reiniciar) y la conversión se reparte en 256 ticks.
* ✔ Regeneración de cristales: Ender Ghast y sonido de wither al destruir uno; el cristal vuelve a salir.
  * ✚ El retardo real es de 2-5 min (2400-6000 ticks). Fabric descontaba el contador una vez cada 20 ticks,
    lo que daba 40-100 min; el plugin usaba 30-240 s.
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
  Shulker Explosivo + vagoneta de pociones); animales de granja → ravagers; 31 % de pigmen con montura;
  piglins → stack Ultra Ravager (21 %); brujas imposibles; aparición de mobs en champiñonales; los endermen
  del Nether pasan a ser Ender Creepers.
* ✔ Láser del guardián al doble de velocidad. Élitros del End casi rotos. No se generan aldeas, templos,
  monumentos ni naufragios.
* ✚ El gato supernova explota a los 20 s (en Fabric a los 2 s por un decremento de 10 por tick).
* ✚ La montura "ghast pig" del pigman salta al recibir daño (20 %). En Fabric nunca se etiquetaba y
  comparaba con el nombre equivocado.
* ✔ Gateway del Overworld en el D40-49: lleva a y = −100 (el vacío). The Beginning abre en el D50.

### D50-59 (Fase 6)
* ✔ The Beginning: gateway del Overworld ↔ plataforma (1.5, 238, 1.5); bendición del primero en entrar;
  expulsión al empezar una tormenta; Wither Skeleton Rosáceo, Ghast Definitivo y Vex Definitivo; las
  explosiones y el Wither no rompen bloques.
* ✔ Quantum Creeper (radio 20) / Ender Creeper (20 %). Zombie Gigante en llanuras (5 %); Wither Emperador en
  el Nether (5 %). ✚ Ambas tiradas se hacen una vez por mob. En Fabric se repetían en cada carga y el
  emperador se curaba hasta 80.
* ✔ Gatos galácticos (tabla de 42 casos) y osos polares explosivos. ✚ El gato supernova galáctico explota
  (en Fabric nunca). ✚ Los casos 27 y 28 del módulo de muerte aparecían en (0, 0, 0); ahora aparecen donde
  muere el gato.
* ✔ Lluvia de mobs en el Nether por encima de y = 117; minar sin herramienta de netherita quita 1 de vida;
  alma de arena ralentiza; la invisibilidad no funciona en The Beginning; levitación aleatoria; el cubo vacío
  no recoge líquidos.
* ✔ Comida peligrosa: ojo de araña, carne podrida, patata venenosa, pez globo, tarta de calabaza.
  ✚ "Héroe de la Aldea" limitado a 5 min. En Fabric el `equals` fallaba y el límite nunca se aplicaba.
  Fatiga de minería ×2.
* ✔ Mordisco de araña de cueva: veneno III y náusea. Escupitajo de llama: veneno III, náusea y empuje.
* ✔ Ahogamiento ×5; ceguera por lluvia 1/5000; Death Train desactiva la regeneración natural ("modo UHC").
* ✔ Recetas desde el D50: los minerales se funden en pepitas. ✚ `iron_ingot_from_blasting_deepslate_iron_ore`
  era una copia de la receta de oro crudo en horno; ahora es pizarra profunda con hierro → pepita de hierro
  en alto horno.
* ✔ Maldición de The Beginning (`/permadeath maldicion`, 12 h, la leche mata). Medalla de superviviente
  desde el D55.
* ✔ Murciélago → blaze con 200 de vida máxima. Fabric ponía `setHealth(200)` sobre 20 de máximo (se quedaba
  en 20). El plugin daba 200 a todos los blazes del D50; aquí solo a los que vienen de murciélagos.

### D60 (Fase 7, final)
* ✔ Otros −8 de vida máxima (−16 en total). Sin reliquias se bloquean 30 huecos; con la Reliquia del Fin, 25
  más la mano secundaria; con la del Comienzo, ninguno.
* ✔ Todos los creepers son Ender Quantum Creepers (mecha de 15 ticks); sin pigmen en el Nether; aldeanos →
  vindicators; guardianes → guardianes ancianos; armas de los monstruos con Aspecto Ígneo II o Fuego; 26 % de
  phantoms → 4 Ender Ghasts; flechas TNT del esqueleto demoníaco; golems de nieve salvajes; Shulker Críptico
  en el Nether.
* ✚ Cofres vacíos desde el D60 mediante un loot modifier global: tablas `chests/*` integradas y cualquier
  contenedor de The Beginning.
* ✔ Life Orb (8 h, después −16 para quien no lo tenga), Wither cada 60 min reales, ahogamiento ×10, 7 % de
  fallo de tótems con 3 tótems, Wither que ignora escudos, cooldown de perlas 40, minar sin netherita quita
  16, modo Mikecrack (OP).
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
