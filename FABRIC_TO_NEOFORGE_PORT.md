# Fabric → NeoForge: correspondencia completa

Para cada pieza del mod Fabric se indica qué hace ahora el port y por qué. Preferencia aplicada en orden:
**evento de NeoForge → API vanilla → `SavedData` → access transformer → mixin** (solo si no había otra vía).

## 1. Carga, metadatos y registros

| Fabric | NeoForge |
|---|---|
| `fabric.mod.json`, `ModInitializer` | `META-INF/neoforge.mods.toml` (plantilla), `@Mod("permadeath")` en `PermadeathMod` |
| `Registry.register(...)` en inicializadores estáticos | `DeferredRegister`: ítems, bloque, materiales de armadura, sonido, serializers de receta, condiciones, loot modifier, density function, feature, structure processor, structure type y placement modifier. Todos con los mismos IDs `permadeath:*` |
| `ResourceConditions` (`permadeath:min_day`, `max_day`) + `fabric:load_conditions` | `ICondition` registradas en `NeoForgeRegistries.Keys.CONDITION_CODECS` (`permadeath:min_day`, `permadeath:max_day`, `permadeath:before_day`); JSON convertido a `neoforge:conditions`. `fabric:all_mods_loaded` con un mod inexistente pasa a `neoforge:false` |
| `ItemGroupEvents.modifyEntriesEvent` | `BuildCreativeModeTabContentsEvent` (mismas posiciones en Combate y Comida) |
| `ModDimensions` (claves) | `beginning/BeginningDimension` (mismas claves; la dimensión sigue definida por JSON) |
| `access widener` / accessors | 7 entradas en `META-INF/accesstransformer.cfg`; `goalSelector` y `targetSelector` ya son públicos con el AT de NeoForge |

## 2. Eventos de Fabric API

| Fabric API | NeoForge |
|---|---|
| `ServerLifecycleEvents.SERVER_STARTED` | `LevelEvent.Load` del Overworld (el calendario arranca antes de cargar los chunks de spawn) |
| `ServerLifecycleEvents.SERVER_STOPPED` | `ServerStoppedEvent` (limpia todo el estado estático) |
| `ServerTickEvents.END_SERVER_TICK` | `ServerTickEvent.Post` |
| `ServerTickEvents.END_WORLD_TICK` | `LevelTickEvent.Post` |
| `ServerEntityEvents.ENTITY_LOAD` | `EntityJoinLevelEvent`: parte inmediata (cancelaciones) + parte diferida al final del tick |
| `ServerLivingEntityEvents.ALLOW_DAMAGE` | `LivingIncomingDamageEvent` |
| `ServerLivingEntityEvents.AFTER_DEATH` | `LivingDeathEvent` + `LivingDropsEvent` |
| `EntitySleepEvents.ALLOW_SLEEPING` | `CanPlayerSleepEvent` (`BedSleepingProblem.OTHER_PROBLEM`) |
| `PlayerBlockBreakEvents.AFTER` | `BlockEvent.BreakEvent` (prioridad LOWEST, solo si no se canceló) |
| `UseBlockCallback` / `UseItemCallback` | `PlayerInteractEvent.RightClickBlock` / `RightClickItem` |
| `CommandRegistrationCallback` | `RegisterCommandsEvent` |
| `ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD` | `PlayerEvent.PlayerChangedDimensionEvent` |
| `ServerPlayConnectionEvents.JOIN` / `DISCONNECT` | `PlayerEvent.PlayerLoggedInEvent` / `PlayerLoggedOutEvent` |
| `ServerPlayerEvents.AFTER_RESPAWN` | `PlayerEvent.Clone` |
| `LootTableEvents.REPLACE` (cofres vacíos en D60) | Global Loot Modifier `permadeath:d60_empty_loot` (solo tablas `minecraft:chests/*`) |
| Hilos con `Thread.sleep` (muerte, baneo) | `util/ServerScheduler`: tareas en el hilo del servidor, con los mismos retardos en ticks |

## 3. Los 54 mixins/accessors de Fabric

