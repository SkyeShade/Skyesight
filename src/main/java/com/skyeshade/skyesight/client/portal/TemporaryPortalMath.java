package com.skyeshade.skyesight.client.portal;

import com.skyeshade.skyesight.api.SkyesightClipPlane;
import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class TemporaryPortalMath {
    private static final double CLIP_PLANE_OFFSET = 0.001D;

    private TemporaryPortalMath() {}

    public static TemporaryPortalViewPlacement placeCamera(
            Camera mainCamera,
            TemporaryPortalFrame entrancePortal,
            TemporaryPortalFrame exitPortal
    ) {
        Vec3 cameraPosition = mainCamera.getPosition();

        Quaternionf entranceRotation = new Quaternionf(entrancePortal.rotation());
        Quaternionf exitRotation = new Quaternionf(exitPortal.rotation());

        Quaternionf inverseEntranceRotation = new Quaternionf(entranceRotation).conjugate();

        Vector3f localPosition = new Vector3f(
                (float) (cameraPosition.x() - entrancePortal.position().x()),
                (float) (cameraPosition.y() - entrancePortal.position().y()),
                (float) (cameraPosition.z() - entrancePortal.position().z())
        );

        localPosition.rotate(inverseEntranceRotation);

        Quaternionf throughPortalFlip = new Quaternionf()
                .rotateY((float) Math.PI);

        localPosition.rotate(throughPortalFlip);

        Vector3f exitRelativePosition = new Vector3f(localPosition)
                .rotate(exitRotation);

        Vector3f worldPosition = exitRelativePosition.add(
                (float) exitPortal.position().x(),
                (float) exitPortal.position().y(),
                (float) exitPortal.position().z()
        );

        Quaternionf worldRotation = portalRenderRotation(exitPortal);

        Vec3 virtualCameraPosition = new Vec3(
                worldPosition.x(),
                worldPosition.y(),
                worldPosition.z()
        );

        Vec3 exitNormal = normal(exitPortal);

        SkyesightClipPlane clipPlane = new SkyesightClipPlane(
                exitPortal.position().add(
                        exitNormal.x() * CLIP_PLANE_OFFSET,
                        exitNormal.y() * CLIP_PLANE_OFFSET,
                        exitNormal.z() * CLIP_PLANE_OFFSET
                ),
                exitNormal
        );

        return new TemporaryPortalViewPlacement(
                virtualCameraPosition,
                worldRotation,
                clipPlane
        );
    }
    public static Quaternionf portalRenderRotation(TemporaryPortalFrame portal) {
        return new Quaternionf(portal.rotation())
                .rotateY((float) Math.PI)
                .normalize();
    }
    public static Matrix4f portalProjection(
            Vec3 cameraPosition,
            TemporaryPortalFrame portal,
            float nearPlane,
            float farPlane
    ) {
        Vec3 portalCenter = portal.position();
        Vec3 portalRight = right(portal).scale(-1.0D);
        Vec3 portalUp = up(portal);

        double halfWidth = portal.width() * 0.5D;
        double halfHeight = portal.height() * 0.5D;

        Vec3 bottomLeft = portalCenter
                .subtract(portalRight.scale(halfWidth))
                .subtract(portalUp.scale(halfHeight));

        Vec3 bottomRight = portalCenter
                .add(portalRight.scale(halfWidth))
                .subtract(portalUp.scale(halfHeight));

        Vec3 topLeft = portalCenter
                .subtract(portalRight.scale(halfWidth))
                .add(portalUp.scale(halfHeight));

        Vec3 topRight = portalCenter
                .add(portalRight.scale(halfWidth))
                .add(portalUp.scale(halfHeight));

        Quaternionf inverseRenderRotation =
                new Quaternionf(portalRenderRotation(portal)).conjugate();

        Vector3f bl = toCameraSpace(bottomLeft, cameraPosition, inverseRenderRotation);
        Vector3f br = toCameraSpace(bottomRight, cameraPosition, inverseRenderRotation);
        Vector3f tl = toCameraSpace(topLeft, cameraPosition, inverseRenderRotation);
        Vector3f tr = toCameraSpace(topRight, cameraPosition, inverseRenderRotation);

        float left = Math.min(bl.x / -bl.z, tl.x / -tl.z) * nearPlane;
        float right = Math.max(br.x / -br.z, tr.x / -tr.z) * nearPlane;
        float bottom = Math.min(bl.y / -bl.z, br.y / -br.z) * nearPlane;
        float top = Math.max(tl.y / -tl.z, tr.y / -tr.z) * nearPlane;

        return new Matrix4f().frustum(
                left,
                right,
                bottom,
                top,
                nearPlane,
                farPlane
        );
    }

    private static Vector3f toCameraSpace(
            Vec3 worldPosition,
            Vec3 cameraPosition,
            Quaternionf inverseCameraRotation
    ) {
        Vector3f relative = new Vector3f(
                (float) (worldPosition.x() - cameraPosition.x()),
                (float) (worldPosition.y() - cameraPosition.y()),
                (float) (worldPosition.z() - cameraPosition.z())
        );

        relative.rotate(inverseCameraRotation);
        return relative;
    }
    public static Vec3 normal(TemporaryPortalFrame portal) {
        Vector3f normal = new Vector3f(0.0F, 0.0F, 1.0F);
        normal.rotate(portal.rotation()).normalize();

        return new Vec3(normal.x(), normal.y(), normal.z());
    }

    public static Vec3 right(TemporaryPortalFrame portal) {
        Vector3f right = new Vector3f(1.0F, 0.0F, 0.0F);
        right.rotate(portal.rotation()).normalize();

        return new Vec3(right.x(), right.y(), right.z());
    }

    public static Vec3 up(TemporaryPortalFrame portal) {
        Vector3f up = new Vector3f(0.0F, 1.0F, 0.0F);
        up.rotate(portal.rotation()).normalize();

        return new Vec3(up.x(), up.y(), up.z());
    }
}