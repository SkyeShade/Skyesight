package com.skyeshade.skyesight.server.portal;

import com.skyeshade.skyesight.Skyesight;
import com.skyeshade.skyesight.api.*;
import com.skyeshade.skyesight.network.SkyesightTraversalPayload;
import com.skyeshade.skyesight.portal.PortalTraversalMath;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;

/** Canonical session-scoped server pair registry. All traversal decisions run on the server thread. */
@EventBusSubscriber(modid = Skyesight.MODID)
public final class TraversalPortalManager {
    private record Pair(long revision, PortalEndpoint a, PortalEndpoint b, PortalPairSettings settings) {}
    private record Sample(ResourceKey<Level> dimension, Vec3 feet) {}
    private static final Map<UUID, PortalEndpoint> SELECTIONS = new HashMap<>();
    private static final Map<UUID, Pair> PAIRS = new LinkedHashMap<>();
    private static final Map<ResourceKey<Level>, java.util.List<com.skyeshade.skyesight.api.SkyesightPortalRaycast.Link>> LINK_CACHE = new HashMap<>();
    private static final Map<UUID, Sample> PREVIOUS = new HashMap<>();
    private record SideKey(UUID owner, boolean fromA, long revision) {}
    private static final Map<UUID, Map<SideKey, com.skyeshade.skyesight.portal.PortalCrossingState>> SIDES = new HashMap<>();
    private static long revision, sequence;
    public static boolean ownsView(net.minecraft.resources.ResourceLocation id) {
        for (UUID owner : PAIRS.keySet()) {
            if (id.toString().equals(SkyesightTraversalPayload.portalId(owner, true))
                    || id.toString().equals(SkyesightTraversalPayload.portalId(owner, false))) return true;
        }
        return false;
    }
    private TraversalPortalManager() {}
    /** Immutable directional geometry snapshot; never consult the client portal registry on a server. */
    public static java.util.List<com.skyeshade.skyesight.api.SkyesightPortalRaycast.Link> links(ResourceKey<Level> dimension) {
        var cached = LINK_CACHE.get(dimension);
        if (cached != null) return cached;
        var result = new java.util.ArrayList<com.skyeshade.skyesight.api.SkyesightPortalRaycast.Link>();
        PAIRS.forEach((owner, pair) -> {
            if (pair.settings.behavior() != PortalBehavior.TRAVERSABLE) return;
            for (boolean fromA : new boolean[]{true, false}) {
                var source = fromA ? pair.a : pair.b; var target = fromA ? pair.b : pair.a;
                if (source.dimension().equals(dimension)) result.add(new com.skyeshade.skyesight.api.SkyesightPortalRaycast.Link(
                        net.minecraft.resources.ResourceLocation.parse(SkyesightTraversalPayload.portalId(owner, fromA)),
                        pair.revision, source, target, pair.settings.renderBackface(), (fromA ? pair.settings.apertureA() : pair.settings.apertureB()).bind(source)));
            }
        });
        var snapshot = java.util.List.copyOf(result);
        LINK_CACHE.put(dimension, snapshot);
        return snapshot;
    }
    public static void teleported(ServerPlayer player) { PREVIOUS.remove(player.getUUID()); SIDES.remove(player.getUUID()); }

