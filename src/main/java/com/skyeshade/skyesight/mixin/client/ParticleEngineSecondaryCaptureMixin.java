package com.skyeshade.skyesight.mixin.client;

import com.skyeshade.skyesight.client.world.SecondaryParticleCapture;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ParticleEngine.class)
public abstract class ParticleEngineSecondaryCaptureMixin {
    // Includes javac's synthetic destroy lambda, where TerrainParticle is constructed.
    // Outside a secondary capture this returns the original engine level unchanged.
    @Redirect(method = "*", at = @At(value = "FIELD", opcode = Opcodes.GETFIELD,
            target = "Lnet/minecraft/client/particle/ParticleEngine;level:Lnet/minecraft/client/multiplayer/ClientLevel;"))
    private ClientLevel skyesight$debrisLevel(ParticleEngine engine) {
        return SecondaryParticleCapture.creationLevel(((ParticleEngineAccessor) engine).skyesight$getLevel());
    }

    @ModifyArg(method = "makeParticle", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/particle/ParticleProvider;createParticle(Lnet/minecraft/core/particles/ParticleOptions;Lnet/minecraft/client/multiplayer/ClientLevel;DDDDDD)Lnet/minecraft/client/particle/Particle;"), index = 1)
    private ClientLevel skyesight$secondaryProviderLevel(ClientLevel level) {
        return SecondaryParticleCapture.creationLevel(level);
    }

    @Inject(method = "add", at = @At("HEAD"), cancellable = true)
    private void skyesight$captureSecondaryChild(Particle particle, CallbackInfo ci) {
        if (SecondaryParticleCapture.captureInstance(particle)) ci.cancel();
    }
}
