package com.serthekiller.permadeath.phase;

import com.serthekiller.permadeath.beginning.BeginningDimension;
import com.serthekiller.permadeath.core.rules.DayRules;
import com.serthekiller.permadeath.mobs.HostileMobConverter;
import com.serthekiller.permadeath.mobs.MobTracking;
import com.serthekiller.permadeath.mobs.SpecialMobs;
import com.serthekiller.permadeath.progression.Permadeath;
import com.serthekiller.permadeath.util.MobUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.entity.monster.Ravager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.player.CanPlayerSleepEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/** Behaviour shared by several phase handlers (identical code was duplicated in every Fabric handler). */
public final class PhaseCommon {
    private static final Map<UUID, Integer> BEDROCK_COOLDOWNS = new HashMap<>();

    private PhaseCommon() {
    }

    public static void reset() {
        BEDROCK_COOLDOWNS.clear();
    }

    /** Fabric night window used by the sleep rules (12541-23458). */
    public static boolean isNight(ServerLevel level) {
        long time = level.getDayTime() % 24000L;
        return time >= 12541L && time <= 23458L;
    }

    /** Skips to the start of the next Minecraft day (in GAME60 this advances the Permadeath day). */
    public static void skipToNextMorning(ServerLevel level) {
        long time = level.getDayTime();
        level.setDayTime(time / 24000L * 24000L + 24000L);
    }

    /** Runs {@code action} on every loaded entity of every dimension (phase start conversions). */
    public static void forEachLoadedEntity(MinecraftServer server, Consumer<Entity> action) {
        for (ServerLevel level : server.getAllLevels()) {
            List<Entity> snapshot = new ArrayList<>();
            level.getAllEntities().forEach(snapshot::add);
            for (Entity entity : snapshot) {
                if (!entity.isRemoved()) {
                    action.accept(entity);
                }
            }
        }
    }

    // ------------------------------------------------------------------------------------------ beds (D20+)

    public enum PhantomReset { ALWAYS_WITH_MESSAGE, TEN_PERCENT }

