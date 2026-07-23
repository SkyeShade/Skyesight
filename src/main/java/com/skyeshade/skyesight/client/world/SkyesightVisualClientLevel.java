package com.skyeshade.skyesight.client.world;

import com.skyeshade.skyesight.Skyesight;
import com.skyeshade.skyesight.SkyesightDebugConfig;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.Holder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;

public final class SkyesightVisualClientLevel extends ClientLevel {
    private SkyesightVisualParticleManager particleManager;
    private String particleCaptureSource = "visual-addParticle";
    private ResourceLocation skyesightViewId;
    private long lastParticleCapturePathLogMillis;
    private final Set<String> suppressedSideEffectsLogged = new HashSet<>();

    public SkyesightVisualClientLevel(
            ClientPacketListener connection,
            ClientLevelData clientLevelData,
            ResourceKey<Level> dimension,
            Holder<DimensionType> dimensionType,
            int viewDistance,
            int serverSimulationDistance,
            Supplier<ProfilerFiller> profiler,
            LevelRenderer levelRenderer,
            boolean debug,
            long biomeZoomSeed
    ) {
        super(
                connection,
                clientLevelData,
                dimension,
                dimensionType,
                viewDistance,
                serverSimulationDistance,
                profiler,
                levelRenderer,
                debug,
                biomeZoomSeed
        );
    }

    public void setSkyesightParticleManager(SkyesightVisualParticleManager particleManager) {
        this.particleManager = particleManager;
    }

    public void setSkyesightViewId(ResourceLocation viewId) {
        this.skyesightViewId = viewId;
    }

    public ResourceLocation skyesightViewId() {
        return this.skyesightViewId;
    }

    public SkyesightVisualParticleManager skyesightParticleManager() {
        return this.particleManager;
    }

    @Override
    public void playSound(
            Player player,
            double x,
            double y,
            double z,
            SoundEvent sound,
            SoundSource category,
            float volume,
            float pitch
    ) {
        logSuppressedSideEffect("playSound", sound == null ? "null" : sound.toString(), x, y, z);
    }

    @Override
    public void playLocalSound(
            double x,
            double y,
            double z,
            SoundEvent sound,
            SoundSource category,
            float volume,
            float pitch,
            boolean distanceDelay
    ) {
        logSuppressedSideEffect("playLocalSound", sound == null ? "null" : sound.toString(), x, y, z);
    }

    @Override
    public void levelEvent(Player player, int type, BlockPos pos, int data) {
        logSuppressedSideEffect("levelEvent", String.valueOf(type), pos.getX(), pos.getY(), pos.getZ());
    }

    @Override
    public void gameEvent(Holder<GameEvent> gameEvent, Vec3 position, GameEvent.Context context) {
        logSuppressedSideEffect("gameEvent", String.valueOf(gameEvent.unwrapKey().orElse(null)), position.x(), position.y(), position.z());
    }

    @Override
    public void gameEvent(Entity entity, Holder<GameEvent> gameEvent, Vec3 position) {
        logSuppressedSideEffect("entityGameEvent", String.valueOf(gameEvent.unwrapKey().orElse(null)), position.x(), position.y(), position.z());
    }

    public void setSkyesightParticleCaptureSource(String particleCaptureSource) {
        this.particleCaptureSource = particleCaptureSource == null || particleCaptureSource.isBlank()
                ? "visual-addParticle"
                : particleCaptureSource;
    }

    @Override
    public void addParticle(
            ParticleOptions particleData,
            double x,
            double y,
            double z,
            double xSpeed,
            double ySpeed,
            double zSpeed
    ) {
        skyesight$captureParticleFromGenericPath(particleData, false, x, y, z, xSpeed, ySpeed, zSpeed, this.particleCaptureSource);
    }

    @Override
    public void addParticle(
            ParticleOptions particleData,
            boolean force,
            double x,
            double y,
            double z,
            double xSpeed,
            double ySpeed,
            double zSpeed
    ) {
        skyesight$captureParticleFromGenericPath(particleData, force, x, y, z, xSpeed, ySpeed, zSpeed, this.particleCaptureSource + ":force");
    }

    @Override
    public void addAlwaysVisibleParticle(
            ParticleOptions particleData,
            double x,
            double y,
            double z,
            double xSpeed,
            double ySpeed,
            double zSpeed
    ) {
        skyesight$captureParticleFromGenericPath(particleData, true, x, y, z, xSpeed, ySpeed, zSpeed, "visual-addAlwaysVisibleParticle");
    }

