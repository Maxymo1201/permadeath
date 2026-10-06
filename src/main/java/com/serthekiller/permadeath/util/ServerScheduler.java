package com.serthekiller.permadeath.util;

import com.serthekiller.permadeath.PermadeathMod;

import java.util.ArrayList;
import java.util.List;

/**
 * Tick based delayed tasks executed on the server thread (replaces the Fabric mod's
 * {@code new Thread(() -> { Thread.sleep(..); server.execute(..) })} pattern). Tasks never touch the world
 * from another thread.
 */
public final class ServerScheduler {
    private static final List<Task> TASKS = new ArrayList<>();
    private static final List<Task> PENDING = new ArrayList<>();
    private static long tick;

    private ServerScheduler() {
    }

    private record Task(long runAt, Runnable action) {
    }

    /** Runs {@code action} on the server thread after {@code delayTicks} server ticks (0 = end of this tick). */
    public static void schedule(int delayTicks, Runnable action) {
        PENDING.add(new Task(tick + Math.max(0, delayTicks), action));
    }

    public static void onServerTick() {
        tick++;
        if (!PENDING.isEmpty()) {
            TASKS.addAll(PENDING);
            PENDING.clear();
        }
        if (TASKS.isEmpty()) {
            return;
        }
        List<Task> due = new ArrayList<>();
        TASKS.removeIf(t -> {
            if (t.runAt <= tick) {
                due.add(t);
                return true;
            }
            return false;
        });
        for (Task t : due) {
            try {
                t.action.run();
            } catch (RuntimeException e) {
                PermadeathMod.LOGGER.error("[Permadeath] Scheduled task failed", e);
            }
        }
    }

    public static void clear() {
        TASKS.clear();
        PENDING.clear();
        tick = 0;
    }
}
