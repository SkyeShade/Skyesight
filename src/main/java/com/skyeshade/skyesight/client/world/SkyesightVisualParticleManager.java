package com.skyeshade.skyesight.client.world;

import com.skyeshade.skyesight.network.SkyesightParticlePayload;
import com.skyeshade.skyesight.mixin.client.ParticleAccessor;
import net.minecraft.client.particle.Particle;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

public final class SkyesightVisualParticleManager {
    private static final int MAX_PARTICLES = 4096;
    private static final int MAX_PARTICLES_PER_PAYLOAD = 64;
    private final ResourceKey<Level> dimension;
    private final List<VisualParticle> particles = new ArrayList<>();
    private final List<VisualParticle> pendingAdds = new ArrayList<>();
    private final Random random = new Random();
    private long nextParticleSequence;
    private long lastProofSpawnMillis;
    private long lastUpdateMillis;
    private boolean campfireSmokeSeen;
    private String lastSource = "never";
    private String lastSample = "-";
    private int lastSourceCaptured;
    private int lastPendingAddsDrained;
    private int lastExpiredRemoved;
    private final Map<String, Integer> captureAuditTypes = new LinkedHashMap<>();
    private int captureAuditCount;
    private final Map<String, Integer> totalCaptureTypes = new LinkedHashMap<>();
    private int totalCaptureCount;

    public SkyesightVisualParticleManager(ResourceKey<Level> dimension) {
        this.dimension = dimension;
    }

    public void addParticle(SkyesightParticlePayload payload) {
        if (payload == null) {
            return;
        }

        int count = payload.count() == 0 ? 1 : Math.min(payload.count(), MAX_PARTICLES_PER_PAYLOAD);
        String particleId = particleId(payload.particle());

        for (int i = 0; i < count; i++) {
            double x = payload.x();
            double y = payload.y();
            double z = payload.z();
            double xd = payload.xDist();
            double yd = payload.yDist();
            double zd = payload.zDist();

            if (payload.count() > 0) {
                x += this.random.nextGaussian() * payload.xDist();
                y += this.random.nextGaussian() * payload.yDist();
                z += this.random.nextGaussian() * payload.zDist();
                xd *= this.random.nextGaussian();
                yd *= this.random.nextGaussian();
                zd *= this.random.nextGaussian();
            }

            if (payload.maxSpeed() != 0.0D) {
                xd *= payload.maxSpeed();
                yd *= payload.maxSpeed();
                zd *= payload.maxSpeed();
            }

            add(new VisualParticle(
                    this.nextParticleSequence++,
                    new Vec3(x, y, z),
                    new Vec3(xd, yd, zd),
                    payload.particle(),
                    particleId,
                    colorFor(particleId, this.dimension),
                    sizeFor(particleId),
                    lifetimeFor(particleId),
                    true,
                    false
            ));
        }

        if (particleId.contains("smoke")) {
            this.campfireSmokeSeen = true;
        }
        recordCaptureAuditType(particleId, count);
        this.lastUpdateMillis = System.currentTimeMillis();
        this.lastSource = "payload";
        this.lastSourceCaptured = count;
        this.lastSample = particleId + " x=" + format(payload.x()) + " y=" + format(payload.y()) + " z=" + format(payload.z()) + " count=" + count;
    }

    public void addVisualParticle(
            ParticleOptions particleOptions,
            double x,
            double y,
            double z,
            double xSpeed,
            double ySpeed,
            double zSpeed,
            boolean longDistance,
            String source
    ) {
        addVisualParticle(null, particleOptions, x, y, z, xSpeed, ySpeed, zSpeed, longDistance, source);
    }

