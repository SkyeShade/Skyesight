package com.skyeshade.skyesight.client.render.slice;

/** Internal renderer policy. A clip-only parent cannot be re-enabled by a nested draw. */
public final class CapContributionScope implements AutoCloseable {
    private static final ThreadLocal<Boolean> CLIP_ONLY = new ThreadLocal<>();
    private final Boolean previous;
    private boolean closed;

    private CapContributionScope() {
        previous=CLIP_ONLY.get();
        CLIP_ONLY.set(true);
    }

    public static CapContributionScope clipOnly() { return new CapContributionScope(); }
    public static boolean producesCap() { return !Boolean.TRUE.equals(CLIP_ONLY.get()); }

    @Override public void close() {
        if(closed) return;
        closed=true;
        if(previous==null) CLIP_ONLY.remove(); else CLIP_ONLY.set(previous);
    }
}
