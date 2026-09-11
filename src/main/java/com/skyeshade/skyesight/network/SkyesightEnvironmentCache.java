package com.skyeshade.skyesight.network;

import com.skyeshade.skyesight.remote.SkyesightRemoteViewRegistry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import java.util.HashMap;
import java.util.Map;

/** Immutable authoritative snapshots may arrive before their visual level exists. */
final class SkyesightEnvironmentCache {
    private final Map<ResourceLocation, SkyesightEnvironmentPayload> snapshots = new HashMap<>();
    private Object connection;
    private void connection(Object current) {
        if (current != connection) { snapshots.clear(); connection = current; }
    }
    void put(Object connection, SkyesightEnvironmentPayload payload) {
        connection(connection);
        if (SkyesightRemoteViewRegistry.accepts(payload.viewId(), payload.generation(), payload.dimension()))
            snapshots.put(payload.viewId(), payload);
    }
    SkyesightEnvironmentPayload get(Object connection, ResourceLocation id, ResourceKey<Level> dimension) {
        connection(connection);
        var payload = snapshots.get(id);
        return payload != null && payload.dimension().equals(dimension)
                && SkyesightRemoteViewRegistry.accepts(id, payload.generation(), dimension) ? payload : null;
    }
    void remove(ResourceLocation id) { snapshots.remove(id); }
    void clear() { snapshots.clear(); connection = null; }
}
