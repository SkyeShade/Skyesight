package com.skyeshade.skyesight.remote;

import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class SkyesightRemoteViewRegistry {
    private static final Map<ResourceLocation, SkyesightRemoteViewRegistration> REGISTRATIONS = new HashMap<>();

    private SkyesightRemoteViewRegistry() {}

    public static synchronized void register(
            ResourceLocation viewId,
            ResourceKey<Level> targetDimension,
            long generation
    ) {
        if (viewId == null || targetDimension == null) {
            return;
        }
        REGISTRATIONS.put(
                viewId,
                new SkyesightRemoteViewRegistration(viewId, targetDimension, generation)
        );
    }

    public static synchronized Optional<SkyesightRemoteViewRegistration> get(ResourceLocation viewId) {
        if (viewId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(REGISTRATIONS.get(viewId));
    }

    public static synchronized boolean accepts(
            ResourceLocation viewId,
            long generation,
            ResourceKey<Level> dimension
    ) {
        SkyesightRemoteViewRegistration registration = REGISTRATIONS.get(viewId);
        return registration != null && registration.accepts(generation, dimension);
    }

    public static synchronized boolean isCurrent(
            ResourceLocation viewId,
            ResourceKey<Level> dimension
    ) {
        SkyesightRemoteViewRegistration registration = REGISTRATIONS.get(viewId);
        return registration != null && registration.targets(dimension);
    }

    public static synchronized void unregister(ResourceLocation viewId) {
        if (viewId != null) {
            REGISTRATIONS.remove(viewId);
        }
    }

    public static synchronized void clear() {
        REGISTRATIONS.clear();
    }
}
