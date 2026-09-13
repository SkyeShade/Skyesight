package com.skyeshade.skyesight.client.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.skyeshade.skyesight.client.world.SkyesightVisualWorld;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

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
        Objects.requireNonNull(level);
        Objects.requireNonNull(view);
        Objects.requireNonNull(context);
        Objects.requireNonNull(output);
        Objects.requireNonNull(prepareOutput);
        Objects.requireNonNull(options);
    }
}
