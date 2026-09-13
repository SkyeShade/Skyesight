package com.skyeshade.skyesight.server;

import com.skyeshade.skyesight.api.SkyesightPortalRegistry;
import com.skyeshade.skyesight.remote.SkyesightRemoteViewRegistration;
import com.skyeshade.skyesight.server.portal.TraversalPortalManager;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Player-scoped server mirror for network camera and portal observers.
 * Unpublished integrated portals may use the process-global fallback.
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
        // A LAN host shares the client portal registry with its server. A global
        // entry must not prevent another observer's independent network generation.
        if (!player.isAlive() || player.isRemoved()) return false;
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
        return resolve(player.getUUID(), viewId);
    }

    static synchronized Optional<SkyesightRemoteViewRegistration> resolve(UUID playerId, ResourceLocation viewId) {
        var dynamic = REGISTRATIONS.getOrDefault(playerId, Map.of()).get(viewId);
        if (dynamic != null) return Optional.of(dynamic);
        // A retired network lease must not be resurrected through the LAN host's
        // process-global portal entry, even when their generation numbers happen to match.
        if (LAST_GENERATIONS.getOrDefault(playerId, Map.of()).containsKey(viewId)) return Optional.empty();
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
    public static synchronized Collection<SkyesightRemoteViewRegistration> removeNonTraversalViews(ServerPlayer player) {
        var views = REGISTRATIONS.get(player.getUUID());
        if (views == null) return List.of();
        var removed = new ArrayList<SkyesightRemoteViewRegistration>();
        views.values().removeIf(view -> {
            if (TraversalPortalManager.ownsView(view.viewId())) return false;
            removed.add(view); return true;
        });
        if (views.isEmpty()) REGISTRATIONS.remove(player.getUUID());
        return removed;
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
