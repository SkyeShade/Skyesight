package com.skyeshade.skyesight.client.render;

/** One-shot composition between cutout and translucent terrain for a primary transition view. */
public final class SecondaryTerrainComposition implements AutoCloseable {
    private static final ThreadLocal<Runnable> PENDING = new ThreadLocal<>();
    private final Runnable previous;
    public SecondaryTerrainComposition(Runnable composition) {
        previous = PENDING.get();
        PENDING.set(composition);
    }
    public static void beforeTranslucent() {
        var composition = PENDING.get();
        if (composition == null) return;
        // Nested destination rendering must not invoke the primary composition again.
        PENDING.remove();
        composition.run();
    }
    @Override public void close() {
        if (previous == null) PENDING.remove(); else PENDING.set(previous);
    }
}
