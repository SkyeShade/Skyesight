package com.skyeshade.skyesight;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import java.util.HashSet;
import java.util.Set;

public final class PortalFirstUseTimeline {
    private static final long START_NANOS = System.nanoTime();
    private static final Set<String> LOGGED_ONCE = new HashSet<>();
    private static long frame = -1L;

    private PortalFirstUseTimeline() {}

    public static void setFrame(long frameId) {
        frame = frameId;
    }

    public static void log(String event, ResourceLocation viewId, String details) {
        if (!SkyesightDebugConfig.VERBOSE_RENDER && !SkyesightDebugConfig.WATCH_DEBUG) {
            return;
        }
        Skyesight.LOGGER.info(
                "[Skyesight] PORTAL_FIRST_USE_TIMELINE: event={} tick={} frame={} timeMs={} viewId={} details={}",
                event == null ? "-" : event,
                currentTick(),
                frame,
                (System.nanoTime() - START_NANOS) / 1_000_000L,
                viewId == null ? "-" : viewId,
                details == null || details.isBlank() ? "-" : details
        );
    }

    public static void logOnce(String key, String event, ResourceLocation viewId, String details) {
        if (LOGGED_ONCE.add(key)) {
            log(event, viewId, details);
        }
    }

    public static long timedStart(String event, ResourceLocation viewId, String details) {
        log(event, viewId, details);
        return System.nanoTime();
    }

    public static long timedStart(String key, String event, ResourceLocation viewId, String details) {
        if (LOGGED_ONCE.add(key)) {
            log(event, viewId, details);
        }
        return System.nanoTime();
    }

    public static void timedEnd(String event, ResourceLocation viewId, long startNanos, String details) {
        long durationMs = (System.nanoTime() - startNanos) / 1_000_000L;
        log(event, viewId, (details == null || details.isBlank() ? "" : details + " ") + "durationMs=" + durationMs);
    }

    public static void timedEnd(String key, String event, ResourceLocation viewId, long startNanos, String details) {
        if (!LOGGED_ONCE.add(key)) {
            return;
        }
        timedEnd(event, viewId, startNanos, details);
    }

    private static long currentTick() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft == null || minecraft.level == null ? -1L : minecraft.level.getGameTime();
    }
}
