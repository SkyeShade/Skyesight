package com.skyeshade.skyesight.server;

import com.skyeshade.skyesight.remote.SkyesightRemoteViewRegistration;
import com.skyeshade.skyesight.api.SkyesightPortalRegistry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Player-scoped server mirror for remote views created through the generic
 * camera API. Portal registrations remain global in SkyesightRemoteViewRegistry.
 */
public final class SkyesightServerRemoteViewRegistry {
    private static final int MAX_DYNAMIC_VIEWS_PER_PLAYER = 32;
    private static final Map<UUID, Map<ResourceLocation, SkyesightRemoteViewRegistration>> REGISTRATIONS =
            new HashMap<>();

    private static final Map<UUID, Map<ResourceLocation, Long>> LAST_GENERATIONS = new HashMap<>();

    private SkyesightServerRemoteViewRegistry() {}

    public static synchronized boolean register(
            ServerPlayer player,
            ResourceLocation viewId,
            ResourceKey<Level> targetDimension,
            long generation
    ) {
        if (player == null || viewId == null || targetDimension == null || generation <= 0L) {
            return false;
        }
        if (!player.isAlive() || player.isRemoved() || SkyesightPortalRegistry.contains(viewId)) return false;
        return register(player.getUUID(), viewId, targetDimension, generation);
    }

    static synchronized boolean register(UUID playerId, ResourceLocation viewId,
                                         ResourceKey<Level> targetDimension, long generation) {
        if (generation <= 0) return false;
        var history = LAST_GENERATIONS.computeIfAbsent(playerId, ignored -> new HashMap<>());
        var existing = REGISTRATIONS.getOrDefault(playerId, Map.of()).get(viewId);
        long last = history.getOrDefault(viewId, 0L);
        if (generation <= last && (existing == null || !existing.accepts(generation, targetDimension))) return false;
        Map<ResourceLocation, SkyesightRemoteViewRegistration> playerViews =
                REGISTRATIONS.computeIfAbsent(playerId, ignored -> new HashMap<>());
        if (!playerViews.containsKey(viewId) && playerViews.size() >= MAX_DYNAMIC_VIEWS_PER_PLAYER) {
            return false;
        }

        history.put(viewId, generation);
        playerViews.put(
                viewId,
                new SkyesightRemoteViewRegistration(viewId, targetDimension, generation)
        );
        return true;
    }

    public static synchronized Optional<SkyesightRemoteViewRegistration> get(
            ServerPlayer player,
            ResourceLocation viewId
    ) {
        if (player == null || viewId == null) {
            return Optional.empty();
        }
        Map<ResourceLocation, SkyesightRemoteViewRegistration> playerViews =
                REGISTRATIONS.get(player.getUUID());
        return playerViews == null
                ? Optional.empty()
                : Optional.ofNullable(playerViews.get(viewId));
    }

    public static synchronized Optional<SkyesightRemoteViewRegistration> resolve(
            ServerPlayer player,
            ResourceLocation viewId
    ) {
        Optional<SkyesightRemoteViewRegistration> dynamic = get(player, viewId);
        if (dynamic.isPresent()) return dynamic;
        // Only actual portal registrations may use the global fallback. A host client's
        // camera entry must never authorize another player's stale/missing camera view.
        var portal = SkyesightPortalRegistry.get(viewId);
        return portal == null ? Optional.empty() : Optional.of(new SkyesightRemoteViewRegistration(
                viewId, portal.target().dimension(), portal.generation()));
    }

    public static synchronized Optional<SkyesightRemoteViewRegistration> unregister(
            ServerPlayer player,
            ResourceLocation viewId,
            long generation
    ) {
        if (player == null || viewId == null) {
            return Optional.empty();
        }
        return unregister(player.getUUID(), viewId, generation);
    }

    static synchronized Optional<SkyesightRemoteViewRegistration> unregister(UUID playerId,
                                                                            ResourceLocation viewId, long generation) {
        Map<ResourceLocation, SkyesightRemoteViewRegistration> playerViews =
                REGISTRATIONS.get(playerId);
        if (playerViews == null) {
            return Optional.empty();
        }
        SkyesightRemoteViewRegistration registration = playerViews.get(viewId);
        if (registration == null || registration.generation() != generation) {
            return Optional.empty();
        }
        playerViews.remove(viewId);
        if (playerViews.isEmpty()) {
            REGISTRATIONS.remove(playerId);
        }
        return Optional.of(registration);
    }

    public static synchronized Collection<SkyesightRemoteViewRegistration> removePlayer(
            ServerPlayer player
    ) {
        if (player == null) {
            return List.of();
        }
        Map<ResourceLocation, SkyesightRemoteViewRegistration> removed =
                REGISTRATIONS.remove(player.getUUID());
        return removed == null ? List.of() : List.copyOf(removed.values());
    }

    public static synchronized void forget(ServerPlayer player) {
        REGISTRATIONS.remove(player.getUUID());
        LAST_GENERATIONS.remove(player.getUUID());
    }

    public static synchronized void clear() {
        LAST_GENERATIONS.clear();
        REGISTRATIONS.clear();
    }
}
