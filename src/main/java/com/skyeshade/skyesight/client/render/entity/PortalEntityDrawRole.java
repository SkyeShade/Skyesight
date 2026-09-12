package com.skyeshade.skyesight.client.render.entity;

import net.minecraft.resources.ResourceLocation;

/** Visibility policy for entity draws, independent of clip-plane orientation. */
enum PortalEntityDrawRole {
    SOURCE,
    TRANSFORMED;

    boolean suppressEntrySelf(boolean localPlayer, boolean firstPersonEntry,
                              ResourceLocation viewId, ResourceLocation generatingPortalId) {
        return this == TRANSFORMED && localPlayer && firstPersonEntry
                && viewId != null && viewId.equals(generatingPortalId);
    }
}
