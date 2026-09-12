package com.skyeshade.skyesight.client.render.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.skyeshade.skyesight.Skyesight;
import com.skyeshade.skyesight.api.*;
import com.skyeshade.skyesight.client.transition.TraversalPortalClient;
import com.skyeshade.skyesight.client.world.SkyesightVisualWorldManager;
import com.skyeshade.skyesight.portal.*;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.*;

import java.util.*;

/**
 * Render-only projection of one authoritative entity. No entity pose or level is mutated.
 */
@EventBusSubscriber(modid = Skyesight.MODID, value = Dist.CLIENT)
public final class PortalEntitySplicing {
    private static boolean drawing;

    /** Local identity is never rendered from a delayed network sample, including stale old-dimension samples. */
    public static PortalRenderableEntity localPose(PortalRenderableEntity candidate, ResourceKey<Level> dimension) {
        var player = Minecraft.getInstance().player;
        if (player == null || candidate.entity() == null || !candidate.entity().getUUID().equals(player.getUUID())) return candidate;
        if (!player.level().dimension().equals(dimension)) return null;
        return new PortalRenderableEntity(player, dimension, "local-live", null, null, true, false, -1);
    }

    /**
     * Spatial description of a draw. The entity is the live pose source, not a frozen model snapshot.
     */
    public record Split(Entity entity, SkyesightPortalRaycast.Link portal, Vec3 position, int side) {
        public SkyesightClipPlane sourcePlane() {
            return new SkyesightClipPlane(portal.source().center(), PortalTraversalMath.normal(portal.source()).scale(side));
        }

        public SkyesightClipPlane destinationPlane() {
            return new SkyesightClipPlane(portal.target().center(), PortalTraversalMath.normal(portal.target()).scale(side));
        }

        public Vec3 transformedPosition() {
            return PortalTraversalMath.position(portal.source(), portal.target(), position);
        }

        public Quaternionf transformRotation() {
            return PortalTraversalMath.rotation(portal.source().rotation(), portal.target().rotation());
        }
    }

    public static Vec3 position(Entity e, float tick) {
        return new Vec3(Mth.lerp(tick, e.xOld, e.getX()), Mth.lerp(tick, e.yOld, e.getY()), Mth.lerp(tick, e.zOld, e.getZ()));
    }

    public static Split intersection(Entity e, float tick, SkyesightPortalRaycast.Link link) {
        if (e.isRemoved() || e.isSpectator() || e.isPassenger() || e.isVehicle()) return null;
        Vec3 p = position(e, tick);
        // Arms, tools, capes and wings extend beyond the collision box. This is only a
        // broad phase; actual vertices are cut against the aperture, never enlarged.
        double reach = e instanceof LivingEntity living ? 1.5 * living.getScale() : .25;
        AABB bounds = e.getBoundingBox().move(p.subtract(e.position())).inflate(reach);
        AABB local = PortalCollisionMath.map(bounds, v -> PortalTraversalMath.local(link.source(), v));
        if (local.minZ >= -1e-6 || local.maxZ <= 1e-6 || !overlapsAperture(link, local)) return null;
        int side = PortalTraversalMath.local(link.source(), p).z >= 0 ? 1 : -1;
        if (!link.backface() && side < 0) return null;
        return new Split(e, link, p, side);
    }

    static boolean overlapsAperture(SkyesightPortalRaycast.Link link, AABB local) {
        var shape = link.aperture() instanceof PortalAperture.Bound b ? b.shape() : PortalAperture.RECTANGLE;
        double w = link.source().width(), h = link.source().height();
        return shape.rectangles().stream().anyMatch(r -> local.maxX > -w / 2 + r.left() * w
                && local.minX < -w / 2 + r.right() * w && local.maxY > h / 2 - r.bottom() * h
                && local.minY < h / 2 - r.top() * h);
    }

