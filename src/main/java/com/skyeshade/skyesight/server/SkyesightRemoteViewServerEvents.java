package com.skyeshade.skyesight.server;

import com.skyeshade.skyesight.Skyesight;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

@EventBusSubscriber(modid = Skyesight.MODID)
public final class SkyesightRemoteViewServerEvents {
    private SkyesightRemoteViewServerEvents() {}

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            SkyesightRemoteViewLifecycleHandler.removeAll(player);
            SkyesightServerRemoteViewRegistry.forget(player);
        }
    }

    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.LOWEST)
    public static void onDeath(net.neoforged.neoforge.event.entity.living.LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) SkyesightRemoteViewLifecycleHandler.removeAll(player);
    }

    @SubscribeEvent
    public static void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) SkyesightRemoteViewLifecycleHandler.dimensionChanged(player);
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) SkyesightRemoteViewLifecycleHandler.removeAll(player);
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            SkyesightRemoteViewLifecycleHandler.removeAll(player);
        }
        SkyesightServerChunkLoader.clear(event.getServer());
        for (var region : com.skyeshade.skyesight.server.portal.PortalRegionTracker.snapshotValues()) {
            com.skyeshade.skyesight.server.portal.PortalChunkTicketController.removeRegionTickets(event.getServer(), region, 1);
            com.skyeshade.skyesight.server.portal.PortalRegionTracker.remove(
                    new com.skyeshade.skyesight.server.portal.PortalRegionTracker.Key(region.playerId(), region.viewId()));
        }
        SkyesightForcedChunkTickets.clear(event.getServer());
        SkyesightServerRemoteViewRegistry.clear();
    }
}
