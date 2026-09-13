package com.skyeshade.skyesight.network;

import com.skyeshade.skyesight.api.*;
import net.minecraft.network.RegistryFriendlyByteBuf;

import java.util.ArrayList;

/**
 * Common-side definition codec; no renderer objects cross the wire.
 */
final class PortalPairWire {
    static void write(RegistryFriendlyByteBuf b, PortalPairSettings s) {
        b.writeEnum(s.behavior());
        shape(b, s.apertureA());
        shape(b, s.apertureB());
        render(b, s.renderA());
        render(b, s.renderB());
        b.writeBoolean(s.renderBackface());
        b.writeUtf(s.sourceTag(), 256);
        b.writeEnum(s.sidednessA());
        b.writeEnum(s.sidednessB());
        b.writeEnum(s.directionality());
    }

    static PortalPairSettings read(RegistryFriendlyByteBuf b) {
        return new PortalPairSettings(b.readEnum(PortalBehavior.class), shape(b), shape(b), render(b), render(b), b.readBoolean(), b.readUtf(256),
                b.readEnum(PortalSidedness.class), b.readEnum(PortalSidedness.class), b.readEnum(PortalDirectionality.class));
    }

    static void shape(RegistryFriendlyByteBuf b, PortalAperture a) {
        b.writeVarInt(a.rows().size());
        a.rows().forEach(r -> b.writeUtf(r, 64));
    }

    static PortalAperture shape(RegistryFriendlyByteBuf b) {
        int count = b.readVarInt();
        if (count < 1 || count > 64) throw new IllegalArgumentException("Invalid aperture rows");
        var rows = new ArrayList<String>();
        for (int i = 0; i < count; i++) rows.add(b.readUtf(64));
        return new PortalAperture(rows);
    }

    static void render(RegistryFriendlyByteBuf b, PortalRenderSettings s) {
        b.writeBoolean(s.enabled());
        b.writeBoolean(s.rendersView());
        b.writeVarInt(s.terrainChunkRadius());
        b.writeVarInt(s.portalOwnedRenderRadiusChunks());
        b.writeVarInt(s.sameDimPlayerLoadedReuseRadiusChunks());
        b.writeBoolean(s.reusePlayerLoadedChunksForSameDim());
        b.writeVarInt(s.entityChunkRadius());
        b.writeVarInt(s.blockEntityChunkRadius());
        b.writeVarInt(s.blockUpdateChunkRadius());
        b.writeBoolean(s.renderSky());
        b.writeBoolean(s.renderTerrain());
        b.writeBoolean(s.renderTranslucent());
        b.writeBoolean(s.renderEntities());
        b.writeBoolean(s.renderBlockEntities());
        b.writeBoolean(s.renderParticles());
    }

    static PortalRenderSettings render(RegistryFriendlyByteBuf b) {
        return new PortalRenderSettings(b.readBoolean(), b.readBoolean(), 0, b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readBoolean(),
                b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readBoolean(), b.readBoolean(), b.readBoolean(), b.readBoolean(), b.readBoolean(), b.readBoolean(), null);
    }
}
