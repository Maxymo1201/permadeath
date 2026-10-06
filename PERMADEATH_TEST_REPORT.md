# Informe de pruebas

Fecha de ejecución: 2026-10-06. Entorno: contenedor Linux con OpenJDK 21.0.12 y Gradle 8.14.3 (wrapper).

## Resumen

| Prueba | Estado |
|---|---|
| Tests unitarios del núcleo (`./gradlew coreTest`) | **Ejecutados: 82/82 OK** |
| Validación de recursos (JSON, condiciones, referencias a Fabric) | **Ejecutada: OK** |
| Lint estático de imports y de referencias entre clases del proyecto | **Ejecutado: OK** |
| `./gradlew clean build` (compilación del mod + 2 jars + `verifyProductionJars`) | **No ejecutado: red bloqueada** |
| GameTests (`runGameTestServer`, 15 tests) | **No ejecutados: necesitan la compilación** |
| Servidor dedicado: arranque, reinicio y persistencia (`tools/server-smoke-test.sh`) | **No ejecutado: necesita los jars y el instalador de NeoForge** |
| Inspección de los jars de producción | **No ejecutada: necesita los jars** |

## Bloqueo de red

La política de red del entorno rechaza (HTTP 403 del proxy, "connect_rejected") los hosts que
ModDevGradle necesita para preparar Minecraft y NeoForge: `maven.neoforged.net`, `piston-meta.mojang.com`,
`piston-data.mojang.com` y `libraries.minecraft.net`. Maven Central, el Gradle Plugin Portal y GitHub sí
funcionan; por eso el núcleo, que no depende de Minecraft, sí se pudo compilar y probar. No se usaron
espejos de terceros para no saltarse la política de red.

Salida literal de `./gradlew compileJava`:

```
> Task :createMinecraftArtifacts FAILED
Execution failed for task ':createMinecraftArtifacts'.
> Could not resolve all artifacts for configuration ':neoFormRuntimeExternalTools'.
   > Could not resolve net.neoforged:neoform-runtime:2.0.31.
      > Could not GET 'https://maven.neoforged.net/releases/net/neoforged/neoform-runtime/2.0.31/neoform-runtime-2.0.31.pom'.
        Received status code 403 from server: Forbidden
```

Con esos cuatro hosts permitidos, la verificación completa se lanza así:

```
./gradlew clean build                 # compila, 82 tests, genera y verifica los 2 jars
./gradlew runGameTestServer -PpermadeathMode=GAME60
./gradlew runGameTestServer -PpermadeathMode=REAL30
tools/server-smoke-test.sh            # GAME60 y REAL30 sobre un servidor NeoForge real
```

## 1. Tests unitarios del núcleo (ejecutados)

`./gradlew --offline coreTest` → `BUILD SUCCESSFUL`. Informes JUnit en `build/test-results/coreTest`.

| Clase | Tests | Fallos | Casos |
|---|---|---|---|
| `GameDayProgressionClockTest` | 13 | 0 | Límites D9→D10, D19→D20, D24→D25, D29→D30, D39→D40, D49→D50, D59→D60; los saltos ejecutan cada hito intermedio una vez; un mundo existente en el día 347 empieza en D0; dormir avanza el calendario; un rollback de `/time` nunca retrocede; `setDay` reancla de forma segura; mover un mundo REAL30 a GAME60 conserva el día |
| `RealTimeProgressionClockTest` | 24 | 0 | T+0 h → D0; 119 h 59 m → D9; 120 h → D10; 239 h 59 m → D19; 240 h → D20; 299 h 59 m → D24; 300 h → D25; 359 h 59 m → D29; 360 h → D30; 479 h 59 m → D39; 480 h → D40; 599 h 59 m → D49; 600 h → D50; 719 h 59 m → D59; 720 h → D60; 721 h y 1000 h → D60; independiente de los TPS; zona horaria y horario de verano no influyen; el tiempo con el servidor apagado cuenta; un retroceso del reloj del sistema nunca retrocede el día; instantes de hito exactos; `setDay` coherente y persistente; mover un mundo GAME60 a REAL30 conserva el día |
| `LegacyFabricStateTest` | 2 | 0 | Lee todos los ficheros de Fabric (fecha, tormenta, wither, Life Orb, Mikecrack, manzanas) y los aplica en GAME60; en REAL30 conserva el día y lo limita a 60 |
| `RulesTest` | 43 | 0 | Tótems D0, D29, D30, D39, D40, D49, D50, D59 y D60 (fallo % y número de tótems), límites de la tirada y prueba de humo con RNG; mobs D0/D9 = 70 y D10/D11/D60 = 140; ahogamiento ×1 (D0, D49), ×5 (D50, D59), ×10 (D60); ceguera D39 = 0, D40/D49 = 1/10000, D50/D59/D60 = 1/5000; duración del Death Train D0…D60 (coincide con PermaDeathCore); buffs del Death Train; umbrales varios (PvP, End, manzanas, vida máxima) |
| **Total** | **82** | **0** | |

