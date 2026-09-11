package com.skyeshade.skyesight.server;

import com.skyeshade.skyesight.network.SkyesightEnvironmentPayload;
import com.skyeshade.skyesight.remote.SkyesightRemoteViewRegistration;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameRules;
import net.neoforged.neoforge.network.PacketDistributor;

public final class SkyesightServerEnvironmentSender {
    public static final int UPDATE_INTERVAL_TICKS = 20;

    private SkyesightServerEnvironmentSender() {}

    public static void send(
            ServerPlayer player,
            SkyesightServerViewTracker.ViewWatch watch,
            ServerLevel targetLevel
    ) {
        if (player == null || watch == null || targetLevel == null) {
            return;
        }

        SkyesightRemoteViewRegistration registration =
                SkyesightServerRemoteViewRegistry.resolve(player, watch.viewId()).orElse(null);
        if (registration == null
                || registration.generation() != watch.generation()
                || !registration.targetDimension().equals(watch.dimension())
                || !targetLevel.dimension().equals(watch.dimension())) {
            return;
        }

        PacketDistributor.sendToPlayer(
                player,
                new SkyesightEnvironmentPayload(
                        watch.viewId(),
                        targetLevel.dimension(),
                        registration.generation(),
                        targetLevel.getGameTime(),
                        targetLevel.getDayTime(),
                        targetLevel.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT),
                        targetLevel.getDayTimeFraction(),
                        targetLevel.getDayTimePerTick(),
                        targetLevel.getLevelData().isRaining(),
                        targetLevel.getLevelData().isThundering(),
                        targetLevel.rainLevel,
                        targetLevel.thunderLevel
                )
        );
    }
}
