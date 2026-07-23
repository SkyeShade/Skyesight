package com.skyeshade.skyesight.network;

import com.skyeshade.skyesight.Skyesight;
import com.skyeshade.skyesight.SkyesightDebugConfig;
import com.skyeshade.skyesight.client.world.SkyesightVisualWorld;
import com.skyeshade.skyesight.client.world.SkyesightVisualWorldManager;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.ChunkPos;

public final class SkyesightClientBlockUpdateHandler {
    private SkyesightClientBlockUpdateHandler() {}

    public static void handle(SkyesightBlockUpdatesPayload payload) {
        SkyesightVisualWorld world =
                SkyesightVisualWorldManager.getIfCurrent(payload.viewId(), payload.dimension());

        if (world == null || world.isClosed()) {
            return;
        }

        for (SkyesightBlockUpdatesPayload.Entry update : payload.updates()) {
            boolean applied = world.chunkReceiver().applyBlockUpdate(
                    update.pos(),
                    update.state()
            );
            boolean blockEntityApplied = update.blockEntityTag() != null
                    && world.chunkReceiver().applyBlockEntityUpdate(
                            update.pos(),
                            update.blockEntityTag()
                    );

            if (applied) {
                world.renderer().scheduleBlockUpdate(update.pos());
            }

            if (blockEntityApplied) {
                world.renderer().scheduleBlockUpdate(update.pos());
            }

            if (SkyesightDebugConfig.WATCH_DEBUG && isCrossDimension(payload, world)) {
                ChunkPos chunkPos = new ChunkPos(update.pos());
                Skyesight.LOGGER.info(
                        "[Skyesight] SKYESIGHT_CROSS_DIM_BLOCK_UPDATE: viewId={} displayDimension={} cameraDimension={} chunk={},{} blockPos={} applied={} blockEntityApplied={}",
                        payload.viewId(),
                        Minecraft.getInstance().level == null ? "-" : Minecraft.getInstance().level.dimension().location(),
                        payload.dimension().location(),
                        chunkPos.x,
                        chunkPos.z,
                        update.pos(),
                        applied,
                        blockEntityApplied
                );

                if (update.blockEntityTag() != null) {
                    Skyesight.LOGGER.info(
                            "[Skyesight] SKYESIGHT_CROSS_DIM_BLOCK_ENTITY_UPDATE_RECEIVE: viewId={} cameraDimension={} blockPos={} blockEntityType={} hasTag=yes",
                            payload.viewId(),
                            payload.dimension().location(),
                            update.pos(),
                            update.blockEntityTag().getString("id")
                    );
                    Skyesight.LOGGER.info(
                            "[Skyesight] SKYESIGHT_CROSS_DIM_BLOCK_ENTITY_APPLY: viewId={} cameraDimension={} blockPos={} blockEntityType={} created=unknown updated={} storedInVisualChunk={} reasonIfSkipped={}",
                            payload.viewId(),
                            payload.dimension().location(),
                            update.pos(),
                            update.blockEntityTag().getString("id"),
                            blockEntityApplied ? "yes" : "no",
                            world.level().getBlockEntity(update.pos()) != null ? "yes" : "no",
                            blockEntityApplied ? "-" : "missing visual chunk or block entity could not be created"
                    );
                    Skyesight.LOGGER.info(
                            "[Skyesight] SKYESIGHT_CROSS_DIM_BLOCK_ENTITY_STORE: viewId={} cameraDimension={} storedBlockEntityCount={} firstFewBlockEntities={}",
                            payload.viewId(),
                            payload.dimension().location(),
                            world.chunkReceiver().countBlockEntities(),
                            world.chunkReceiver().firstBlockEntities(5)
                    );
                }
            }
        }
    }

    private static boolean isCrossDimension(SkyesightBlockUpdatesPayload payload, SkyesightVisualWorld world) {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level != null
                && payload != null
                && world != null
                && !minecraft.level.dimension().equals(payload.dimension());
    }
}
