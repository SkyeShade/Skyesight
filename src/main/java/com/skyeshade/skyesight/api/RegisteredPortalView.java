package com.skyeshade.skyesight.api;

import net.minecraft.resources.ResourceLocation;

public record RegisteredPortalView(
        ResourceLocation id,
        PortalEndpoint source,
        PortalEndpoint target,
        PortalRenderSettings renderSettings,
        boolean renderEnabled,
        String pairedId,
        String groupId,
        String sourceTag,
        boolean renderBackface,
        long generation,
        boolean cacheRetainedDisabled
) {
    public boolean active() {
        return this.renderEnabled && !this.cacheRetainedDisabled;
    }

    public ResourceLocation sourceDimension() {
        return this.source.dimension().location();
    }

    public ResourceLocation targetDimension() {
        return this.target.dimension().location();
    }

    public boolean isCrossDimension() {
        return !this.source.dimension().equals(this.target.dimension());
    }

    public boolean isSameDimension() {
        return this.source.dimension().equals(this.target.dimension());
    }
}
