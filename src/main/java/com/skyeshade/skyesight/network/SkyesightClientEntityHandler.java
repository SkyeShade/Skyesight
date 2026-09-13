package com.skyeshade.skyesight.network;

import com.skyeshade.skyesight.client.render.PlayerPerspectiveViews;
import com.skyeshade.skyesight.client.world.SkyesightClientChunkRequester;
import com.skyeshade.skyesight.client.world.SkyesightVisualWorld;
import com.skyeshade.skyesight.client.world.SkyesightVisualWorldManager;
import com.skyeshade.skyesight.remote.SkyesightRemoteViewRegistry;
import com.skyeshade.skyesight.Skyesight;

public final class SkyesightClientEntityHandler {
    private static boolean snapshotApplyFailureLogged;

    private SkyesightClientEntityHandler() {}

    public static void handle(SkyesightEntitySnapshotPayload payload) {
        if (!SkyesightRemoteViewRegistry.accepts(
                payload.viewId(), payload.generation(), payload.dimension())) {
            return;
        }

        if (PlayerPerspectiveViews.contains(payload.viewId())
                && !SkyesightClientChunkRequester.hasDemand(payload.viewId())) return;
        SkyesightVisualWorld world =
                SkyesightVisualWorldManager.getIfCurrent(payload.viewId(), payload.dimension());

        if (world == null || world.isClosed()) {
            world = SkyesightVisualWorldManager.getOrCreateIfCurrent(payload.viewId(), payload.dimension());
        }

        if (world == null || world.isClosed()) {
            return;
        }

        try {
            world.entityStore().applySnapshot(payload);
        } catch (RuntimeException exception) {
            if (!snapshotApplyFailureLogged) {
                snapshotApplyFailureLogged = true;
                Skyesight.LOGGER.warn(
                        "[Skyesight] Dropped visual entity snapshot payload viewId={} dimension={} reason={}: {}",
                        payload.viewId(),
                        payload.dimension().location(),
                        exception.getClass().getSimpleName(),
                        exception.getMessage()
                );
            }
        }
    }
}
