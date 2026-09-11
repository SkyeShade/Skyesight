package com.skyeshade.skyesight.client.render;

/** Per-thread override while an isolated terrain backend sizes/updates its own renderer. */
public final class SkyesightViewDistanceScope implements AutoCloseable {
    private static final ThreadLocal<Integer> CURRENT = new ThreadLocal<>();
    private final Integer previous;
    public SkyesightViewDistanceScope(int radius) {
        previous = CURRENT.get();
        CURRENT.set(Math.max(1, radius));
    }
    public static int resolve(int physicalDistance) {
        Integer radius = CURRENT.get();
        return radius == null ? physicalDistance : radius;
    }
    @Override public void close() {
        if (previous == null) CURRENT.remove(); else CURRENT.set(previous);
    }
}
