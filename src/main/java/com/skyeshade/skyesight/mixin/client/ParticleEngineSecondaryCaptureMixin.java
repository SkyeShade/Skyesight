package com.skyeshade.skyesight.mixin.client;

import com.skyeshade.skyesight.client.world.SecondaryParticleCapture;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ParticleEngine.class)
public abstract class ParticleEngineSecondaryCaptureMixin {
    @ModifyArg(method = "makeParticle", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/particle/ParticleProvider;createParticle(Lnet/minecraft/core/particles/ParticleOptions;Lnet/minecraft/client/multiplayer/ClientLevel;DDDDDD)Lnet/minecraft/client/particle/Particle;"), index = 1)
    private ClientLevel skyesight$secondaryProviderLevel(ClientLevel level) { return SecondaryParticleCapture.creationLevel(level); }

    @Inject(method = "add", at = @At("HEAD"), cancellable = true)
    private void skyesight$captureSecondaryChild(Particle particle, CallbackInfo ci) {
        if (SecondaryParticleCapture.captureInstance(particle)) ci.cancel();
    }
}