    /**
     * Intercepts world draws within bounded feature reach of a gameplay aperture.
     */
    public static boolean source(EntityRenderDispatcher dispatcher, Entity entity, double x, double y, double z,
                                 float yaw, float tick, PoseStack pose, MultiBufferSource output, int light) {
        if (drawing || entity.isRemoved() || dispatcher.camera == null) return false;
        // Inventory/GUI renderers also call the dispatcher. They do not use world-relative coordinates.
        if (position(entity, tick).subtract(dispatcher.camera.getPosition()).distanceToSqr(new Vec3(x, y, z)) > 1e-6)
            return false;
        var renderLevel = ((com.skyeshade.skyesight.mixin.client.EntityRenderDispatcherAccessor) dispatcher).skyesight$getLevel();
        if (renderLevel == null) return false;
        for (var link : TraversalPortalClient.interactionLinks(renderLevel.dimension())) {
            var split = intersection(entity, tick, link);
            if (split == null) continue;
            var removedRegions = aperturePlanes(link, -split.side, dispatcher.camera, pose);
            drawing = true;
            try (var clipped = new PortalEntityClipBuffer(output, removedRegions, false)) {
                dispatcher.render(entity, x, y, z, yaw, tick, pose, clipped, light);
            } finally {
                drawing = false;
            }
            return true;
        }
        return false;
    }

    /**
     * Convert a world-space keep-positive plane into the coordinates written by this PoseStack.
     */
    private static Vector4f plane(PortalEndpoint endpoint, int side, Vec3 camera, Matrix4f vertexTransform) {
        Vec3 normal = PortalTraversalMath.normal(endpoint).scale(side);
        var p = new Vector4f((float) normal.x, (float) normal.y, (float) normal.z, (float) normal.dot(camera.subtract(endpoint.center())));
        return new Matrix4f(vertexTransform).invert().transpose().transform(p);
    }

    private static Vector4f localPlane(PortalEndpoint endpoint, Vec3 n, double offset, Vec3 camera, Matrix4f pose) {
        Vec3 normal = PortalTraversalMath.rotate(n, endpoint.rotation());
        return new Matrix4f(pose).invert().transpose().transform(new Vector4f((float) normal.x, (float) normal.y, (float) normal.z,
                (float) (normal.dot(camera.subtract(endpoint.center())) + offset)));
    }

    private static List<List<Vector4f>> aperturePlanes(SkyesightPortalRaycast.Link reverse, int side, Camera camera, PoseStack pose) {
        var endpoint = reverse.source();
        var shape = reverse.aperture() instanceof PortalAperture.Bound b ? b.shape() : PortalAperture.RECTANGLE;
        double w = endpoint.width(), h = endpoint.height();
        var regions = new ArrayList<List<Vector4f>>();
        for (var rect : shape.rectangles()) {
            double left = -w / 2 + rect.left() * w, right = -w / 2 + rect.right() * w, top = h / 2 - rect.top() * h, bottom = h / 2 - rect.bottom() * h;
            regions.add(List.of(plane(endpoint, side, camera.getPosition(), pose.last().pose()),
                    localPlane(endpoint, new Vec3(1, 0, 0), -left, camera.getPosition(), pose.last().pose()),
                    localPlane(endpoint, new Vec3(-1, 0, 0), right, camera.getPosition(), pose.last().pose()),
                    localPlane(endpoint, new Vec3(0, 1, 0), -bottom, camera.getPosition(), pose.last().pose()),
                    localPlane(endpoint, new Vec3(0, -1, 0), top, camera.getPosition(), pose.last().pose())));
        }
        return regions;
    }

