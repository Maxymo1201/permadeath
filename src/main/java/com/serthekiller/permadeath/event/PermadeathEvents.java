package com.serthekiller.permadeath.event;

import com.serthekiller.permadeath.PermadeathMod;
import com.serthekiller.permadeath.beginning.BeginningEvents;
import com.serthekiller.permadeath.command.PermadeathCommands;
import com.serthekiller.permadeath.end.DragonFireballs;
import com.serthekiller.permadeath.end.EndAccess;
import com.serthekiller.permadeath.end.EndArena;
import com.serthekiller.permadeath.end.EndCrystals;
import com.serthekiller.permadeath.end.EndPillars;
import com.serthekiller.permadeath.end.EnderDragonDemon;
import com.serthekiller.permadeath.mechanics.DeathHandler;
import com.serthekiller.permadeath.mechanics.DeathTrain;
import com.serthekiller.permadeath.mechanics.GameplayRules;
import com.serthekiller.permadeath.mechanics.LifeOrb;
import com.serthekiller.permadeath.mechanics.Mikecrack;
import com.serthekiller.permadeath.mechanics.MilkCurse;
import com.serthekiller.permadeath.mechanics.MobCapController;
import com.serthekiller.permadeath.mechanics.MushroomSpawn;
import com.serthekiller.permadeath.mechanics.PlayerHealth;
import com.serthekiller.permadeath.mechanics.TotemSystem;
import com.serthekiller.permadeath.mechanics.WelcomeMessage;
import com.serthekiller.permadeath.mechanics.WitherSpawner;
import com.serthekiller.permadeath.mechanics.WorldRules;
import com.serthekiller.permadeath.mobs.ExplodingAnimals;
import com.serthekiller.permadeath.mobs.GoalRestorer;
import com.serthekiller.permadeath.mobs.MobTracking;
import com.serthekiller.permadeath.phase.PhaseCommon;
import com.serthekiller.permadeath.phase.PhaseHandler;
import com.serthekiller.permadeath.phase.PhaseManager;
import com.serthekiller.permadeath.progression.DayController;
import com.serthekiller.permadeath.progression.Permadeath;
import com.serthekiller.permadeath.recipes.CraftingCost;
import com.serthekiller.permadeath.recipes.RecipeFilter;
import com.serthekiller.permadeath.util.ServerScheduler;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.CommandEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityEvent;
import net.neoforged.neoforge.event.entity.EntityInvulnerabilityCheckEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingBreatheEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDestroyBlockEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingShieldBlockEvent;
import net.neoforged.neoforge.event.entity.living.LivingUseTotemEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;
import net.neoforged.neoforge.event.entity.player.CanPlayerSleepEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.List;
import java.util.function.Consumer;

/**
 * Single router between the NeoForge game event bus and the Permadeath systems. Keeping every subscription
 * in one place makes the order explicit (Fabric relied on registration order spread over many classes).
 * Every handler is server side only; nothing runs when the calendar is not running.
 */
public final class PermadeathEvents {
    private PermadeathEvents() {
    }

    public static void register(IEventBus bus) {
        // lifecycle
        bus.addListener(PermadeathEvents::onLevelLoad);
        bus.addListener(PermadeathEvents::onServerStopped);
        bus.addListener(PermadeathEvents::onServerTick);
        bus.addListener(PermadeathEvents::onLevelTick);
        bus.addListener(PermadeathEvents::onRegisterCommands);
        bus.addListener(RecipeFilter::onDatapackSync);

        // entities
        bus.addListener(EventPriority.HIGH, PermadeathEvents::onEntityJoin);
        bus.addListener(PermadeathEvents::onEntityTickPre);
        bus.addListener(PermadeathEvents::onEntityTickPost);
        serverOnly(bus, LivingIncomingDamageEvent.class, PermadeathEvents::onIncomingDamage);
        serverOnly(bus, LivingDamageEvent.Post.class, GameplayRules::onDamagePost);
        serverOnly(bus, EntityInvulnerabilityCheckEvent.class, GameplayRules::onInvulnerabilityCheck);
        serverOnly(bus, LivingShieldBlockEvent.class, GameplayRules::onShieldBlock);
        serverOnly(bus, LivingDestroyBlockEvent.class, GameplayRules::onLivingDestroyBlock);
        bus.addListener(PermadeathEvents::onDeath);
        bus.addListener(PermadeathEvents::onDrops);
        serverOnly(bus, MobEffectEvent.Applicable.class, GameplayRules::onEffectApplicable);
        serverOnly(bus, MobEffectEvent.Remove.class, GameplayRules::onEffectRemove);
        serverOnly(bus, LivingEntityUseItemEvent.Finish.class, GameplayRules::onFinishUsingItem);
        serverOnly(bus, EntityTeleportEvent.EnderEntity.class, GameplayRules::onEnderTeleport);
        bus.addListener(PermadeathEvents::onSpawnPlacementCheck);
        bus.addListener(PermadeathEvents::onProjectileImpact);
        serverOnly(bus, LivingUseTotemEvent.class, TotemSystem::onUseTotem);
        bus.addListener(WorldRules::onBreathe);
        serverOnly(bus, EntityTravelToDimensionEvent.class, EndAccess::onTravelToDimension);
        bus.addListener(PermadeathEvents::onExplosionStart);
        bus.addListener(PermadeathEvents::onExplosionDetonate);

        // players
        bus.addListener(PermadeathEvents::onLogin);
        bus.addListener(PermadeathEvents::onLogout);
        bus.addListener(PermadeathEvents::onClone);
        bus.addListener(BeginningEvents::onChangedDimension);
        bus.addListener(PermadeathEvents::onSleep);
        bus.addListener(EventPriority.LOWEST, PermadeathEvents::onBlockBreak);
        bus.addListener(PermadeathEvents::onRightClickBlock);
        bus.addListener(PermadeathEvents::onRightClickItem);
        bus.addListener(PermadeathEvents::onItemCrafted);
        bus.addListener(PermadeathEvents::onCommand);
    }

