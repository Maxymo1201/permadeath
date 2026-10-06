package com.serthekiller.permadeath.mechanics;

import com.serthekiller.permadeath.data.ServerModeData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

/** Message shown on join (Fabric WelcomeMessageHandler; the command list matches the port's commands). */
public final class WelcomeMessage {
    private WelcomeMessage() {
    }

    public static void send(ServerPlayer player) {
        boolean restricted = ServerModeData.get(player.server).isRestricted();
        boolean operator = player.hasPermissions(2);
        player.sendSystemMessage(Component.empty());
        player.sendSystemMessage(Component.literal("§8§m                                                    "));
        player.sendSystemMessage(Component.literal("¡Bienvenido a ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal("PERMADEATH").withStyle(ChatFormatting.RED, ChatFormatting.BOLD))
                .append(Component.literal("!").withStyle(ChatFormatting.GRAY)));
        player.sendSystemMessage(Component.literal("Creado por ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal("serthekiller").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)));
        player.sendSystemMessage(Component.empty());
        if (operator || !restricted) {
            player.sendSystemMessage(Component.literal("Comandos disponibles:").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD));
            player.sendSystemMessage(commandLine("/permadeath status", "Ver el día actual y la fase"));
            if (operator) {
                player.sendSystemMessage(commandLine("/permadeath setday", "Cambiar de día/fase (OP)"));
            }
            player.sendSystemMessage(commandLine("/permadeath info", "Lista de comandos"));
            player.sendSystemMessage(commandLine("/permadeath mensaje", "Cambiar tu mensaje personal al morir"));
        } else {
            player.sendSystemMessage(Component.literal("Comando disponible:").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD));
            player.sendSystemMessage(commandLine("/permadeath mensaje", "Cambiar tu mensaje personal al morir"));
        }
        player.sendSystemMessage(Component.empty());
        player.sendSystemMessage(Component.literal("Suscríbete a su canal: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal("youtube.com/@serthekiller").withStyle(style -> style.withColor(ChatFormatting.AQUA)
                        .withUnderlined(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, "https://www.youtube.com/@serthekiller"))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Abrir canal"))))));
        player.sendSystemMessage(Component.literal("§8§m                                                    "));
        player.sendSystemMessage(Component.empty());
    }

    private static MutableComponent commandLine(String command, String description) {
        return Component.literal(" » ").withStyle(ChatFormatting.DARK_GRAY)
                .append(Component.literal(command).withStyle(style -> style.withColor(ChatFormatting.GREEN)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, command))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Click para usar")))))
                .append(Component.literal(" - " + description).withStyle(ChatFormatting.GRAY));
    }
}
