package com.skyeshade.skyesight.mixin.server;

import com.skyeshade.skyesight.server.SkyesightServerParticleBroadcaster;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerLevel.class)
public abstract class ServerLevelParticlesMixin {
    @Inject(method = "sendParticles(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/core/particles/ParticleOptions;ZDDDIDDDD)Z", at = @At("TAIL"))
    private <T extends ParticleOptions> void skyesight$onTargetedParticles(
            net.minecraft.server.level.ServerPlayer player, T type, boolean force,
            double x, double y, double z, int count, double dx, double dy, double dz, double speed,
            CallbackInfoReturnable<Boolean> cir) {
        SkyesightServerParticleBroadcaster.send((ServerLevel) (Object) this, player, type, force, x, y, z, count, dx, dy, dz, speed);
    }
    @Inject(
            method = "sendParticles(Lnet/minecraft/core/particles/ParticleOptions;DDDIDDDD)I",
            at = @At("TAIL")
    )
    private <T extends ParticleOptions> void skyesight$onSendParticles(
            T particleData,
            double x,
            double y,
            double z,
            int count,
            double xDist,
            double yDist,
            double zDist,
            double maxSpeed,
            CallbackInfoReturnable<Integer> cir
    ) {
        SkyesightServerParticleBroadcaster.send(
                (ServerLevel) (Object) this,
                particleData,
                false,
                x,
                y,
                z,
                count,
                xDist,
                yDist,
                zDist,
                maxSpeed
        );
    }
}