    public void addVisualParticle(
            ResourceLocation viewId,
            ParticleOptions particleOptions,
            double x,
            double y,
            double z,
            double xSpeed,
            double ySpeed,
            double zSpeed,
            boolean longDistance,
            String source
    ) {
        if (particleOptions == null) {
            return;
        }

        String particleId = particleId(particleOptions);
        boolean watched = SkyesightVisualParticleWatch.near(viewId, x, y, z, 2.5D);
        add(new VisualParticle(
                this.nextParticleSequence++,
                new Vec3(x, y, z),
                new Vec3(xSpeed, ySpeed, zSpeed),
                particleOptions,
                particleId,
                colorFor(particleId, this.dimension),
                sizeFor(particleId),
                lifetimeFor(particleId),
                true,
                watched
        ));

        if (particleId.contains("smoke")) {
            this.campfireSmokeSeen = true;
        }
        recordCaptureAuditType(particleId, 1);
        this.lastUpdateMillis = System.currentTimeMillis();
        this.lastSource = source == null || source.isBlank() ? "visual-addParticle" : source;
        this.lastSourceCaptured = 1;
        this.lastSample = particleId + " x=" + format(x) + " y=" + format(y) + " z=" + format(z)
                + " velocity=" + format(xSpeed) + "," + format(ySpeed) + "," + format(zSpeed)
                + " longDistance=" + yesNo(longDistance);
    }

    public void spawnProofParticles(Vec3 center) {
        if (center == null) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now - this.lastProofSpawnMillis < 250L) {
            return;
        }

        this.lastProofSpawnMillis = now;
        ParticleOptions particleOptions = Level.END.equals(this.dimension) ? ParticleTypes.END_ROD : ParticleTypes.FLAME;
        String type = particleId(particleOptions);

        for (int i = 0; i < 8; i++) {
            double angle = (Math.PI * 2.0D * i) / 8.0D;
            Vec3 pos = center.add(Math.cos(angle) * 1.25D, 0.25D + (i % 3) * 0.25D, Math.sin(angle) * 1.25D);
            Vec3 vel = new Vec3(Math.cos(angle) * 0.01D, 0.015D, Math.sin(angle) * 0.01D);
            add(new VisualParticle(
                    this.nextParticleSequence++,
                    pos,
                    vel,
                    particleOptions,
                    type,
                    colorFor(type, this.dimension),
                    Level.END.equals(this.dimension) ? 0.18F : 0.22F,
                    36,
                    true,
                    false
            ));
        }

