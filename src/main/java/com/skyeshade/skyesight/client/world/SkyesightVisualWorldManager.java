package com.skyeshade.skyesight.client.world;

import com.skyeshade.skyesight.Skyesight;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;

public final class SkyesightVisualWorldManager {
    private static final Map<ResourceLocation, SkyesightVisualWorld> WORLDS = new HashMap<>();

    private SkyesightVisualWorldManager() {}

    public static void tickAll() {
        for (SkyesightVisualWorld world : WORLDS.values()) {
            if (!world.isClosed()) {
                world.tick();
            }
        }
    }

    public static SkyesightVisualWorld get(ResourceLocation viewId) {
        return WORLDS.get(viewId);
    }

    public static SkyesightVisualWorld getOrCreate(
            ResourceLocation viewId,
            ResourceKey<Level> dimension
    ) {
        SkyesightVisualWorld existing = WORLDS.get(viewId);

        if (existing != null && !existing.isClosed()) {
            return existing;
        }

        if (existing != null) {
            WORLDS.remove(viewId);
        }

        Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.level == null || minecraft.getConnection() == null) {
            return null;
        }

        ClientLevel skyesightLevel = SkyesightClientLevelFactory.create(dimension);
        SkyesightVisualWorld world = new SkyesightVisualWorld(dimension, skyesightLevel);

        Skyesight.LOGGER.info(
                "[Skyesight] Created visual world view={} dimension={} sameObjectAsMain={}",
                viewId,
                dimension.location(),
                skyesightLevel == minecraft.level
        );

        WORLDS.put(viewId, world);

        return world;
    }

    public static void close(ResourceLocation viewId) {
        SkyesightVisualWorld world = WORLDS.remove(viewId);

        if (world != null) {
            world.close();
        }
    }

    public static void closeAll() {
        for (SkyesightVisualWorld world : WORLDS.values()) {
            world.close();
        }

        WORLDS.clear();
    }
}