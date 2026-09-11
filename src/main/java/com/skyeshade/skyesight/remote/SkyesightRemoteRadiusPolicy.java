package com.skyeshade.skyesight.remote;

/** Chunk units throughout. No dimension, player or view state is stored here. */
public final class SkyesightRemoteRadiusPolicy {
    private SkyesightRemoteRadiusPolicy() {}
    public static int accepted(int requested, int serverMaximum) {
        return Math.max(0, Math.min(requested, serverMaximum));
    }
    public static int loadRadius(int drawRadius, int limit) {
        return (int) Math.max(0L, Math.min((long) drawRadius + 1L, limit));
    }
}
