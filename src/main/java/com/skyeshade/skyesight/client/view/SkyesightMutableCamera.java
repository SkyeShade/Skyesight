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

        Vector3f forward = copied.transform(new Vector3f(0, 0, -1));
        Vector3f up = copied.transform(new Vector3f(0, 1, 0));
        Vector3f left = copied.transform(new Vector3f(-1, 0, 0));
        double horizontal = Math.hypot(forward.x, forward.z);
        // Euler fields are compatibility metadata only. At a pole choose roll zero;
        // the quaternion and all three basis vectors remain authoritative and exact.
        float yaw = (float) Math.toDegrees(horizontal > 1.0E-6
                ? Math.atan2(-forward.x, forward.z) : Math.atan2(left.z, left.x));
        float pitch = (float) Math.toDegrees(Math.atan2(-forward.y, horizontal));
        float roll = horizontal > 1.0E-6 ? (float) Math.toDegrees(Math.atan2(left.y, up.y)) : 0;
        this.setRotation(yaw, pitch, roll);
        this.rotation().set(copied);
        this.getLookVector().set(forward);
        this.getUpVector().set(up);
        this.getLeftVector().set(left);
    }

    public void copyFrom(Camera camera) {
        this.setPosition(camera.getPosition());
        this.setRotationPublic(camera.rotation());
    }

    public void copyFromWithOffset(Camera camera, Vec3 offset) {
        this.setPosition(camera.getPosition().add(offset));
        this.setRotationPublic(camera.rotation());
    }

}
