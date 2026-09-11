package com.skyeshade.skyesight.client.transition;

import com.skyeshade.skyesight.Skyesight;
import com.skyeshade.skyesight.api.*;
import com.skyeshade.skyesight.network.SkyesightTraversalPayload;
import com.skyeshade.skyesight.portal.PortalTraversalMath;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import java.util.*;

@EventBusSubscriber(modid = Skyesight.MODID, value = Dist.CLIENT)
public final class TraversalPortalClient {
    private static final Map<UUID, Long> REVISIONS = new HashMap<>();
    private static SkyesightTransition prepared;
    private static String selected;
    private static long lastSequence;
    private record Motion(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension,
                          net.minecraft.world.phys.Vec3 position, net.minecraft.world.phys.Vec3 velocity,
                          long deadline, Object connection, boolean onGround, float fallDistance,
                          net.minecraft.world.entity.Pose pose, float headYaw, float bodyYaw,
                          float walkDist, float walkDistO, float bob, float oBob, boolean sprinting,
                          net.minecraft.world.phys.Vec3 previousOffset, net.minecraft.world.phys.Vec3 oldOffset,
                          float previousYaw, float previousPitch) {}
    private static Motion motion;
    private static com.skyeshade.skyesight.portal.PortalCrossingState eyeSide = new com.skyeshade.skyesight.portal.PortalCrossingState();
    private static double predictedSourceSide;
    private static String guardedPortal;
    private static PortalEndpoint guardedExit;
    private TraversalPortalClient() {}
    public static void receive(SkyesightTraversalPayload message) {
        UUID owner = message.owner();
        String a = SkyesightTraversalPayload.portalId(owner, true), b = SkyesightTraversalPayload.portalId(owner, false);
        if (message.action() == SkyesightTraversalPayload.DEFINE) {
            if (message.revision() <= REVISIONS.getOrDefault(owner, -1L)) return;
            SkyesightPortalApi.registerPortalPair(a, message.a(), b, message.b(), true, true, "debug-traversal", true, true);
            com.skyeshade.skyesight.client.render.PlayerPerspectiveViews.register(a);
            com.skyeshade.skyesight.client.render.PlayerPerspectiveViews.register(b);
            REVISIONS.put(owner, message.revision());
        } else if (Objects.equals(REVISIONS.get(owner), message.revision())) {
            if (message.action() == SkyesightTraversalPayload.REMOVE) {
                SkyesightPortalApi.removePortalPair(a, b);
                com.skyeshade.skyesight.client.render.PlayerPerspectiveViews.remove(a);
                com.skyeshade.skyesight.client.render.PlayerPerspectiveViews.remove(b);
                if (a.equals(selected) || b.equals(selected)) cancel();
                if (a.equals(guardedPortal) || b.equals(guardedPortal)) { guardedPortal = null; guardedExit = null; }
            } else if (message.action() == SkyesightTraversalPayload.BEGIN && message.sequence() > lastSequence) {
                lastSequence = message.sequence();
                String source = message.fromA() ? a : b;
                var mc = Minecraft.getInstance();
                var portal = SkyesightPortalApi.getPortal(source);
                if (portal != null && mc.player != null && mc.level != null
                        && portal.source().dimension().equals(mc.level.dimension())) {
                    guardedPortal = message.fromA() ? b : a;
                    guardedExit = portal.target();
                    // Vanilla absolute position packets zero velocity. Preserve the actual local
                    // motion (server player deltaMovement omits walking) across this ordered teleport.
                    motion = new Motion(portal.target().dimension(), message.destinationFeet(),
                            PortalTraversalMath.rotate(mc.player.getDeltaMovement(), PortalTraversalMath.rotation(
                                    portal.source().rotation(), portal.target().rotation())),
                            net.minecraft.Util.getMillis() + 2000, mc.getConnection(), mc.player.onGround(), mc.player.fallDistance,
                            mc.player.getPose(), transformedYaw(portal, mc.player.getYHeadRot()), transformedYaw(portal, mc.player.yBodyRot),
                            mc.player.walkDist, mc.player.walkDistO, mc.player.bob, mc.player.oBob, mc.player.isSprinting(),
                            PortalTraversalMath.rotate(new net.minecraft.world.phys.Vec3(mc.player.xo, mc.player.yo, mc.player.zo).subtract(mc.player.position()),
                                    PortalTraversalMath.rotation(portal.source().rotation(), portal.target().rotation())),
                            PortalTraversalMath.rotate(new net.minecraft.world.phys.Vec3(mc.player.xOld, mc.player.yOld, mc.player.zOld).subtract(mc.player.position()),
                                    PortalTraversalMath.rotation(portal.source().rotation(), portal.target().rotation())),
                            transformedYaw(portal, mc.player.yRotO), mc.player.xRotO);
                    if (Boolean.getBoolean("skyesight.debugTraversal")) Skyesight.LOGGER.info(
                            "[TraversalMotion] sourceFeet={} sourceEye={} predictedFeet={} requestedFeet={} velocity={} previousOffset={} ground={} fall={} pose={}",
                            mc.player.position(), mc.player.getEyePosition(), PortalTraversalMath.position(portal.source(), portal.target(), mc.player.position()),
                            message.destinationFeet(), motion.velocity, motion.previousOffset, motion.onGround, motion.fallDistance, motion.pose);
                }
                boolean began = source.equals(selected) && prepared != null && prepared.beginLive();
                if (!began) cancel();
                if (Boolean.getBoolean("skyesight.debugTraversal")) Skyesight.LOGGER.info(
                        "[Traversal] token={} source={} prepared={}", message.sequence(), source, began);
            }
        }
    }
    public static void authoritativePosition() {
        var pending = motion; motion = null;
        var mc = Minecraft.getInstance();
        if (pending != null && mc.player != null && mc.level != null && mc.getConnection() == pending.connection
                && net.minecraft.Util.getMillis() <= pending.deadline && mc.level.dimension().equals(pending.dimension)
                && mc.player.position().distanceToSqr(pending.position) < .01) {
            mc.player.setDeltaMovement(pending.velocity);
            mc.player.setPose(pending.pose);
            var collision = SecondaryTransition.collisionView(mc.level, mc.player);
            boolean supported = pending.onGround && pending.velocity.y <= 0
                    && collision.noCollision(mc.player, mc.player.getBoundingBox())
                    && !collision.noCollision(mc.player,
                            mc.player.getBoundingBox().move(0, -1e-5, 0));
            mc.player.setOnGround(supported);
            mc.player.verticalCollision = mc.player.verticalCollisionBelow = supported;
            mc.player.fallDistance = supported ? 0 : pending.fallDistance;
            mc.player.setYHeadRot(pending.headYaw); mc.player.yHeadRotO = pending.headYaw;
            mc.player.yBodyRot = mc.player.yBodyRotO = pending.bodyYaw;
            mc.player.walkDist = pending.walkDist; mc.player.walkDistO = pending.walkDistO;
            mc.player.bob = pending.bob; mc.player.oBob = pending.oBob;
            mc.player.setSprinting(pending.sprinting);
            // Absolute teleport packets collapse both interpolation endpoints. Preserve the old
            // render interval relative to the authoritative current position, never move that position.
            var previous = mc.player.position().add(pending.previousOffset);
            var old = mc.player.position().add(pending.oldOffset);
            mc.player.xo = previous.x; mc.player.yo = previous.y; mc.player.zo = previous.z;
            mc.player.xOld = old.x; mc.player.yOld = old.y; mc.player.zOld = old.z;
            mc.player.yRotO = mc.player.getYRot() + net.minecraft.util.Mth.wrapDegrees(pending.previousYaw - mc.player.getYRot());
            mc.player.xRotO = pending.previousPitch;
            if (Boolean.getBoolean("skyesight.debugTraversal")) Skyesight.LOGGER.info(
                    "[Traversal] restored local momentum={} feet={} requestedDelta={} ground={} fall={} receivedCollisionChunk={}",
                    pending.velocity, mc.player.position(), mc.player.position().subtract(pending.position), supported, mc.player.fallDistance,
                    SecondaryCollisionView.received(mc.level, mc.player.chunkPosition().x, mc.player.chunkPosition().z) != null);
        }
    }
    private static float transformedYaw(RegisteredPortalView portal, float yaw) {
        return PortalTraversalMath.yaw(PortalTraversalMath.rotate(net.minecraft.world.phys.Vec3.directionFromRotation(0, yaw),
                PortalTraversalMath.rotation(portal.source().rotation(), portal.target().rotation())));
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        SecondaryTransition.tickRetained();
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.isPaused()) return;
        if (guardedExit != null && guardedExit.dimension().equals(mc.level.dimension())
                && PortalTraversalMath.clearedExit(guardedExit, mc.player.position(), mc.player.getBbWidth())) {
            guardedPortal = null; guardedExit = null;
        }
        if (prepared != null && (prepared.status() == SkyesightTransition.Status.PRESENTING || prepared.status() == SkyesightTransition.Status.PREDICTING)) return;
        String nearest = null; double distance = 36;
        for (UUID owner : REVISIONS.keySet()) for (boolean a : new boolean[]{true, false}) {
            String id = SkyesightTraversalPayload.portalId(owner, a);
            var portal = SkyesightPortalApi.getPortal(id);
            if (portal == null || !portal.active() || !portal.source().dimension().equals(mc.level.dimension())) continue;
            var local = PortalTraversalMath.local(portal.source(), mc.player.position());
            double d = mc.player.position().distanceToSqr(portal.source().center());
            if (d < distance && Math.abs(local.x) < 2 && Math.abs(local.y) < 3) { distance = d; nearest = id; }
        }
        if (!Objects.equals(selected, nearest)) {
            cancel();
            if (nearest != null) { selected = nearest; prepared = SkyesightTransitionApi.preparePortalTransition(ResourceLocation.parse(nearest)); }
        } else if (nearest != null && prepared != null && prepared.status() != SkyesightTransition.Status.READY
                && prepared.status() != SkyesightTransition.Status.PREPARING) {
            cancel(); // Reprepare on the following tick after a completed/fallback handoff.
        }
    }
    /** Render-frame eye sampling, independent of server feet-crossing authority. */
    public static void beforePresentation() {
        var mc = Minecraft.getInstance();
        if (prepared == null || selected == null || mc.player == null || mc.level == null
                || !mc.options.getCameraType().isFirstPerson()) return;
        if (selected.equals(guardedPortal)) return;
        var portal = SkyesightPortalApi.getPortal(selected);
        if (portal == null || !portal.active() || !portal.source().dimension().equals(mc.level.dimension())) return;
        var eye = mc.player.getEyePosition(mc.getTimer().getGameTimeDeltaPartialTick(true));
        double distance = PortalTraversalMath.local(portal.source(), eye).z;
        int stableSide = eyeSide.stableSide();
        var confirmed = eyeSide.update(portal.source(), eye.subtract(0, mc.player.getEyeHeight(), 0), mc.player.getBbWidth(), mc.player.getBbHeight());
        if (prepared.status() == SkyesightTransition.Status.PREDICTING) {
            if (distance * predictedSourceSide > com.skyeshade.skyesight.portal.PortalCrossingState.EXIT_PLANE) cancel();
            return;
        }
        if (prepared.status() == SkyesightTransition.Status.READY && stableSide != 0) {
            if (confirmed != null || eyeSide.hasPendingCrossing() && distance * stableSide <= 0) {
                predictedSourceSide = stableSide;
                boolean predicted = prepared.predictCrossing();
                if (Boolean.getBoolean("skyesight.debugTraversal")) Skyesight.LOGGER.info(
                        "[LiveTransition] eyeCross time={} signedDistance={} predicted={}", net.minecraft.Util.getMillis(), distance, predicted);
            }
        }
        // Keep the last unambiguous side across the on-plane tolerance band.
    }
    private static void cancel() {
        eyeSide = new com.skyeshade.skyesight.portal.PortalCrossingState();
        if (prepared != null) prepared.close(); prepared = null; selected = null;
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        cancel(); motion = null; guardedPortal = null; guardedExit = null;
        SkyesightPortalApi.clearPortalsBySource("debug-traversal"); REVISIONS.clear(); lastSequence = 0;
        com.skyeshade.skyesight.client.render.PlayerPerspectiveViews.clear();
    }
}
