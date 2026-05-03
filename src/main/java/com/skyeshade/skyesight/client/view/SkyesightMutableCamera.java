package com.skyeshade.skyesight.client.view;

import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class SkyesightMutableCamera extends Camera {
    public void setPositionPublic(Vec3 position) {
        this.setPosition(position);
    }

    public void setRotationPublic(float yaw, float pitch, float roll) {
        this.setRotation(yaw, pitch, roll);
    }

    public void setRotationPublic(Quaternionf rotation) {
        Quaternionf copied = new Quaternionf(rotation);

        this.setRotation(
                yawFromRotation(copied),
                pitchFromRotation(copied),
                0.0F
        );
    }

    public void copyFrom(Camera camera) {
        this.setPosition(camera.getPosition());
        this.setRotation(camera.getYRot(), camera.getXRot(), camera.getRoll());
    }

    public void copyFromWithOffset(Camera camera, Vec3 offset) {
        this.setPosition(camera.getPosition().add(offset));
        this.setRotation(camera.getYRot(), camera.getXRot(), camera.getRoll());
    }

    private static float yawFromRotation(Quaternionf rotation) {
        Vector3f forward = new Vector3f(0.0F, 0.0F, -1.0F);
        forward.rotate(rotation);

        return (float) Math.toDegrees(Math.atan2(-forward.x(), forward.z()));
    }

    private static float pitchFromRotation(Quaternionf rotation) {
        Vector3f forward = new Vector3f(0.0F, 0.0F, -1.0F);
        forward.rotate(rotation);

        double horizontalLength = Math.sqrt(
                forward.x() * forward.x() +
                        forward.z() * forward.z()
        );

        return (float) Math.toDegrees(Math.atan2(-forward.y(), horizontalLength));
    }
}