    @Override
    public void addAlwaysVisibleParticle(
            ParticleOptions particleData,
            boolean force,
            double x,
            double y,
            double z,
            double xSpeed,
            double ySpeed,
            double zSpeed
    ) {
        skyesight$captureParticleFromGenericPath(particleData, true, x, y, z, xSpeed, ySpeed, zSpeed, "visual-addAlwaysVisibleParticle:force");
    }

    public void skyesight$captureParticleFromGenericPath(
            ParticleOptions particleData,
            boolean longDistance,
            double x,
            double y,
            double z,
            double xSpeed,
            double ySpeed,
            double zSpeed,
            String source
    ) {
        if (this.particleManager == null) {
            logParticleCapturePathIfDue(particleData, x, y, z, xSpeed, ySpeed, zSpeed, source, false, "no-visual-particle-manager");
            logWatchAddParticleIfNeeded(particleData, x, y, z, xSpeed, ySpeed, zSpeed, source, false, "no-visual-particle-manager");
            return;
        }

        this.particleManager.addVisualParticle(
                this.skyesightViewId,
                particleData,
                x,
                y,
                z,
                xSpeed,
                ySpeed,
                zSpeed,
                longDistance,
                source
        );
        logParticleCapturePathIfDue(particleData, x, y, z, xSpeed, ySpeed, zSpeed, source, true, "-");
        logWatchAddParticleIfNeeded(particleData, x, y, z, xSpeed, ySpeed, zSpeed, source, true, "-");
    }

    private void logSuppressedSideEffect(
            String kind,
            String detail,
            double x,
            double y,
            double z
    ) {
        String key = kind + ":" + detail;
        if (!this.suppressedSideEffectsLogged.add(key) || this.suppressedSideEffectsLogged.size() > 16) {
            return;
        }
        Skyesight.LOGGER.info(
                "[Skyesight] Suppressed visual-level side effect viewId={} dim={} kind={} detail={} pos={},{},{}",
                this.skyesightViewId == null ? "-" : this.skyesightViewId,
                dimension().location(),
                kind,
                detail,
                format(x),
                format(y),
                format(z)
        );
    }

    private void logParticleCapturePathIfDue(
            ParticleOptions particleData,
            double x,
            double y,
            double z,
            double xSpeed,
            double ySpeed,
            double zSpeed,
            String source,
            boolean captured,
            String reason
    ) {
        if (!SkyesightDebugConfig.VERBOSE_RENDER && !SkyesightDebugConfig.WATCH_DEBUG) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now - this.lastParticleCapturePathLogMillis < 1000L) {
            return;
        }

        this.lastParticleCapturePathLogMillis = now;
        Skyesight.LOGGER.info(
                "[Skyesight] PORTAL_VISUAL_PARTICLE_CAPTURE_PATH: viewId={} targetDim={} captureMethod={} type={} pos={},{},{} velocity={},{},{} captured={} reason={}",
                this.skyesightViewId == null ? "-" : this.skyesightViewId,
                dimension().location(),
                source == null || source.isBlank() ? "unknown" : source,
                particleData == null ? "null" : BuiltInRegistries.PARTICLE_TYPE.getKey(particleData.getType()),
                format(x),
                format(y),
                format(z),
                format(xSpeed),
                format(ySpeed),
                format(zSpeed),
                captured ? "yes" : "no",
                reason == null || reason.isBlank() ? "-" : reason
        );
    }

    private void logWatchAddParticleIfNeeded(
            ParticleOptions particleData,
            double x,
            double y,
            double z,
            double xSpeed,
            double ySpeed,
            double zSpeed,
            String source,
            boolean storedManager,
            String reason
    ) {
        if (!SkyesightVisualParticleWatch.near(this.skyesightViewId, x, y, z, 2.5D)) {
            return;
        }
        String particleType = particleData == null ? "null" : String.valueOf(BuiltInRegistries.PARTICLE_TYPE.getKey(particleData.getType()));
        SkyesightVisualParticleWatch.recordAddParticle(this.skyesightViewId, particleType, storedManager);

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
                "[Skyesight] PORTAL_PARTICLE_WATCH_ADD_PARTICLE: viewId={} levelClass={} overload={} type={} pos={},{},{} vel={},{},{} captured={} storedManager={} reason={} stackTop={}",
                this.skyesightViewId == null ? "-" : this.skyesightViewId,
                getClass().getName(),
                source == null || source.isBlank() ? "unknown" : source,
                particleType,
                format(x),
                format(y),
                format(z),
                format(xSpeed),
                format(ySpeed),
                format(zSpeed),
                storedManager ? "yes" : "no",
                storedManager ? "yes" : "no",
                reason == null || reason.isBlank() ? "-" : reason,
                stackTop
        );
    }

    private static String format(double value) {
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }
}