| Mixin Fabric | Qué hacía | NeoForge |
|---|---|---|
| `AreaEffectCloudMixin` | Nubes personalizadas (blanca 10 de daño, humo que limpia efectos, corazones oscuros, nube eléctrica) | `EntityTickEvent.Post` → `GameplayRules.customCloud` |
| `creepermixin` | Cancela la nube de efectos de los creepers | `EntityJoinLevelEvent` cancelado si la nube nace en el punto y tick de la explosión de un creeper (`ExplosionEvent.Start`) |
| `BeeEntityMixin` | D20+: las abejas conservan el aguijón | AT `Bee#setHasStung` + `EntityTickEvent.Post` |
| `BucketItemMixin` | D50+: el cubo vacío no recoge agua ni lava | `PlayerInteractEvent.RightClickItem` → `FAIL` (además, en The Beginning no se vacía ningún cubo, como en el plugin) |
| `ChunkGeneratorStructureBlockMixin` | D40+: no se generan aldeas, templos, monumentos ni naufragios | **Mixin** `ChunkGeneratorMixin` (no existe evento de inicio de estructura y los structure sets no se recargan) |
| `CommandMonitorMixin` | Anota trampas para el logro de supervivencia | `CommandEvent` |
| `DragonFireballMixin` | Tabla de nubes, lava, bedrock y rayos de las bolas del dragón | `ProjectileImpactEvent` → `end/DragonFireballs` |
| `EndAccessMixin` | End cerrado antes del D30 | `EntityTravelToDimensionEvent` cancelado + impulso hacia arriba |
| `EndCrystalMixin` | Regeneración de cristales y Ender Ghast al destruirlos | `EntityTickEvent.Pre` (registro) + `LevelTickEvent.Post` → `end/EndCrystals` |
| `EndDragonFightMixin` | Ladrillos de piedra del End, altares y pociones | `LevelTickEvent.Post` del End → `end/EndArena` (incremental y persistente) |
| `EnderDragonMixin` | "PERMADEATH DEMON": vida, fases, giro, ataques | `EntityJoinLevelEvent` + `EntityTickEvent.Pre/Post` → `end/EnderDragonDemon` |
| `EndermanMixin` | Inmune dentro de la nube verde (regeneración + tótem) | `LivingIncomingDamageEvent` |
| `EnderPearlCooldownMixin` | D60: cooldown de perlas 40 ticks | `EntityTeleportEvent.EnderPearl`: 120 ticks al caer la perla, como en el plugin |
| `EndGatewayMixin` | Gateways del Overworld y de The Beginning | **Mixin** `EndGatewayBlockMixin` (no existe evento que cambie el destino de un portal) |
| `EntityAccessor`, `LivingEntityAccessor`, `MobAccessor`, `GoalSelectorAccessor`, `MobPathfindingAccessor`, `PathNavigationAccessor`, `MinecraftServerAccessor`, `RecipeMapAccessor`, `ShulkerAccessor`, `SleepCounter$PlayerAccessor` | Acceso a miembros privados | Innecesarios: API pública, AT de NeoForge o AT propio |
| `ExplosionMixin` | Las explosiones no rompen bloques en The Beginning | `ExplosionEvent.Detonate` (vacía `getAffectedBlocks`) |
| `GiantAIMixin` | IA del Zombie Gigante | Goals añadidos al crear el gigante (`SpecialMobs.injectGiantAI`) y restaurados al cargar (`GoalRestorer`) |
| `GuardianAttackGoalMixin` | D40+: el láser carga el doble de rápido | AT `Guardian$GuardianAttackGoal#attackTime` + `EntityTickEvent.Pre` |
| `ItemFrameMixin` | D40+: los élitros de los marcos del End aparecen casi rotos | `EntityJoinLevelEvent` (marco con élitros en el End) |
| `LargeFireballMinix` | Potencia de las bolas de los ghasts especiales | AT `LargeFireball#explosionPower` + `EntityJoinLevelEvent` |
| `LivingEntityMixin` | Ahogamiento acelerado, inmunidad a pociones, TNT y conchas al morir un shulker… | `LivingBreatheEvent`, `LivingIncomingDamageEvent`, `LivingDeathEvent`, `LivingDropsEvent` |
| `LootTableMixin` | Contenedores vacíos en The Beginning (D60) | **No se porta**: dejaba sin loot todos los cofres de The Beginning abiertos desde el D60 (ver PERMADEATH_AUDIT.md §6) |
| `MagmaCubeSpawnLimitMixin` | D25: límite de magma cubes en los deltas de basalto | `MobSpawnEvent.SpawnPlacementCheck` |
| `MikecrackCreeperSpawnMixin`, `MikecrackSpawnPlacementsMixin` | Modo Mikecrack: los creepers ignoran las reglas de aparición | Sustituido por la regla del plugin (activa por defecto el D60): Ender Quantum Creepers junto a los jugadores desde el tick del servidor (`Mikecrack`) |
| `MinecartTickMixin` | La vagoneta-spawner huérfana recupera la gravedad | `EntityTickEvent.Pre` |
| `MixinCaveSpider` | D50: veneno III + náusea al morder | `LivingDamageEvent.Post`. El punto de anclaje para un shulker no se porta: solo actúa si la araña va montada **sobre** un shulker, cosa que no ocurre nunca |
| `MixinLivingEntityFatigue` | Fatiga de minería ×2; leche y tótems no quitaban fatiga, hambre ni veneno | `MobEffectEvent.Applicable` (se sustituye por la instancia ajustada) + `MobEffectEvent.Remove` (solo la leche respeta la fatiga, como en el plugin) |
| `MixinLlamaSpit` | D50: el escupitajo envenena y empuja | `ProjectileImpactEvent` |
| `MixinMob` | La araña con shulker y el "ghast pig" no los controla el jinete | **Mixin** `MobMixin` (`getControllingPassenger`) |
| `MixinPlayerFoodEffects` | Comidas peligrosas a partir del D50 | `LivingEntityUseItemEvent.Finish` (tabla del plugin) |
| `MobCapMixin` | Límite de monstruos ×2 desde el D10 | AT `MobCategory#max` (public-f) + `MobCapController` |
| `NetheriteUpgradeMixin` | Herramientas de netherita irrompibles y con nombre dorado | `ModifyDefaultComponentsEvent` (`UNBREAKABLE` oculto + `ITEM_NAME` dorado) |
| `PlayerMixin` | D30-39: al acabarse la visión nocturna en el End, nube de corazones oscuros | Tick del jugador (`GameplayRules.onPlayerTick`), D30-60 como en el plugin |
| `PrimedTntMixin` | TNT del dragón: lanza bloques por los aires | `EntityTickEvent.Pre` |
| `QuantumCreeperMixin` | Radio 20 y mecha de 15 ticks (D60) | AT `Creeper#explosionRadius`/`maxSwell` + `EntityTickEvent.Pre` |
| `RaidHeroEffectMixin` | D50: Héroe de la Aldea máximo 5 min | `MobEffectEvent.Applicable` |
| `RecipeManagerMixin` | Quita antorchas (D40) y lingotes de horno (D50) | `OnDatapackSyncEvent` + `RecipeManager#replaceRecipes` (`recipes/RecipeFilter`) |
| `ResultSlotMixin` | Coste extra de las recetas especiales | `PlayerEvent.ItemCraftedEvent` (`recipes/CraftingCost`) |
| `ShulkerBulletMixin` | Las balas de shulker generan TNT | `ProjectileImpactEvent` (en el End, mechas del plugin) |
| `ShulkerMixin` | El shulker montado no colisiona ni empuja; el "ShulkerRojo" no se teletransporta | **Mixin** `ShulkerMixin` (colisión/empuje) + `EntityTeleportEvent.EnderEntity` (teletransporte) |
| `SnowGolemSnowballMixin` | Bolas de nieve de 50 de daño en los días 61-69 | **No se porta**: el D60 es el último día (contenido D61-69 fuera de alcance) |
| `VehicleEntityMixin` | La vagoneta-spawner es invulnerable | `EntityInvulnerabilityCheckEvent` |
| `WitherBossMixin` | El Wither no rompe bloques en The Beginning | `LivingDestroyBlockEvent` |
| `WitherShieldBreakMixin` | D60: el Wither ignora el escudo y lo desactiva | `LivingShieldBlockEvent` |

