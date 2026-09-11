package com.skyeshade.skyesight.client.world;

import com.skyeshade.skyesight.Skyesight;
import com.skyeshade.skyesight.network.SkyesightDimensionMetadataPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/** Connection-scoped metadata survives physical dimension changes, never server changes. */
@EventBusSubscriber(modid = Skyesight.MODID, value = Dist.CLIENT)
public final class SkyesightClientDimensionMetadata {
    private static final Map<ResourceKey<Level>, Holder<DimensionType>> TYPES = new HashMap<>();
    private static final Map<ResourceKey<Level>, Long> LAST_WARNING = new HashMap<>();
    private static ClientPacketListener owner;

    private SkyesightClientDimensionMetadata() {}

    public static void accept(SkyesightDimensionMetadataPayload payload) {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection == null) return;
        bind(connection);
        TYPES.put(payload.dimension(), payload.dimensionType());
        LAST_WARNING.remove(payload.dimension());
    }

    @Nullable
    public static Holder<DimensionType> resolve(ClientPacketListener connection, ResourceKey<Level> dimension) {
        bind(connection);
        Holder<DimensionType> type = TYPES.get(dimension);
        if (type == null) {
            long now = System.nanoTime();
            Long previous = LAST_WARNING.get(dimension);
            if (previous == null || now - previous >= 5_000_000_000L) {
                LAST_WARNING.put(dimension, now);
                Skyesight.LOGGER.warn("[Skyesight] VISUAL_DIMENSION_TYPE_UNAVAILABLE dim={} reason=awaiting_server_metadata",
                        dimension.location());
            }
        }
        return type;
    }

    private static void bind(ClientPacketListener connection) {
        if (owner != connection) {
            TYPES.clear();
            LAST_WARNING.clear();
            owner = connection;
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        bind(null);
    }
}
