package com.skyeshade.skyesight.network;

import com.skyeshade.skyesight.api.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;

/** Revisioned definition or tombstone; local windows are never sent over the network. */
public record SkyesightPortalRegionPayload(ResourceLocation id, long revision, PortalRegionDefinition definition)
        implements CustomPacketPayload {
    public static final Type<SkyesightPortalRegionPayload> TYPE = new Type<>(ResourceLocation.parse("skyesight:portal_region"));
    public static final StreamCodec<RegistryFriendlyByteBuf,SkyesightPortalRegionPayload> STREAM_CODEC = StreamCodec.of(
            (b,p) -> {
                b.writeResourceLocation(p.id); b.writeVarLong(p.revision); b.writeBoolean(p.definition != null);
                if (p.definition == null) return;
                var d=p.definition; writeFrame(b,d.source()); writeFrame(b,d.destination());
                b.writeVarInt(d.shape().strips().size());
                for(var s:d.shape().strips()) { b.writeInt(s.minU()); b.writeInt(s.minV()); b.writeInt(s.maxU()); b.writeInt(s.maxV()); }
                b.writeEnum(d.sidedness()); b.writeBoolean(d.bidirectional()); b.writeDouble(d.activationDistance());
                b.writeVarInt(d.renderRadiusChunks()); b.writeVarInt(d.entityRadiusChunks()); b.writeVarInt(d.simulationRadiusChunks());
                b.writeEnum(d.behavior()); var w=d.windowSettings();
                b.writeVarInt(w.visibleSize());b.writeVarInt(w.warmMargin());b.writeVarInt(w.lookAhead());b.writeVarInt(w.hysteresis());
            }, b -> {
                var id=b.readResourceLocation(); long revision=b.readVarLong();
                if (!b.readBoolean()) return new SkyesightPortalRegionPayload(id,revision,null);
                var source=readFrame(b); var destination=readFrame(b); int count=b.readVarInt();
                if(count<1 || count>PortalRegionShape.MAX_STRIPS) throw new IllegalArgumentException("Region strip count");
                var strips=new ArrayList<PortalRegionShape.Strip>();
                for(int i=0;i<count;i++) strips.add(new PortalRegionShape.Strip(b.readInt(),b.readInt(),b.readInt(),b.readInt()));
                return new SkyesightPortalRegionPayload(id,revision,new PortalRegionDefinition(id,source,destination,
                        new PortalRegionShape(strips),b.readEnum(PortalSidedness.class),b.readBoolean(),b.readDouble(),b.readVarInt(),b.readVarInt(),b.readVarInt(),
                        b.readEnum(PortalBehavior.class),new PortalRegionWindowSettings(b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readVarInt())));
            });
    private static void writeFrame(RegistryFriendlyByteBuf b, PortalRegionFrame f) {
        b.writeResourceLocation(f.dimension().location()); b.writeDouble(f.origin().x); b.writeDouble(f.origin().y);
        b.writeDouble(f.origin().z); b.writeQuaternion(f.rotation());
    }
    private static PortalRegionFrame readFrame(RegistryFriendlyByteBuf b) {
        return new PortalRegionFrame(ResourceKey.create(Registries.DIMENSION,b.readResourceLocation()),
                new Vec3(b.readDouble(),b.readDouble(),b.readDouble()),b.readQuaternion());
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static ResourceLocation viewId(ResourceLocation id, boolean reverse) {
        return ResourceLocation.fromNamespaceAndPath(id.getNamespace(),"portal_region/"+id.getPath()+(reverse?"/b":"/a"));
    }
}
