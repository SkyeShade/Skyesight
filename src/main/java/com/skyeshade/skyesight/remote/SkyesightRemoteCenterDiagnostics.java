package com.skyeshade.skyesight.remote;

import com.skyeshade.skyesight.Skyesight;
import net.minecraft.resources.ResourceLocation;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** Opt-in center traces. Sequence IDs are diagnostic only, never request acceptance state. */
public final class SkyesightRemoteCenterDiagnostics {
    private static final boolean ENABLED = Boolean.getBoolean("skyesight.debug.remoteCenters");
    private static final int MAX_KEYS = 512;
    private static final ConcurrentHashMap<String, Stamp> LAST = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<ResourceLocation, Stamp> RESPONSES = new ConcurrentHashMap<>();
    private static final AtomicLong SEQUENCE = new AtomicLong();

    private SkyesightRemoteCenterDiagnostics() {}

    public static long nextSequence() { return SEQUENCE.incrementAndGet(); }
    public static boolean enabled() { return ENABLED; }

    public static boolean due(String phase, ResourceLocation view, long generation) {
        if (!ENABLED) return false;
        String key = phase + "/" + view;
        long now = System.nanoTime();
        Stamp previous = LAST.get(key);
        if (previous != null && previous.generation == generation && now - previous.value < 2_000_000_000L) return false;
        if (LAST.size() >= MAX_KEYS) LAST.clear();
        LAST.put(key, new Stamp(generation, now));
        return true;
    }

    /** Sample the first packet of each response batch, without retaining retired generations. */
    public static boolean firstResponse(ResourceLocation view, long generation, long sequence) {
        if (!ENABLED) return false;
        if (RESPONSES.size() >= MAX_KEYS) RESPONSES.clear();
        Stamp previous = RESPONSES.put(view, new Stamp(generation, sequence));
        return previous == null || previous.generation != generation || previous.value != sequence;
    }

    public static void trace(String phase, ResourceLocation view, long generation, long sequence, String detail) {
        if (ENABLED) Skyesight.LOGGER.info("{} view={} generation={} sequence={} {}", phase, view, generation, sequence, detail);
    }

    public static void log(String phase, ResourceLocation view, long generation, String detail) {
        if (ENABLED) Skyesight.LOGGER.info("REMOTE_VIEW_CENTER phase={} view={} generation={} {}", phase, view, generation, detail);
    }

    private record Stamp(long generation, long value) {}
}
