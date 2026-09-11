package com.skyeshade.skyesight.server;

import com.skyeshade.skyesight.Skyesight;
import com.skyeshade.skyesight.network.SkyesightDimensionMetadataPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = Skyesight.MODID)
public final class SkyesightDimensionMetadataEvents {
    private SkyesightDimensionMetadataEvents() {}

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            // Play login follows registry synchronization. Publish every actual server level,
            // so cameras and portals need neither a guessed type ID nor a chunk request first.
            for (var level : player.server.getAllLevels()) {
                PacketDistributor.sendToPlayer(player, new SkyesightDimensionMetadataPayload(
                        level.dimension(), level.dimensionTypeRegistration()));
            }
        }
    }
}
