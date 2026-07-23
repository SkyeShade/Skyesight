package com.skyeshade.skyesight.client.portal;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;

final class PortalStickSkyWarmup {
    private static final int NEW_PORTAL_SKY_SKIP_FRAMES = 2;

    private static final Map<ResourceLocation, Integer> activeFramesByView = new HashMap<>();
    private static final Map<ResourceLocation, Long> activeLastFrameByView = new HashMap<>();

    private PortalStickSkyWarmup() {
    }

    static void clear(ResourceLocation viewId) {
        activeFramesByView.remove(viewId);
        activeLastFrameByView.remove(viewId);
    }

    static boolean shouldSkip(ResourceLocation viewId, long frame) {
        Long lastFrame = activeLastFrameByView.get(viewId);
        int frames;
        if (lastFrame == null || lastFrame.longValue() != frame) {
            activeLastFrameByView.put(viewId, frame);
            frames = activeFramesByView.merge(viewId, 1, Integer::sum);
        } else {
            frames = activeFramesByView.getOrDefault(viewId, 0);
        }
        return frames <= NEW_PORTAL_SKY_SKIP_FRAMES;
    }
}
