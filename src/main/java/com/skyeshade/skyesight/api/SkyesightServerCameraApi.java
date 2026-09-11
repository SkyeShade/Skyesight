package com.skyeshade.skyesight.api;

import com.skyeshade.skyesight.server.SkyesightRemoteViewLifecycleHandler;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/** Server-thread gameplay cleanup for one player's dynamic camera registration. */
public final class SkyesightServerCameraApi {
    private SkyesightServerCameraApi() {}
    public static void close(ServerPlayer player, ResourceLocation viewId) {
        if (player != null && viewId != null) SkyesightRemoteViewLifecycleHandler.removeView(player, viewId);
    }
}
