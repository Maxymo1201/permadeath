# Permadeath para NeoForge 1.21.1: notas del port

Port nativo a **NeoForge 21.1.x (compilado y probado con 21.1.256) / Minecraft 1.21.1 / Java 21** del mod Fabric `permadeath-1.21.1.jar`
(Serthekiller, CC0-1.0, basado en la serie Permadeath de ElRichMC y en el plugin PermaDeathCore). No usa
Fabric Loader, Fabric API, Sinytra Connector ni `fabric.mod.json`. El código usa nombres Mojang (Mojmap);
no hace falta Parchment.

## Estructura

| Ruta | Contenido |
|---|---|
| `src/core/java` | Núcleo **sin dependencias de Minecraft**: calendario (`ProgressionClock`, `GameDayProgressionClock`, `RealTimeProgressionClock`), `MilestoneTracker`, estado persistido (`ProgressionState`), reglas numéricas por día (`rules/DayRules`, `rules/TotemRules`) y el parser de los ficheros del mod Fabric (`legacy/LegacyFabricState`). |
| `src/coreTest/java` | Tests JUnit 5 del núcleo (82 casos). |
| `src/main/java/.../gametest` | 15 GameTests (se ejecutan en los dos perfiles). |
| `src/main/java` | El mod NeoForge (registros, eventos, fases, mobs, End, The Beginning, comandos, worldgen, mixins, GameTests). |
| `src/main/resources` | Assets y datos del jar original convertidos (`fabric:load_conditions` → `neoforge:conditions`), `accesstransformer.cfg`, `permadeath.mixins.json`, loot modifier global. |
| `src/main/templates/META-INF/neoforge.mods.toml` | Descriptor del mod (se expande con `gradle.properties`). |
| `src/profiles/GAME60`, `src/profiles/REAL30` | Descriptor de identidad del calendario que se empaqueta en cada jar. |
| `tools/server-smoke-test.sh` | Prueba de servidor dedicado + reinicio con los jars de producción. |

## Un núcleo, dos jars

Las dos variantes se construyen con **las mismas clases compiladas**. La única diferencia es
`permadeath_profile.properties` (`mode=GAME60` o `mode=REAL30`), que se añade a cada jar. `BuildProfile` lo
lee al arrancar y el modo no se puede cambiar por configuración. Un jar sin perfil se niega a cargar, y por
eso la tarea `jar` estándar está desactivada.

```
./gradlew clean build          # tests del núcleo + permadeath-GAME60-neoforge-1.21.1.jar + permadeath-REAL30-neoforge-1.21.1.jar + verifyProductionJars
./gradlew buildGame60          # solo GAME60 (+ verificación)
./gradlew buildReal30          # solo REAL30 (+ verificación)
./gradlew coreTest             # 82 tests unitarios del calendario y las reglas (no necesita Minecraft)
./gradlew runGameTestServer -PpermadeathMode=GAME60   # GameTests en el entorno de desarrollo (también REAL30)
./gradlew runServer -PpermadeathMode=REAL30           # servidor de desarrollo con el perfil indicado
tools/server-smoke-test.sh GAME60 REAL30              # servidor dedicado con los jars de build/libs, arranque + reinicio
```

`verifyProductionJars` abre los dos jars y falla si falta `META-INF/neoforge.mods.toml`, el AT, la
configuración de mixins o el perfil; si el perfil no corresponde al nombre del jar; si hay `fabric.mod.json`,
`net/fabricmc/*` o un `.accesswidener`; o si alguna clase compilada referencia `net.fabricmc`.

## Dependencias de red de la build

ModDevGradle descarga NeoForm/NeoForge de `maven.neoforged.net` y el servidor, el cliente y las librerías de
Minecraft de `piston-meta.mojang.com`, `piston-data.mojang.com` y `libraries.minecraft.net`. Con esos cuatro
hosts basta para `./gradlew clean build`, los GameTests y `tools/server-smoke-test.sh`. Resultados en
`PERMADEATH_TEST_REPORT.md`.

* Las runs de servidor (`runServer`, `runGameTestServer` y `smokeServer`) usan un descriptor de assets vacío
  (`writeServerAssetStub`): un servidor no lee los assets del cliente, así que no necesitan
  `resources.download.minecraft.net`. `runClient` sí lo necesita.
