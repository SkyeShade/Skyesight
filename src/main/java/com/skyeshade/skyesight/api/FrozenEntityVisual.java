package com.skyeshade.skyesight.api;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.skyeshade.skyesight.client.render.entity.SkyesightNameTagSuppressor;
import com.skyeshade.skyesight.client.render.slice.EntityClipContext;
import com.skyeshade.skyesight.client.render.slice.FrozenVisualState;
import com.skyeshade.skyesight.mixin.client.EntityRenderDispatcherAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Quaternionf;

/**
 * Detached, fixed-time visual. No gameplay entity or ClientLevel is retained. Close when finished.
 * Rendering uses an unregistered, unticked surrogate and the current type/skin EntityRenderer.
 * Caller owns buffers and must flush them normally. All operations require the render thread.
 */
public final class FrozenEntityVisual implements AutoCloseable {
    private FrozenVisualState state;
    private final float partialTick;
    private final Vec3 position;
    private final AABB bounds;
    private final ResourceLocation entityType;

    FrozenEntityVisual(Entity entity, float partialTick) {
        this.partialTick = partialTick;
        position = new Vec3(Mth.lerp(partialTick, entity.xOld, entity.getX()),
                Mth.lerp(partialTick, entity.yOld, entity.getY()), Mth.lerp(partialTick, entity.zOld, entity.getZ()));
        bounds = entity.getBoundingBox().move(position.subtract(entity.position()));
        entityType = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        state = new FrozenVisualState(entity);
    }

    public Vec3 position() { return position; }
    /** Collision bounds at capture, not a tight bound on held items, wings or other features. */
    public AABB bounds() { return bounds; }
    public ResourceLocation entityType() { return entityType; }
    public boolean isClosed() { return state == null; }

    /**
     * pose must be the world pass's camera-relative root, before translating to the entity.
     * plane is in capture-world coordinates. root maps offsets from position() to output-world
     * offsets from position(). It transforms BOTH geometry and cut, letting halves move apart.
     * camera must belong to this render pass (including recursive/secondary cameras).
     */
    public void render(PoseStack pose, MultiBufferSource buffers, int light, Vec3 camera,
                       SkyesightClipPlane plane, ClipSide side, Matrix4fc root) {
        render(pose,buffers,light,camera,plane,side,root,SliceRenderOptions.UNCAPPED);
    }

    public void render(PoseStack pose, MultiBufferSource buffers, int light, Vec3 camera,
                       SkyesightClipPlane plane, ClipSide side, Matrix4fc root, SliceRenderOptions options) {
        RenderSystem.assertOnRenderThread();
        java.util.Objects.requireNonNull(options, "options");
        if (state == null) throw new IllegalStateException("Frozen visual is closed");
        if (!root.isAffine() || !Float.isFinite(root.determinant()) || Math.abs(root.determinant()) < 1e-12)
            throw new IllegalArgumentException("Root must be invertible and affine");
        var mc = Minecraft.getInstance();
        if (mc.level == null) throw new IllegalStateException("Rendering requires a current ClientLevel");
        Entity surrogate = state.create(mc.level);
        var dispatcher = mc.getEntityRenderDispatcher();
        var orientation = new Quaternionf(((EntityRenderDispatcherAccessor)dispatcher).skyesight$getCameraOrientation());
        // Plane calculation stays near the entity, avoiding world-coordinate float precision loss.
        Vec3 relative = position.subtract(camera);
        var captureToVertices = new Matrix4f(pose.last().pose()).translate((float)relative.x, (float)relative.y, (float)relative.z).mul(root);
        try (var clip = new EntityClipContext(buffers, plane, side, position, captureToVertices, options, light);
             var names = SkyesightNameTagSuppressor.suppressOwner(surrogate.getUUID())) {
            var previousPose = pose.last();
            pose.pushPose();
            try {
                dispatcher.overrideCameraOrientation(root.getUnnormalizedRotation(new Quaternionf()).conjugate().mul(orientation));
                pose.translate(relative.x, relative.y, relative.z);
                pose.mulPose(new Matrix4f(root));
                var renderer = dispatcher.getRenderer(surrogate);
                Vec3 offset = renderer.getRenderOffset(surrogate, partialTick);
                pose.translate(offset.x, offset.y, offset.z);
                // Direct normal renderer pipeline avoids dispatcher portal interception, shadows,
                // fire and hitboxes, without changing any live portal/local-player state.
                renderer.render(surrogate, Mth.rotLerp(partialTick, surrogate.yRotO, surrogate.getYRot()),
                        partialTick, pose, clip.buffers(), light);
            } finally {
                dispatcher.overrideCameraOrientation(orientation);
                // Vanilla renderers may throw after pushing their own poses.
                while (pose.last() != previousPose) pose.popPose();
            }
        }
    }

    public void render(PoseStack pose, MultiBufferSource buffers, int light, Vec3 camera,
                       SkyesightClipPlane plane, ClipSide side) {
        render(pose, buffers, light, camera, plane, side, new Matrix4f());
    }

    /** One captured pose, opposite signs, independently movable root transforms. */
    public void renderHalves(PoseStack pose, MultiBufferSource buffers, int light, Vec3 camera,
                             SkyesightClipPlane plane, Matrix4fc positiveRoot, Matrix4fc negativeRoot) {
        render(pose, buffers, light, camera, plane, ClipSide.POSITIVE, positiveRoot);
        render(pose, buffers, light, camera, plane, ClipSide.NEGATIVE, negativeRoot);
    }

    public void renderHalves(PoseStack pose, MultiBufferSource buffers, int light, Vec3 camera,
                             SkyesightClipPlane plane, Matrix4fc positiveRoot, Matrix4fc negativeRoot, SliceRenderOptions options) {
        render(pose,buffers,light,camera,plane,ClipSide.POSITIVE,positiveRoot,options);
        render(pose,buffers,light,camera,plane,ClipSide.NEGATIVE,negativeRoot,options);
    }

    /** Output-world cut for cap consumers. root follows the same pivot convention as render(). */
    public SkyesightClipPlane transformedPlane(SkyesightClipPlane plane, Matrix4fc root) {
        return plane.translated(position.scale(-1)).transformed(root).translated(position);
    }

    /** Conservative transformed collision bounds for future cut-surface consumers. */
    public AABB transformedBounds(Matrix4fc root) {
        Vec3 min = new Vec3(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY);
        Vec3 max = new Vec3(Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY);
        for (double x : new double[]{bounds.minX,bounds.maxX})
            for (double y : new double[]{bounds.minY,bounds.maxY})
                for (double z : new double[]{bounds.minZ,bounds.maxZ}) {
                    var p = root.transformPosition(new Vec3(x,y,z).subtract(position).toVector3f());
                    min = new Vec3(Math.min(min.x,p.x),Math.min(min.y,p.y),Math.min(min.z,p.z));
                    max = new Vec3(Math.max(max.x,p.x),Math.max(max.y,p.y),Math.max(max.z,p.z));
                }
        return new AABB(min.add(position), max.add(position));
    }

    @Override public void close() { RenderSystem.assertOnRenderThread(); state = null; }
}
