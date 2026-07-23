package com.skyeshade.skyesight.client.portal;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

public final class PortalDebugLineRenderer {
    private static final double AXIS_LENGTH = 0.75D;
    private static final double NORMAL_LENGTH = 1.0D;
    private static final double CLIP_PLANE_SIZE = 1.25D;
    private static final double CAMERA_MARKER_SIZE = 0.08D;

    private PortalDebugLineRenderer() {}

    public static void renderPortalDebugLines(
            Camera mainCamera,
            PortalFrame portal,
            PortalViewPlacement placement
    ) {
        Quaternionf inverseCameraRotation = new Quaternionf(mainCamera.rotation()).conjugate();

        Matrix4f matrix = new Matrix4f()
                .rotate(inverseCameraRotation);

        Vec3 cameraPosition = mainCamera.getPosition();

        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.disableCull();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.lineWidth(2.0F);

        var buffer = Tesselator.getInstance().begin(
                VertexFormat.Mode.DEBUG_LINES,
                DefaultVertexFormat.POSITION_COLOR
        );

        drawPortalOutline(buffer, matrix, cameraPosition, portal);
        drawPortalAxes(buffer, matrix, cameraPosition, portal);
        drawClipPlane(buffer, matrix, cameraPosition, placement.clipPlane().point(), placement.clipPlane().normal());
        drawCameraMarker(buffer, matrix, cameraPosition, placement.cameraPosition());
        drawFrustumRays(buffer, matrix, cameraPosition, placement.cameraPosition(), portal);

        BufferUploader.drawWithShader(buffer.buildOrThrow());

        RenderSystem.lineWidth(1.0F);
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
        RenderSystem.enableCull();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private static void drawPortalOutline(
            BufferBuilder buffer,
            Matrix4f matrix,
            Vec3 cameraPosition,
            PortalFrame portal
    ) {
        PortalCorners corners = corners(portal);

        drawLine(buffer, matrix, cameraPosition, corners.bottomLeft, corners.bottomRight, 255, 255, 0, 255);
        drawLine(buffer, matrix, cameraPosition, corners.bottomRight, corners.topRight, 255, 255, 0, 255);
        drawLine(buffer, matrix, cameraPosition, corners.topRight, corners.topLeft, 255, 255, 0, 255);
        drawLine(buffer, matrix, cameraPosition, corners.topLeft, corners.bottomLeft, 255, 255, 0, 255);
    }

    private static void drawPortalAxes(
            BufferBuilder buffer,
            Matrix4f matrix,
            Vec3 cameraPosition,
            PortalFrame portal
    ) {
        Vec3 center = portal.position();

        Vec3 right = PortalFrameMath.right(portal);
        Vec3 up = PortalFrameMath.up(portal);
        Vec3 normal = PortalFrameMath.normal(portal);

        drawLine(buffer, matrix, cameraPosition, center, center.add(right.scale(AXIS_LENGTH)), 255, 0, 0, 255);
        drawLine(buffer, matrix, cameraPosition, center, center.add(up.scale(AXIS_LENGTH)), 0, 255, 0, 255);
        drawLine(buffer, matrix, cameraPosition, center, center.add(normal.scale(NORMAL_LENGTH)), 0, 96, 255, 255);

        drawLine(buffer, matrix, cameraPosition, center, center.add(normal.scale(-0.35D)), 255, 0, 255, 180);
    }

    private static void drawClipPlane(
            BufferBuilder buffer,
            Matrix4f matrix,
            Vec3 cameraPosition,
            Vec3 planePosition,
            Vec3 planeNormal
    ) {
        Vec3 tangent = Math.abs(planeNormal.y()) < 0.95D
                ? planeNormal.cross(new Vec3(0.0D, 1.0D, 0.0D)).normalize()
                : planeNormal.cross(new Vec3(1.0D, 0.0D, 0.0D)).normalize();

        Vec3 bitangent = tangent.cross(planeNormal).normalize();

        double half = CLIP_PLANE_SIZE * 0.5D;

        Vec3 a = planePosition.add(tangent.scale(-half)).add(bitangent.scale(-half));
        Vec3 b = planePosition.add(tangent.scale(half)).add(bitangent.scale(-half));
        Vec3 c = planePosition.add(tangent.scale(half)).add(bitangent.scale(half));
        Vec3 d = planePosition.add(tangent.scale(-half)).add(bitangent.scale(half));

        drawLine(buffer, matrix, cameraPosition, a, b, 0, 255, 255, 180);
        drawLine(buffer, matrix, cameraPosition, b, c, 0, 255, 255, 180);
        drawLine(buffer, matrix, cameraPosition, c, d, 0, 255, 255, 180);
        drawLine(buffer, matrix, cameraPosition, d, a, 0, 255, 255, 180);

        drawLine(
                buffer,
                matrix,
                cameraPosition,
                planePosition,
                planePosition.add(planeNormal.normalize().scale(0.5D)),
                0,
                255,
                255,
                255
        );
    }

    private static void drawCameraMarker(
            BufferBuilder buffer,
            Matrix4f matrix,
            Vec3 cameraPosition,
            Vec3 markerPosition
    ) {
        double s = CAMERA_MARKER_SIZE;

        drawLine(buffer, matrix, cameraPosition, markerPosition.add(-s, 0, 0), markerPosition.add(s, 0, 0), 255, 128, 0, 255);
        drawLine(buffer, matrix, cameraPosition, markerPosition.add(0, -s, 0), markerPosition.add(0, s, 0), 255, 128, 0, 255);
        drawLine(buffer, matrix, cameraPosition, markerPosition.add(0, 0, -s), markerPosition.add(0, 0, s), 255, 128, 0, 255);
    }

    private static void drawFrustumRays(
            BufferBuilder buffer,
            Matrix4f matrix,
            Vec3 cameraPosition,
            Vec3 virtualCameraPosition,
            PortalFrame portal
    ) {
        PortalCorners corners = corners(portal);

        drawLine(buffer, matrix, cameraPosition, virtualCameraPosition, corners.bottomLeft, 255, 128, 0, 180);
        drawLine(buffer, matrix, cameraPosition, virtualCameraPosition, corners.bottomRight, 255, 128, 0, 180);
        drawLine(buffer, matrix, cameraPosition, virtualCameraPosition, corners.topLeft, 255, 128, 0, 180);
        drawLine(buffer, matrix, cameraPosition, virtualCameraPosition, corners.topRight, 255, 128, 0, 180);
    }

    private static PortalCorners corners(PortalFrame portal) {
        Vec3 center = portal.position();

        Vec3 right = PortalFrameMath.right(portal).scale(-1.0D);
        Vec3 up = PortalFrameMath.up(portal);

        double halfWidth = portal.width() * 0.5D;
        double halfHeight = portal.height() * 0.5D;

        Vec3 bottomLeft = center
                .subtract(right.scale(halfWidth))
                .subtract(up.scale(halfHeight));

        Vec3 bottomRight = center
                .add(right.scale(halfWidth))
                .subtract(up.scale(halfHeight));

        Vec3 topLeft = center
                .subtract(right.scale(halfWidth))
                .add(up.scale(halfHeight));

        Vec3 topRight = center
                .add(right.scale(halfWidth))
                .add(up.scale(halfHeight));

        return new PortalCorners(bottomLeft, bottomRight, topLeft, topRight);
    }

    private static void drawLine(
            BufferBuilder buffer,
            Matrix4f matrix,
            Vec3 cameraPosition,
            Vec3 from,
            Vec3 to,
            int red,
            int green,
            int blue,
            int alpha
    ) {
        addVertex(buffer, matrix, cameraPosition, from, red, green, blue, alpha);
        addVertex(buffer, matrix, cameraPosition, to, red, green, blue, alpha);
    }

    private static void addVertex(
            BufferBuilder buffer,
            Matrix4f matrix,
            Vec3 cameraPosition,
            Vec3 position,
            int red,
            int green,
            int blue,
            int alpha
    ) {
        buffer.addVertex(
                matrix,
                (float) (position.x() - cameraPosition.x()),
                (float) (position.y() - cameraPosition.y()),
                (float) (position.z() - cameraPosition.z())
        ).setColor(red, green, blue, alpha);
    }

    private record PortalCorners(
            Vec3 bottomLeft,
            Vec3 bottomRight,
            Vec3 topLeft,
            Vec3 topRight
    ) {}
}
