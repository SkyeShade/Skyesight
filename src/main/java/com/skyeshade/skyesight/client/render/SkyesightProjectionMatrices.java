package com.skyeshade.skyesight.client.render;

import com.skyeshade.skyesight.api.SkyesightClipPlane;
import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;

public final class SkyesightProjectionMatrices {
    private SkyesightProjectionMatrices() {}

    public static Matrix4f perspective(
            float fovDegrees,
            float aspect,
            float nearPlane,
            float farPlane
    ) {
        return new Matrix4f().perspective(
                (float) Math.toRadians(fovDegrees),
                aspect,
                nearPlane,
                farPlane
        );
    }

    public static Matrix4f applyObliqueClipPlane(
            Matrix4f projectionMatrix,
            Camera camera,
            SkyesightClipPlane clipPlane
    ) {
        Vector4f cameraSpacePlane = toCameraSpacePlane(camera, clipPlane);

        Matrix4f inverseProjection = new Matrix4f(projectionMatrix).invert();

        Vector4f q = new Vector4f(
                sign(cameraSpacePlane.x),
                sign(cameraSpacePlane.y),
                1.0F,
                1.0F
        );

        inverseProjection.transform(q);

        float dot = cameraSpacePlane.dot(q);

        if (Math.abs(dot) < 1.0E-6F) {
            return projectionMatrix;
        }

        Vector4f c = cameraSpacePlane.mul(2.0F / dot, new Vector4f());

        Matrix4f result = new Matrix4f(projectionMatrix);

        result.m02(c.x - result.m03());
        result.m12(c.y - result.m13());
        result.m22(c.z - result.m23());
        result.m32(c.w - result.m33());

        return result;
    }

    private static Vector4f toCameraSpacePlane(
            Camera camera,
            SkyesightClipPlane clipPlane
    ) {
        Vec3 cameraPosition = camera.getPosition();
        Vec3 planePoint = clipPlane.point();
        Vec3 worldNormal = clipPlane.normal().normalize();

        Vec3 relativePoint = planePoint.subtract(cameraPosition);

        Quaternionf inverseCameraRotation = new Quaternionf(camera.rotation()).conjugate();

        Vector3f cameraSpacePoint = new Vector3f(
                (float) relativePoint.x(),
                (float) relativePoint.y(),
                (float) relativePoint.z()
        );
        cameraSpacePoint.rotate(inverseCameraRotation);

        Vector3f cameraSpaceNormal = new Vector3f(
                (float) worldNormal.x(),
                (float) worldNormal.y(),
                (float) worldNormal.z()
        );
        cameraSpaceNormal.rotate(inverseCameraRotation);
        cameraSpaceNormal.normalize();

        float d = -cameraSpaceNormal.dot(cameraSpacePoint);

        Vector4f plane = new Vector4f(
                cameraSpaceNormal.x(),
                cameraSpaceNormal.y(),
                cameraSpaceNormal.z(),
                d
        );

        if (plane.w > 0.0F) {
            plane.negate();
        }

        return plane;
    }

    private static float sign(float value) {
        return value >= 0.0F ? 1.0F : -1.0F;
    }
}