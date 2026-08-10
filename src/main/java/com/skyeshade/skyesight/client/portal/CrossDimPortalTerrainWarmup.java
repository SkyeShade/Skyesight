package com.skyeshade.skyesight.client.portal;

import com.skyeshade.skyesight.api.PortalCachePolicy;
import com.skyeshade.skyesight.api.RegisteredPortalView;
import com.skyeshade.skyesight.api.SkyesightPortalRegistry;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

@OnlyIn(Dist.CLIENT)
public final class CrossDimPortalTerrainWarmup {
    private static final Map<ResourceLocation, PendingWarmup> PENDING = new LinkedHashMap<>();

    private CrossDimPortalTerrainWarmup() {}

    public static void onPortalViewChanged(
            RegisteredPortalView oldView,
            RegisteredPortalView newView,
            PortalCachePolicy cachePolicy
    ) {
        if (oldView != null && (newView == null || oldView.generation() != newView.generation())) {
            PENDING.remove(oldView.id());
        }
        if (newView == null || cachePolicy == PortalCachePolicy.REMOVE || cachePolicy == PortalCachePolicy.CLEAR) {
            return;
        }
        if (isWarmupCandidate(newView)) {
            PENDING.put(newView.id(), new PendingWarmup(newView.id(), newView.generation()));
        } else {
            PENDING.remove(newView.id());
        }
    }

    public static void clear() {
        PENDING.clear();
    }

    public static void tick() {
        if (PENDING.isEmpty()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.level == null || minecraft.player == null || minecraft.gameRenderer == null) {
            return;
        }

        Camera camera = minecraft.gameRenderer.getMainCamera();
        if (camera == null) {
            return;
        }

        Iterator<Map.Entry<ResourceLocation, PendingWarmup>> iterator = PENDING.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<ResourceLocation, PendingWarmup> entry = iterator.next();
            PendingWarmup pending = entry.getValue();
            RegisteredPortalView current = SkyesightPortalRegistry.get(pending.viewId());

            if (current == null || current.generation() != pending.generation()) {
                iterator.remove();
                continue;
            }
            if (!isWarmupCandidate(current)) {
                iterator.remove();
                continue;
            }
            if (!current.source().dimension().equals(minecraft.level.dimension())) {
                continue;
            }

            if (CrossDimPortalViewUpdater.requestInitialTerrainWarmup(minecraft, camera, current)) {
                iterator.remove();
            }
        }
    }

    private static boolean isWarmupCandidate(RegisteredPortalView view) {
        return view != null
                && view.source() != null
                && view.target() != null
                && view.renderSettings() != null
                && view.active()
                && view.renderSettings().enabled()
                && view.renderSettings().rendersView()
                && view.renderSettings().renderTerrain()
                && view.isCrossDimension();
    }

    private record PendingWarmup(ResourceLocation viewId, long generation) {}
}