* `tools/server-smoke-test.sh` arranca por defecto el servidor dedicado NeoForge que prepara ModDevGradle
  (run `smokeServer`, sin clases del mod: el mod se carga solo desde el jar de producción copiado en `mods/`).
  Con `SMOKE_RUNTIME=installer` usa el instalador oficial de NeoForge, que además necesita
  `launchermeta.mojang.com`.

## Decisiones de diseño

* **Eventos de NeoForge antes que mixins.** De los 54 mixins/accessors de Fabric quedan 4 mixins, que no
  tienen ningún evento equivalente (destino de los End Gateway, pasajero que controla la montura, inicio de
  estructuras a partir del D40 y colisiones del shulker montado). Hay además 7 entradas de access
  transformer. Ver `FABRIC_TO_NEOFORGE_PORT.md`.
* **Un único enrutador de eventos** (`event/PermadeathEvents`). El orden queda explícito y todo es solo de
  servidor: si el calendario no está en marcha, no se ejecuta nada.
* **El calendario es la única fuente de verdad.** Ninguna mecánica calcula el día por su cuenta:
  `Permadeath.day()` lee `ProgressionClock`. Los hitos D0, D10, D20, D25, D30, D40, D50 y D60 se ejecutan una
  sola vez y de forma idempotente con `MilestoneTracker`, también cuando hay saltos de días.
* **Las entidades que se unen al mundo se procesan al final del tick.** La lógica de fase que crea,
  sustituye o elimina entidades no se ejecuta dentro de `EntityJoinLevelEvent` (cargar un chunk mientras se
  hace es inseguro). Las cancelaciones y los cambios simples sí son inmediatos.
* **Marcas persistentes por entidad.** `pmd_proc_<clave>` es el mismo formato de tag que Fabric, así que
  ningún mob se vuelve a procesar al recargar. Las IA (goals), que Minecraft no guarda, se restauran al
  cargar (`GoalRestorer`).
* **Persistencia con `SavedData`.** `permadeath_progression` guarda calendario, Death Train, Life Orb,
  temporizadores del Wither, manzanas, Mikecrack, arena del End y migración. Se mantienen los ficheros
  `SavedData` de Fabric con el mismo nombre y las mismas claves: `permadeath_portal_state_day40`,
  `permadeath_custom_messages`, `permadeath_server_mode`, `permadeath_beginning_curse` y
  `permadeath_survival_achievement`. Los ficheros sueltos de Fabric (`permadeath_date.txt`,
  `permadeath_storm.txt`, …) se importan una vez y **no se borran nunca**. En el log aparece
  `[Permadeath] Legacy state migrated successfully`.
* **IDs.** Se conservan todos los `permadeath:*` (ítems, bloque, materiales de armadura, sonido, recetas,
  serializers, condiciones, dimensión `permadeath:the_beginning`, tipos de worldgen, estructuras y patrón de
  estandarte), así que los mundos existentes siguen siendo válidos.

## Ficheros del jar original que no se copian

* `data/minecraft/recipe/dragon_breath.json`: usa la sintaxis de ingredientes de 1.21.2+ (cadenas planas).
  En 1.21.1 no se podía leer y además duplica `permadeath:dragon_breath`, que sí funciona y se conserva.
* `fabric.mod.json`, `permadeath.mixins.json` (Fabric), `modid.client.mixins.json` y `LICENSE_modid`: los
  sustituyen sus equivalentes de NeoForge y el fichero `LICENSE` (CC0-1.0).

Las carpetas `assets/permadeath/items`, `assets/permadeath/equipment` y `data/permadeath/equipment` (formato
1.21.2+) se copian tal cual. 1.21.1 las ignora, igual que las ignoraba el mod Fabric.

## Créditos y licencia

Mod original para Fabric: **Serthekiller**. Diseño de juego: serie Permadeath de **ElRichMC** y plugin
**PermaDeathCore** (fuente histórica que se usa como referencia cuando el jar es ambiguo). Licencia
**CC0-1.0** (`LICENSE`), la misma del mod original.
