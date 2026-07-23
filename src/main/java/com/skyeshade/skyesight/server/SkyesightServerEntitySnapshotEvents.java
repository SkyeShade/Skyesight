package com.skyeshade.skyesight.server;

import com.skyeshade.skyesight.Skyesight;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = Skyesight.MODID)
public final class SkyesightServerEntitySnapshotEvents {
    private static final int SNAPSHOT_INTERVAL_TICKS = 2;

    private SkyesightServerEntitySnapshotEvents() {}

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        PortalSimulationCoordinator.tick(event.getServer());
        if (event.getServer().getTickCount() % SNAPSHOT_INTERVAL_TICKS != 0) {
            return;
        }

        SkyesightServerViewTracker.forEachWatch((playerId, watch) -> {
            ServerPlayer player = event.getServer().getPlayerList().getPlayer(playerId);

            if (player == null) {
                return;
            }

            ServerLevel level = event.getServer().getLevel(watch.dimension());

            if (level == null) {
                return;
            }

            SkyesightServerEntitySnapshotSender.sendSnapshot(
                    player,
                    watch,
                    level
            );
        });
    }
}
