package com.skyeshade.skyesight.client.render.remote;

import com.skyeshade.skyesight.Skyesight;
import com.skyeshade.skyesight.api.SkyesightPortalApi;
import com.skyeshade.skyesight.client.world.SkyesightClientChunkRequester;
import com.skyeshade.skyesight.network.SkyesightRemoteViewLifecyclePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.HashMap;
import java.util.Map;

/** Network counterpart of the integrated portal loader; owns only per-observer streaming leases. */
@EventBusSubscriber(modid = Skyesight.MODID, value = Dist.CLIENT)
public final class PortalNetworkStreaming {
    private record Lease(long generation, ResourceKey<Level> dimension) {}
    private static final Map<ResourceLocation, Lease> LEASES = new HashMap<>();
    private PortalNetworkStreaming() {}

    public static boolean ensure(Minecraft minecraft, ResourceLocation id, ResourceKey<Level> dimension) {
        if (minecraft.getConnection() == null) return false;
        var portal = SkyesightPortalApi.getPortal(id.toString());
        if (portal == null || !portal.active() || !portal.target().dimension().equals(dimension)) return false;
        var lease = new Lease(portal.generation(), dimension);
        if (lease.equals(LEASES.get(id))) return true;
        retire(id);
        LEASES.put(id, lease);
        send(id, lease, true);
        // The ordered lifecycle packet must precede the initial terrain request.
        return false;
    }
    private static void send(ResourceLocation id, Lease lease, boolean active) {
        if (Minecraft.getInstance().getConnection() != null)
            PacketDistributor.sendToServer(new SkyesightRemoteViewLifecyclePayload(id, lease.generation, lease.dimension, active));
    }
    private static void retire(ResourceLocation id) {
        var lease = LEASES.remove(id);
        if (lease == null) return;
        SkyesightClientChunkRequester.reset(id, lease.generation);
        send(id, lease, false);
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        for (var id : java.util.List.copyOf(LEASES.keySet())) {
            var portal = SkyesightPortalApi.getPortal(id.toString());
            var lease = LEASES.get(id);
            if (portal == null || !portal.active() || portal.generation() != lease.generation
                    || !portal.target().dimension().equals(lease.dimension)) retire(id);
        }
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        for (var id : java.util.List.copyOf(LEASES.keySet())) retire(id);
    }
}
