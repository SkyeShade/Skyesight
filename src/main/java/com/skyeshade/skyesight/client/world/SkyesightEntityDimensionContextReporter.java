package com.skyeshade.skyesight.client.world;

import com.skyeshade.skyesight.Skyesight;
import com.skyeshade.skyesight.SkyesightDebugConfig;
import com.skyeshade.skyesight.SkyesightNativeVisualEntityRoutingDebug;
import com.skyeshade.skyesight.entity.SkyesightEntityDimensionContext;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.LinkedHashMap;
import java.util.Map;

public final class SkyesightEntityDimensionContextReporter {
    private static final long LOG_INTERVAL_MILLIS = 5000L;
    private static final int MAX_SAMPLES_PER_DIMENSION = 5;
    private static long lastLogMillis;

    private SkyesightEntityDimensionContextReporter() {}

    public static void tick() {
        if (!SkyesightDebugConfig.ENTITY_DIMENSION_CONTEXT) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastLogMillis < LOG_INTERVAL_MILLIS) {
            return;
        }
        lastLogMillis = now;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }

        Map<ResourceKey<Level>, DimensionBucket> buckets = new LinkedHashMap<>();
        ResourceKey<Level> mainDimension = minecraft.level.dimension();

        for (Entity entity : minecraft.level.entitiesForRendering()) {
            addEntity(buckets, mainDimension, entity, "main_level", mainDimension);
        }

        SkyesightVisualWorldManager.forEachWorld((viewId, world) -> {
            if (world == null || world.isClosed()) {
                return;
            }
            ResourceKey<Level> visualDimension = world.dimension();
            int snapshotCount = 0;
            for (SkyesightVisualEntity visualEntity : world.entityStore().entities()) {
                if (visualEntity == null || visualEntity.entity() == null) {
                    continue;
                }
                snapshotCount++;
                addEntity(
                        buckets,
                        visualDimension,
                        visualEntity.entity(),
                        "visual_world_snapshot:" + viewId,
                        visualDimension
                );
            }
            int portalPoolCount = 0;
            for (Entity entity : SkyesightPortalEntityPool.entities(viewId, visualDimension)) {
                if (entity == null) {
                    continue;
                }
                portalPoolCount++;
                addEntity(
                        buckets,
                        visualDimension,
                        entity,
                        "portal_entity_pool:" + viewId,
                        visualDimension
                );
            }
            SkyesightNativeVisualEntityRoutingDebug.entityCounts(viewId, portalPoolCount, snapshotCount);
        });

        Skyesight.LOGGER.info(
                "[Skyesight] ENTITY_DIMENSION_CONTEXT: mainLevel={} {}",
                mainDimension.location(),
                formatBuckets(buckets)
        );
    }

    private static void addEntity(
            Map<ResourceKey<Level>, DimensionBucket> buckets,
            ResourceKey<Level> fallbackDimension,
            Entity entity,
            String sourcePool,
            ResourceKey<Level> defaultBucket
    ) {
        SkyesightEntityDimensionContext context = (SkyesightEntityDimensionContext) entity;
        ResourceKey<Level> dimension = context.skyesight$getEffectiveDimension(fallbackDimension);
        DimensionBucket bucket = buckets.computeIfAbsent(
                dimension == null ? defaultBucket : dimension,
                ignored -> new DimensionBucket()
        );
        bucket.count++;

        if (bucket.samples.length() >= 420 || bucket.sampleCount >= MAX_SAMPLES_PER_DIMENSION) {
            return;
        }
        if (bucket.samples.length() > 0) {
            bucket.samples.append(", ");
        }
        bucket.samples.append(sample(entity, context.skyesight$hasExplicitDimension(), sourcePool));
        bucket.sampleCount++;
    }

    private static String sample(Entity entity, boolean explicit, String sourcePool) {
        ResourceLocation typeId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        Vec3 pos = entity.position();
        return typeId
                + "#"
                + entity.getId()
                + " explicit="
                + explicit
                + " source="
                + sourcePool
                + " pos="
                + format(pos.x())
                + ","
                + format(pos.y())
                + ","
                + format(pos.z());
    }

    private static String formatBuckets(Map<ResourceKey<Level>, DimensionBucket> buckets) {
        if (buckets.isEmpty()) {
            return "dimensions=none";
        }

        StringBuilder builder = new StringBuilder();
        for (Map.Entry<ResourceKey<Level>, DimensionBucket> entry : buckets.entrySet()) {
            if (builder.length() > 0) {
                builder.append(" | ");
            }
            DimensionBucket bucket = entry.getValue();
            builder.append(entry.getKey().location())
                    .append(" count=")
                    .append(bucket.count)
                    .append(" samples=[")
                    .append(bucket.samples)
                    .append("]");
        }
        return builder.toString();
    }

    private static String format(double value) {
        return String.format(java.util.Locale.ROOT, "%.1f", value);
    }

    private static final class DimensionBucket {
        private int count;
        private int sampleCount;
        private final StringBuilder samples = new StringBuilder();
    }
}
