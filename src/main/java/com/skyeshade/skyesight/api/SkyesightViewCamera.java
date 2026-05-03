package com.skyeshade.skyesight.api;

import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

public interface SkyesightViewCamera {
    Vec3 position();

    float pitch();

    float yaw();

    float roll();

    Quaternionf rotation();

    void setPosition(Vec3 position);

    void setRotation(float yaw, float pitch);

    void setRotation(float yaw, float pitch, float roll);
    void setRotation(Quaternionf rotation);
    void copyFromMainCamera();

    void copyFromMainCameraWithOffset(Vec3 offset);

}