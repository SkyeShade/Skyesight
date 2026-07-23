package com.skyeshade.skyesight.server.portal;

import com.skyeshade.skyesight.server.portal.PortalRegionTracker.Key;
import com.skyeshade.skyesight.server.portal.PortalRegionTracker.Region;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;

import java.util.HashMap;
import java.util.Map;

public final class PortalChunkTicketController {
    private static final Map<Key, Long> LAST_TICKET_REFRESH_TICKS = new HashMap<>();

    private PortalChunkTicketController() {
    }

    public static void refreshRegionTickets(ServerLevel level, Region region, boolean entityTickingEnabled, int pathfindingChunkMargin) {
        ChunkPos center = new ChunkPos(region.centerChunkX(), region.centerChunkZ());
        if (entityTickingEnabled) {
            level.getChunkSource().addRegionTicket(TicketType.PLAYER, center, region.loadRadiusChunks() + pathfindingChunkMargin, center, true);
            LAST_TICKET_REFRESH_TICKS.put(new Key(region.playerId(), region.viewId()), (long) level.getServer().getTickCount());
        }
        for (long packed : region.chunks()) {
            level.setChunkForced(ChunkPos.getX(packed), ChunkPos.getZ(packed), true);
        }
    }

    public static void removeRegionTickets(MinecraftServer server, Region region, int pathfindingChunkMargin) {
        ServerLevel level = server.getLevel(region.dimension());
        if (level == null) {
            return;
        }
        ChunkPos center = new ChunkPos(region.centerChunkX(), region.centerChunkZ());
        level.getChunkSource().removeRegionTicket(TicketType.PLAYER, center, region.loadRadiusChunks() + pathfindingChunkMargin, center, true);
        LAST_TICKET_REFRESH_TICKS.remove(new Key(region.playerId(), region.viewId()));
        for (long packed : region.chunks()) {
            level.setChunkForced(ChunkPos.getX(packed), ChunkPos.getZ(packed), false);
        }
    }

    public static long lastTicketRefreshTick(Region region) {
        return LAST_TICKET_REFRESH_TICKS.getOrDefault(new Key(region.playerId(), region.viewId()), -1L);
    }
}
