package com.skyeshade.skyesight.client.render.sodium;

import com.skyeshade.skyesight.Skyesight;
import com.skyeshade.skyesight.SkyesightDebugConfig;
import com.mojang.blaze3d.systems.RenderSystem;
import it.unimi.dsi.fastutil.longs.Long2ReferenceMap;
import net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer;
import net.caffeinemc.mods.sodium.client.render.chunk.ChunkRenderMatrices;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSection;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager;
import net.caffeinemc.mods.sodium.client.render.chunk.lists.SortedRenderLists;
import net.caffeinemc.mods.sodium.client.render.chunk.lists.VisibleChunkCollector;
import net.caffeinemc.mods.sodium.client.render.chunk.region.RenderRegion;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.DefaultTerrainRenderPasses;
import net.caffeinemc.mods.sodium.client.render.viewport.Viewport;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.opengl.GL11;
import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class SameDimMainSodiumSectionReuse {
    private static Field sodiumWorldRendererSectionManagerField;
    private static Field sodiumSectionManagerSectionByPositionField;
    private static Field sodiumSectionManagerRenderListsField;
    private static boolean reflectionFailed;
    private static final Map<ResourceLocation, Long> LAST_ACTIVE_PATH_LOG_MILLIS = new HashMap<>();
    private static final Map<ResourceLocation, Long> LAST_SUMMARY_LOG_MILLIS = new HashMap<>();
    private static final Map<ResourceLocation, Long> LAST_DUPLICATE_LOG_MILLIS = new HashMap<>();
    private static final Map<ResourceLocation, Long> LAST_EXPERIMENTAL_WARNING_MILLIS = new HashMap<>();
    private static final Map<ResourceLocation, Long> LAST_BORROW_SWAP_AUDIT_MILLIS = new HashMap<>();
    private static final Map<ResourceLocation, Long> LAST_BORROW_SKIP_MILLIS = new HashMap<>();
    private static final Map<ResourceLocation, Long> LAST_MAIN_RESTORE_CHECK_MILLIS = new HashMap<>();
    private static final Map<String, Long> LAST_MAIN_TERRAIN_STATE_AUDIT_MILLIS = new HashMap<>();

    private SameDimMainSodiumSectionReuse() {}

    public static Optional<MainSectionInfo> findMainCompiledSection(ClientLevel level, SectionPos sectionPos) {
        if (level == null || sectionPos == null) {
            return Optional.empty();
        }

        SodiumWorldRenderer mainRenderer = SodiumWorldRenderer.instanceNullable();
        if (mainRenderer == null) {
            return Optional.empty();
        }

        RenderSection section = findSection(mainRenderer, sectionPos);
        if (section == null || !section.isBuilt()) {
            return Optional.empty();
        }

        return Optional.of(MainSectionInfo.from(section));
    }

    public static boolean isPortalSectionCompiledForAnyBorrowablePass(
            SodiumWorldRenderer portalRenderer,
            SectionPos sectionPos
    ) {
        RenderSection section = findSection(portalRenderer, sectionPos);
        if (section == null || !section.isBuilt()) {
            return false;
        }

        SectionPassInfo info = inspect(section);
        return info.solid() || info.cutout();
    }

    public static SectionAvailability sectionAvailability(SodiumWorldRenderer renderer, SectionPos sectionPos) {
        return SectionAvailability.from(inspect(findSection(renderer, sectionPos)));
    }

    public static Audit audit(
            ResourceLocation viewId,
            ClientLevel level,
            SodiumWorldRenderer portalRenderer,
            Vec3 portalCameraPos,
            int portalOwnedRadius,
            int reuseRadius,
            boolean reuseEnabled
    ) {
        if (level == null || portalRenderer == null || portalCameraPos == null || !reuseEnabled) {
            return Audit.empty(viewId);
        }

        SodiumWorldRenderer mainRenderer = SodiumWorldRenderer.instanceNullable();
        if (mainRenderer == null || mainRenderer == portalRenderer) {
            return Audit.empty(viewId);
        }

        ChunkPos centerChunk = new ChunkPos(net.minecraft.core.BlockPos.containing(portalCameraPos));
        int minSection = level.getMinSection();
        int maxSection = level.getMaxSection();
        int ownedRadius = Math.max(0, portalOwnedRadius);
        int checkedRadius = Math.max(ownedRadius, reuseRadius);

        int candidateSections = 0;
        int mainCompiledAvailable = 0;
        int portalCompiledAvailable = 0;
        int bothCompiled = 0;
        int mainOnlyCompiled = 0;
        int portalOnlyCompiled = 0;
        int neitherCompiled = 0;
        int borrowableSolid = 0;
        int borrowableCutout = 0;
        int borrowableTranslucent = 0;

        for (int chunkZ = centerChunk.z - checkedRadius; chunkZ <= centerChunk.z + checkedRadius; chunkZ++) {
            for (int chunkX = centerChunk.x - checkedRadius; chunkX <= centerChunk.x + checkedRadius; chunkX++) {
                int chunkDistance = Math.max(Math.abs(chunkX - centerChunk.x), Math.abs(chunkZ - centerChunk.z));
                if (chunkDistance <= ownedRadius || chunkDistance > checkedRadius) {
                    continue;
                }
                if (level.getChunkSource().getChunk(chunkX, chunkZ, false) == null) {
                    continue;
                }

                for (int sectionY = minSection; sectionY < maxSection; sectionY++) {
                    candidateSections++;
                    SectionPos sectionPos = SectionPos.of(chunkX, sectionY, chunkZ);
                    SectionPassInfo mainInfo = inspect(findSection(mainRenderer, sectionPos));
                    SectionPassInfo portalInfo = inspect(findSection(portalRenderer, sectionPos));
                    boolean mainBorrowable = mainInfo.solid() || mainInfo.cutout();
                    boolean portalBorrowable = portalInfo.solid() || portalInfo.cutout();

                    if (mainBorrowable) {
                        mainCompiledAvailable++;
                    }
                    if (portalBorrowable) {
                        portalCompiledAvailable++;
                    }
                    if (mainBorrowable && portalBorrowable) {
                        bothCompiled++;
                    } else if (mainBorrowable) {
                        mainOnlyCompiled++;
                    } else if (portalBorrowable) {
                        portalOnlyCompiled++;
                    } else {
                        neitherCompiled++;
                    }
                    if (mainInfo.solid() && !portalInfo.solid()) {
                        borrowableSolid++;
                    }
                    if (mainInfo.cutout() && !portalInfo.cutout()) {
                        borrowableCutout++;
                    }
                    if (mainInfo.translucent() && !portalInfo.translucent()) {
                        borrowableTranslucent++;
                    }
                }
            }
        }

        return new Audit(
                viewId,
                candidateSections,
                mainCompiledAvailable,
                portalCompiledAvailable,
                bothCompiled,
                mainOnlyCompiled,
                portalOnlyCompiled,
                neitherCompiled,
                borrowableSolid,
                borrowableCutout,
                borrowableTranslucent
        );
    }

    public static BorrowDrawResult drawBorrowedSolidCutoutSections(
            ResourceLocation viewId,
            ClientLevel level,
            SodiumWorldRenderer portalRenderer,
            Vec3 portalCameraPos,
            int portalOwnedRadius,
            int reuseRadius,
            boolean reuseEnabled,
            Viewport viewport,
            ChunkRenderMatrices matrices,
            boolean screenOpen,
            boolean mainFramebufferBound,
            boolean portalStencilActive,
            boolean experimentalWarningEnabled
    ) {
        if (screenOpen) {
            logBorrowSkippedIfDue(viewId, "screen-open");
            return BorrowDrawResult.implementedNotDrawn();
        }
        if (level == null
                || portalRenderer == null
                || portalCameraPos == null
                || viewport == null
                || matrices == null
                || !reuseEnabled) {
            return BorrowDrawResult.implementedNotDrawn();
        }

        SodiumWorldRenderer mainRenderer = SodiumWorldRenderer.instanceNullable();
        if (mainRenderer == null || mainRenderer == portalRenderer) {
            return BorrowDrawResult.implementedNotDrawn();
        }

        try {
            RenderSectionManager portalManager = sectionManager(portalRenderer);
            if (portalManager == null) {
                return BorrowDrawResult.implementedNotDrawn();
            }
            int mainRenderListsExpectedIdentity = renderListsIdentity(mainRenderer);
            if (experimentalWarningEnabled) {
                logExperimentalWarningIfDue(viewId);
            }

            BorrowLists borrowLists = collectBorrowLists(
                    level,
                    mainRenderer,
                    portalRenderer,
                    portalCameraPos,
                    portalOwnedRadius,
                    reuseRadius,
                    viewport
            );

            int solidDrawn = drawBorrowedPass(
                    viewId,
                    portalRenderer,
                    portalManager,
                    borrowLists.solidLists(),
                    RenderType.solid(),
                    matrices,
                    portalCameraPos,
                    "solid",
                    screenOpen,
                    mainFramebufferBound,
                    portalStencilActive,
                    mainRenderListsExpectedIdentity
            );
            int cutoutMippedDrawn = drawBorrowedPass(
                    viewId,
                    portalRenderer,
                    portalManager,
                    borrowLists.cutoutLists(),
                    RenderType.cutoutMipped(),
                    matrices,
                    portalCameraPos,
                    "cutout_mipped",
                    screenOpen,
                    mainFramebufferBound,
                    portalStencilActive,
                    mainRenderListsExpectedIdentity
            );
            int cutoutDrawn = drawBorrowedPass(
                    viewId,
                    portalRenderer,
                    portalManager,
                    borrowLists.cutoutLists(),
                    RenderType.cutout(),
                    matrices,
                    portalCameraPos,
                    "cutout",
                    screenOpen,
                    mainFramebufferBound,
                    portalStencilActive,
                    mainRenderListsExpectedIdentity
            );
            logMainRenderListRestoreCheckIfDue(viewId, screenOpen, mainRenderListsExpectedIdentity);

            return new BorrowDrawResult(
                    true,
                    solidDrawn,
                    Math.max(cutoutMippedDrawn, cutoutDrawn),
                    0
            );
        } catch (ReflectiveOperationException | RuntimeException exception) {
            Skyesight.LOGGER.warn(
                    "[Skyesight] Same-dim main section borrowed draw failed viewId={}",
                    viewId == null ? "-" : viewId,
                    exception
            );
            return BorrowDrawResult.implementedNotDrawn();
        }
    }

    public static void logSummaryIfDue(
            Audit audit,
            boolean experimentalBorrowDrawEnabled,
            boolean scheduledPortalCompile,
            BorrowDrawResult borrowDrawResult,
            int primerPrimedTotal,
            int primerQueuedCompile
    ) {
        if (audit == null || audit.candidateSections() <= 0) {
            return;
        }

        long now = System.currentTimeMillis();
        if (!shouldLog(LAST_SUMMARY_LOG_MILLIS, audit.viewId(), now)) {
            return;
        }

        Skyesight.LOGGER.info(
                "[Skyesight] PORTAL_MAIN_SECTION_REUSE_SUMMARY: viewId={} candidateSections={} mainCompiledAvailable={} portalCompiledAvailable={} bothCompiled={} mainOnlyCompiled={} portalOnlyCompiled={} neitherCompiled={} scheduledPortalCompiles={} borrowableSolid={} borrowableCutout={} borrowableTranslucent={} borrowedSectionsDrawnSolid={} borrowedSectionsDrawnCutout={} borrowedSectionsDrawnTranslucent={} duplicateDrawAvoided={} reuseHitRate={} borrowAuditEnabled={} primerPrimedTotal={} primerQueuedCompile={} postDrawSeedEnabled={} experimentalBorrowDrawEnabled={} borrowedDrawingImplemented={} reason={}",
                audit.viewId() == null ? "-" : audit.viewId(),
                audit.candidateSections(),
                audit.mainCompiledAvailable(),
                audit.portalCompiledAvailable(),
                audit.bothCompiled(),
                audit.mainOnlyCompiled(),
                audit.portalOnlyCompiled(),
                audit.neitherCompiled(),
                scheduledPortalCompile ? audit.candidateSections() : 0,
                audit.borrowableSolid(),
                audit.borrowableCutout(),
                audit.borrowableTranslucent(),
                borrowDrawResult == null ? 0 : borrowDrawResult.solidSectionsDrawn(),
                borrowDrawResult == null ? 0 : borrowDrawResult.cutoutSectionsDrawn(),
                borrowDrawResult == null ? 0 : borrowDrawResult.translucentSectionsDrawn(),
                audit.bothCompiled(),
                audit.reuseHitRate(),
                "yes",
                primerPrimedTotal,
                primerQueuedCompile,
                "no",
                experimentalBorrowDrawEnabled ? "yes" : "no",
                borrowDrawResult != null && borrowDrawResult.implemented() ? "yes" : "no",
                experimentalBorrowDrawEnabled ? "experimental-borrowed-solid-cutout-draw-enabled" : "audit-or-primer-only"
        );
    }

    public static void logDuplicateCompileCandidatesIfDue(Audit audit, boolean scheduledPortalCompile) {
        if (audit == null || !scheduledPortalCompile || audit.mainOnlyCompiled() <= 0) {
            return;
        }

        long now = System.currentTimeMillis();
        if (!shouldLog(LAST_DUPLICATE_LOG_MILLIS, audit.viewId(), now)) {
            return;
        }

        Skyesight.LOGGER.info(
                "[Skyesight] PORTAL_DUPLICATE_SECTION_COMPILE_CANDIDATES: viewId={} scheduledPortalCompiles={} alreadyCompiledInMain={} avoidableCandidates={} reason={}",
                audit.viewId() == null ? "-" : audit.viewId(),
                audit.candidateSections(),
                audit.mainOnlyCompiled(),
                audit.mainOnlyCompiled(),
                "portal-secondary-renderer-has-independent-section-manager"
        );
    }

    public static void logActivePathIfDue(
            ResourceLocation viewId,
            String method,
            boolean sameDim,
            boolean sodiumRendererPresent,
            boolean chunkTrackerReady,
            int candidateSections,
            String reason
    ) {
        long now = System.currentTimeMillis();
        if (!shouldLog(LAST_ACTIVE_PATH_LOG_MILLIS, viewId, now)) {
            return;
        }

        Skyesight.LOGGER.info(
                "[Skyesight] PORTAL_SAME_DIM_TERRAIN_ACTIVE_PATH: viewId={} method={} sameDim={} sodiumRendererPresent={} chunkTrackerReady={} candidateSections={} reason={}",
                viewId == null ? "-" : viewId,
                method,
                sameDim ? "yes" : "no",
                sodiumRendererPresent ? "yes" : "no",
                chunkTrackerReady ? "yes" : "no",
                candidateSections,
                reason == null || reason.isBlank() ? "-" : reason
        );
    }

    private static BorrowLists collectBorrowLists(
            ClientLevel level,
            SodiumWorldRenderer mainRenderer,
            SodiumWorldRenderer portalRenderer,
            Vec3 portalCameraPos,
            int portalOwnedRadius,
            int reuseRadius,
            Viewport viewport
    ) {
        ChunkPos centerChunk = new ChunkPos(net.minecraft.core.BlockPos.containing(portalCameraPos));
        int minSection = level.getMinSection();
        int maxSection = level.getMaxSection();
        int ownedRadius = Math.max(0, portalOwnedRadius);
        int checkedRadius = Math.max(ownedRadius, reuseRadius);
        VisibleChunkCollector solidCollector = new VisibleChunkCollector(nextBorrowFrameId());
        VisibleChunkCollector cutoutCollector = new VisibleChunkCollector(nextBorrowFrameId());
        int solidSections = 0;
        int cutoutSections = 0;

        for (int chunkZ = centerChunk.z - checkedRadius; chunkZ <= centerChunk.z + checkedRadius; chunkZ++) {
            for (int chunkX = centerChunk.x - checkedRadius; chunkX <= centerChunk.x + checkedRadius; chunkX++) {
                int chunkDistance = Math.max(Math.abs(chunkX - centerChunk.x), Math.abs(chunkZ - centerChunk.z));
                if (chunkDistance <= ownedRadius || chunkDistance > checkedRadius) {
                    continue;
                }
                if (level.getChunkSource().getChunk(chunkX, chunkZ, false) == null) {
                    continue;
                }

                for (int sectionY = minSection; sectionY < maxSection; sectionY++) {
                    SectionPos sectionPos = SectionPos.of(chunkX, sectionY, chunkZ);
                    RenderSection mainSection = findSection(mainRenderer, sectionPos);
                    SectionPassInfo mainInfo = inspect(mainSection);
                    SectionPassInfo portalInfo = inspect(findSection(portalRenderer, sectionPos));

                    if (mainInfo.solid() && !portalInfo.solid()) {
                        solidCollector.visit(mainSection);
                        solidSections++;
                    }
                    if (mainInfo.cutout() && !portalInfo.cutout()) {
                        cutoutCollector.visit(mainSection);
                        cutoutSections++;
                    }
                }
            }
        }

        return new BorrowLists(
                solidCollector.createRenderLists(viewport),
                cutoutCollector.createRenderLists(viewport),
                solidSections,
                cutoutSections
        );
    }

    private static int drawBorrowedPass(
            ResourceLocation viewId,
            SodiumWorldRenderer portalRenderer,
            RenderSectionManager portalManager,
            SortedRenderLists lists,
            RenderType renderType,
            ChunkRenderMatrices matrices,
            Vec3 portalCameraPos,
            String borrowLayer,
            boolean screenOpen,
            boolean mainFramebufferBound,
            boolean portalStencilActive,
            int mainRenderListsExpectedIdentity
    ) throws ReflectiveOperationException {
        if (lists == null || renderType == null) {
            return 0;
        }

        SortedRenderLists originalLists = (SortedRenderLists) sodiumSectionManagerRenderListsField.get(portalManager);
        int mainBefore = mainRenderListsExpectedIdentity;
        int borrowIdentity = System.identityHashCode(lists);
        sodiumSectionManagerRenderListsField.set(portalManager, lists);
        int mainDuring = currentMainRenderListsIdentity();
        String exceptionSummary = "";
        boolean restored = false;
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        renderType.setupRenderState();

        try {
            portalRenderer.drawChunkLayer(
                    renderType,
                    matrices,
                    portalCameraPos.x(),
                    portalCameraPos.y(),
                    portalCameraPos.z()
            );
            return countSections(lists);
        } catch (RuntimeException exception) {
            exceptionSummary = exception.getClass().getSimpleName() + ": " + exception.getMessage();
            throw exception;
        } finally {
            try {
                renderType.clearRenderState();
            } finally {
                sodiumSectionManagerRenderListsField.set(portalManager, originalLists);
                restored = true;
                logBorrowSwapAuditIfDue(
                        viewId,
                        screenOpen,
                        borrowLayer,
                        mainBefore,
                        borrowIdentity,
                        mainDuring,
                        currentMainRenderListsIdentity(),
                        restored,
                        exceptionSummary,
                        mainFramebufferBound,
                        portalStencilActive
                );
            }
        }
    }

    public static int currentMainRenderListsIdentity() {
        SodiumWorldRenderer mainRenderer = SodiumWorldRenderer.instanceNullable();
        return renderListsIdentity(mainRenderer);
    }

    public static MainTerrainState captureMainTerrainState(Minecraft minecraft) {
        SodiumWorldRenderer mainRenderer = SodiumWorldRenderer.instanceNullable();
        int sectionManagerIdentity = 0;
        int renderListsIdentity = 0;
        int renderListsSize = 0;
        int visibleSectionCount = -1;

        if (mainRenderer != null) {
            visibleSectionCount = mainRenderer.getVisibleChunkCount();
            try {
                RenderSectionManager manager = sectionManager(mainRenderer);
                sectionManagerIdentity = manager == null ? 0 : System.identityHashCode(manager);
                Object renderLists = manager == null ? null : sodiumSectionManagerRenderListsField.get(manager);
                renderListsIdentity = renderLists == null ? 0 : System.identityHashCode(renderLists);
                renderListsSize = renderLists instanceof SortedRenderLists sorted ? countSections(sorted) : 0;
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                sectionManagerIdentity = -1;
                renderListsIdentity = -1;
                renderListsSize = -1;
            }
        }

        int framebuffer = org.lwjgl.opengl.GL30.glGetInteger(org.lwjgl.opengl.GL30.GL_FRAMEBUFFER_BINDING);
        int mainTarget = minecraft == null ? -1 : minecraft.getMainRenderTarget().frameBufferId;
        int[] viewport = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        ByteBuffer colorMask = BufferUtils.createByteBuffer(4);
        GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK, colorMask);

        return new MainTerrainState(
                mainRenderer == null ? 0 : System.identityHashCode(mainRenderer),
                sectionManagerIdentity,
                renderListsIdentity,
                renderListsSize,
                visibleSectionCount,
                framebuffer,
                mainTarget,
                viewport[0] + "," + viewport[1] + "," + viewport[2] + "," + viewport[3],
                System.identityHashCode(RenderSystem.getProjectionMatrix()),
                System.identityHashCode(RenderSystem.getModelViewStack()),
                (colorMask.get(0) != 0) + "," + (colorMask.get(1) != 0) + "," + (colorMask.get(2) != 0) + "," + (colorMask.get(3) != 0),
                GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK),
                GL11.glIsEnabled(GL11.GL_STENCIL_TEST)
        );
    }

    public static void logMainTerrainStateAuditIfDue(
            String phase,
            ResourceLocation viewId,
            boolean firstFrameAfterRegistration,
            boolean screenOpen,
            MainTerrainState before,
            MainTerrainState current,
            String reason
    ) {
        if (!SkyesightDebugConfig.TERRAIN_AUDIT) {
            return;
        }
        long now = System.currentTimeMillis();
        String key = phase + ":" + (viewId == null ? "-" : viewId);
        Long last = LAST_MAIN_TERRAIN_STATE_AUDIT_MILLIS.get(key);
        if (last != null && now - last < 1_000L) {
            return;
        }
        LAST_MAIN_TERRAIN_STATE_AUDIT_MILLIS.put(key, now);

        boolean changed = before != null && current != null && !before.sameTerrainState(current);
        Skyesight.LOGGER.info(
                "[Skyesight] PORTAL_MAIN_TERRAIN_STATE_AUDIT: phase={} viewId={} firstFrameAfterRegistration={} screenOpen={} mainRendererIdentity={} mainSectionManagerIdentity={} mainRenderListsIdentity={} mainRenderListsSize={} mainVisibleSectionCount={} mainFramebufferBound={} mainTargetIdentity={} viewport={} projectionHash={} modelViewHash={} terrainRenderTargetMain={} colorMask={} depthMask={} stencilEnabled={} stateChangedFromBefore={} reason={}",
                phase,
                viewId == null ? "-" : viewId,
                firstFrameAfterRegistration ? "yes" : "no",
                screenOpen ? "yes" : "no",
                current == null ? 0 : current.mainRendererIdentity(),
                current == null ? 0 : current.mainSectionManagerIdentity(),
                current == null ? 0 : current.mainRenderListsIdentity(),
                current == null ? 0 : current.mainRenderListsSize(),
                current == null ? 0 : current.mainVisibleSectionCount(),
                current != null && current.framebuffer() == current.mainTargetFramebuffer() ? "yes" : "no",
                current == null ? -1 : current.mainTargetFramebuffer(),
                current == null ? "-" : current.viewport(),
                current == null ? 0 : current.projectionHash(),
                current == null ? 0 : current.modelViewHash(),
                current != null && current.framebuffer() == current.mainTargetFramebuffer() ? "yes" : "no",
                current == null ? "-" : current.colorMask(),
                current != null && current.depthMask() ? "true" : "false",
                current != null && current.stencilEnabled() ? "yes" : "no",
                changed ? "yes" : "no",
                reason == null || reason.isBlank() ? "-" : reason
        );
    }

    private static int renderListsIdentity(SodiumWorldRenderer renderer) {
        if (renderer == null) {
            return 0;
        }

        try {
            RenderSectionManager manager = sectionManager(renderer);
            Object lists = manager == null ? null : sodiumSectionManagerRenderListsField.get(manager);
            return lists == null ? 0 : System.identityHashCode(lists);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return -1;
        }
    }

    public static void logMainRenderListRestoreCheckIfDue(
            ResourceLocation viewId,
            boolean screenOpen,
            int expectedIdentity
    ) {
        long now = System.currentTimeMillis();
        if (!shouldLog(LAST_MAIN_RESTORE_CHECK_MILLIS, viewId, now)) {
            return;
        }

        int currentIdentity = currentMainRenderListsIdentity();
        boolean restored = expectedIdentity == currentIdentity;
        Skyesight.LOGGER.info(
                "[Skyesight] PORTAL_MAIN_RENDER_LIST_RESTORE_CHECK: screenOpen={} mainRenderListsCurrentIdentity={} mainRenderListsExpectedIdentity={} restored={} reason={}",
                screenOpen ? "yes" : "no",
                currentIdentity,
                expectedIdentity,
                restored ? "yes" : "no",
                restored ? "main-render-list-unchanged" : "main-render-list-changed"
        );
    }

    private static void logExperimentalWarningIfDue(ResourceLocation viewId) {
        long now = System.currentTimeMillis();
        if (!shouldLog(LAST_EXPERIMENTAL_WARNING_MILLIS, viewId, now)) {
            return;
        }

        Skyesight.LOGGER.warn(
                "[Skyesight] PORTAL_BORROWED_DRAWING_EXPERIMENTAL: enabled=yes risk=main-render-list-swap reason=experimental-do-not-enable-by-default"
        );
    }

    private static void logBorrowSkippedIfDue(ResourceLocation viewId, String reason) {
        long now = System.currentTimeMillis();
        if (!shouldLog(LAST_BORROW_SKIP_MILLIS, viewId, now)) {
            return;
        }

        Skyesight.LOGGER.info(
                "[Skyesight] PORTAL_BORROWED_DRAW_SKIPPED: viewId={} reason={}",
                viewId == null ? "-" : viewId,
                reason
        );
    }

    private static void logBorrowSwapAuditIfDue(
            ResourceLocation viewId,
            boolean screenOpen,
            String borrowLayer,
            int mainBefore,
            int borrowIdentity,
            int mainDuring,
            int mainAfter,
            boolean restored,
            String exception,
            boolean mainFramebufferBound,
            boolean portalStencilActive
    ) {
        long now = System.currentTimeMillis();
        if (!shouldLog(LAST_BORROW_SWAP_AUDIT_MILLIS, viewId, now)) {
            return;
        }

        Skyesight.LOGGER.info(
                "[Skyesight] PORTAL_BORROW_SWAP_AUDIT: viewId={} screenOpen={} borrowLayer={} mainRenderListsBeforeIdentity={} borrowRenderListsIdentity={} mainRenderListsDuringIdentity={} mainRenderListsAfterIdentity={} restored={} exception={} mainFramebufferBound={} portalStencilActive={} reason={}",
                viewId == null ? "-" : viewId,
                screenOpen ? "yes" : "no",
                borrowLayer,
                mainBefore,
                borrowIdentity,
                mainDuring,
                mainAfter,
                restored ? "yes" : "no",
                exception == null || exception.isBlank() ? "-" : exception,
                mainFramebufferBound ? "yes" : "no",
                portalStencilActive ? "yes" : "no",
                "borrowed-list-installed-on-portal-renderer"
        );
    }

    private static int countSections(SortedRenderLists lists) {
        int sections = 0;
        if (lists == null) {
            return 0;
        }

        var iterator = lists.iterator();
        while (iterator.hasNext()) {
            sections += iterator.next().size();
        }
        return sections;
    }

    private static int nextBorrowFrameId() {
        return (int) (System.nanoTime() & 0x7FFF_FFFFL);
    }

    private static boolean shouldLog(Map<ResourceLocation, Long> lastLogMillis, ResourceLocation viewId, long now) {
        Long last = lastLogMillis.get(viewId);
        if (last != null && now - last < 1_000L) {
            return false;
        }

        lastLogMillis.put(viewId, now);
        return true;
    }

    private static RenderSection findSection(SodiumWorldRenderer renderer, SectionPos sectionPos) {
        if (renderer == null || sectionPos == null) {
            return null;
        }

        try {
            RenderSectionManager manager = sectionManager(renderer);
            if (manager == null) {
                return null;
            }

            @SuppressWarnings("unchecked")
            Long2ReferenceMap<RenderSection> sections =
                    (Long2ReferenceMap<RenderSection>) sodiumSectionManagerSectionByPositionField.get(manager);
            return sections.get(SectionPos.asLong(sectionPos.getX(), sectionPos.getY(), sectionPos.getZ()));
        } catch (ReflectiveOperationException | RuntimeException exception) {
            reflectionFailed = true;
            return null;
        }
    }

    private static RenderSectionManager sectionManager(SodiumWorldRenderer renderer)
            throws ReflectiveOperationException {
        initializeReflection();
        if (reflectionFailed) {
            return null;
        }

        return (RenderSectionManager) sodiumWorldRendererSectionManagerField.get(renderer);
    }

    private static void initializeReflection() throws NoSuchFieldException {
        if (sodiumWorldRendererSectionManagerField != null || reflectionFailed) {
            return;
        }

        sodiumWorldRendererSectionManagerField =
                SodiumWorldRenderer.class.getDeclaredField("renderSectionManager");
        sodiumWorldRendererSectionManagerField.setAccessible(true);
        sodiumSectionManagerSectionByPositionField =
                RenderSectionManager.class.getDeclaredField("sectionByPosition");
        sodiumSectionManagerSectionByPositionField.setAccessible(true);
        sodiumSectionManagerRenderListsField =
                RenderSectionManager.class.getDeclaredField("renderLists");
        sodiumSectionManagerRenderListsField.setAccessible(true);
    }

    private static SectionPassInfo inspect(RenderSection section) {
        if (section == null || !section.isBuilt()) {
            return SectionPassInfo.EMPTY;
        }

        RenderRegion region = section.getRegion();
        if (region == null) {
            return SectionPassInfo.EMPTY;
        }

        return new SectionPassInfo(
                region.getStorage(DefaultTerrainRenderPasses.SOLID) != null,
                region.getStorage(DefaultTerrainRenderPasses.CUTOUT) != null,
                region.getStorage(DefaultTerrainRenderPasses.TRANSLUCENT) != null
        );
    }

    public record MainSectionInfo(
            SectionPos sectionPos,
            boolean solid,
            boolean cutout,
            boolean translucent
    ) {
        private static MainSectionInfo from(RenderSection section) {
            SectionPassInfo info = inspect(section);
            return new MainSectionInfo(
                    section.getPosition(),
                    info.solid(),
                    info.cutout(),
                    info.translucent()
            );
        }
    }

    private record SectionPassInfo(boolean solid, boolean cutout, boolean translucent) {
        private static final SectionPassInfo EMPTY = new SectionPassInfo(false, false, false);
    }

    public record SectionAvailability(boolean solid, boolean cutout, boolean translucent) {
        private static SectionAvailability from(SectionPassInfo info) {
            return new SectionAvailability(info.solid(), info.cutout(), info.translucent());
        }

        public boolean solidOrCutout() {
            return this.solid || this.cutout;
        }
    }

    private record BorrowLists(
            SortedRenderLists solidLists,
            SortedRenderLists cutoutLists,
            int solidSections,
            int cutoutSections
    ) {}

    public record BorrowDrawResult(
            boolean implemented,
            int solidSectionsDrawn,
            int cutoutSectionsDrawn,
            int translucentSectionsDrawn
    ) {
        public static BorrowDrawResult implementedNotDrawn() {
            return new BorrowDrawResult(true, 0, 0, 0);
        }
    }

    public record MainTerrainState(
            int mainRendererIdentity,
            int mainSectionManagerIdentity,
            int mainRenderListsIdentity,
            int mainRenderListsSize,
            int mainVisibleSectionCount,
            int framebuffer,
            int mainTargetFramebuffer,
            String viewport,
            int projectionHash,
            int modelViewHash,
            String colorMask,
            boolean depthMask,
            boolean stencilEnabled
    ) {
        public boolean sameTerrainState(MainTerrainState other) {
            return other != null
                    && this.mainRendererIdentity == other.mainRendererIdentity
                    && this.mainSectionManagerIdentity == other.mainSectionManagerIdentity
                    && this.mainRenderListsIdentity == other.mainRenderListsIdentity
                    && this.mainRenderListsSize == other.mainRenderListsSize
                    && this.mainVisibleSectionCount == other.mainVisibleSectionCount
                    && this.framebuffer == other.framebuffer
                    && this.viewport.equals(other.viewport)
                    && this.depthMask == other.depthMask
                    && this.stencilEnabled == other.stencilEnabled;
        }
    }

    public record Audit(
            ResourceLocation viewId,
            int candidateSections,
            int mainCompiledAvailable,
            int portalCompiledAvailable,
            int bothCompiled,
            int mainOnlyCompiled,
            int portalOnlyCompiled,
            int neitherCompiled,
            int borrowableSolid,
            int borrowableCutout,
            int borrowableTranslucent
    ) {
        public static Audit empty(ResourceLocation viewId) {
            return new Audit(viewId, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        }

        public String reuseHitRate() {
            if (this.candidateSections <= 0) {
                return "0%";
            }

            int percent = Math.round((this.mainCompiledAvailable * 100.0F) / this.candidateSections);
            return percent + "%";
        }
    }
}
