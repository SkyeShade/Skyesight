package com.skyeshade.skyesight.client.render.sodium;

import net.caffeinemc.mods.sodium.client.render.chunk.map.ChunkTracker;

public final class SkyesightSodiumRenderContext {
    private static final ThreadLocal<ChunkTracker> ACTIVE_TRACKER = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> DISABLE_OCCLUSION_CULLING = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> FORCE_RENDER_LIST_CONSTRUCTION = new ThreadLocal<>();

    private SkyesightSodiumRenderContext() {}

    public static Scope push(ChunkTracker tracker) {
        return push(tracker, false);
    }

    public static Scope push(ChunkTracker tracker, boolean disableOcclusionCulling) {
        ACTIVE_TRACKER.set(tracker);
        DISABLE_OCCLUSION_CULLING.set(disableOcclusionCulling);
        return new Scope();
    }

    public static ChunkTracker currentTracker() {
        return ACTIVE_TRACKER.get();
    }

    public static boolean isActive() {
        return ACTIVE_TRACKER.get() != null;
    }

    public static boolean shouldDisableOcclusionCulling() {
        return Boolean.TRUE.equals(DISABLE_OCCLUSION_CULLING.get());
    }

    public static boolean isForceRenderListConstruction() {
        return Boolean.TRUE.equals(FORCE_RENDER_LIST_CONSTRUCTION.get());
    }

    public static Scope pushForceRenderListConstruction() {
        FORCE_RENDER_LIST_CONSTRUCTION.set(true);
        return new Scope(false);
    }

    public static final class Scope implements AutoCloseable {
        private final boolean fullScope;

        private Scope() {
            this(true);
        }

        private Scope(boolean fullScope) {
            this.fullScope = fullScope;
        }

        @Override
        public void close() {
            if (this.fullScope) {
                ACTIVE_TRACKER.remove();
                DISABLE_OCCLUSION_CULLING.remove();
            }

            FORCE_RENDER_LIST_CONSTRUCTION.remove();
        }
    }
}
