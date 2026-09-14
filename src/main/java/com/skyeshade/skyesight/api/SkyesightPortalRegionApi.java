package com.skyeshade.skyesight.api;

import com.skyeshade.skyesight.server.portal.PortalRegionManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.resources.ResourceLocation;
import java.util.Optional;

/** Server-thread registration. Definitions are synchronized to each observer; no chunks load at registration. */
public final class SkyesightPortalRegionApi {
    private SkyesightPortalRegionApi() {}
    public static void register(MinecraftServer server, PortalRegionDefinition definition) { PortalRegionManager.put(server,definition); }
    public static void update(MinecraftServer server, PortalRegionDefinition definition) { register(server,definition); }
    public static void remove(MinecraftServer server, ResourceLocation id) { PortalRegionManager.remove(server,id); }
    public static Optional<PortalRegionDefinition> get(MinecraftServer server, ResourceLocation id) { return PortalRegionManager.get(server,id); }
}
