package com.skyeshade.skyesight.client.view;

import com.skyeshade.skyesight.api.SkyesightApi;
import com.skyeshade.skyesight.api.SkyesightViewHandle;
import com.skyeshade.skyesight.api.SkyesightViewSpec;
import net.minecraft.resources.ResourceLocation;

import java.util.*;

public final class SkyesightClientApi implements SkyesightApi {
    private final Map<ResourceLocation, SkyesightView> views = new HashMap<>();

    @Override
    public SkyesightViewHandle createView(SkyesightViewSpec spec) {
        SkyesightView existing = this.views.get(spec.id());

        if (existing != null) {
            existing.close();
        }

        SkyesightView view = new SkyesightView(spec);
        this.views.put(spec.id(), view);
        return view;
    }

    @Override
    public Optional<SkyesightViewHandle> getView(ResourceLocation id) {
        return Optional.ofNullable(this.views.get(id));
    }


    @Override
    public boolean destroyView(ResourceLocation id) {
        SkyesightView view = this.views.remove(id);

        if (view == null) {
            return false;
        }

        view.close();
        return true;
    }

    @Override
    public Collection<? extends SkyesightViewHandle> views() {
        return this.views.values();
    }

    public void closeAll() {
        for (SkyesightView view : this.views.values()) {
            view.close();
        }

        this.views.clear();
    }
}