package com.skyeshade.skyesight.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Intent plus an untrusted predicted block identity for native destroy actions. The server must
 * reconstruct the portal ray and compare that block; no client dimension, entity id or reach is accepted.
 */
public record SkyesightPortalInteractionPayload(ResourceLocation portal, long revision, long sequence, Action action,
                                                net.minecraft.core.BlockPos predictedBlock)
        implements CustomPacketPayload {
    public enum Action {START, STOP, ABORT, ATTACK, USE}

    public SkyesightPortalInteractionPayload(ResourceLocation portal, long revision, long sequence, Action action) {
        this(portal, revision, sequence, action, null);
    }

    public static final Type<SkyesightPortalInteractionPayload> TYPE = new Type<>(ResourceLocation.parse("skyesight:portal_interaction"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SkyesightPortalInteractionPayload> STREAM_CODEC = StreamCodec.of(
            (b, p) -> {
                b.writeResourceLocation(p.portal);
                b.writeVarLong(p.revision);
                b.writeVarLong(p.sequence);
                b.writeEnum(p.action);
                b.writeNullable(p.predictedBlock, (buf, pos) -> buf.writeBlockPos(pos));
            },
            b -> new SkyesightPortalInteractionPayload(b.readResourceLocation(), b.readVarLong(), b.readVarLong(), b.readEnum(Action.class), b.readNullable(buf -> buf.readBlockPos())));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