    /**
     * Registers an entity event handler that only runs on the server while the calendar is running. The event
     * class is passed explicitly: the bus cannot infer it from a generic wrapper lambda.
     */
    private static <E extends EntityEvent> void serverOnly(IEventBus bus, Class<E> type, Consumer<E> handler) {
        bus.addListener(EventPriority.NORMAL, false, type, event -> {
            if (!event.getEntity().level().isClientSide() && Permadeath.isRunning()) {
                handler.accept(event);
            }
        });
    }

    private static PhaseHandler phase() {
        return Permadeath.isRunning() ? PhaseManager.current() : null;
    }

    // ------------------------------------------------------------------------------------------------ lifecycle

    /**
     * The calendar starts as soon as the Overworld exists (before the spawn chunks load), so entities loaded
     * during startup are already handled with the correct day.
     */
    private static void onLevelLoad(LevelEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level && level.dimension() == Level.OVERWORLD && !Permadeath.isRunning()) {
            Permadeath.start(level.getServer());
        }
    }

    private static void onServerStopped(ServerStoppedEvent event) {
        Permadeath.stop();
        ServerScheduler.clear();
        PhaseManager.reset();
        DayController.reset();
        MobTracking.clearAll();
        PhaseCommon.reset();
        GameplayRules.reset();
        TotemSystem.reset();
        LifeOrb.reset();
        WitherSpawner.reset();
        WorldRules.reset();
        ExplodingAnimals.reset();
        BeginningEvents.reset();
        EndArena.reset();
        EndCrystals.reset();
        EndPillars.reset();
        EnderDragonDemon.reset();
        MushroomSpawn.disable();
        MobCapController.restoreVanilla();
        PermadeathMod.LOGGER.info("[Permadeath] Server stopped: runtime state cleared");
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (Permadeath.isRunning()) {
            DayController.onServerTick(server);
            int day = Permadeath.day();
            DeathTrain.tick(server);
            LifeOrb.tick(server);
            WitherSpawner.tick(server);
            Mikecrack.tick(server);
            MilkCurse.tick(server);
            BeginningEvents.onServerTick(server);
            WorldRules.onServerTick(server);
            for (ServerPlayer player : List.copyOf(server.getPlayerList().getPlayers())) {
                if (player.isRemoved()) {
                    continue;
                }
                PlayerHealth.tick(player);
                GameplayRules.onPlayerTick(player);
                TotemSystem.giveSurvivorMedalIfEligible(player, day);
            }
        }
        ServerScheduler.onServerTick();
    }

    private static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !Permadeath.isRunning()) {
            return;
        }
        PhaseHandler handler = phase();
        if (handler != null) {
            handler.onLevelTick(level);
        }
        EndCrystals.onLevelTick(level);
        MushroomSpawn.tick(level);
        if (level.dimension() == Level.END) {
            if (!level.players().isEmpty()) {
                EndPillars.onPlayerInEnd(level);
            }
            EndArena.tick(level);
        }
    }

    private static void onRegisterCommands(RegisterCommandsEvent event) {
        PermadeathCommands.register(event.getDispatcher());
    }

    // ------------------------------------------------------------------------------------------------ entities

    private static void onEntityJoin(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !Permadeath.isRunning()) {
            return;
        }
        Entity entity = event.getEntity();
        boolean loadedFromDisk = event.loadedFromDisk();
        // Immediate part: cancellations and plain field edits that are safe during chunk loading.
        GameplayRules.onEntityJoinImmediate(event);
        if (event.isCanceled()) {
            return;
        }
        if (entity instanceof EnderDragon dragon) {
            EnderDragonDemon.onJoin(dragon, loadedFromDisk);
            if (level.dimension() == Level.END && !loadedFromDisk) {
                EndPillars.onDragonJoin(level);
            }
        }
        if (loadedFromDisk) {
            GoalRestorer.restore(entity);
        }
        // Deferred part: phase logic may spawn, replace or discard entities.
        ServerScheduler.schedule(0, () -> {
            PhaseHandler handler = phase();
            if (handler != null && !entity.isRemoved() && entity.level() == level) {
                handler.onEntityJoin(entity, level, loadedFromDisk);
            }
        });
    }

    private static void onEntityTickPre(EntityTickEvent.Pre event) {
        Entity entity = event.getEntity();
        if (entity.level().isClientSide() || !Permadeath.isRunning()) {
            return;
        }
        GameplayRules.onEntityTickPre(entity);
        if (entity instanceof EnderDragon dragon) {
            EnderDragonDemon.onTickPre(dragon);
        } else if (entity instanceof EndCrystal crystal) {
            EndCrystals.onCrystalTick(crystal);
        }
    }

    private static void onEntityTickPost(EntityTickEvent.Post event) {
        Entity entity = event.getEntity();
        if (entity.level().isClientSide() || !Permadeath.isRunning()) {
            return;
        }
        GameplayRules.onEntityTickPost(entity);
        if (entity instanceof EnderDragon dragon) {
            EnderDragonDemon.onTickPost(dragon);
        }
    }

    private static void onIncomingDamage(LivingIncomingDamageEvent event) {
        GameplayRules.onIncomingDamage(event);
        PhaseHandler handler = phase();
        if (!event.isCanceled() && handler != null) {
            handler.onIncomingDamage(event);
        }
    }

    private static void onDeath(LivingDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (!(entity.level() instanceof ServerLevel level) || !Permadeath.isRunning()) {
            return;
        }
        if (entity instanceof ServerPlayer player) {
            DeathHandler.onPlayerDeath(player);
        }
        GameplayRules.onDeath(entity);
        PhaseHandler handler = phase();
        if (handler != null) {
            handler.onDeath(entity, event.getSource(), level);
        }
    }

    private static void onDrops(LivingDropsEvent event) {
        if (event.getEntity().level().isClientSide() || !Permadeath.isRunning()) {
            return;
        }
        GameplayRules.onDrops(event);
        PhaseHandler handler = phase();
        if (handler != null) {
            handler.onDrops(event);
        }
    }

    private static void onSpawnPlacementCheck(MobSpawnEvent.SpawnPlacementCheck event) {
        if (!Permadeath.isRunning()) {
            return;
        }
        GameplayRules.onSpawnPlacementCheck(event);
        Mikecrack.onSpawnPlacementCheck(event);
    }

    private static void onProjectileImpact(ProjectileImpactEvent event) {
        if (event.getProjectile().level().isClientSide() || !Permadeath.isRunning()) {
            return;
        }
        DragonFireballs.onProjectileImpact(event);
        if (event.isCanceled()) {
            return;
        }
        GameplayRules.onProjectileImpact(event);
        PhaseHandler handler = phase();
        if (!event.isCanceled() && handler != null) {
            handler.onProjectileImpact(event);
        }
    }

    private static void onExplosionStart(ExplosionEvent.Start event) {
        if (!event.getLevel().isClientSide() && Permadeath.isRunning()) {
            GameplayRules.onExplosionStart(event);
        }
    }

    private static void onExplosionDetonate(ExplosionEvent.Detonate event) {
        if (!event.getLevel().isClientSide() && Permadeath.isRunning()) {
            GameplayRules.onExplosionDetonate(event);
        }
    }

    // ------------------------------------------------------------------------------------------------ players

    private static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && Permadeath.isRunning()) {
            WelcomeMessage.send(player);
            PlayerHealth.applyHyperAppleBonus(player);
        }
    }

    private static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && Permadeath.isRunning()) {
            LifeOrb.onLogout(player);
        }
    }

    private static void onClone(PlayerEvent.Clone event) {
        if (event.getEntity() instanceof ServerPlayer player && Permadeath.isRunning()) {
            PlayerHealth.applyHyperAppleBonus(player);
        }
    }

    private static void onSleep(CanPlayerSleepEvent event) {
        PhaseHandler handler = phase();
        if (handler != null && !event.getEntity().level().isClientSide()) {
            handler.onSleepAttempt(event);
        }
    }

    private static void onBlockBreak(BlockEvent.BreakEvent event) {
        PhaseHandler handler = phase();
        if (handler != null && event.getPlayer() instanceof ServerPlayer player) {
            handler.onBlockBroken(player, event.getPos(), event.getState());
        }
    }

    private static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        PhaseHandler handler = phase();
        if (handler != null && !event.getLevel().isClientSide()) {
            handler.onRightClickBlock(event);
        }
    }

    private static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (event.getLevel().isClientSide() || !Permadeath.isRunning()) {
            return;
        }
        MilkCurse.onRightClickItem(event);
        if (!event.isCanceled()) {
            GameplayRules.onRightClickItem(event);
        }
    }

    private static void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        if (!event.getEntity().level().isClientSide() && Permadeath.isRunning()) {
            CraftingCost.onItemCrafted(event);
        }
    }

    private static void onCommand(CommandEvent event) {
        if (Permadeath.isRunning()) {
            GameplayRules.onCommand(event);
        }
    }
}
