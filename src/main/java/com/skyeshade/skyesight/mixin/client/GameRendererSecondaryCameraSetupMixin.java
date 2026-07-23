package com.skyeshade.skyesight.mixin.client;

import com.skyeshade.skyesight.client.render.SkyesightSecondaryRenderContext;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(GameRenderer.class)
public abstract class GameRendererSecondaryCameraSetupMixin {
    @Redirect(
            method = "renderLevel",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/Camera;setup(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/world/entity/Entity;ZZF)V"
            )
    )
    private void skyesight$setupSecondaryCamera(
            Camera camera,
            BlockGetter level,
            Entity entity,
            boolean detached,
            boolean mirrored,
            float partialTick
    ) {
        camera.setup(level, entity, detached, mirrored, partialTick);

        if (!SkyesightSecondaryRenderContext.isActive()) {
            return;
        }

        Camera secondaryCamera = SkyesightSecondaryRenderContext.currentCamera();
        if (secondaryCamera == null) {
            return;
        }

        ((CameraInvoker) camera).skyesight$setPosition(secondaryCamera.getPosition());
        ((CameraInvoker) camera).skyesight$setRotation(
                secondaryCamera.getYRot(),
                secondaryCamera.getXRot(),
                secondaryCamera.getRoll()
        );
    }
}
