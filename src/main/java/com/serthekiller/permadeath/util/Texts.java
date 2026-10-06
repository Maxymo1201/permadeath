package com.serthekiller.permadeath.util;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

/** Chat helpers (the Fabric mod used legacy '§' colour codes; they are kept verbatim). */
public final class Texts {
    private Texts() {
    }

    public static void broadcast(MinecraftServer server, String legacyText) {
        server.getPlayerList().broadcastSystemMessage(Component.literal(legacyText), false);
    }

    public static void broadcast(MinecraftServer server, Component component) {
        server.getPlayerList().broadcastSystemMessage(component, false);
    }
}
