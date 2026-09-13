package com.skyeshade.skyesight.api;

import net.minecraft.resources.ResourceLocation;

/** Opt-in cut material. Texture must tile; density is repetitions per world block before root scaling. */
public record SliceRenderOptions(boolean cap, ResourceLocation texture, float tilesPerBlock) {
    public static final ResourceLocation ENERGY_TEXTURE = ResourceLocation.fromNamespaceAndPath("skyesight", "textures/entity/energy_cut.png");
    public static final SliceRenderOptions UNCAPPED = new SliceRenderOptions(false, ENERGY_TEXTURE, 2);
    public static final SliceRenderOptions ENERGY_CAP = new SliceRenderOptions(true, ENERGY_TEXTURE, 2);

    public SliceRenderOptions {
        java.util.Objects.requireNonNull(texture);
        if (!Float.isFinite(tilesPerBlock) || tilesPerBlock <= 0) throw new IllegalArgumentException("Positive finite texture density required");
    }
}
