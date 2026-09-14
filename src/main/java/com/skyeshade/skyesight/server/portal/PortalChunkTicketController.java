package com.skyeshade.skyesight.server.portal;

import com.skyeshade.skyesight.server.portal.PortalRegionTracker.Key;
import com.skyeshade.skyesight.server.portal.PortalRegionTracker.Region;
import com.skyeshade.skyesight.server.SkyesightForcedChunkTickets;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.ChunkPos;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

public final class PortalChunkTicketController {
    private static final TicketType<String> SIMULATION_TICKET = TicketType.create("skyesight_simulation", Comparator.<String>naturalOrder());
    private static final Map<Key, Long> LAST_TICKET_REFRESH_TICKS = new HashMap<>();

    private PortalChunkTicketController() {
    }

    public static void refreshRegionTickets(ServerLevel level, Region region, boolean entityTickingEnabled, int pathfindingChunkMargin) {
        ChunkPos center = new ChunkPos(region.centerChunkX(), region.centerChunkZ());
        if (entityTickingEnabled) {
            level.getChunkSource().addRegionTicket(SIMULATION_TICKET, center, region.loadRadiusChunks() + pathfindingChunkMargin, region.playerId() + "/" + region.viewId(), true);
            LAST_TICKET_REFRESH_TICKS.put(new Key(region.playerId(), region.viewId()), (long) level.getServer().getTickCount());
        }
        for (long packed : region.chunks()) {
            SkyesightForcedChunkTickets.acquire(level, packed, "simulation", region.playerId(), region.viewId());
        }
    }

    public static void removeRegionTickets(MinecraftServer server, Region region, int pathfindingChunkMargin) {
        ServerLevel level = server.getLevel(region.dimension());

        ChunkPos center = new ChunkPos(region.centerChunkX(), region.centerChunkZ());
        if (level != null) level.getChunkSource().removeRegionTicket(SIMULATION_TICKET, center, region.loadRadiusChunks() + pathfindingChunkMargin, region.playerId() + "/" + region.viewId(), true);
        LAST_TICKET_REFRESH_TICKS.remove(new Key(region.playerId(), region.viewId()));
        for (long packed : region.chunks()) {
            SkyesightForcedChunkTickets.release(server, region.dimension(), packed, "simulation", region.playerId(), region.viewId());
        }
    }

    /** Moving mapped regions retain overlapping forced ownership; only the center simulation ticket moves. */
    public static void moveRegionTickets(ServerLevel level, Region previous, Region next, int margin) {
        var server = level.getServer();
        var oldLevel = server.getLevel(previous.dimension());
        if (oldLevel != null) oldLevel.getChunkSource().removeRegionTicket(SIMULATION_TICKET,
                new ChunkPos(previous.centerChunkX(),previous.centerChunkZ()),previous.loadRadiusChunks()+margin,
                previous.playerId()+"/"+previous.viewId(),true);
        for(long chunk:previous.chunks())
            if(!previous.dimension().equals(next.dimension()) || !next.chunks().contains(chunk))
                SkyesightForcedChunkTickets.release(server,previous.dimension(),chunk,"simulation",previous.playerId(),previous.viewId());
    }

    public static long lastTicketRefreshTick(Region region) {
        return LAST_TICKET_REFRESH_TICKS.getOrDefault(new Key(region.playerId(), region.viewId()), -1L);
    }
}
