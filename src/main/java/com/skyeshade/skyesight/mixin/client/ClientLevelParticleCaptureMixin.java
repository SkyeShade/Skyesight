package com.skyeshade.skyesight.mixin.client;

import com.skyeshade.skyesight.Skyesight;
import com.skyeshade.skyesight.client.world.SkyesightVisualClientLevel;
import com.skyeshade.skyesight.client.world.SkyesightVisualParticleWatch;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientLevel.class)
public abstract class ClientLevelParticleCaptureMixin {
    @Inject(
            method = "addParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V",
            at = @At("HEAD")
    )
    private void skyesight$captureVisualAddParticle(
            ParticleOptions particleData,
            double x,
            double y,
            double z,
            double xSpeed,
            double ySpeed,
            double zSpeed,
            CallbackInfo callbackInfo
    ) {
        if ((Object) this instanceof SkyesightVisualClientLevel visualLevel) {
            visualLevel.skyesight$captureParticleFromGenericPath(
                    particleData,
                    false,
                    x,
                    y,
                    z,
                    xSpeed,
                    ySpeed,
                    zSpeed,
                    "ClientLevel.addParticle"
            );
        } else {
            skyesight$logMainLevelParticleNearWatch(particleData, x, y, z, xSpeed, ySpeed, zSpeed, "ClientLevel.addParticle");
        }
    }

    @Inject(
            method = "addParticle(Lnet/minecraft/core/particles/ParticleOptions;ZDDDDDD)V",
            at = @At("HEAD")
    )
    private void skyesight$captureVisualAddParticleForced(
            ParticleOptions particleData,
            boolean force,
            double x,
            double y,
            double z,
            double xSpeed,
            double ySpeed,
            double zSpeed,
            CallbackInfo callbackInfo
    ) {
        if ((Object) this instanceof SkyesightVisualClientLevel visualLevel) {
            visualLevel.skyesight$captureParticleFromGenericPath(
                    particleData,
                    force,
                    x,
                    y,
                    z,
                    xSpeed,
                    ySpeed,
                    zSpeed,
                    "ClientLevel.addParticle:force"
            );
        } else {
            skyesight$logMainLevelParticleNearWatch(particleData, x, y, z, xSpeed, ySpeed, zSpeed, "ClientLevel.addParticle:force");
        }
    }

    @Inject(
            method = "addAlwaysVisibleParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V",
            at = @At("HEAD")
    )
    private void skyesight$captureVisualAlwaysVisibleParticle(
            ParticleOptions particleData,
            double x,
            double y,
            double z,
            double xSpeed,
            double ySpeed,
            double zSpeed,
            CallbackInfo callbackInfo
    ) {
        if ((Object) this instanceof SkyesightVisualClientLevel visualLevel) {
            visualLevel.skyesight$captureParticleFromGenericPath(
                    particleData,
                    true,
                    x,
                    y,
                    z,
                    xSpeed,
                    ySpeed,
                    zSpeed,
                    "ClientLevel.addAlwaysVisibleParticle"
            );
        } else {
            skyesight$logMainLevelParticleNearWatch(particleData, x, y, z, xSpeed, ySpeed, zSpeed, "ClientLevel.addAlwaysVisibleParticle");
        }
    }

    @Inject(
            method = "addAlwaysVisibleParticle(Lnet/minecraft/core/particles/ParticleOptions;ZDDDDDD)V",
            at = @At("HEAD")
    )
    private void skyesight$captureVisualAlwaysVisibleParticleForced(
            ParticleOptions particleData,
            boolean force,
            double x,
            double y,
            double z,
            double xSpeed,
            double ySpeed,
            double zSpeed,
            CallbackInfo callbackInfo
    ) {
        if ((Object) this instanceof SkyesightVisualClientLevel visualLevel) {
            visualLevel.skyesight$captureParticleFromGenericPath(
                    particleData,
                    true,
                    x,
                    y,
                    z,
                    xSpeed,
                    ySpeed,
                    zSpeed,
                    "ClientLevel.addAlwaysVisibleParticle:force"
            );
        } else {
            skyesight$logMainLevelParticleNearWatch(particleData, x, y, z, xSpeed, ySpeed, zSpeed, "ClientLevel.addAlwaysVisibleParticle:force");
        }
    }

    private void skyesight$logMainLevelParticleNearWatch(
            ParticleOptions particleData,
            double x,
            double y,
            double z,
            double xSpeed,
            double ySpeed,
            double zSpeed,
            String overload
    ) {
        if (!SkyesightVisualParticleWatch.nearAnyView(x, y, z, 2.5D)) {
            return;
        }

        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        String stackTop = "-";
        for (StackTraceElement element : stackTrace) {
            String className = element.getClassName();
            if (!className.contains("Skyesight") && !className.equals(Thread.class.getName())) {
                stackTop = element.toString();
                break;
            }
        }

        Skyesight.LOGGER.info(
                "[Skyesight] PORTAL_PARTICLE_WATCH_ADD_PARTICLE: viewId=main-client-level levelClass={} overload={} type={} pos={},{},{} vel={},{},{} captured=no storedManager=no reason=main-client-level-compare-only stackTop={}",
                ((Object) this).getClass().getName(),
                overload,
                particleData == null ? "null" : BuiltInRegistries.PARTICLE_TYPE.getKey(particleData.getType()),
                format(x),
                format(y),
                format(z),
                format(xSpeed),
                format(ySpeed),
                format(zSpeed),
                stackTop
        );
    }

    private static String format(double value) {
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }
}
