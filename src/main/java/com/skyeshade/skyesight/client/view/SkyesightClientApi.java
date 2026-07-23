package com.skyeshade.skyesight.client.view;

import com.skyeshade.skyesight.api.SkyesightApi;
import com.skyeshade.skyesight.api.SkyesightViewHandle;
import com.skyeshade.skyesight.api.SkyesightViewSpec;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class SkyesightClientApi implements SkyesightApi {
    private final Map<ResourceLocation, SkyesightView> views = new HashMap<>();

    @Override
    public synchronized SkyesightViewHandle createView(SkyesightViewSpec spec) {
        SkyesightView existing = this.views.get(spec.id());

        if (existing != null) {
            existing.close();
        }

        SkyesightView view = new SkyesightView(spec);
        this.views.put(spec.id(), view);
        return view;
    }

    @Override
    public synchronized Optional<SkyesightViewHandle> getView(ResourceLocation id) {
        return Optional.ofNullable(this.views.get(id));
    }


    @Override
    public synchronized boolean destroyView(ResourceLocation id) {
        SkyesightView view = this.views.remove(id);

        if (view == null) {
            return false;
        }

        view.close();
        return true;
    }

    @Override
    public synchronized Collection<? extends SkyesightViewHandle> views() {
        return List.copyOf(this.views.values());
    }

    public synchronized void closeAll() {
        List<SkyesightView> viewsToClose = new ArrayList<>(this.views.values());
        this.views.clear();

        for (SkyesightView view : viewsToClose) {
            view.close();
        }
    }
}
