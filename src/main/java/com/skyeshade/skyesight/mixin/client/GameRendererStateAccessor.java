package com.skyeshade.skyesight.mixin.client;

import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(GameRenderer.class)
public interface GameRendererStateAccessor {
    @Accessor("renderHand")
    boolean skyesight$getRenderHand();

    @Accessor("renderBlockOutline")
    boolean skyesight$getRenderBlockOutline();

    @Accessor("mainCamera")
    Camera skyesight$getMainCameraField();
}
