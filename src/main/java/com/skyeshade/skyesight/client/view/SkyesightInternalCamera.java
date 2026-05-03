package com.skyeshade.skyesight.client.view;

import com.skyeshade.skyesight.api.SkyesightViewCamera;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

public final class SkyesightInternalCamera implements SkyesightViewCamera {
    private final SkyesightMutableCamera camera = new SkyesightMutableCamera();

    public Camera minecraftCamera() {
        return this.camera;
    }

    @Override
    public Vec3 position() {
        return this.camera.getPosition();
    }

    @Override
    public float pitch() {
        return this.camera.getXRot();
    }

    @Override
    public float yaw() {
        return this.camera.getYRot();
    }

    @Override
    public float roll() {
        return this.camera.getRoll();
    }

    @Override
    public Quaternionf rotation() {
        return new Quaternionf(this.camera.rotation());
    }

    @Override
    public void setPosition(Vec3 position) {
        this.camera.setPositionPublic(position);
    }

    @Override
    public void setRotation(float yaw, float pitch) {
        this.camera.setRotationPublic(yaw, pitch, 0.0F);
    }

    @Override
    public void setRotation(float yaw, float pitch, float roll) {
        this.camera.setRotationPublic(yaw, pitch, roll);
    }

    @Override
    public void setRotation(Quaternionf rotation) {
        this.camera.setRotationPublic(rotation);
    }
    @Override
    public void copyFromMainCamera() {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;

        if (player == null) {
            Camera mainCamera = minecraft.gameRenderer.getMainCamera();
            this.camera.copyFrom(mainCamera);
            return;
        }

        float partialTick = minecraft.getTimer().getGameTimeDeltaPartialTick(true);

        Vec3 eyePosition = player.getEyePosition(partialTick);
        float yaw = player.getViewYRot(partialTick);
        float pitch = player.getViewXRot(partialTick);

        this.camera.setPositionPublic(eyePosition);
        this.camera.setRotationPublic(yaw, pitch, 0.0F);
    }

    @Override
    public void copyFromMainCameraWithOffset(Vec3 offset) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;

        if (player == null) {
            Camera mainCamera = minecraft.gameRenderer.getMainCamera();
            this.camera.copyFromWithOffset(mainCamera, offset);
            return;
        }

        float partialTick = minecraft.getTimer().getGameTimeDeltaPartialTick(true);

        Vec3 eyePosition = player.getEyePosition(partialTick).add(offset);
        float yaw = player.getViewYRot(partialTick);
        float pitch = player.getViewXRot(partialTick);

        this.camera.setPositionPublic(eyePosition);
        this.camera.setRotationPublic(yaw, pitch, 0.0F);
    }
}