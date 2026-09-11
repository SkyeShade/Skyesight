package com.skyeshade.skyesight.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.skyeshade.skyesight.client.render.CloudVertexNdcDiagnostics;
import net.minecraft.client.renderer.LevelRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererCloudNdcDiagnosticMixin {
    @Inject(method = "renderClouds", at = @At("HEAD"))
    private void skyesight$recordCloudNdc(
            PoseStack poseStack,
            Matrix4f frustumMatrix,
            Matrix4f projectionMatrix,
            float partialTick,
            double camX,
            double camY,
            double camZ,
            CallbackInfo callbackInfo
    ) {
        CloudVertexNdcDiagnostics.recordRender(
                (LevelRenderer) (Object) this,
                poseStack,
                frustumMatrix,
                projectionMatrix,
                partialTick,
                camX,
                camY,
                camZ
        );
    }
}
