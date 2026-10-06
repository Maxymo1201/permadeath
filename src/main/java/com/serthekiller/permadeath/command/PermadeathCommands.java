package com.serthekiller.permadeath.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.serthekiller.permadeath.core.PermadeathCalendar;
import com.serthekiller.permadeath.core.ProgressionState;
import com.serthekiller.permadeath.core.TimeFormat;
import com.serthekiller.permadeath.data.BeginningCurseData;
import com.serthekiller.permadeath.data.CustomMessagesData;
import com.serthekiller.permadeath.data.PortalState;
import com.serthekiller.permadeath.data.ServerModeData;
import com.serthekiller.permadeath.data.SurvivalAchievementData;
import com.serthekiller.permadeath.mechanics.DeathHandler;
import com.serthekiller.permadeath.mechanics.DeathTrain;
import com.serthekiller.permadeath.mechanics.Mikecrack;
import com.serthekiller.permadeath.phase.PhaseManager;
import com.serthekiller.permadeath.progression.DayController;
import com.serthekiller.permadeath.progression.Permadeath;
import com.serthekiller.permadeath.util.Texts;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * {@code /permadeath} (Fabric PermadeathCommands).
 *
 * <p>Changes over Fabric (PERMADEATH_AUDIT.md): every administrative subcommand (changeday/setday, reload,
 * reset, resetstorm, mikecrack, server, maldicion, mensaje set, debug) requires OP level 2 - in Fabric any
 * player could change the day unless the restricted mode was on. {@code speedrun} is not ported: the calendar
 * is fixed by the jar (GAME60 or REAL30). Days are limited to 0-60 (D60 is final).
 */
public final class PermadeathCommands {
    private static final long CURSE_DURATION_MILLIS = 12L * 60L * 60L * 1000L;
    private static final int CURSE_DURATION_TICKS = 864000;
    private static final int MAX_MESSAGE_LENGTH = 100;

    private PermadeathCommands() {
    }

    /** Readable by everybody unless the server is in restricted mode (Fabric restrictedRequire). */
    private static Predicate<CommandSourceStack> publicRequire() {
        return source -> source.hasPermission(2) || !ServerModeData.get(source.getServer()).isRestricted();
    }

