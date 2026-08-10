package com.skyeshade.skyesight.client.render.sodium;

import com.skyeshade.skyesight.Skyesight;
import com.skyeshade.skyesight.SkyesightDebugConfig;
import com.skyeshade.skyesight.client.render.SecondaryViewContext;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;

public final class SameDimPortalTerrainPrimer {
    private static final Map<ResourceLocation, Long> LAST_PRIMER_LOG_MILLIS = new HashMap<>();

    private SameDimPortalTerrainPrimer() {}

    public static Result primeFromMainCompiledSections(
            ResourceLocation viewId,
            ClientLevel level,
            SecondaryViewContext context,
            SodiumWorldRenderer portalRenderer,
            Vec3 portalCameraPos,
            int primerRadiusChunks,
            int maxSectionsPerFrame,
            int maxPrimerFrames
    ) {
        if (level == null
                || context == null
                || portalRenderer == null
                || portalCameraPos == null
                || context.mainCompiledSectionsPrimed()) {
            return Result.empty(context == null ? 0 : context.primedSections());
        }
        SodiumSecondaryViewState state = SodiumSecondaryViewState.getOrCreate(context);

        SodiumWorldRenderer mainRenderer = SodiumWorldRenderer.instanceNullable();
        if (mainRenderer == null || mainRenderer == portalRenderer) {
            context.setMainCompiledSectionsPrimed(true);
            Result result = new Result(0, 0, 0, 0, 0, 0, 0, 0, context.primedSections(), 0, true, "no-distinct-main-renderer");
            logIfDue(viewId, context, result, Math.max(0, primerRadiusChunks));
            return result;
        }

        int radius = Math.max(0, primerRadiusChunks);
        int budget = Math.max(0, maxSectionsPerFrame);
        if (budget <= 0) {
            return Result.empty(context.primedSections());
        }

        ChunkPos centerChunk = new ChunkPos(BlockPos.containing(portalCameraPos));
        int minSection = level.getMinSection();
        int maxSection = level.getMaxSection();
        int sectionCount = Math.max(1, maxSection - minSection);
        int side = radius * 2 + 1;
        int totalSections = side * side * sectionCount;
        int start = totalSections <= 0 ? 0 : Math.floorMod(context.primerCursor(), totalSections);
        int sectionsChecked = 0;
        int mainCompiledSolid = 0;
        int mainCompiledCutout = 0;
        int portalAlreadyReady = 0;
        int primedNow = 0;
        int queuedForPortalCompile = 0;
        int skippedUnsafe = 0;
        LongSet queuedChunksThisFrame = new LongOpenHashSet();

        ChunkPos queueCenter = centerChunk;
        if (context.pendingSodiumRebuildCenter() == null
                || context.pendingSodiumRebuildChunks().isEmpty()) {
            context.setPendingSodiumRebuildCenter(queueCenter);
        }

        for (int offset = 0; offset < totalSections && sectionsChecked < budget; offset++) {
            int index = (start + offset) % totalSections;
            int sectionOffset = index % sectionCount;
            int chunkIndex = index / sectionCount;
            int dx = (chunkIndex % side) - radius;
            int dz = (chunkIndex / side) - radius;
            int chunkX = centerChunk.x + dx;
            int chunkZ = centerChunk.z + dz;
            int sectionY = minSection + sectionOffset;
            sectionsChecked++;

            ChunkPos chunk = new ChunkPos(chunkX, chunkZ);
            if (level.getChunkSource().getChunk(chunkX, chunkZ, false) == null) {
                skippedUnsafe++;
                continue;
            }

            SectionPos sectionPos = SectionPos.of(chunkX, sectionY, chunkZ);
            SameDimMainSodiumSectionReuse.SectionAvailability mainAvailability =
                    SameDimMainSodiumSectionReuse.sectionAvailability(mainRenderer, sectionPos);
            if (!mainAvailability.solidOrCutout()) {
                continue;
            }
            if (mainAvailability.solid()) {
                mainCompiledSolid++;
            }
            if (mainAvailability.cutout()) {
                mainCompiledCutout++;
            }

            SameDimMainSodiumSectionReuse.SectionAvailability portalAvailability =
                    SameDimMainSodiumSectionReuse.sectionAvailability(portalRenderer, sectionPos);
            if (portalAvailability.solidOrCutout()) {
                portalAlreadyReady++;
                continue;
            }

            if (state.chunkSource().primeTrackedChunk(state.chunkTracker(), chunk)) {
                primedNow++;
            }
            long packedChunk = ChunkPos.asLong(chunkX, chunkZ);
            if (queuedChunksThisFrame.add(packedChunk)
                    && !context.pendingSodiumRebuildChunks().contains(chunk)) {
                context.pendingSodiumRebuildChunks().add(chunk);
                queuedForPortalCompile++;
            }
        }

        if (totalSections > 0) {
            context.setPrimerCursor((start + sectionsChecked) % totalSections);
        }
        context.addPrimedSections(primedNow);
        context.incrementPrimerFrames();
        boolean completedScan = totalSections <= 0 || start + sectionsChecked >= totalSections;
        boolean completed = completedScan || context.primerFrames() >= Math.max(1, maxPrimerFrames);
        if (completed) {
            context.setMainCompiledSectionsPrimed(true);
        }

        Result result = new Result(
                sectionsChecked,
                mainCompiledSolid,
                mainCompiledCutout,
                portalAlreadyReady,
                primedNow,
                queuedForPortalCompile,
                0,
                0,
                context.primedSections(),
                skippedUnsafe,
                true,
                completed ? "primer-complete" : "metadata-prime-budgeted"
        );
        logIfDue(viewId, context, result, radius);
        return result;
    }

    private static void logIfDue(ResourceLocation viewId, SecondaryViewContext context, Result result, int primerRadius) {
        if (!SkyesightDebugConfig.TERRAIN_AUDIT) {
            return;
        }
        long now = System.currentTimeMillis();
        Long last = LAST_PRIMER_LOG_MILLIS.get(viewId);
        if (last != null && now - last < 1_000L) {
            return;
        }
        LAST_PRIMER_LOG_MILLIS.put(viewId, now);

        Skyesight.LOGGER.info(
                "[Skyesight] PORTAL_MAIN_SECTION_PRIMER: viewId={} frame={} primerRadius={} sectionsChecked={} mainCompiledSolid={} mainCompiledCutout={} portalAlreadyReady={} primedNow={} primedTotal={} queuedForPortalCompile={} borrowedReadyReferences={} fallbackMetadataOnly={} skippedUnsafe={} reason={}",
                viewId == null ? "-" : viewId,
                context == null ? 0 : context.primerFrames(),
                primerRadius,
                result.sectionsChecked(),
                result.mainCompiledSolid(),
                result.mainCompiledCutout(),
                result.portalAlreadyReady(),
                result.primedNow(),
                result.primedTotal(),
                result.queuedForPortalCompile(),
                result.borrowedReadyReferences(),
                result.fallbackMetadataOnly() ? "yes" : "no",
                result.skippedUnsafe(),
                result.reason()
        );
    }

    public record Result(
            int sectionsChecked,
            int mainCompiledSolid,
            int mainCompiledCutout,
            int portalAlreadyReady,
            int primedNow,
            int queuedForPortalCompile,
            int borrowedReadyReferences,
            int rawMeshReferences,
            int primedTotal,
            int skippedUnsafe,
            boolean fallbackMetadataOnly,
            String reason
    ) {
        public static Result empty(int primedTotal) {
            return new Result(0, 0, 0, 0, 0, 0, 0, 0, primedTotal, 0, true, "not-run");
        }
    }
}
