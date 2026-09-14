package com.skyeshade.skyesight.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/** Ordered before vanilla transfer; only an existing revision can authorize client presentation. */
public record SkyesightRegionTraversalPayload(ResourceLocation view, long revision, long sequence, Vec3 feet)
        implements CustomPacketPayload {
    public static final Type<SkyesightRegionTraversalPayload> TYPE=new Type<>(ResourceLocation.parse("skyesight:region_traversal"));
    public static final StreamCodec<RegistryFriendlyByteBuf,SkyesightRegionTraversalPayload> STREAM_CODEC=StreamCodec.of(
            (b,p)->{b.writeResourceLocation(p.view);b.writeVarLong(p.revision);b.writeVarLong(p.sequence);
                b.writeDouble(p.feet.x);b.writeDouble(p.feet.y);b.writeDouble(p.feet.z);},
            b->new SkyesightRegionTraversalPayload(b.readResourceLocation(),b.readVarLong(),b.readVarLong(),
                    new Vec3(b.readDouble(),b.readDouble(),b.readDouble())));
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