**Mixins que quedan: 4** (`ChunkGeneratorMixin`, `EndGatewayBlockMixin`, `MobMixin`, `ShulkerMixin`), todos
`@Inject` en `HEAD` con `defaultRequire = 1`. Si cambia un objetivo, el arranque falla de forma visible en
lugar de seguir sin la funcionalidad. La única excepción es el empuje del shulker montado al abrirse
(`onPeekAmountChange`, `require = 0`): es un detalle menor y nunca debe impedir que el servidor arranque.

## 4. Sistemas propios del mod

| Fabric | NeoForge |
|---|---|
| `DateManager` (fecha real `dd/MM/yyyy`, días de 24 h, `speedrun`) | `ProgressionClock` con dos implementaciones fijadas por build (ver `PERMADEATH_TIME_MODES.md`). `speedrun` no se porta: el modo de calendario no se puede cambiar en caliente |
| `PermadeathUtils` (tormenta en ticks, en memoria + fichero) | `mechanics/DeathTrain`: fin absoluto en epoch ms dentro de `SavedData` |
| `TotemManager` (dentro de `ALLOW_DAMAGE`) | `mechanics/TotemSystem` sobre `LivingUseTotemEvent` |
| `WitherSpawn` (72000 ticks por jugador, en fichero) | `mechanics/WitherSpawner` (60 min reales por jugador en `SavedData`) |
| `LifeOrbManager` | `mechanics/LifeOrb` (barra de jefe, plazo absoluto) |
| `ArmorBonusHandler` | `mechanics/PlayerHealth` (modificadores con ID estable, sin acumulación) |
| `DeathHandler` | `mechanics/DeathHandler` (mismos mensajes, sonidos, monumento y baneo) |
| `PhaseManager` + `DayXtoYHandler` (7 clases y `Day70Handler`) | `phase/PhaseManager` + 7 handlers; D40, D50 y D60 comparten `LatePhaseHandler`. `Day70Handler` no existe (el D60 es el último día) |
| `HyperAppleDataManager` (`hyper_apple_consumed.json`) | `ProgressionState.hyperApplesConsumed` (el JSON se importa) |
| Ficheros `*_State` de `PersistentState` | `SavedData` con el mismo nombre y las mismas claves NBT |