    private static Predicate<CommandSourceStack> adminRequire() {
        return source -> source.hasPermission(2);
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("permadeath")
                .then(Commands.literal("info").requires(publicRequire()).executes(PermadeathCommands::info))
                .then(Commands.literal("status").requires(publicRequire()).executes(PermadeathCommands::status))
                .then(Commands.literal("days").requires(publicRequire()).executes(PermadeathCommands::status))
                .then(Commands.literal("BeginningLocation").requires(publicRequire()).executes(PermadeathCommands::beginningLocation))
                .then(Commands.literal("changeday").requires(adminRequire())
                        .then(Commands.argument("day", IntegerArgumentType.integer(0, PermadeathCalendar.FINAL_DAY))
                                .executes(PermadeathCommands::changeDay)))
                .then(Commands.literal("setday").requires(adminRequire())
                        .then(Commands.argument("day", IntegerArgumentType.integer(0, PermadeathCalendar.FINAL_DAY))
                                .executes(PermadeathCommands::changeDay)))
                .then(Commands.literal("reload").requires(adminRequire()).executes(PermadeathCommands::reload))
                .then(Commands.literal("reset").requires(adminRequire()).executes(PermadeathCommands::reset))
                .then(Commands.literal("resetstorm").requires(adminRequire()).executes(PermadeathCommands::resetStorm))
                .then(Commands.literal("mikecrack").requires(adminRequire())
                        .then(Commands.literal("enable").executes(ctx -> mikecrack(ctx, true)))
                        .then(Commands.literal("disable").executes(ctx -> mikecrack(ctx, false))))
                .then(Commands.literal("mensaje")
                        .executes(PermadeathCommands::messageStatus)
                        .then(Commands.literal("set").requires(adminRequire())
                                .then(Commands.argument("jugador", EntityArgument.player())
                                        .then(Commands.argument("texto", StringArgumentType.greedyString())
                                                .executes(PermadeathCommands::messageSetOther))))
                        .then(Commands.argument("texto", StringArgumentType.greedyString())
                                .executes(PermadeathCommands::messageSet)))
                .then(Commands.literal("server").requires(adminRequire()).executes(PermadeathCommands::serverToggle))
                .then(Commands.literal("maldicion").requires(adminRequire())
                        .then(Commands.argument("jugador", EntityArgument.player()).executes(PermadeathCommands::curse)))
                .then(Commands.literal("survival").executes(PermadeathCommands::survival))
                .then(Commands.literal("debug").requires(adminRequire()).executes(PermadeathCommands::debug)));
    }

    private static void reply(CommandContext<CommandSourceStack> ctx, String text, boolean broadcastToOps) {
        ctx.getSource().sendSuccess(() -> Component.literal(text), broadcastToOps);
    }

    private static int fail(CommandContext<CommandSourceStack> ctx, String text) {
        ctx.getSource().sendFailure(Component.literal(text));
        return 0;
    }

    private static boolean notRunning(CommandContext<CommandSourceStack> ctx) {
        if (Permadeath.isRunning()) {
            return false;
        }
        fail(ctx, "§cEl calendario de Permadeath no está activo.");
        return true;
    }

    // ------------------------------------------------------------------------------------------------ info

    private static int info(CommandContext<CommandSourceStack> ctx) {
        if (notRunning(ctx)) {
            return 0;
        }
        String calendar = switch (Permadeath.mode()) {
            case GAME60 -> "GAME60 (1 día Permadeath = 1 día de Minecraft)";
            case REAL30 -> "REAL30 (1 día Permadeath = 12 h reales, D60 = 720 h)";
        };
        reply(ctx, "§6=== Permadeath ===\n§7Modo: Hardcore Permadeath\n§7Calendario: §f" + calendar
                + "\n§7Día actual: §f" + Permadeath.day() + "§7/" + PermadeathCalendar.FINAL_DAY
                + "\n§7\n§7Comandos disponibles:"
                + "\n§e/permadeath info §7- Información del mod"
                + "\n§e/permadeath status §7- Día actual, fase y próximos hitos (alias: days)"
                + "\n§e/permadeath BeginningLocation §7- Coords del portal a The Beginning"
                + "\n§e/permadeath mensaje <texto> §7- Cambia tu mensaje personal al morir"
                + "\n§e/permadeath survival §7- Progreso del logro de supervivencia"
                + "\n§e/permadeath setday <día> §7- (OP) Cambiar día (0-60, alias: changeday)"
                + "\n§e/permadeath reload §7- (OP) Reinicia la fase actual"
                + "\n§e/permadeath reset §7- (OP) Vuelve al día 0"
                + "\n§e/permadeath resetstorm §7- (OP) Termina la tormenta"
                + "\n§e/permadeath mikecrack enable|disable §7- (OP) Cambio de Mikecrack (día 60)"
                + "\n§e/permadeath mensaje set <jugador> <texto> §7- (OP) Cambia el mensaje de otro"
                + "\n§e/permadeath server §7- (OP) Activa/desactiva el modo restringido"
                + "\n§e/permadeath maldicion <jugador> §7- (OP) Maldice al último en entrar a The Beginning"
                + "\n§e/permadeath debug §7- (OP) Estado interno", false);
        return 1;
    }

    private static int status(CommandContext<CommandSourceStack> ctx) {
        if (notRunning(ctx)) {
            return 0;
        }
        StringBuilder sb = new StringBuilder("§6=== Permadeath ===\n§7");
        sb.append(Permadeath.clock().describe().replace("\n", "\n§7"));
        if (DeathTrain.isActive()) {
            sb.append("\n§cDeath Train activo: quedan ").append(TimeFormat.hms(DeathTrain.remainingMillis()));
        }
        reply(ctx, sb.toString(), false);
        return 1;
    }

    private static int beginningLocation(CommandContext<CommandSourceStack> ctx) {
        MinecraftServer server = ctx.getSource().getServer();
        SurvivalAchievementData.get(server).flagBeginningPortalCommandUsed(server);
        PortalState state = PortalState.get(server);
        if (!state.hasSpawned) {
            return fail(ctx, "§cEl portal aún no ha sido generado (día 40 requerido).");
        }
        BlockPos pos = state.getPortalPos();
        if (pos == null) {
            return fail(ctx, "§cLa posición del portal no está guardada (fue generado con una versión anterior del mod). "
                    + "Usa el comando /permadeath reset y espera al día 40 para regenerarlo.");
        }
        MutableComponent button = Component.literal("§e[TP]").setStyle(Style.EMPTY
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tp " + pos.getX() + " " + pos.getY() + " " + pos.getZ()))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("§7Clic para teletransportarte al portal"))));
        MutableComponent message = Component.literal("§6Portal hacia The Beginning:\n§7Coordenadas: §f" + pos.toShortString()
                + "\n§7(Click para teletransportarse)  ").append(button);
        ctx.getSource().sendSuccess(() -> message, false);
        return 1;
    }

    // ------------------------------------------------------------------------------------------------ admin

    private static int changeDay(CommandContext<CommandSourceStack> ctx) {
        if (notRunning(ctx)) {
            return 0;
        }
        int target = IntegerArgumentType.getInteger(ctx, "day");
        MinecraftServer server = ctx.getSource().getServer();
        SurvivalAchievementData.get(server).flagChangeDayUsed(server);
        Permadeath.clock().setDay(target);
        Permadeath.data().setDirty();
        DayController.onServerTick(server);
        reply(ctx, "§a✓ Día cambiado a: §f" + target + "\n§7Fase: §f" + PhaseManager.currentName(), true);
        Texts.broadcast(server, "§6⚠ El servidor ha cambiado al día " + target);
        return 1;
    }

    private static int reload(CommandContext<CommandSourceStack> ctx) {
        if (notRunning(ctx)) {
            return 0;
        }
        MinecraftServer server = ctx.getSource().getServer();
        PhaseManager.reload(server);
        DayController.onServerTick(server);
        reply(ctx, "§a✓ Fase recargada\n§7Día actual: §f" + Permadeath.day() + "\n§7Fase: §f" + PhaseManager.currentName(), true);
        return 1;
    }

    private static int reset(CommandContext<CommandSourceStack> ctx) {
        if (notRunning(ctx)) {
            return 0;
        }
        MinecraftServer server = ctx.getSource().getServer();
        SurvivalAchievementData.get(server).flagStormResetUsed(server);
        clearDeathTags(server);
        Permadeath.clock().setDay(0);
        Permadeath.data().setDirty();
        DeathTrain.reset(server);
        PhaseManager.reload(server);
        DayController.onServerTick(server);
        reply(ctx, "§a✓ Permadeath reseteado correctamente (día 0)\n§7Tags de muerte y tormenta limpiados", true);
        return 1;
    }

    private static int resetStorm(CommandContext<CommandSourceStack> ctx) {
        if (notRunning(ctx)) {
            return 0;
        }
        MinecraftServer server = ctx.getSource().getServer();
        SurvivalAchievementData.get(server).flagStormResetUsed(server);
        clearDeathTags(server);
        DeathTrain.reset(server);
        reply(ctx, "§a✓ Permadeath tormenta reseteado correctamente\n§7Tags de muerte y tormenta limpiados", true);
        return 1;
    }

    private static void clearDeathTags(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.removeTag(DeathHandler.DEATH_TAG);
        }
    }

    private static int mikecrack(CommandContext<CommandSourceStack> ctx, boolean enable) {
        if (notRunning(ctx)) {
            return 0;
        }
        int day = Permadeath.day();
        if (day < PermadeathCalendar.FINAL_DAY) {
            return fail(ctx, "§cEl cambio de Mikecrack solo puede " + (enable ? "activarse" : "desactivarse")
                    + " a partir del día §f60§c.\n§7Día actual: §f" + day);
        }
        if (Mikecrack.isEnabled() == enable) {
            return fail(ctx, enable ? "§eEl cambio de Mikecrack ya está activo." : "§eEl cambio de Mikecrack ya está desactivado.");
        }
        Mikecrack.setEnabled(enable);
        if (enable) {
            reply(ctx, "§a Cambio de Mikecrack ACTIVADO\n", true);
            Texts.broadcast(ctx.getSource().getServer(), "§c ¡CAMBIO DE MIKECRACK ACTIVADO!\n");
        } else {
            reply(ctx, "§a Cambio de Mikecrack DESACTIVADO\n", true);
        }
        return 1;
    }

    // ------------------------------------------------------------------------------------------------ messages

    private static int messageStatus(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) {
            return fail(ctx, "§cEste comando solo puede ser usado por un jugador.");
        }
        String current = CustomMessagesData.get(ctx.getSource().getServer()).getMessage(player.getUUID());
        reply(ctx, "§7Tu mensaje personal al morir es:\n§f\"" + current + "\"\n§7Usa §e/permadeath mensaje <texto> §7para cambiarlo.", false);
        return 1;
    }

    private static int messageSet(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) {
            return fail(ctx, "§cEste comando solo puede ser usado por un jugador.");
        }
        String text = StringArgumentType.getString(ctx, "texto");
        if (text.length() > MAX_MESSAGE_LENGTH) {
            return fail(ctx, "§cEl mensaje es demasiado largo (máximo 100 caracteres).");
        }
        CustomMessagesData.get(ctx.getSource().getServer()).setMessage(player.getUUID(), text);
        reply(ctx, "§a✓ Tu mensaje personal al morir ahora es:\n§f\"" + text + "\"", false);
        return 1;
    }

    private static int messageSetOther(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "jugador");
        String text = StringArgumentType.getString(ctx, "texto");
        if (text.length() > MAX_MESSAGE_LENGTH) {
            return fail(ctx, "§cEl mensaje es demasiado largo (máximo 100 caracteres).");
        }
        CustomMessagesData.get(ctx.getSource().getServer()).setMessage(target.getUUID(), text);
        reply(ctx, "§a✓ Mensaje personal de " + target.getName().getString() + " actualizado a:\n§f\"" + text + "\"", true);
        target.sendSystemMessage(Component.literal("§7Un administrador cambió tu mensaje personal al morir a:\n§f\"" + text + "\""));
        return 1;
    }

    private static int serverToggle(CommandContext<CommandSourceStack> ctx) {
        ServerModeData data = ServerModeData.get(ctx.getSource().getServer());
        boolean restricted = !data.isRestricted();
        data.setRestricted(restricted);
        reply(ctx, restricted
                ? "§a✓ Modo servidor RESTRINGIDO activado.\n§7Los jugadores sin OP solo verán/podrán usar §e/permadeath mensaje§7."
                : "§a✓ Modo servidor restringido DESACTIVADO.\n§7Los jugadores sin OP pueden consultar los comandos informativos de Permadeath.", true);
        // Command trees are filtered per player: resend them so the client sees the change immediately.
        for (ServerPlayer player : ctx.getSource().getServer().getPlayerList().getPlayers()) {
            ctx.getSource().getServer().getCommands().sendCommands(player);
        }
        return 1;
    }

    private static int curse(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        if (notRunning(ctx)) {
            return 0;
        }
        int day = Permadeath.day();
        if (day < 50) {
            return fail(ctx, "§cLa maldición solo puede usarse a partir del día §f50§c.\n§7Día actual: §f" + day);
        }
        ServerPlayer target = EntityArgument.getPlayer(ctx, "jugador");
        MinecraftServer server = ctx.getSource().getServer();
        BeginningCurseData.get(server).curse(target.getUUID(), CURSE_DURATION_MILLIS);
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, CURSE_DURATION_TICKS, 0, false, true, true));
        target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, CURSE_DURATION_TICKS, 0, false, true, true));
        Texts.broadcast(server, "§c[PERMADEATH] §d" + target.getGameProfile().getName()
                + ", ¡Desgracia! Has recibido la maldición de The Beginning por entrar el último. ¡Sufre y muere por lento! "
                + "NO puedes usar cubos de leche durante 12 horas dentro de Permadeath o morirás al instante.");
        reply(ctx, "§a✓ Maldición aplicada a " + target.getGameProfile().getName() + " (12h reales, sin leche).", true);
        return 1;
    }

    private static int survival(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) {
            return fail(ctx, "§cEste comando solo lo puede usar un jugador.");
        }
        String info = SurvivalAchievementData.get(ctx.getSource().getServer()).progressInfo(player, Permadeath.day());
        reply(ctx, info, false);
        return 1;
    }

    private static int debug(CommandContext<CommandSourceStack> ctx) {
        if (notRunning(ctx)) {
            return 0;
        }
        ProgressionState s = Permadeath.state();
        long now = Permadeath.nowMillis();
        StringBuilder sb = new StringBuilder("§6=== Permadeath debug ===");
        sb.append("\n§7mode=§f").append(s.mode).append(" §7formatVersion=§f").append(s.formatVersion)
                .append(" §7legacyMigrated=§f").append(s.legacyMigrated);
        sb.append("\n§7day=§f").append(Permadeath.day()).append(" §7maxEffectiveDay=§f").append(s.maxEffectiveDay)
                .append(" §7phase=§f").append(PhaseManager.currentPhase()).append(" (").append(PhaseManager.currentName()).append(')');
        sb.append("\n§7baseWorldDay=§f").append(s.baseWorldDay).append(" §7overworldDayTime=§f")
                .append(ctx.getSource().getServer().overworld().getDayTime());
        if (s.startEpochMillis > 0L) {
            sb.append("\n§7start=§f").append(TimeFormat.utc(Instant.ofEpochMilli(s.startEpochMillis)))
                    .append(" §7maxElapsed=§f").append(TimeFormat.realDuration(Duration.ofMillis(s.maxElapsedMillis)));
        }
        sb.append("\n§7executedMilestones=§f").append(s.executedMilestones);
        sb.append("\n§7deathTrain=§f").append(DeathTrain.isActive() ? TimeFormat.hms(DeathTrain.remainingMillis()) : "off")
                .append(" §7uhc=§f").append(s.deathTrainUhcActive);
        sb.append("\n§7lifeOrb: deadline=§f").append(s.lifeOrbDeadlineEpochMillis < 0L ? "-" : TimeFormat.utc(Instant.ofEpochMilli(s.lifeOrbDeadlineEpochMillis)))
                .append(" §7active=§f").append(s.lifeOrbActive);
        for (Map.Entry<UUID, Long> e : s.witherRemainingMillis.entrySet()) {
            sb.append("\n§7wither[").append(e.getKey()).append("]=§f").append(TimeFormat.hms(e.getValue()));
        }
        sb.append("\n§7mikecrack=§f").append(s.mikecrackEnabled).append(" §7endArenaPrepared=§f").append(s.endArenaPrepared)
                .append(" §7now=§f").append(TimeFormat.utc(Instant.ofEpochMilli(now)));
        reply(ctx, sb.toString(), false);
        return 1;
    }
}
