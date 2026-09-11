package com.skyeshade.skyesight.server;

import com.skyeshade.skyesight.Skyesight;
import com.skyeshade.skyesight.network.SkyesightParticleWatchPayload;
import com.skyeshade.skyesight.remote.SecondaryParticlePolicy;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import java.util.*;
import java.util.function.BiConsumer;

@EventBusSubscriber(modid = Skyesight.MODID)
public final class SkyesightParticleWatches {
    private record Watch(SkyesightParticleWatchPayload request, long expires) {}
    private static final Map<UUID, Map<ResourceLocation, Watch>> WATCHES = new HashMap<>();
    private static long tick;
    private SkyesightParticleWatches() {}

    public static void update(ServerPlayer player, SkyesightParticleWatchPayload request) {
        var views = WATCHES.computeIfAbsent(player.getUUID(), ignored -> new HashMap<>());
        var previous = views.get(request.viewId());
        if (request.generation() <= 0 || previous != null && request.generation() < previous.request.generation()) return;
        if (!request.active()) {
            if (previous != null && previous.request.generation() == request.generation()) views.remove(request.viewId());
            return;
        }
        if (player.server.getLevel(request.dimension()) == null || request.radius() < 1 || request.radius() > SecondaryParticlePolicy.MAX_RADIUS
                || !SecondaryParticlePolicy.contains(request.center(), 0, request.center().x, request.center().y, request.center().z)
                || views.size() >= 64 && previous == null) return;
        views.put(request.viewId(), new Watch(request, tick + SecondaryParticlePolicy.SERVER_LEASE_TICKS));
    }

    public static void forEvent(ServerLevel level, ServerPlayer recipient, double x, double y, double z,
                               BiConsumer<ServerPlayer, SkyesightParticleWatchPayload> consumer) {
        for (var playerEntry : WATCHES.entrySet()) {
            var player = level.getServer().getPlayerList().getPlayer(playerEntry.getKey());
            if (player == null || recipient != null && recipient != player) continue;
            for (var watch : playerEntry.getValue().values()) {
                var request = watch.request;
                if (watch.expires >= tick && request.dimension().equals(level.dimension())
                        && SecondaryParticlePolicy.contains(request.center(), request.radius(), x, y, z)) consumer.accept(player, request);
            }
        }
    }

    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        tick++;
        WATCHES.values().forEach(views -> views.values().removeIf(watch -> watch.expires < tick));
        WATCHES.values().removeIf(Map::isEmpty);
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) { WATCHES.remove(event.getEntity().getUUID()); }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { WATCHES.clear(); tick = 0; }
}