## 2. Validaciones estáticas (ejecutadas)

* **Recursos**: los 160 ficheros JSON de `src/main/resources` se leen sin errores. Hay 31 ficheros con
  condiciones convertidas de `fabric:load_conditions` a `neoforge:conditions`, y ningún fichero de recursos
  menciona `fabric`.
* **Imports**: ningún identificador de clase queda sin importar o declarar, salvo los tipos anidados
  heredados, que son válidos en Java (`SavedData.Factory`, `Item.Properties`, `Goal.Flag`,
  `ICondition.IContext`, `Structure.GenerationContext`…).
* **Referencias entre clases del proyecto**: cada `Clase.miembro` que apunta a una clase del proyecto tiene
  una declaración real. 0 problemas en 91 ficheros (≈13 000 líneas).
* `bash -n tools/server-smoke-test.sh`: sintaxis correcta.

Estas comprobaciones **no sustituyen** a la compilación. Los nombres Mojmap de la API de Minecraft/NeoForge,
los objetivos de los 4 mixins (`getPortalDestination`, `getControllingPassenger`, `tryGenerateStructure`,
`canBeCollidedWith`, `onPeekAmountChange`) y las 7 entradas del AT (`validateAccessTransformers = true`) se
validan en la primera build con red y en el primer arranque del servidor.

## 3. GameTests escritos (pendientes de ejecución)

`gametest/PermadeathGameTests` (15 tests, plantilla `permadeath:gametest_empty`). Cada lote fija el día en
`@BeforeBatch`.

| Lote | Test | Comprueba |
|---|---|---|
| d0 | `hostileMobCapVanillaBeforeD10` | Límite 70 |
| d0 | `drowningVanillaBeforeD50` | Un cerdo sumergido conserva ≥ 280 de aire a los 10 ticks |
| d0 | `oneTotemSavesBeforeD30` | Un tótem salva y se consume |
| d0 | `endClosedBeforeD30` | `EntityTravelToDimensionEvent` hacia el End cancelado |
| d10 | `calendarPhaseFollowsDay` | Día 10 y fase D10-19 |
| d10 | `hostileMobCapDoubledFromD10` | Límite 140 |
| d10 | `pvpDisabledBeforeD40` | PvP desactivado |
| d40 | `torchRecipeRemovedOnD40` | `minecraft:torch` no existe y el hierro de horno sí |
| d40 | `chestLootPresentBeforeD60` | El loot de mazmorra no está vacío |
| d40 | `oneTotemIsNotEnoughOnD40` | Con un tótem el jugador muere y el tótem se consume |
| d40 | `maxHealthPenaltyAndLockedSlotsOnD40` | 12 de vida máxima, hueco 4 bloqueado, PvP activado |
| d60 | `calendarNeverGoesBeyondD60` | `setDay(70)` → 60 |
| d60 | `drowningTenTimesFasterOnD60` | ≤ 230 de aire a los 10 ticks (vanilla ≈ 290) |
| d60 | `chestLootEmptyOnD60` | El loot de mazmorra está vacío |
| d60 | `maxHealthPenaltyOnD60` | 4 de vida máxima |

## 4. Prueba de servidor dedicado (preparada)

`tools/server-smoke-test.sh` instala el servidor NeoForge de `gradle.properties` y copia el jar de producción
de cada perfil. Después:

1. **Primer arranque**: comprueba `Calendar <PERFIL> started` y ejecuta `/permadeath status`, `setday 40`,
   `status`, `debug` y `save-all flush`. Exige `Milestone D40 executed` y `Día Permadeath: 40/60`, sin
   errores de mixin ni errores de `permadeath`.
2. **Reinicio**: exige `Calendar <PERFIL> started: PD day 40` (persistencia) y `Día Permadeath: 40/60`, y que
   el hito D40 **no** se vuelva a ejecutar (idempotencia).