        this.lastUpdateMillis = now;
        this.lastSource = "proof_spawn";
        this.lastSourceCaptured = 8;
        recordCaptureAuditType(type, 8);
        this.lastSample = type + " center=" + format(center.x()) + "," + format(center.y()) + "," + format(center.z());
    }

    public void tick() {
        this.lastPendingAddsDrained = drainPendingAdds();

        for (VisualParticle particle : this.particles) {
            particle.tick();
        }

        int beforeRemove = this.particles.size();
        this.particles.removeIf(VisualParticle::removed);
        this.lastExpiredRemoved = beforeRemove - this.particles.size();
        this.lastPendingAddsDrained += drainPendingAdds();
    }

    public List<VisualParticle> particles() {
        drainPendingAdds();
        return List.copyOf(this.particles);
    }

    public int size() {
        return this.particles.size() + this.pendingAdds.size();
    }

    public String debugSummary() {
        return "size=" + size()
                + " active=" + this.particles.size()
                + " pendingAdds=" + this.pendingAdds.size()
                + " pendingAddsDrained=" + this.lastPendingAddsDrained
                + " expiredRemoved=" + this.lastExpiredRemoved
                + " lastSource=" + this.lastSource
                + " lastSourceCaptured=" + this.lastSourceCaptured
                + " lastUpdateMs=" + this.lastUpdateMillis
                + " campfireSmokeSeen=" + yesNo(this.campfireSmokeSeen)
                + " sample=" + this.lastSample;
    }

    public String debugTypeSummary() {
        List<VisualParticle> snapshot = particles();
        if (snapshot.isEmpty()) {
            return "-";
        }

        Map<String, Integer> counts = new LinkedHashMap<>();
        for (VisualParticle particle : snapshot) {
            counts.merge(particle.particleId(), 1, Integer::sum);
        }

        StringBuilder summary = new StringBuilder();
        int emitted = 0;
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            if (emitted++ >= 12) {
                summary.append(",...");
                break;
            }
            if (!summary.isEmpty()) {
                summary.append(',');
            }
            summary.append(entry.getKey()).append('=').append(entry.getValue());
        }

        return summary.toString();
    }

    public void beginCaptureAudit() {
        this.captureAuditTypes.clear();
        this.captureAuditCount = 0;
    }

    public int captureAuditCount() {
        return this.captureAuditCount;
    }

    public String captureAuditTypeSummary() {
        return mapSummary(this.captureAuditTypes);
    }

    public int totalCaptureCount() {
        return this.totalCaptureCount;
    }

    public Map<String, Integer> totalCaptureTypesSnapshot() {
        return new LinkedHashMap<>(this.totalCaptureTypes);
    }

    public String captureDeltaSummary(Map<String, Integer> before) {
        Map<String, Integer> delta = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : this.totalCaptureTypes.entrySet()) {
            int previous = before == null ? 0 : before.getOrDefault(entry.getKey(), 0);
            int value = entry.getValue() - previous;
            if (value > 0) {
                delta.put(entry.getKey(), value);
            }
        }
        return mapSummary(delta);
    }

    public boolean campfireSmokeSeen() {
        return this.campfireSmokeSeen;
    }

    public String lastSource() {
        return this.lastSource;
    }

    public int lastSourceCaptured() {
        return this.lastSourceCaptured;
    }

    public void close() {
        this.particles.clear();
        this.pendingAdds.clear();
    }

    private void add(VisualParticle particle) {
        this.pendingAdds.add(particle);
    }

    private void recordCaptureAuditType(String particleId, int count) {
        this.captureAuditCount += count;
        this.captureAuditTypes.merge(particleId, count, Integer::sum);
        this.totalCaptureCount += count;
        this.totalCaptureTypes.merge(particleId, count, Integer::sum);
    }

    private int drainPendingAdds() {
        if (this.pendingAdds.isEmpty()) {
            return 0;
        }

        if (this.pendingAdds.size() > MAX_PARTICLES) {
            this.pendingAdds.subList(0, this.pendingAdds.size() - MAX_PARTICLES).clear();
        }

        int overflow = this.particles.size() + this.pendingAdds.size() - MAX_PARTICLES;
        if (overflow > 0) {
            int removeFromActive = Math.min(overflow, this.particles.size());
            if (removeFromActive > 0) {
                this.particles.subList(0, removeFromActive).clear();
            }

            int remainingOverflow = overflow - removeFromActive;
            if (remainingOverflow > 0) {
                this.pendingAdds.subList(0, remainingOverflow).clear();
            }
        }

        int drained = this.pendingAdds.size();
        this.particles.addAll(this.pendingAdds);
        this.pendingAdds.clear();
        return drained;
    }

    private static String particleId(ParticleOptions particle) {
        ResourceLocation id = particle == null ? null : BuiltInRegistries.PARTICLE_TYPE.getKey(particle.getType());
        return id == null ? "unknown" : id.toString();
    }

    private static int lifetimeFor(String particleId) {
        if (particleId.contains("smoke")) {
            return 50;
        }
        if (particleId.contains("dragon_breath") || particleId.contains("portal")) {
            return 60;
        }
        return 36;
    }

    private static float sizeFor(String particleId) {
        if (particleId.contains("smoke") || particleId.contains("dragon_breath")) {
            return 0.35F;
        }
        return 0.22F;
    }

    private static ParticleColor colorFor(String particleId, ResourceKey<Level> dimension) {
        if (particleId.contains("smoke")) {
            return new ParticleColor(0.45F, 0.43F, 0.40F, 0.55F);
        }
        if (particleId.contains("portal") || particleId.contains("dragon_breath")) {
            return new ParticleColor(0.62F, 0.22F, 0.95F, 0.70F);
        }
        if (particleId.contains("end_rod") || Level.END.equals(dimension)) {
            return new ParticleColor(0.72F, 0.64F, 1.0F, 0.72F);
        }
        return new ParticleColor(1.0F, 0.38F, 0.08F, 0.72F);
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static String yesNo(boolean value) {
        return value ? "yes" : "no";
    }

    private static String mapSummary(Map<String, Integer> values) {
        if (values == null || values.isEmpty()) {
            return "-";
        }

        StringBuilder summary = new StringBuilder();
        int emitted = 0;
        for (Map.Entry<String, Integer> entry : values.entrySet()) {
            if (emitted++ >= 12) {
                summary.append(",...");
                break;
            }
            if (!summary.isEmpty()) {
                summary.append(',');
            }
            summary.append(entry.getKey()).append('=').append(entry.getValue());
        }
        return summary.toString();
    }

    public static final class VisualParticle {
        private Vec3 previousPosition;
        private Vec3 position;
        private Vec3 velocity;
        private final ParticleOptions particleOptions;
        private final String particleId;
        private final String cacheKey;
        private final ParticleColor color;
        private final float size;
        private final int lifetime;
        private final boolean translucent;
        private final boolean watched;
        private Particle clientParticle;
        private int age;

        private VisualParticle(
                long sequence,
                Vec3 position,
                Vec3 velocity,
                ParticleOptions particleOptions,
                String particleId,
                ParticleColor color,
                float size,
                int lifetime,
                boolean translucent,
                boolean watched
        ) {
            this.previousPosition = position;
            this.position = position;
            this.velocity = velocity;
            this.particleOptions = particleOptions;
            this.particleId = particleId;
            this.cacheKey = particleId + "#" + sequence + "@" + quantize(position.x()) + "," + quantize(position.y()) + "," + quantize(position.z());
            this.color = color;
            this.size = size;
            this.lifetime = lifetime;
            this.translucent = translucent;
            this.watched = watched;
        }

        private void tick() {
            if (this.clientParticle != null) {
                this.clientParticle.tick();
                ParticleAccessor accessor = (ParticleAccessor) this.clientParticle;
                this.previousPosition = new Vec3(accessor.skyesight$getXo(), accessor.skyesight$getYo(), accessor.skyesight$getZo());
                this.position = this.clientParticle.getPos();
                this.velocity = this.position.subtract(this.previousPosition);
                this.age = accessor.skyesight$getAge();
                return;
            }

            this.previousPosition = this.position;
            this.position = this.position.add(this.velocity);
            this.velocity = this.velocity.scale(0.92D).add(0.0D, 0.002D, 0.0D);
            this.age++;
        }

        public Vec3 renderPosition(float partialTick) {
            return new Vec3(
                    this.previousPosition.x() + (this.position.x() - this.previousPosition.x()) * partialTick,
                    this.previousPosition.y() + (this.position.y() - this.previousPosition.y()) * partialTick,
                    this.previousPosition.z() + (this.position.z() - this.previousPosition.z()) * partialTick
            );
        }

        public ParticleColor color() {
            return this.color;
        }

        public float size() {
            return this.size;
        }

        public boolean translucent() {
            return this.translucent;
        }

        public ParticleOptions particleOptions() {
            return this.particleOptions;
        }

        public Vec3 velocity() {
            return this.velocity;
        }

        public String particleId() {
            return this.particleId;
        }

        public String cacheKey() {
            return this.cacheKey;
        }

        public Particle clientParticle() {
            return this.clientParticle;
        }

        public void attachClientParticle(Particle particle) {
            this.clientParticle = particle;
        }

        public boolean hasClientParticle() {
            return this.clientParticle != null;
        }

        public boolean watched() {
            return this.watched;
        }

        public int age() {
            if (this.clientParticle != null) {
                return ((ParticleAccessor) this.clientParticle).skyesight$getAge();
            }

            return this.age;
        }

        public int lifetime() {
            if (this.clientParticle != null) {
                return ((ParticleAccessor) this.clientParticle).skyesight$getLifetime();
            }

            return this.lifetime;
        }

        private boolean removed() {
            if (this.clientParticle != null) {
                return !this.clientParticle.isAlive();
            }

            return this.age >= this.lifetime;
        }

        private static long quantize(double value) {
            return Math.round(value * 1000.0D);
        }
    }

    public record ParticleColor(float red, float green, float blue, float alpha) {}
}