    public static void select(ServerPlayer player, BlockPos block) {
        if (player.isShiftKeyDown()) { clear(player); return; }
        UUID owner = player.getUUID();
        if (PAIRS.containsKey(owner)) {
            player.sendSystemMessage(Component.literal("Sneak-use the traversal stick to remove your existing pair.")); return;
        }
        Direction facing = player.getDirection().getOpposite();
        PortalEndpoint endpoint = PortalEndpoint.of("traversal", player.level().dimension(),
                new Vec3(block.getX() + .5, block.getY() + 2.5, block.getZ() + .5), facing, 2, 3);
        PortalEndpoint a = SELECTIONS.remove(owner);
        player.sendSystemMessage(Component.literal("Traversal " + (a == null ? "A" : "B") + ": "
                + endpoint.dimension().location() + " center=" + endpoint.center() + " normal=" + PortalTraversalMath.normal(endpoint)));
        if (a == null) { SELECTIONS.put(owner, endpoint); return; }
        SkyesightPortalApi.registerPortalPair(player.server, owner, a, endpoint, PortalPairSettings.of(PortalBehavior.TRAVERSABLE));
        player.sendSystemMessage(Component.literal("Created 2x3 pair " + SkyesightTraversalPayload.portalId(owner, true)
                + " <-> " + SkyesightTraversalPayload.portalId(owner, false) + ". Walk through either side; sneak-use to remove."));
    }
    public static void clear(ServerPlayer player) {
        UUID owner = player.getUUID(); SELECTIONS.remove(owner);
        SkyesightPortalApi.removePortalPair(player.server, owner);
        player.sendSystemMessage(Component.literal("Traversal selection/pair removed."));
    }
    public static void registerPair(net.minecraft.server.MinecraftServer server, UUID owner, PortalEndpoint a, PortalEndpoint b) {
        SkyesightPortalApi.registerPortalPair(server,owner,a,b,PortalPairSettings.of(PortalBehavior.TRAVERSABLE));
    }
    public static void registerPair(net.minecraft.server.MinecraftServer server, UUID owner, PortalEndpoint a, PortalEndpoint b, PortalPairSettings settings) {
        var pair = new Pair(++revision, a, b, settings);
        PAIRS.put(owner, pair); LINK_CACHE.clear();
        SIDES.values().forEach(states -> states.keySet().removeIf(key -> key.owner.equals(owner)));
        PacketDistributor.sendToAllPlayers(definition(owner, pair));
    }
    public static void removePair(net.minecraft.server.MinecraftServer server, UUID owner) {
        Pair pair = PAIRS.remove(owner);
        LINK_CACHE.clear();
        if (pair != null) PacketDistributor.sendToAllPlayers(new SkyesightTraversalPayload(owner, pair.revision,
                0, SkyesightTraversalPayload.REMOVE, true, null, null));
        SIDES.values().forEach(states -> states.keySet().removeIf(key -> key.owner.equals(owner)));
    }
    private static SkyesightTraversalPayload definition(UUID owner, Pair pair) {
        return new SkyesightTraversalPayload(owner, pair.revision, 0, SkyesightTraversalPayload.DEFINE, true, pair.a, pair.b, Vec3.ZERO, pair.settings);
    }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player)
            PAIRS.forEach((owner, pair) -> PacketDistributor.sendToPlayer(player, definition(owner, pair)));
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        PREVIOUS.remove(event.getEntity().getUUID());
        SIDES.remove(event.getEntity().getUUID());
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) {
        PAIRS.clear(); SELECTIONS.clear(); PREVIOUS.clear(); revision = sequence = 0;
        SIDES.clear();
        LINK_CACHE.clear();
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        com.skyeshade.skyesight.server.SkyesightServerChunkLoader.expireTraversalStandby(event.getServer());
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) movementAccepted(player);
    }
    /** Called after vanilla validates movement, before another movement packet can accumulate. */
    public static void movementAccepted(ServerPlayer player) {
        if (PAIRS.isEmpty()) return;
        UUID id = player.getUUID(); Vec3 current = player.position();
        Sample previous = PREVIOUS.put(id, new Sample(player.level().dimension(), current));
        if (previous == null || !previous.dimension.equals(player.level().dimension())
                || !player.isAlive() || player.isPassenger()) return;
        boolean traversed = false;
        for (var entry : PAIRS.entrySet()) {
            for (boolean fromA : new boolean[]{true, false}) {
                Pair pair = entry.getValue(); if (pair.settings.behavior() != PortalBehavior.TRAVERSABLE) continue; PortalEndpoint source = fromA ? pair.a : pair.b, target = fromA ? pair.b : pair.a;
                                if (!source.dimension().equals(player.level().dimension())) continue;
                var aperture = fromA ? pair.settings.apertureA() : pair.settings.apertureB();
                java.util.function.Predicate<Vec3> fits = point -> aperture.fitsPlayer(source,point,player.getBbWidth(),player.getBbHeight());
                if (!pair.settings.renderBackface() && PortalTraversalMath.local(source,previous.feet).z < 0) continue;
                var states = SIDES.computeIfAbsent(id, ignored -> new HashMap<>());
                var side = states.computeIfAbsent(new SideKey(entry.getKey(), fromA, pair.revision), ignored -> {
                    var state = new com.skyeshade.skyesight.portal.PortalCrossingState();
                    state.update(source, previous.feet, fits);
                    return state;
                });
                Vec3 hit = side.update(source, current, fits);
                if (hit == null) continue;
                var exitShape=fromA ? pair.settings.apertureB() : pair.settings.apertureA();
                if (!exitShape.fitsPlayer(target,PortalTraversalMath.position(source,target,hit),player.getBbWidth(),player.getBbHeight())) continue;
                var destination = player.server.getLevel(target.dimension()); if (destination == null) continue;
                var rotation = PortalTraversalMath.rotation(source.rotation(), target.rotation());
                // ServerPlayer horizontal deltaMovement does not track client-controlled walking.
                Vec3 incomingVelocity = current.subtract(previous.feet);
                var pose = PortalTraversalMath.transform(source, target, current, incomingVelocity, player.getYRot(), player.getXRot());
                Vec3 velocity = pose.velocity();
                float yaw = pose.yaw(), pitch = pose.pitch();
                float headYaw = PortalTraversalMath.yaw(PortalTraversalMath.rotate(Vec3.directionFromRotation(0, player.getYHeadRot()), rotation));
                float bodyYaw = PortalTraversalMath.yaw(PortalTraversalMath.rotate(Vec3.directionFromRotation(0, player.yBodyRot), rotation));
                // current is already strictly past the source plane; the geometric re-entry
                // guard prevents ping-pong. An extra exit offset disagrees with the rendered eye.
                Vec3 position = pose.position();
                boolean wasOnGround = player.onGround();
                float previousFallDistance = player.fallDistance;
                long token = ++sequence;
                PacketDistributor.sendToPlayer(player, new SkyesightTraversalPayload(entry.getKey(), pair.revision,
                        token, SkyesightTraversalPayload.BEGIN, fromA, null, null, position));
                player.teleportTo(destination, position.x, position.y, position.z, Set.of(), yaw, pitch);
                player.setYHeadRot(headYaw); player.yHeadRotO = headYaw;
                player.yBodyRot = bodyYaw; player.yBodyRotO = bodyYaw;
                player.setDeltaMovement(velocity);

                PREVIOUS.put(id, new Sample(target.dimension(), position));
                // Teleport reset the old samples. Seed the reverse endpoint at arrival;
                // an actual return sweep is valid even before full-body exit clearance.
                var arrival = new com.skyeshade.skyesight.portal.PortalCrossingState();
                arrival.arrive(target, position);
                SIDES.computeIfAbsent(id, ignored -> new HashMap<>()).put(
                        new SideKey(entry.getKey(), !fromA, pair.revision), arrival);
                if (Boolean.getBoolean("skyesight.debugTraversal")) Skyesight.LOGGER.info(
                        "[Traversal] time={} player={} portal={} token={} hit={} local={} destination={} incomingVelocity={} velocity={} yaw={} pitch={} actualPosition={} actualVelocity={} ground={}->{} fall={}->{}",
                        System.currentTimeMillis(), id, SkyesightTraversalPayload.portalId(entry.getKey(), fromA), token, hit,
                        PortalTraversalMath.local(source, hit), position, incomingVelocity, velocity, yaw, pitch,
                        player.position(), player.getDeltaMovement(), wasOnGround, player.onGround(), previousFallDistance, player.fallDistance);
                traversed = true; break;
            }
            if (traversed) break;
        }
    }
}
