package com.skyeshade.skyesight.client.portal;

import com.skyeshade.skyesight.api.SkyesightClipPlane;
import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Math for the direct-stencil portal renderer.
 *
 * PortalFrameMath contains shared frame/projection helpers. This class models the
 * seamless/stencil path where the destination view is rendered directly into the
 * main framebuffer through an entrance aperture.
 */
public final class DirectStencilPortalMath {
    private static final double CLIP_PLANE_OFFSET = 0.001D;

    private DirectStencilPortalMath() {}

    public static PortalCameraPose transformCamera(
            Camera sourceCamera,
            PortalFrame entrancePortal,
            PortalFrame exitPortal
    ) {
        return transformPose(
                sourceCamera.getPosition(),
                sourceCamera.rotation(),
                entrancePortal,
                exitPortal
        );
    }

    public static PortalCameraPose transformPose(
            Vec3 sourcePosition,
            Quaternionf sourceRotation,
            PortalFrame entrancePortal,
            PortalFrame exitPortal
    ) {
        Quaternionf inverseEntranceRotation = new Quaternionf(entrancePortal.rotation()).conjugate();
        Quaternionf yawFlip = new Quaternionf().rotateY((float) Math.PI);

        Vector3f localPosition = new Vector3f(
                (float) (sourcePosition.x() - entrancePortal.position().x()),
                (float) (sourcePosition.y() - entrancePortal.position().y()),
                (float) (sourcePosition.z() - entrancePortal.position().z())
        );
        localPosition.rotate(inverseEntranceRotation);
        localPosition.rotate(yawFlip);
        localPosition.rotate(exitPortal.rotation());

        Vec3 transformedPosition = exitPortal.position().add(
                localPosition.x(),
                localPosition.y(),
                localPosition.z()
        );

        Quaternionf transformedRotation = new Quaternionf(exitPortal.rotation())
                .mul(yawFlip)
                .mul(inverseEntranceRotation)
                .mul(new Quaternionf(sourceRotation))
                .normalize();

        return new PortalCameraPose(transformedPosition, transformedRotation);
    }

    public static SkyesightClipPlane exitClipPlane(PortalFrame exitPortal) {
        Vec3 normal = normal(exitPortal);
        return new SkyesightClipPlane(
                exitPortal.position().add(
                        normal.x() * CLIP_PLANE_OFFSET,
                        normal.y() * CLIP_PLANE_OFFSET,
                        normal.z() * CLIP_PLANE_OFFSET
                ),
                normal
        );
    }

    public static Vec3 normal(PortalFrame portal) {
        return rotate(portal, 0.0F, 0.0F, 1.0F);
    }

    public static Vec3 right(PortalFrame portal) {
        return rotate(portal, 1.0F, 0.0F, 0.0F);
    }

    public static Vec3 up(PortalFrame portal) {
        return rotate(portal, 0.0F, 1.0F, 0.0F);
    }

    public static String localPositionSummary(
            Vec3 mainPosition,
            Vec3 secondaryPosition,
            PortalFrame entrancePortal,
            PortalFrame exitPortal
    ) {
        Vec3 entranceLocal = localPosition(mainPosition, entrancePortal);
        Vec3 exitLocal = localPosition(secondaryPosition, exitPortal);

        return "mainInEntrance="
                + formatVec3(entranceLocal)
                + " secondaryInExit="
                + formatVec3(exitLocal);
    }

    private static Vec3 localPosition(Vec3 worldPosition, PortalFrame portal) {
        Vector3f local = new Vector3f(
                (float) (worldPosition.x() - portal.position().x()),
                (float) (worldPosition.y() - portal.position().y()),
                (float) (worldPosition.z() - portal.position().z())
        );
        local.rotate(new Quaternionf(portal.rotation()).conjugate());

        return new Vec3(local.x(), local.y(), local.z());
    }

    private static Vec3 rotate(
            PortalFrame portal,
            float x,
            float y,
            float z
    ) {
        Vector3f vector = new Vector3f(x, y, z);
        vector.rotate(portal.rotation()).normalize();
        return new Vec3(vector.x(), vector.y(), vector.z());
    }

    private static String formatVec3(Vec3 value) {
        return String.format("%.2f,%.2f,%.2f", value.x(), value.y(), value.z());
    }

    public record PortalCameraPose(Vec3 position, Quaternionf rotation) {}
}