    /**
     * D20+: beds never let the player sleep. They "explode" harmlessly (particle + sound) and reset the
     * phantom counter (always on D20-49, 10 % from D50, with a message, as in the plugin; Fabric reset it
     * silently on D20-29 and used "nextInt(100) &lt;= 10", 11 %, from D50). Fabric reset an unrelated internal
     * sleep timer; the counter that spawns phantoms is the {@link Stats#TIME_SINCE_REST} statistic, which is
     * what is reset here.
     */
    public static void denySleep(CanPlayerSleepEvent event, PhantomReset reset) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ServerLevel level = player.serverLevel();
        BlockPos pos = event.getPos();
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, 1, 0.0, 0.0, 0.0, 0.0);
        level.playSound(null, pos.getX(), pos.getY(), pos.getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 1.0F, 1.0F);
        boolean doReset = switch (reset) {
            case ALWAYS_WITH_MESSAGE -> true;
            case TEN_PERCENT -> level.random.nextInt(100) < DayRules.D50_PHANTOM_RESET_PERCENT;
        };
        if (doReset) {
            player.resetStat(Stats.CUSTOM.get(Stats.TIME_SINCE_REST));
            player.displayClientMessage(Component.literal("[PERMADEATH] Contador de phantoms reiniciado").withStyle(ChatFormatting.RED), false);
        }
        event.setProblem(Player.BedSleepingProblem.OTHER_PROBLEM);
    }

    // ------------------------------------------------------------------------------------------ mobs

    /** Creepers are charged from D30 (same NBT round trip as Fabric; there is no public setter). */
    public static void makePowered(Creeper creeper) {
        if (creeper.isPowered()) {
            return;
        }
        CompoundTag tag = new CompoundTag();
        creeper.addAdditionalSaveData(tag);
        tag.putBoolean("powered", true);
        creeper.readAdditionalSaveData(tag);
    }

    /**
     * D20+ phantoms: size 9 (18 from D50, plugin) and double health. Fabric re-applied this on every load (health
     * doubled again after each restart); it is applied once per phantom. Phantoms enlarged before D50 grow to 18
     * once when they are loaded on D50+.
     */
    public static boolean enlargePhantom(Phantom phantom) {
        int size = DayRules.phantomSize(Permadeath.day());
        if (!MobTracking.tryClaim(phantom, "phantom_giant")) {
            if (size > 9 && phantom.getPhantomSize() < size && MobTracking.tryClaim(phantom, "phantom_giant_d50")) {
                phantom.setPhantomSize(size);
            }
            return false;
        }
        if (size > 9) {
            MobTracking.markProcessed(phantom, "phantom_giant_d50");
        }
        phantom.setPhantomSize(size);
        MobUtil.multiplyMaxHealth(phantom, 2.0);
        return true;
    }

    /** Fabric: the hostile conversion is skipped for already converted mobs unless they are "special". */
    public static void convertIfNeeded(LivingEntity entity) {
        if (!HostileMobConverter.isSpecialNeutral(entity.getType()) && HostileMobConverter.isAlreadyHostile(entity)) {
            return;
        }
        HostileMobConverter.convertToHostile(entity);
    }

    private static final String[] RAVAGER_NO_TOTEM = {
            "Vaya que mala suerte, ese ravager no tenia nada :(",
            "¡Porras... otro ravager sin suerte!",
            "Nada... hoy no hay totem :(",
            "¡Hoy no es tu día!"
    };

    /**
     * D20-39 ravager totem drop: 1% before D25, 20% from D25 (Fabric used "day + 1 &gt;= 25" and "&lt;= 20" on 0-99,
     * i.e. 21% from D24). As in the plugin, only ravagers killed by a player roll, and the killer is told the
     * result ("¡Un tótem!" or one of the four plugin lines).
     */
    public static void ravagerTotemDrop(LivingEntity entity, DamageSource source, ServerLevel level) {
        if (!(entity instanceof Ravager) || !(source.getEntity() instanceof ServerPlayer killer)) {
            return;
        }
        int roll = level.random.nextInt(100);
        int chance = Permadeath.day() >= 25 ? 20 : 1;
        if (roll < chance) {
            level.addFreshEntity(new ItemEntity(level, entity.getX(), entity.getY(), entity.getZ(), new ItemStack(Items.TOTEM_OF_UNDYING)));
            killer.sendSystemMessage(Component.literal("¡Un tótem!").withStyle(ChatFormatting.YELLOW));
            level.playSound(null, killer.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0F, 1.0F);
        } else {
            killer.sendSystemMessage(Component.literal(RAVAGER_NO_TOTEM[level.random.nextInt(RAVAGER_NO_TOTEM.length)])
                    .withStyle(ChatFormatting.RED));
        }
    }

    /**
     * Clears the vanilla loot of a mob (Fabric deleted every item entity within 2 blocks after the death,
     * which also deleted the custom drops spawned before and any player item lying around). Only the drops
     * produced by the mob itself are removed now; custom drops spawned in {@code onDeath} are kept.
     */
    public static void clearDrops(LivingDropsEvent event) {
        event.getDrops().clear();
    }

    // ------------------------------------------------------------------------------------------ players

    /** End/Beginning bedrock: Levitation X for 10 s every 10 s while standing on bedrock. */
    public static void bedrockLevitation(ServerPlayer player, boolean includeBeginning) {
        ServerLevel level = player.serverLevel();
        boolean dimension = level.dimension() == Level.END
                || includeBeginning && level.dimension() == BeginningDimension.LEVEL_KEY;
        if (!dimension || !level.getBlockState(player.getOnPos()).is(Blocks.BEDROCK)) {
            return;
        }
        int now = player.server.getTickCount();
        Integer last = BEDROCK_COOLDOWNS.get(player.getUUID());
        if (last == null || now - last >= 200) {
            player.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 200, 9, false, true));
            level.sendParticles(ParticleTypes.PORTAL, player.getX(), player.getY() + 0.1, player.getZ(), 20, 0.5, 0.1, 0.5, 0.1);
            BEDROCK_COOLDOWNS.put(player.getUUID(), now);
        }
    }

    /**
     * D50+: soul sand / soul soil slow the player (Fabric: Slowness II 1 s at D50, Slowness III 3 s at D60). On D60
     * soul sand leaves Slowness III for 30 s, as in the plugin.
     */
    public static void soulSandSlowness(ServerPlayer player, boolean d60) {
        ServerLevel level = player.serverLevel();
        var below = level.getBlockState(player.getOnPos());
        if (d60 && below.is(Blocks.SOUL_SAND)) {
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, DayRules.D60_SOUL_SAND_SLOWNESS_TICKS, 2, false, true));
        } else if (below.is(Blocks.SOUL_SAND) || below.is(Blocks.SOUL_SOIL)) {
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, d60 ? 60 : 20, d60 ? 2 : 1, false, true));
        }
    }

    /** D50+: invisibility does not work in The Beginning. */
    public static void removeInvisibilityInBeginning(ServerPlayer player) {
        if (player.level().dimension() == BeginningDimension.LEVEL_KEY
                && player.hasEffect(MobEffects.INVISIBILITY)) {
            player.removeEffect(MobEffects.INVISIBILITY);
        }
    }

    /**
     * D50+: a player in the rain under open sky in the Overworld gets Levitation I for 3-19 s with probability
     * 1/10000 every second (plugin player loop). Fabric rolled 1/10000 every tick also at night, indoors and
     * underground included.
     */
    public static void randomLevitation(ServerPlayer player) {
        Level level = player.level();
        if (level.dimension() != Level.OVERWORLD || player.isSpectator()
                || player.tickCount % DayRules.RANDOM_LEVITATION_PERIOD_TICKS != 0) {
            return;
        }
        if (level.isRaining() && level.canSeeSky(player.blockPosition())
                && level.random.nextInt(DayRules.RANDOM_LEVITATION_ONE_IN) == 0) {
            player.addEffect(new MobEffectInstance(MobEffects.LEVITATION, DayRules.randomLevitationTicks(level.random.nextInt(17)), 0, false, true));
        }
    }

    /** Stack members never processed by the generic per-phase logic. */
    public static boolean isStackMember(Entity entity) {
        return entity.getTags().contains(SpecialMobs.PROCESSED_STACK);
    }

    public static boolean isMob(Entity entity) {
        return entity instanceof Mob;
    }
}
