package com.skyeshade.skyesight.client.transition;

import com.skyeshade.skyesight.api.SkyesightPortalApi;
import com.skyeshade.skyesight.client.chunk.SkyesightPortalChunkStorage;
import com.skyeshade.skyesight.client.portal.PortalDirectStencilRenderer;
import com.skyeshade.skyesight.client.world.SkyesightClientChunkRequester;
import com.skyeshade.skyesight.client.world.SkyesightVisualWorldManager;
import com.skyeshade.skyesight.Skyesight;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.Util;
import net.minecraft.world.level.ChunkPos;

import java.util.*;

/** Short, connection-local leases for both directions of a nearby traversable pair. */
public final class TraversalPortalStandby {
    private static final Map<ResourceLocation, Long> HOT = new LinkedHashMap<>();
    private static final Set<ResourceLocation> FAILED_WARMUPS = new HashSet<>();
    private static long lastWarm;
    private static int cursor;
    private TraversalPortalStandby() {}
    public static void touch(String a, String b) {
        long until = Util.getMillis() + 15_000;
        HOT.put(ResourceLocation.parse(a), until);
        HOT.put(ResourceLocation.parse(b), until);
    }
    public static Set<ResourceLocation> retained() { return Set.copyOf(HOT.keySet()); }
    public static boolean contains(ResourceLocation id) { return HOT.containsKey(id); }
    public static void remove(String id) {
        HOT.remove(ResourceLocation.parse(id)); FAILED_WARMUPS.remove(ResourceLocation.parse(id));
    }
    public static void clear() { HOT.clear(); FAILED_WARMUPS.clear(); lastWarm = 0; cursor = 0; }
    public static void tick() {
        long now = Util.getMillis();
        for (var id : List.copyOf(HOT.keySet())) {
            var portal = SkyesightPortalApi.getPortal(id.toString());
            if (portal == null || !portal.active() || now > HOT.get(id)) {
                HOT.remove(id);
                FAILED_WARMUPS.remove(id);
                PortalDirectStencilRenderer.invalidateViewCaches(id);
                SkyesightVisualWorldManager.close(id);
                SkyesightClientChunkRequester.reset(id);
                SkyesightPortalChunkStorage.clearView(id);
            } else {
                var mc = Minecraft.getInstance();
                boolean nearbySource = portal.source().dimension().equals(mc.level.dimension())
                        && mc.player.position().distanceToSqr(portal.source().center()) < 256;
                // An active direction owns its transformed-camera center. Do not oscillate
                // its chunk watch against the endpoint-centered standby request every tick.
                if (!nearbySource || !SkyesightClientChunkRequester.hasDemand(id))
                    SkyesightClientChunkRequester.requestChunksFor(id, portal.target().dimension(),
                            new ChunkPos(BlockPos.containing(portal.target().center())),
                            mc.options.getEffectiveRenderDistance());
                SkyesightClientChunkRequester.keepAlive(id);
            }
        }
        if (HOT.isEmpty()) PortalDirectStencilRenderer.closeStandbyTarget();
    }
    public static void warmFrame() {
        long now = Util.getMillis();
        if (HOT.isEmpty() || now - lastWarm < 50
                || SecondaryTransition.concealsLoadingScreen() && !SecondaryTransition.preparingSuccessor()) return;
        lastWarm = now;
        var ids = List.copyOf(HOT.keySet());
        var portal = SkyesightPortalApi.getPortal(ids.get(Math.floorMod(cursor++, ids.size())).toString());
        if (portal != null && !FAILED_WARMUPS.contains(portal.id())
                && !PortalDirectStencilRenderer.hasPresentedPortal(portal.id())) {
            try { PortalDirectStencilRenderer.warmStandby(portal); }
            catch (RuntimeException failure) {
                FAILED_WARMUPS.add(portal.id());
                Skyesight.LOGGER.warn("Unable to prewarm traversal view {}; normal portal rendering remains available", portal.id(), failure);
            }
        }
    }
}
