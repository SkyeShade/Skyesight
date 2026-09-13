package com.skyeshade.skyesight.client.render.slice;

import com.mojang.blaze3d.systems.RenderSystem;
import com.skyeshade.skyesight.api.ClipSide;
import com.skyeshade.skyesight.api.SkyesightClipPlane;
import com.skyeshade.skyesight.api.SliceRenderOptions;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector4f;

import java.util.List;
import java.util.Objects;

/** Explicit buffer scope: composes by wrapping another scope's buffers; owns no GPU batch. */
public final class EntityClipContext implements AutoCloseable {
    private final MultiBufferSource buffers;
    private final EntityClipBuffer clip;

    private EntityClipContext(MultiBufferSource output, List<List<Vector4f>> regions, boolean union) {
        RenderSystem.assertOnRenderThread();
        clip = new EntityClipBuffer(output, regions, union);
        buffers = clip;
    }

    /** Advanced consumer: retain a union of convex regions, or subtract it (e.g. apertures). */
    public static EntityClipContext regions(MultiBufferSource output, List<List<Vector4f>> vertexRegions, boolean union) {
        return new EntityClipContext(output, vertexRegions, union);
    }

    public EntityClipContext(MultiBufferSource output, SkyesightClipPlane plane, ClipSide side,
                             Vec3 camera, Matrix4fc cameraRelativeToVertices) {
        this(output,plane,side,camera,cameraRelativeToVertices,SliceRenderOptions.UNCAPPED,0);
    }

    public EntityClipContext(MultiBufferSource output, SkyesightClipPlane plane, ClipSide side,
                             Vec3 camera, Matrix4fc cameraRelativeToVertices, SliceRenderOptions options, int light) {
        RenderSystem.assertOnRenderThread();
        Objects.requireNonNull(output);
        Objects.requireNonNull(side);
        clip = side == ClipSide.NONE ? null : new EntityClipBuffer(output,
                List.of(vertexPlane(Objects.requireNonNull(plane), side, camera, cameraRelativeToVertices)));
        buffers = clip == null ? output : clip;
        if (clip != null && options.cap()) clip.withCaps(new SliceCapCollector(plane,side,camera,cameraRelativeToVertices,options,light));
    }

    public static Vector4f vertexPlane(SkyesightClipPlane plane, ClipSide side, Vec3 camera, Matrix4fc transform) {
        if (side == ClipSide.NONE) throw new IllegalArgumentException("NONE has no plane equation");
        if (!transform.isAffine() || !Float.isFinite(transform.determinant()) || Math.abs(transform.determinant()) < 1e-12)
            throw new IllegalArgumentException("Expected an invertible affine vertex transform");
        var n = plane.normal();
        var equation = new Vector4f((float)n.x, (float)n.y, (float)n.z,
                (float)n.dot(camera.subtract(plane.point())));
        if (side == ClipSide.NEGATIVE) equation.negate();
        return new Matrix4f(transform).invert().transpose().transform(equation);
    }

    public MultiBufferSource buffers() { return buffers; }
    @Override public void close() { if (clip != null) clip.close(); }
}
