package com.skyeshade.skyesight.server.portal;

import com.skyeshade.skyesight.Skyesight;
import com.skyeshade.skyesight.api.PortalEndpoint;
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

/** Session-scoped debug infrastructure. All traversal decisions run on the server thread. */
@EventBusSubscriber(modid = Skyesight.MODID)
public final class TraversalPortalManager {
    private record Pair(long revision, PortalEndpoint a, PortalEndpoint b) {}
    private record Sample(ResourceKey<Level> dimension, Vec3 feet) {}
    private record Guard(UUID pair, PortalEndpoint exit) {}
    private static final Map<UUID, PortalEndpoint> SELECTIONS = new HashMap<>();
    private static final Map<UUID, Pair> PAIRS = new LinkedHashMap<>();
    private static final Map<UUID, Sample> PREVIOUS = new HashMap<>();
    private static final Map<UUID, Guard> GUARDS = new HashMap<>();
    private record SideKey(UUID owner, boolean fromA, long revision) {}
    private static final Map<UUID, Map<SideKey, com.skyeshade.skyesight.portal.PortalCrossingState>> SIDES = new HashMap<>();
    private static long revision, sequence;
    private TraversalPortalManager() {}
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
        Pair pair = new Pair(++revision, a, endpoint);
        PAIRS.put(owner, pair);
        PacketDistributor.sendToAllPlayers(definition(owner, pair));
        player.sendSystemMessage(Component.literal("Created 2x3 pair " + SkyesightTraversalPayload.portalId(owner, true)
                + " <-> " + SkyesightTraversalPayload.portalId(owner, false) + ". Walk through either side; sneak-use to remove."));
    }
    public static void clear(ServerPlayer player) {
        UUID owner = player.getUUID(); SELECTIONS.remove(owner); Pair pair = PAIRS.remove(owner);
        if (pair != null) PacketDistributor.sendToAllPlayers(new SkyesightTraversalPayload(owner, pair.revision,
                0, SkyesightTraversalPayload.REMOVE, true, null, null));
        GUARDS.values().removeIf(guard -> guard.pair.equals(owner));
        SIDES.values().forEach(states -> states.keySet().removeIf(key -> key.owner.equals(owner)));
        player.sendSystemMessage(Component.literal("Traversal selection/pair removed."));
    }
    private static SkyesightTraversalPayload definition(UUID owner, Pair pair) {
        return new SkyesightTraversalPayload(owner, pair.revision, 0, SkyesightTraversalPayload.DEFINE, true, pair.a, pair.b);
    }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player)
            PAIRS.forEach((owner, pair) -> PacketDistributor.sendToPlayer(player, definition(owner, pair)));
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        PREVIOUS.remove(event.getEntity().getUUID()); GUARDS.remove(event.getEntity().getUUID());
        SIDES.remove(event.getEntity().getUUID());
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) {
        PAIRS.clear(); SELECTIONS.clear(); PREVIOUS.clear(); GUARDS.clear(); revision = sequence = 0;
        SIDES.clear();
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) movementAccepted(player);
    }
    /** Called after vanilla validates movement, before another movement packet can accumulate. */
    public static void movementAccepted(ServerPlayer player) {
        if (PAIRS.isEmpty()) return;
        UUID id = player.getUUID(); Vec3 current = player.position();
        Sample previous = PREVIOUS.put(id, new Sample(player.level().dimension(), current));
        if (previous == null || !previous.dimension.equals(player.level().dimension())
                || previous.feet.distanceToSqr(current) > 64 || !player.isAlive() || player.isPassenger()) return;
        Guard guard = GUARDS.get(id);
        if (guard != null && (!guard.exit.dimension().equals(player.level().dimension())
                || PortalTraversalMath.clearedExit(guard.exit, current, player.getBbWidth()))) {
            GUARDS.remove(id); guard = null;
        }
        boolean traversed = false;
        for (var entry : PAIRS.entrySet()) {
            if (guard != null && guard.pair.equals(entry.getKey())) continue;
            for (boolean fromA : new boolean[]{true, false}) {
                Pair pair = entry.getValue(); PortalEndpoint source = fromA ? pair.a : pair.b, target = fromA ? pair.b : pair.a;
                if (!source.dimension().equals(player.level().dimension())) continue;
                var states = SIDES.computeIfAbsent(id, ignored -> new HashMap<>());
                var side = states.computeIfAbsent(new SideKey(entry.getKey(), fromA, pair.revision), ignored -> {
                    var state = new com.skyeshade.skyesight.portal.PortalCrossingState();
                    state.update(source, previous.feet, player.getBbWidth(), player.getBbHeight());
                    return state;
                });
                Vec3 hit = side.update(source, current, player.getBbWidth(), player.getBbHeight());
                if (hit == null) continue;
                var destination = player.server.getLevel(target.dimension()); if (destination == null) continue;
                var rotation = PortalTraversalMath.rotation(source.rotation(), target.rotation());
                // ServerPlayer horizontal deltaMovement does not track client-controlled walking.
                Vec3 incomingVelocity = current.subtract(previous.feet);
                Vec3 velocity = PortalTraversalMath.rotate(incomingVelocity, rotation);
                Vec3 look = PortalTraversalMath.rotate(player.getLookAngle(), rotation);
                float yaw = PortalTraversalMath.yaw(look), pitch = PortalTraversalMath.pitch(look);
                float headYaw = PortalTraversalMath.yaw(PortalTraversalMath.rotate(Vec3.directionFromRotation(0, player.getYHeadRot()), rotation));
                float bodyYaw = PortalTraversalMath.yaw(PortalTraversalMath.rotate(Vec3.directionFromRotation(0, player.yBodyRot), rotation));
                // current is already strictly past the source plane; the geometric re-entry
                // guard prevents ping-pong. An extra exit offset disagrees with the rendered eye.
                Vec3 position = PortalTraversalMath.position(source, target, current);
                boolean wasOnGround = player.onGround();
                float previousFallDistance = player.fallDistance;
                long token = ++sequence;
                PacketDistributor.sendToPlayer(player, new SkyesightTraversalPayload(entry.getKey(), pair.revision,
                        token, SkyesightTraversalPayload.BEGIN, fromA, null, null, position));
                player.teleportTo(destination, position.x, position.y, position.z, Set.of(), yaw, pitch);
                player.setYHeadRot(headYaw); player.yHeadRotO = headYaw;
                player.yBodyRot = bodyYaw; player.yBodyRotO = bodyYaw;
                player.setDeltaMovement(velocity);

                GUARDS.put(id, new Guard(entry.getKey(), target));
                PREVIOUS.put(id, new Sample(target.dimension(), position));
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
