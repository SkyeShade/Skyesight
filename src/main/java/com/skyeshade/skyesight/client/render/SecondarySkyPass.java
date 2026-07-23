package com.skyeshade.skyesight.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.skyeshade.skyesight.client.render.env.SkyesightEnvironmentRendererSelector;
import net.minecraft.client.Minecraft;

public final class SecondarySkyPass {
    private SecondarySkyPass() {}

    public static void render(SecondaryViewFrame frame, Minecraft minecraft, float partialTick) {
        if (minecraft.level == null) {
            return;
        }

        SkyesightEnvironmentRendererSelector.get().renderSky(
                minecraft.level,
                frame.camera(),
                frame.modelViewMatrix(),
                frame.projectionMatrix(),
                partialTick
        );

        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }
}
