package com.skyeshade.skyesight.api;

import net.minecraft.resources.ResourceLocation;

import java.util.Collection;
import java.util.Optional;

public interface SkyesightApi {
    SkyesightViewHandle createView(SkyesightViewSpec spec);

    Optional<SkyesightViewHandle> getView(ResourceLocation id);

    boolean destroyView(ResourceLocation id);

    Collection<? extends SkyesightViewHandle> views();
}