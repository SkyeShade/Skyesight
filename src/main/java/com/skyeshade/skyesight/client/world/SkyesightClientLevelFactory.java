package com.skyeshade.skyesight.client.world;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;

import org.jetbrains.annotations.Nullable;

public final class SkyesightClientLevelFactory {
    private SkyesightClientLevelFactory() {}

    @Nullable
    public static SkyesightVisualClientLevel create(ResourceKey<Level> dimension) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientPacketListener connection = minecraft.getConnection();

        if (connection == null || minecraft.level == null) {
            throw new IllegalStateException("Cannot create Skyesight ClientLevel before the main client level exists");
        }

        Holder<DimensionType> dimensionType =
                resolveDimensionType(connection, minecraft.level, dimension);

        return dimensionType == null ? null : create(dimension, dimensionType);
    }

    public static SkyesightVisualClientLevel create(
            ResourceKey<Level> dimension,
            Holder<DimensionType> dimensionType
    ) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientPacketListener connection = minecraft.getConnection();

        if (connection == null || minecraft.level == null) {
            throw new IllegalStateException("Cannot create Skyesight ClientLevel before the main client level exists");
        }

        ClientLevel.ClientLevelData levelData = new ClientLevel.ClientLevelData(
                Difficulty.NORMAL,
                false,
                false
        );

        int viewDistance = minecraft.options.getEffectiveRenderDistance();
        int simulationDistance = 8;
        boolean debug = false;
        long biomeZoomSeed = 0L;

        return new SkyesightVisualClientLevel(
                connection,
                levelData,
                dimension,
                dimensionType,
                viewDistance,
                simulationDistance,
                minecraft::getProfiler,
                minecraft.levelRenderer,
                debug,
                biomeZoomSeed
        );
    }

    @Nullable
    private static Holder<DimensionType> resolveDimensionType(
            ClientPacketListener connection,
            ClientLevel fallbackLevel,
            ResourceKey<Level> dimension
    ) {
        if (dimension == fallbackLevel.dimension()) {
            return fallbackLevel.dimensionTypeRegistration();
        }

        return SkyesightClientDimensionMetadata.resolve(connection, dimension);
    }
}
