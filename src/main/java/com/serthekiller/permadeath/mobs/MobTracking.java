package com.serthekiller.permadeath.mobs;

import net.minecraft.world.entity.Mob;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Port of the Fabric MobProcessingTracker: "session" claims live only in memory (lost on restart, so AI
 * goals are re-injected after a reload), persistent claims are stored as entity tags {@code pmd_proc_<key>}.
 */
public final class MobTracking {
    private static final String TAG_PREFIX = "pmd_proc_";
    private static final Map<Mob, Set<String>> SESSION = new WeakHashMap<>();

    private MobTracking() {
    }

    public static boolean isSessionProcessed(Mob mob, String key) {
        Set<String> tags = SESSION.get(mob);
        return tags != null && tags.contains(key);
    }

    public static boolean tryClaimSession(Mob mob, String key) {
        return SESSION.computeIfAbsent(mob, k -> new HashSet<>()).add(key);
    }

    public static void clearSession(Mob mob, String key) {
        Set<String> tags = SESSION.get(mob);
        if (tags != null) {
            tags.remove(key);
        }
    }

    public static boolean isProcessed(Mob mob, String key) {
        return mob.getTags().contains(TAG_PREFIX + key);
    }

    public static void markProcessed(Mob mob, String key) {
        mob.addTag(TAG_PREFIX + key);
    }

    public static boolean tryClaim(Mob mob, String key) {
        if (isProcessed(mob, key)) {
            return false;
        }
        markProcessed(mob, key);
        return true;
    }

    public static void clearAll() {
        SESSION.clear();
    }
}
