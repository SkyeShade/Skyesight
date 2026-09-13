package com.skyeshade.skyesight.api;

import com.skyeshade.skyesight.network.SkyesightTraversalPayload;
import com.skyeshade.skyesight.portal.PortalCollisionMath;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

import java.util.UUID;

/**
 * Server-owned rectangular portal pairs: traversal, interaction and synchronized client presentation.
 * Call on the server thread. Render-only registrations and camera feeds never implicitly gain gameplay authority.
 * Registration is session-scoped; callers that persist portals must register them again when the server starts.
 */
public final class SkyesightPortalGameplayApi {
    private SkyesightPortalGameplayApi() {
    }

    public static void registerPair(MinecraftServer server, UUID identity, PortalEndpoint a, PortalEndpoint b) {
        if (!server.isSameThread())
            throw new IllegalStateException("Portal gameplay registration requires the server thread");
        if (server.getLevel(a.dimension()) == null || server.getLevel(b.dimension()) == null)
            throw new IllegalArgumentException("Missing portal dimension");
        if (!PortalCollisionMath.supported(a) || !PortalCollisionMath.supported(b))
            throw new IllegalArgumentException("Gameplay pairs currently require upright cardinal endpoints");
        SkyesightPortalApi.registerPortalPair(server, identity, a, b, PortalPairSettings.of(PortalBehavior.TRAVERSABLE));
    }

    public static void removePair(MinecraftServer server, UUID identity) {
        if (!server.isSameThread())
            throw new IllegalStateException("Portal gameplay removal requires the server thread");
        SkyesightPortalApi.removePortalPair(server, identity);
    }

    public static ResourceLocation directionId(UUID identity, boolean fromA) {
        return ResourceLocation.parse(SkyesightTraversalPayload.portalId(identity, fromA));
    }
}

