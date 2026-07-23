package com.skyeshade.skyesight.client.render.sodium;

import com.skyeshade.skyesight.Skyesight;
import com.skyeshade.skyesight.SkyesightDebugConfig;
import com.skyeshade.skyesight.PortalFirstUseTimeline;
import net.caffeinemc.mods.sodium.client.gl.device.RenderDevice;
import net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayDeque;
import java.util.Deque;

public final class PortalSodiumRendererPool {
    private static final Deque<SodiumWorldRenderer> AVAILABLE = new ArrayDeque<>();
    private static ClientLevel level;
    private static int renderFramesSinceLevel;
    private static boolean prewarmCompleteForLevel;
    private static boolean firstEverPortalTerrainUse = true;

    private PortalSodiumRendererPool() {}

    public static void prewarmIfNeeded(
            Minecraft minecraft,
            int targetSize,
            int delayFrames,
            int maxCreatesPerFrame
    ) {
        if (minecraft == null || minecraft.level == null || targetSize <= 0 || maxCreatesPerFrame <= 0) {
            return;
        }

        resetIfLevelChanged(minecraft.level);
        renderFramesSinceLevel++;
        if (prewarmCompleteForLevel || renderFramesSinceLevel < delayFrames) {
            return;
        }

        int created = 0;
        while (AVAILABLE.size() < targetSize && created < maxCreatesPerFrame) {
            long start = System.nanoTime();
            PortalFirstUseTimeline.logOnce(
                    "portal_sodium_pool_prewarm_start:" + AVAILABLE.size(),
                    "portal_sodium_pool_prewarm_start",
                    null,
                    "targetSize=" + targetSize + " available=" + AVAILABLE.size()
            );
            logPool("prewarm_start", null, false, false, 0L, "prewarm-create-start");
            SodiumWorldRenderer renderer = createRenderer(minecraft, minecraft.level);
            long durationMs = (System.nanoTime() - start) / 1_000_000L;
            if (renderer == null) {
                logPool("prewarm_end", null, false, false, durationMs, "create-failed");
                return;
            }
            AVAILABLE.addLast(renderer);
            created++;
            PortalFirstUseTimeline.logOnce(
                    "portal_sodium_pool_prewarm_end:" + AVAILABLE.size(),
                    "portal_sodium_pool_prewarm_end",
                    null,
                    "durationMs=" + durationMs + " available=" + AVAILABLE.size()
            );
            logPool("prewarm_end", null, true, false, durationMs, "prewarmed-renderer");
        }

        prewarmCompleteForLevel = AVAILABLE.size() >= targetSize;
    }

    public static SodiumWorldRenderer acquire(ResourceLocation viewId, ClientLevel expectedLevel) {
        if (expectedLevel == null || expectedLevel != level || AVAILABLE.isEmpty()) {
            logPool("assign", viewId, false, false, 0L, "no-available-renderer");
            return null;
        }

        long start = System.nanoTime();
        SodiumWorldRenderer renderer = AVAILABLE.removeFirst();
        long durationMs = (System.nanoTime() - start) / 1_000_000L;
        PortalFirstUseTimeline.logOnce(
                "portal_sodium_pool_assign:" + viewId,
                "portal_sodium_pool_assign",
                viewId,
                "availableAfter=" + AVAILABLE.size() + " durationMs=" + durationMs
        );
        logPool("assign", viewId, false, true, durationMs, "assigned-from-pool");
        return renderer;
    }

    public static void logFallbackCreate(ResourceLocation viewId, long durationMs, String reason) {
        logPool("fallback-create", viewId, false, false, durationMs, reason);
    }

    public static void clear() {
        RenderDevice.enterManagedCode();
        try {
            while (!AVAILABLE.isEmpty()) {
                SodiumWorldRenderer renderer = AVAILABLE.removeFirst();
                try {
                    renderer.setLevel(null);
                } catch (RuntimeException exception) {
                    Skyesight.LOGGER.warn("[Skyesight] Failed to release pooled portal Sodium renderer", exception);
                }
            }
        } finally {
            RenderDevice.exitManagedCode();
        }

        level = null;
        renderFramesSinceLevel = 0;
        prewarmCompleteForLevel = false;
        firstEverPortalTerrainUse = true;
    }

    public static int available() {
        return AVAILABLE.size();
    }

    private static void resetIfLevelChanged(ClientLevel currentLevel) {
        if (level == currentLevel) {
            return;
        }

        clear();
        level = currentLevel;
    }

    private static SodiumWorldRenderer createRenderer(Minecraft minecraft, ClientLevel level) {
        long start = System.nanoTime();
        boolean created = false;
        boolean calledSetLevel = false;
        RenderDevice.enterManagedCode();
        try {
            SodiumWorldRenderer renderer = new SodiumWorldRenderer(minecraft);
            created = true;
            renderer.setLevel(level);
            calledSetLevel = true;
            renderer.scheduleTerrainUpdate();
            return renderer;
        } catch (IllegalStateException exception) {
            Skyesight.LOGGER.warn(
                    "[Skyesight] Portal Sodium renderer prewarm skipped; managed render context unavailable",
                    exception
            );
            return null;
        } finally {
            RenderDevice.exitManagedCode();
            logFirstUseInitAudit(
                    "prewarm_create",
                    created,
                    calledSetLevel,
                    (System.nanoTime() - start) / 1_000_000L,
                    created ? "prewarm-renderer-created" : "prewarm-renderer-create-failed"
            );
        }
    }

    public static void logFirstUseInitAudit(
            String phase,
            boolean createdSodiumRenderer,
            boolean calledSetLevel,
            long durationMs,
            String reason
    ) {
        if (!SkyesightDebugConfig.SODIUM_RENDERER_AUDIT) {
            return;
        }
        boolean first = firstEverPortalTerrainUse;
        if (firstEverPortalTerrainUse) {
            firstEverPortalTerrainUse = false;
        }

        Skyesight.LOGGER.info(
                "[Skyesight] PORTAL_FIRST_USE_INIT_AUDIT: phase={} firstEverPortalTerrainUse={} createdSodiumRenderer={} calledSetLevel={} createdChunkBuilderWorkers={} allocatedRenderTarget={} initializedReflection={} durationMs={} reason={}",
                phase == null ? "-" : phase,
                first ? "yes" : "no",
                createdSodiumRenderer ? "yes" : "no",
                calledSetLevel ? "yes" : "no",
                createdSodiumRenderer ? "likely" : "unknown",
                "no",
                "unknown",
                durationMs,
                reason == null || reason.isBlank() ? "-" : reason
        );
    }

    private static void logPool(
            String phase,
            ResourceLocation viewId,
            boolean created,
            boolean assignedFromPool,
            long durationMs,
            String reason
    ) {
        if (!SkyesightDebugConfig.SODIUM_RENDERER_AUDIT) {
            return;
        }
        Skyesight.LOGGER.info(
                "[Skyesight] PORTAL_SODIUM_RENDERER_POOL: phase={} viewId={} poolSize={} available={} created={} assignedFromPool={} createdInRenderStage={} durationMs={} reason={}",
                phase,
                viewId == null ? "-" : viewId,
                AVAILABLE.size(),
                AVAILABLE.size(),
                created ? "yes" : "no",
                assignedFromPool ? "yes" : "no",
                created ? "yes" : "no",
                durationMs,
                reason == null || reason.isBlank() ? "-" : reason
        );
    }
}
