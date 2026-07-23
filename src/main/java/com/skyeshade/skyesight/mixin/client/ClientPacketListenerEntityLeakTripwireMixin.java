package com.skyeshade.skyesight.mixin.client;

import com.skyeshade.skyesight.client.world.SkyesightPortalEntityPoolLeakTripwire;
import com.skyeshade.skyesight.mixin.common.ClientboundEntityEventPacketAccessor;
import com.skyeshade.skyesight.mixin.common.ClientboundRotateHeadPacketAccessor;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundAnimatePacket;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundRotateHeadPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerEntityLeakTripwireMixin {
    @Inject(method = "handleAddEntity", at = @At("HEAD"))
    private void skyesight$recordRawAddEntity(ClientboundAddEntityPacket packet, CallbackInfo ci) {
        SkyesightPortalEntityPoolLeakTripwire.rawVanillaAddPacketHandled(
                packet.getClass().getSimpleName(),
                packet.getId(),
                packet.getType().toString(),
                packet.getX(),
                packet.getY(),
                packet.getZ()
        );
        SkyesightPortalEntityPoolLeakTripwire.rawVanillaPacketReachedMainClient(
                packet.getClass().getSimpleName(),
                packet.getId()
        );
    }

    @Inject(method = "handleRemoveEntities", at = @At("HEAD"))
    private void skyesight$recordRawRemoveEntities(ClientboundRemoveEntitiesPacket packet, CallbackInfo ci) {
        int entityId = packet.getEntityIds().isEmpty() ? -1 : packet.getEntityIds().getInt(0);
        SkyesightPortalEntityPoolLeakTripwire.rawVanillaPacketReachedMainClient(
                packet.getClass().getSimpleName(),
                entityId
        );
    }

    @Inject(method = "handleSetEntityData", at = @At("HEAD"))
    private void skyesight$recordRawSetEntityData(ClientboundSetEntityDataPacket packet, CallbackInfo ci) {
        SkyesightPortalEntityPoolLeakTripwire.rawVanillaPacketReachedMainClient(
                packet.getClass().getSimpleName(),
                packet.id()
        );
    }

    @Inject(method = "handleSetEquipment", at = @At("HEAD"))
    private void skyesight$recordRawSetEquipment(ClientboundSetEquipmentPacket packet, CallbackInfo ci) {
        SkyesightPortalEntityPoolLeakTripwire.rawVanillaPacketReachedMainClient(
                packet.getClass().getSimpleName(),
                packet.getEntity()
        );
    }

    @Inject(method = "handleSetEntityMotion", at = @At("HEAD"))
    private void skyesight$recordRawSetEntityMotion(ClientboundSetEntityMotionPacket packet, CallbackInfo ci) {
        SkyesightPortalEntityPoolLeakTripwire.rawVanillaPacketReachedMainClient(
                packet.getClass().getSimpleName(),
                packet.getId()
        );
    }

    @Inject(method = "handleTeleportEntity", at = @At("HEAD"))
    private void skyesight$recordRawTeleportEntity(ClientboundTeleportEntityPacket packet, CallbackInfo ci) {
        SkyesightPortalEntityPoolLeakTripwire.rawVanillaPacketReachedMainClient(
                packet.getClass().getSimpleName(),
                packet.getId()
        );
    }

    @Inject(method = "handleRotateMob", at = @At("HEAD"))
    private void skyesight$recordRawRotateHead(ClientboundRotateHeadPacket packet, CallbackInfo ci) {
        SkyesightPortalEntityPoolLeakTripwire.rawVanillaPacketReachedMainClient(
                packet.getClass().getSimpleName(),
                ((ClientboundRotateHeadPacketAccessor) packet).skyesight$getEntityId()
        );
    }

    @Inject(method = "handleAnimate", at = @At("HEAD"))
    private void skyesight$recordRawAnimate(ClientboundAnimatePacket packet, CallbackInfo ci) {
        SkyesightPortalEntityPoolLeakTripwire.rawVanillaPacketReachedMainClient(
                packet.getClass().getSimpleName(),
                packet.getId()
        );
    }

    @Inject(method = "handleEntityEvent", at = @At("HEAD"))
    private void skyesight$recordRawEntityEvent(ClientboundEntityEventPacket packet, CallbackInfo ci) {
        SkyesightPortalEntityPoolLeakTripwire.rawVanillaPacketReachedMainClient(
                packet.getClass().getSimpleName(),
                ((ClientboundEntityEventPacketAccessor) packet).skyesight$getEntityId()
        );
    }
}
