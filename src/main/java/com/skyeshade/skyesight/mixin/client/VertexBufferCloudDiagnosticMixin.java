package com.skyeshade.skyesight.mixin.client;

import com.skyeshade.skyesight.client.render.CloudVertexNdcDiagnostics;
import net.minecraft.client.renderer.ShaderInstance;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(com.mojang.blaze3d.vertex.VertexBuffer.class)
public abstract class VertexBufferCloudDiagnosticMixin {
    @Inject(method = "drawWithShader", at = @At("HEAD"))
    private void skyesight$recordCloudDrawMatrices(
            Matrix4f modelView,
            Matrix4f projection,
            ShaderInstance shader,
            CallbackInfo callbackInfo
    ) {
        CloudVertexNdcDiagnostics.recordDraw(
                (com.mojang.blaze3d.vertex.VertexBuffer) (Object) this,
                modelView,
                projection,
                shader
        );
    }
}