    @SubscribeEvent
    public static void afterEntities(RenderLevelStageEvent event) {
        var mc = Minecraft.getInstance();
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || mc.level == null || event.getPoseStack() == null)
            return;
        projections(mc.level, event.getCamera(), event.getPoseStack(), event.getPartialTick().getGameTimeDeltaPartialTick(false));
    }

    /**
     * Called inside the existing secondary stencil, and after physical destination entities.
     */
    public static void projections(ClientLevel destination, Camera camera, PoseStack pose, float tick) {
        projections(destination, camera, pose, tick, null);
    }

    public static void projections(ClientLevel destination, Camera camera, PoseStack pose, float tick, ResourceLocation viewId) {
        if (drawing) return;
        var mc = Minecraft.getInstance();
        var dispatcher = mc.getEntityRenderDispatcher();
        var buffer = mc.renderBuffers().bufferSource();
        Set<UUID> rendered = new HashSet<>();
        for (var reverse : TraversalPortalClient.interactionLinks(destination.dimension())) {
            if (camera.getPosition().distanceToSqr(reverse.source().center()) > 128 * 128) continue;
            var candidates = new LinkedHashMap<UUID, Entity>();
            var world = SkyesightVisualWorldManager.get(reverse.id());
            if (world != null && world.environmentReady()) for (var snapshot : world.entityStore().entities()) {
                var e = snapshot.entity();
                if (e != null && mc.player != null && e.getUUID().equals(mc.player.getUUID())) continue;
                snapshot.applyInterpolated();
                if (e != null) candidates.put(e.getUUID(), e);
            }
            if (mc.player != null && mc.player.level() instanceof ClientLevel physical && physical.dimension().equals(reverse.target().dimension())
                    && com.skyeshade.skyesight.client.portal.PortalInteractionClient.loaded(physical, reverse.target().center().add(-2, -2, -2), reverse.target().center().add(2, 2, 2))) {
                // A loaded native source owns entity lifecycle, including absence after removal.
                candidates.clear();
                for (Entity e : physical.entitiesForRendering()) candidates.put(e.getUUID(), e);
            }
            if (mc.player != null) {
                candidates.remove(mc.player.getUUID());
                if (mc.player.level().dimension().equals(reverse.target().dimension())) candidates.put(mc.player.getUUID(), mc.player);
            }
            // Recover the actual source direction, including its independently defined aperture.
            var forward = TraversalPortalClient.interactionLinks(reverse.target().dimension()).stream()
                    .filter(l -> l.id().equals(com.skyeshade.skyesight.network.SkyesightTraversalPayload.pairedPortalId(reverse.id()))).findFirst().orElse(null);
            if (forward == null) continue;
            for (Entity entity : candidates.values()) {
                if (entity.isRemoved() || rendered.contains(entity.getUUID())) continue;
                // Suppress only this entry portal's generated duplicate, never native geometry
                // or a duplicate generated by another portal observed through this view.
                if (entity == mc.player && PortalEntityDrawRole.TRANSFORMED.suppressEntrySelf(true,
                        TraversalPortalClient.suppressEntrySelf(viewId), viewId, forward.id())) continue;
                var split = intersection(entity, tick, forward);
                if (split == null) continue;
                Vec3 target = PortalTraversalMath.position(forward.source(), forward.target(), split.position);
                var rotation = PortalTraversalMath.rotation(forward.source().rotation(), forward.target().rotation());
                var regions = aperturePlanes(reverse, split.side, camera, pose);
                // Only actual geometry inside the aperture survives; feature bounds are broad-phase only.
                var accessor = (com.skyeshade.skyesight.mixin.client.EntityRenderDispatcherAccessor) dispatcher;
                boolean shadow = accessor.skyesight$getShouldRenderShadow();
                var orientation = new Quaternionf(accessor.skyesight$getCameraOrientation());
                pose.pushPose();
                drawing = true;
                dispatcher.setRenderShadow(false);
                dispatcher.overrideCameraOrientation(new Quaternionf(rotation).conjugate().mul(orientation));
                try (var clipped = new PortalEntityClipBuffer(buffer, regions, true)) {
                    Vec3 relative = target.subtract(camera.getPosition());
                    pose.translate(relative.x, relative.y, relative.z);
                    pose.mulPose(rotation);
                    int light = net.minecraft.client.renderer.LevelRenderer.getLightColor(destination, net.minecraft.core.BlockPos.containing(target));
                    dispatcher.render(entity, 0, 0, 0, Mth.lerp(tick, entity.yRotO, entity.getYRot()), tick, pose, clipped, light);
                } finally {
                    dispatcher.setRenderShadow(shadow);
                    dispatcher.overrideCameraOrientation(orientation);
                    drawing = false;
                    pose.popPose();
                }
                rendered.add(entity.getUUID());
            }
        }
        buffer.endBatch();
    }
}

