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
    private static final Map<UUID, PortalPairSettings> DEFINITIONS = new HashMap<>();
    private static boolean gameplay(UUID id) { var s=DEFINITIONS.get(id); return s != null && s.behavior()==PortalBehavior.TRAVERSABLE; }
    private static SkyesightTransition prepared;
    private static String selected;
    private static long lastSequence;
    private record Motion(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension,
                          net.minecraft.world.phys.Vec3 position, net.minecraft.world.phys.Vec3 velocity,
                          long deadline, Object connection, boolean onGround, float fallDistance,
                          net.minecraft.world.entity.Pose pose, float headYaw, float bodyYaw,
                          float walkDist, float walkDistO, float bob, float oBob, boolean sprinting,
                          net.minecraft.world.phys.Vec3 previousOffset, net.minecraft.world.phys.Vec3 oldOffset,
                          float previousYaw, float previousPitch, FirstPersonState firstPerson) {}
    private record FirstPersonState(float xBob, float xBobO, float yBob, float yBobO,
                                    int attackTicks, float attack, float oldAttack, int swingTime,
                                    boolean swinging, net.minecraft.world.InteractionHand hand) {
        static FirstPersonState capture(RegisteredPortalView portal, net.minecraft.client.player.LocalPlayer player) {
            return new FirstPersonState(player.xBob, player.xBobO, transformedYaw(portal, player.yBob),
                    transformedYaw(portal, player.yBobO),
                    ((com.skyeshade.skyesight.mixin.common.LivingEntityAnimationAccessor) player).skyesight$getAttackStrengthTicker(),
                    player.attackAnim, player.oAttackAnim, player.swingTime, player.swinging, player.swingingArm);
        }
        void restore(net.minecraft.client.player.LocalPlayer player) {
            player.xBob = xBob; player.xBobO = xBobO;
            player.yBob = player.getYRot() + net.minecraft.util.Mth.wrapDegrees(yBob - player.getYRot());
            player.yBobO = player.yBob + net.minecraft.util.Mth.wrapDegrees(yBobO - player.yBob);
            ((com.skyeshade.skyesight.mixin.common.LivingEntityAnimationAccessor) player).skyesight$setAttackStrengthTicker(attackTicks);
            player.attackAnim = attack; player.oAttackAnim = oldAttack; player.swingTime = swingTime;
            player.swinging = swinging; player.swingingArm = hand;
        }
    }
    private static Motion motion;
    private static com.skyeshade.skyesight.portal.PortalCrossingState eyeSide = new com.skyeshade.skyesight.portal.PortalCrossingState();
    private static double predictedSourceSide;
    private static String guardedPortal;
    private static PortalEndpoint guardedExit;
    private static boolean awaitingArrivalEye;
    private static int arrivalSide;
    private static long arrivalDeadline;
    private TraversalPortalClient() {}
    /** Server-defined traversal links only; render-only camera/portal registrations confer no gameplay authority. */
    public static java.util.List<SkyesightPortalRaycast.Link> interactionLinks(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension) {
        var result = new java.util.ArrayList<SkyesightPortalRaycast.Link>();
        REVISIONS.forEach((owner, revision) -> {
            if (!gameplay(owner)) return;
            for (boolean fromA : new boolean[]{true, false}) {
                var portal = SkyesightPortalApi.getPortal(SkyesightTraversalPayload.portalId(owner, fromA));
                if (portal != null && portal.active() && portal.source().dimension().equals(dimension))
                    result.add(new SkyesightPortalRaycast.Link(portal.id(), revision, portal.source(), portal.target(), portal.renderBackface(), (fromA ? DEFINITIONS.get(owner).apertureA() : DEFINITIONS.get(owner).apertureB()).bind(portal.source())));
            }
        });
        return java.util.List.copyOf(result);
    }
    public static void receive(SkyesightTraversalPayload message) {
        com.skyeshade.skyesight.portal.PortalCollisions.clientAccess(new com.skyeshade.skyesight.portal.PortalCollisions.ClientAccess() {
            public Iterable<SkyesightPortalRaycast.Link> links(net.minecraft.world.level.Level source) { return interactionLinks(source.dimension()); }
            public net.minecraft.world.level.Level destination(SkyesightPortalRaycast.Link link) { return com.skyeshade.skyesight.client.portal.PortalInteractionClient.destination(link); }
            public boolean loaded(net.minecraft.world.level.Level level,net.minecraft.world.phys.AABB box) {
                return level instanceof net.minecraft.client.multiplayer.ClientLevel client && com.skyeshade.skyesight.client.portal.PortalInteractionClient.loaded(client,
                        new net.minecraft.world.phys.Vec3(box.minX,box.minY,box.minZ),new net.minecraft.world.phys.Vec3(box.maxX,box.maxY,box.maxZ));
            }
        });
        UUID owner = message.owner();
        String a = SkyesightTraversalPayload.portalId(owner, true), b = SkyesightTraversalPayload.portalId(owner, false);
        if (message.action() == SkyesightTraversalPayload.DEFINE) {
            if (message.revision() <= REVISIONS.getOrDefault(owner, -1L)) return;
            // A prepared frame captures endpoint transforms. Never carry it across a definition revision.
            if (a.equals(selected) || b.equals(selected)) cancel();
            if (a.equals(guardedPortal) || b.equals(guardedPortal)) {
                guardedPortal = null; guardedExit = null; awaitingArrivalEye = false;
            }
            TraversalPortalStandby.remove(a); TraversalPortalStandby.remove(b);
            var settings = message.settings();
            DEFINITIONS.put(owner,settings);
            SkyesightPortalApi.registerPortalPair(a, message.a(), b, message.b(),
                    settings.renderA().withStencilMask(PortalStencilMask.aperture(settings.apertureA())),
                    settings.renderB().withStencilMask(PortalStencilMask.aperture(settings.apertureB())),
                    true,true,settings.sourceTag(),true,settings.renderBackface());
            com.skyeshade.skyesight.client.render.PlayerPerspectiveViews.remove(a);
            com.skyeshade.skyesight.client.render.PlayerPerspectiveViews.remove(b);
            if (gameplay(owner)) {
                com.skyeshade.skyesight.client.render.PlayerPerspectiveViews.register(a);
                com.skyeshade.skyesight.client.render.PlayerPerspectiveViews.register(b);
            }
            REVISIONS.put(owner, message.revision());
        } else if (Objects.equals(REVISIONS.get(owner), message.revision())) {
            if (message.action() == SkyesightTraversalPayload.REMOVE) {
                DEFINITIONS.remove(owner);
                TraversalPortalStandby.remove(a); TraversalPortalStandby.remove(b);
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
                    arrivalSide = PortalTraversalMath.local(guardedExit, message.destinationFeet()).z >= 0 ? 1 : -1;
                    awaitingArrivalEye = true;
                    arrivalDeadline = net.minecraft.Util.getMillis() + 2000;
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
                            transformedYaw(portal, mc.player.yRotO), mc.player.xRotO, FirstPersonState.capture(portal, mc.player));
                    if (Boolean.getBoolean("skyesight.debugTraversal")) Skyesight.LOGGER.info(
                            "[TraversalMotion] sourceFeet={} sourceEye={} predictedFeet={} requestedFeet={} velocity={} previousOffset={} ground={} fall={} pose={}",
                            mc.player.position(), mc.player.getEyePosition(), PortalTraversalMath.position(portal.source(), portal.target(), mc.player.position()),
                            message.destinationFeet(), motion.velocity, motion.previousOffset, motion.onGround, motion.fallDistance, motion.pose);
                }
                if (portal != null && (!source.equals(selected) || prepared == null)) {
                    if (SecondaryTransition.canPrepareSuccessor()) { prepared = null; selected = null; }
                    else cancel();
                    selected = source;
                    prepared = SkyesightTransitionApi.preparePortalTransition(ResourceLocation.parse(source));
                }
                // A fast reverse packet can beat the next render/tick preparation. Bootstrap
                // from its already-hot scene before processing the following vanilla teleport.
                if (portal != null && prepared != null && prepared.status() == SkyesightTransition.Status.PREPARING)
                    com.skyeshade.skyesight.client.portal.PortalDirectStencilRenderer.warmStandby(portal);
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
            // Respawn creates a new LocalPlayer with zero attack/equip and arm-bob phase.
            // Its zero cooldown lowers even an empty hand through ItemInHandRenderer.tick().
            pending.firstPerson.restore(mc.player);
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
        for (UUID owner : REVISIONS.keySet()) {
            if (!gameplay(owner)) continue;
            String a = SkyesightTraversalPayload.portalId(owner, true), b = SkyesightTraversalPayload.portalId(owner, false);
            for (String id : new String[]{a, b}) {
                var portal = SkyesightPortalApi.getPortal(id);
                if (portal != null && portal.active() && portal.source().dimension().equals(mc.level.dimension())
                        && mc.player.position().distanceToSqr(portal.source().center()) < 256)
                    TraversalPortalStandby.touch(a, b);
            }
        }
        TraversalPortalStandby.tick();
        prepareNearest(mc);
    }
    private static void prepareNearest(Minecraft mc) {
        if (guardedExit != null && guardedExit.dimension().equals(mc.level.dimension())
                && !awaitingArrivalEye && PortalTraversalMath.clearedExit(guardedExit, mc.player.position(), mc.player.getBbWidth())) {
            guardedPortal = null; guardedExit = null;
        }
        if (prepared != null && (prepared.status() == SkyesightTransition.Status.PRESENTING || prepared.status() == SkyesightTransition.Status.PREDICTING)) {
            if (!SecondaryTransition.canPrepareSuccessor()) return;
            // Keep the current live presentation until a separately warmed reverse view begins.
            prepared = null; selected = null;
            eyeSide = new com.skyeshade.skyesight.portal.PortalCrossingState();
        }
        String nearest = null; double distance = 36;
        for (UUID owner : REVISIONS.keySet()) for (boolean a : new boolean[]{true, false}) {
            if (!gameplay(owner)) continue;
            String id = SkyesightTraversalPayload.portalId(owner, a);
            var portal = SkyesightPortalApi.getPortal(id);
            if (portal == null || !portal.active() || !portal.source().dimension().equals(mc.level.dimension())) continue;
            var local = PortalTraversalMath.local(portal.source(), mc.player.position());
            double d = mc.player.position().distanceToSqr(portal.source().center());
            if (d < distance && Math.abs(local.x) < 2 && Math.abs(local.y) < 3) { distance = d; nearest = id; }
        }
        if (!Objects.equals(selected, nearest)) {
            cancel();
            if (nearest != null) {
                selected = nearest; prepared = SkyesightTransitionApi.preparePortalTransition(ResourceLocation.parse(nearest));
                if (selected.equals(guardedPortal)) eyeSide.arrive(SkyesightPortalApi.getPortal(selected).source(), mc.player.position());
            }
        } else if (nearest != null && prepared != null && prepared.status() != SkyesightTransition.Status.READY
                && prepared.status() != SkyesightTransition.Status.PREPARING) {
            cancel(); // Reprepare on the following tick after a completed/fallback handoff.
        }
    }
    /** Render-frame eye sampling, independent of server feet-crossing authority. */
    public static void beforePresentation() {
        var mc = Minecraft.getInstance();
        if (mc.player != null && mc.level != null) prepareNearest(mc);
        TraversalPortalStandby.warmFrame();
    }
    /** Resolve against the Camera actually passed to LevelRenderer, before any primary world draw. */
    public static void beforeWorld(net.minecraft.client.Camera camera) {
        var mc = Minecraft.getInstance();
        if (awaitingArrivalEye && (net.minecraft.Util.getMillis() > arrivalDeadline || guardedExit == null))
            awaitingArrivalEye = false;
        if (awaitingArrivalEye && motion == null && mc.level != null && guardedExit.dimension().equals(mc.level.dimension())
                && PortalTraversalMath.local(guardedExit, camera.getPosition()).z * arrivalSide >= 0) {
            awaitingArrivalEye = false;
            if (Objects.equals(selected, guardedPortal) && mc.player != null)
                eyeSide.arrive(guardedExit, camera.getPosition().subtract(0, mc.player.getEyeHeight(), 0));
        }
        if (prepared == null || selected == null || mc.player == null || mc.level == null
                || !mc.options.getCameraType().isFirstPerson()) return;
        if (awaitingArrivalEye && selected.equals(guardedPortal)) return;
        var portal = SkyesightPortalApi.getPortal(selected);
        if (portal == null || !portal.active() || !portal.source().dimension().equals(mc.level.dimension())) return;
        var eye = camera.getPosition();
        double distance = PortalTraversalMath.local(portal.source(), eye).z;
        int stableSide = eyeSide.stableSide();
                var gameplayLink = interactionLinks(mc.level.dimension()).stream().filter(l -> l.id().equals(portal.id())).findFirst().orElse(null);
        if(gameplayLink == null) return;
        var confirmed = eyeSide.update(portal.source(), eye.subtract(0, mc.player.getEyeHeight(), 0),
                point -> ((PortalAperture.Bound)gameplayLink.aperture()).shape().fitsPlayer(portal.source(),point,mc.player.getBbWidth(),mc.player.getBbHeight()));
        if (stableSide == 0 && (confirmed != null || eyeSide.hasPendingCrossing()))
            stableSide = confirmed != null ? -eyeSide.stableSide() : eyeSide.stableSide();
        if (!gameplayLink.backface() && stableSide < 0) return;
        if (prepared.status() == SkyesightTransition.Status.PREDICTING) {
            // Render interpolation can briefly revisit the source side after correction.
            // Roll back only a meaningful retreat of the actual local body, not eye jitter.
            double bodyDistance = PortalTraversalMath.local(portal.source(), mc.player.position()).z;
            if (bodyDistance * predictedSourceSide > .1) cancel();
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
    /** Identifies first-person entry context; only that portal's transformed duplicate may be hidden. */
    public static boolean suppressEntrySelf(ResourceLocation id) {
        var mc = Minecraft.getInstance();
        if (id == null || mc.player == null || !mc.options.getCameraType().isFirstPerson()
                || !id.toString().equals(selected)) return false;
        var status = prepared == null ? SkyesightTransition.Status.PREPARING : prepared.status();
        if (status == SkyesightTransition.Status.PREDICTING || status == SkyesightTransition.Status.PRESENTING) return true;
        var portal = SkyesightPortalApi.getPortal(selected);
        if (portal == null || !portal.active() || !portal.source().dimension().equals(mc.player.level().dimension())) return false;
        var link = interactionLinks(portal.source().dimension()).stream().filter(l -> l.id().equals(id)).findFirst().orElse(null);
        // Use exactly the existing render-feature intersection, not an eye-distance gate.
        // A selected candidate with no potentially spliced geometry is merely nearby.
        return link != null && com.skyeshade.skyesight.client.render.entity.PortalEntitySplicing.intersection(
                mc.player, mc.getTimer().getGameTimeDeltaPartialTick(false), link) != null;
    }

    /** A preserved pre-arrival interpolation sample is not a fresh reverse crossing. */
    public static boolean suppressArrivalPortal(ResourceLocation id) {
        var mc = Minecraft.getInstance();
        return awaitingArrivalEye && guardedExit != null && mc.level != null
                && guardedExit.dimension().equals(mc.level.dimension()) && id.toString().equals(guardedPortal);
    }
    private static void cancel() {
        eyeSide = new com.skyeshade.skyesight.portal.PortalCrossingState();
        if (prepared != null) prepared.close(); prepared = null; selected = null;
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        SecondaryTransition.closeAll();
        TraversalPortalStandby.clear();
        cancel(); motion = null; guardedPortal = null; guardedExit = null;
        awaitingArrivalEye = false;
        for(UUID id : REVISIONS.keySet()) SkyesightPortalApi.removePortalPair(SkyesightTraversalPayload.portalId(id,true),SkyesightTraversalPayload.portalId(id,false));
        DEFINITIONS.clear(); REVISIONS.clear(); lastSequence = 0;
        com.skyeshade.skyesight.client.render.PlayerPerspectiveViews.clear();
    }
}
