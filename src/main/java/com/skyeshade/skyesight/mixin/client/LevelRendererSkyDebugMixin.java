package com.skyeshade.skyesight.mixin.client;

import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.skyeshade.skyesight.client.portal.PortalSkyCaptureManager;
import com.skyeshade.skyesight.client.render.SkyesightLevelRendererSkyDebug;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.LevelRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererSkyDebugMixin {
    @Shadow
    private net.minecraft.client.multiplayer.ClientLevel level;

    @Inject(
            method = "renderSky",
            at = @At("HEAD")
    )
    private void skyesight$debugSkyEnter(
            Matrix4f frustumMatrix,
            Matrix4f projectionMatrix,
            float partialTick,
            Camera camera,
            boolean isFoggy,
            Runnable skyFogSetup,
            CallbackInfo ci
    ) {
        DimensionSpecialEffects.SkyType skyType = this.level == null ? null : this.level.effects().skyType();
        SkyesightLevelRendererSkyDebug.enter(camera, isFoggy, skyType);
    }

    @Inject(
            method = "renderSky",
            at = @At("RETURN")
    )
    private void skyesight$debugSkyReturn(
            Matrix4f frustumMatrix,
            Matrix4f projectionMatrix,
            float partialTick,
            Camera camera,
            boolean isFoggy,
            Runnable skyFogSetup,
            CallbackInfo ci
    ) {
        SkyesightLevelRendererSkyDebug.returned();
    }

    @Redirect(
            method = "renderSky",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/vertex/VertexBuffer;drawWithShader(Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lnet/minecraft/client/renderer/ShaderInstance;)V",
                    ordinal = 0
            )
    )
    private void skyesight$redirectUpperSkyDraw(
            VertexBuffer buffer,
            Matrix4f poseMatrix,
            Matrix4f projectionMatrix,
            ShaderInstance shader
    ) {
        PortalSkyCaptureManager.onSkyBufferDrawAttempt();
        SkyesightLevelRendererSkyDebug.upperSkyDraw();
        buffer.drawWithShader(poseMatrix, projectionMatrix, shader);
        PortalSkyCaptureManager.onSkyBufferDrawn();
    }

    @Inject(
            method = "renderSky",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/vertex/BufferUploader;drawWithShader(Lcom/mojang/blaze3d/vertex/MeshData;)V",
                    ordinal = 0
            )
    )
    private void skyesight$debugSunriseDraw(
            Matrix4f frustumMatrix,
            Matrix4f projectionMatrix,
            float partialTick,
            Camera camera,
            boolean isFoggy,
            Runnable skyFogSetup,
            CallbackInfo ci
    ) {
        SkyesightLevelRendererSkyDebug.sunriseDraw();
    }

    @Inject(
            method = "renderSky",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/vertex/BufferUploader;drawWithShader(Lcom/mojang/blaze3d/vertex/MeshData;)V",
                    ordinal = 1
            )
    )
    private void skyesight$debugSunDraw(
            Matrix4f frustumMatrix,
            Matrix4f projectionMatrix,
            float partialTick,
            Camera camera,
            boolean isFoggy,
            Runnable skyFogSetup,
            CallbackInfo ci
    ) {
        SkyesightLevelRendererSkyDebug.sunDraw();
    }

    @Inject(
            method = "renderSky",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/vertex/BufferUploader;drawWithShader(Lcom/mojang/blaze3d/vertex/MeshData;)V",
                    ordinal = 2
            )
    )
    private void skyesight$debugMoonDraw(
            Matrix4f frustumMatrix,
            Matrix4f projectionMatrix,
            float partialTick,
            Camera camera,
            boolean isFoggy,
            Runnable skyFogSetup,
            CallbackInfo ci
    ) {
        SkyesightLevelRendererSkyDebug.moonDraw();
    }

    @Inject(
            method = "renderSky",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/vertex/VertexBuffer;drawWithShader(Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lnet/minecraft/client/renderer/ShaderInstance;)V",
                    ordinal = 1
            )
    )
    private void skyesight$debugStarsDraw(
            Matrix4f frustumMatrix,
            Matrix4f projectionMatrix,
            float partialTick,
            Camera camera,
            boolean isFoggy,
            Runnable skyFogSetup,
            CallbackInfo ci
    ) {
        SkyesightLevelRendererSkyDebug.starsDraw();
    }

    @Redirect(
            method = "renderSky",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/vertex/VertexBuffer;drawWithShader(Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lnet/minecraft/client/renderer/ShaderInstance;)V",
                    ordinal = 2
            )
    )
    private void skyesight$redirectDarkSkyDraw(
            VertexBuffer buffer,
            Matrix4f poseMatrix,
            Matrix4f projectionMatrix,
            ShaderInstance shader
    ) {
        if (PortalSkyCaptureManager.onDarkHorizonDrawAttempt()) {
            return;
        }
        SkyesightLevelRendererSkyDebug.darkSkyDraw();
        buffer.drawWithShader(poseMatrix, projectionMatrix, shader);
    }
}
