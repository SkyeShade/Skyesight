package com.skyeshade.skyesight.client.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.skyeshade.skyesight.client.world.SkyesightVisualWorld;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceLocation;

/** Resolved scene inputs. Projection, aperture and camera transforms belong to the caller. */
public record SecondarySceneFrame(
        ResourceLocation viewId,
        ClientLevel level,
        SkyesightVisualWorld visualWorld,
        SecondaryViewFrame view,
        SecondaryViewContext context,
        float partialTick,
        RenderTarget output,
        Runnable prepareOutput,
        SecondarySceneOptions options
) {
    public SecondarySceneFrame(ResourceLocation viewId, ClientLevel level, SkyesightVisualWorld visualWorld,
                               SecondaryViewFrame view, SecondaryViewContext context, float partialTick,
                               RenderTarget output, Runnable prepareOutput) {
        this(viewId, level, visualWorld, view, context, partialTick, output, prepareOutput, SecondarySceneOptions.from(view));
    }

    public SecondarySceneFrame {
        java.util.Objects.requireNonNull(level);
        java.util.Objects.requireNonNull(view);
        java.util.Objects.requireNonNull(context);
        java.util.Objects.requireNonNull(output);
        java.util.Objects.requireNonNull(prepareOutput);
        java.util.Objects.requireNonNull(options);
    }
}
