package com.skyeshade.skyesight.server.portal;

import com.skyeshade.skyesight.Skyesight;
import com.skyeshade.skyesight.SkyesightDebugConfig;
import com.skyeshade.skyesight.network.SkyesightProxyMarkerPayload;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@EventBusSubscriber(modid = Skyesight.MODID)
public final class PortalProxyArmorStandDebugManager {
    private static final String TAG_PROXY_MARKER = "skyesight_proxy_marker";
    private static final String TAG_DEBUG = "skyesight_debug";
    private static final String TAG_KEY_PREFIX = "skyesight_proxy_key_";
    private static final long STALE_MILLIS = 2_000L;
    private static final long DUPLICATE_SCAN_INTERVAL_MILLIS = 2_000L;

    private static final Map<ProxyStandKey, ServerMarker> MARKERS = new HashMap<>();
    private static final Map<ProxyStandKey, UUID> ACTIVE_STAND_IDS = new HashMap<>();
    private static final Map<ProxyStandKey, Long> LAST_SEEN_MILLIS = new HashMap<>();

    private static long lastSummaryMillis;
    private static long lastDuplicateScanMillis;
    private static int spawnedSinceSummary;
    private static int reusedSinceSummary;
    private static int teleportedSinceSummary;
    private static int removedSinceSummary;
    private static int expiredRemovedSinceSummary;
    private static int duplicatesRemovedSinceSummary;
    private static boolean disabledCleanupDone = true;
    private static boolean cleanupInProgress;

    private PortalProxyArmorStandDebugManager() {}

    public static void handleMarker(SkyesightProxyMarkerPayload payload, ServerPlayer sender) {
        if (payload == null || sender == null || sender.server == null || !SkyesightDebugConfig.SHOW_PROXY_ARMOR_STANDS) {
            return;
        }
        disabledCleanupDone = false;
        ProxyStandKey key = stableKey(payload);
        long now = System.currentTimeMillis();
        MARKERS.put(key, new ServerMarker(
                key,
                payload.markerName(),
                payload.apparentPosition(),
                payload.realPlayerName(),
                payload.direction(),
                payload.syntheticReverse(),
                payload.variant(),
                now
        ));
        LAST_SEEN_MILLIS.put(key, now);
    }

    public static void removeAll(MinecraftServer server) {
        cleanupInProgress = true;
        MARKERS.clear();
        ACTIVE_STAND_IDS.clear();
        LAST_SEEN_MILLIS.clear();
        try {
            if (server == null) {
                return;
            }
            List<Entity> toRemove = new ArrayList<>();
            for (ServerLevel level : server.getAllLevels()) {
                for (Entity entity : level.getAllEntities()) {
                    if (entity.getTags().contains(TAG_PROXY_MARKER)) {
                        toRemove.add(entity);
                    }
                }
            }
            discardAll(toRemove);
        } finally {
            cleanupInProgress = false;
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server == null) {
            return;
        }
        if (!SkyesightDebugConfig.SHOW_PROXY_ARMOR_STANDS) {
            if (!disabledCleanupDone) {
                removeAll(server);
                disabledCleanupDone = true;
            }
            return;
        }
        disabledCleanupDone = false;
        if (cleanupInProgress) {
            return;
        }

        long now = System.currentTimeMillis();
        cleanupDuplicates(server, now);
        expireStale(server, now);

        for (ServerMarker marker : MARKERS.values()) {
            ServerLevel level = server.getLevel(marker.key().markerDimension());
            if (level == null) {
                continue;
            }
            ArmorStand stand = findOrCreateStand(level, marker);
            if (stand == null) {
                continue;
            }
            applyStandProperties(stand, marker);
            stand.teleportTo(marker.apparentPosition().x, marker.apparentPosition().y, marker.apparentPosition().z);
            teleportedSinceSummary++;
        }
        maybeLogSummary(now);
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        removeAll(event.getServer());
    }

    private static ArmorStand findOrCreateStand(ServerLevel level, ServerMarker marker) {
        ProxyStandKey key = marker.key();
        UUID knownUuid = ACTIVE_STAND_IDS.get(key);
        if (knownUuid != null) {
            Entity entity = level.getEntity(knownUuid);
            if (isLiveProxyStand(entity, key)) {
                reusedSinceSummary++;
                return (ArmorStand) entity;
            }
            ACTIVE_STAND_IDS.remove(key);
        }

        ArmorStand existing = findExistingStandByKey(level, key);
        if (existing != null) {
            ACTIVE_STAND_IDS.put(key, existing.getUUID());
            reusedSinceSummary++;
            return existing;
        }

        ArmorStand stand = EntityType.ARMOR_STAND.create(level);
        if (stand == null) {
            return null;
        }
        stand.setPos(marker.apparentPosition());
        applyStandProperties(stand, marker);
        if (!level.addFreshEntity(stand)) {
            return null;
        }
        ACTIVE_STAND_IDS.put(key, stand.getUUID());
        spawnedSinceSummary++;
        Skyesight.LOGGER.info(
                "[Skyesight] PORTAL_PROXY_ARMOR_STAND_SPAWN: key={} dim={} pos={} name={}",
                key.stableHash(),
                key.markerDimension().location(),
                formatVec(marker.apparentPosition()),
                marker.markerName()
        );
        return stand;
    }

    private static void applyStandProperties(ArmorStand stand, ServerMarker marker) {
        stand.setNoGravity(true);
        stand.setInvulnerable(true);
        stand.setSilent(true);
        stand.setCustomName(Component.literal(marker.markerName()));
        stand.setCustomNameVisible(true);
        stand.setGlowingTag(true);
        stand.setInvisible(false);
        stand.addTag(TAG_PROXY_MARKER);
        stand.addTag(TAG_DEBUG);
        stand.addTag(marker.key().stableTag());
        stand.addTag("skyesight_view_" + sanitize(marker.key().viewId().toString()));
    }

    private static boolean isLiveProxyStand(Entity entity, ProxyStandKey key) {
        return entity instanceof ArmorStand
                && !entity.isRemoved()
                && entity.getTags().contains(TAG_PROXY_MARKER)
                && entity.getTags().contains(key.stableTag());
    }

    private static ArmorStand findExistingStandByKey(ServerLevel level, ProxyStandKey key) {
        ArmorStand found = null;
        List<Entity> duplicates = new ArrayList<>();
        for (Entity entity : level.getAllEntities()) {
            if (!isLiveProxyStand(entity, key)) {
                continue;
            }
            if (found == null) {
                found = (ArmorStand) entity;
            } else {
                duplicates.add(entity);
            }
        }
        discardDuplicates(duplicates);
        return found;
    }

    private static void cleanupDuplicates(MinecraftServer server, long now) {
        if (now - lastDuplicateScanMillis < DUPLICATE_SCAN_INTERVAL_MILLIS) {
            return;
        }
        cleanupInProgress = true;
        lastDuplicateScanMillis = now;
        try {
            Map<String, Entity> keptByStableTag = new HashMap<>();
            List<Entity> toRemove = new ArrayList<>();
            for (ServerLevel level : server.getAllLevels()) {
                for (Entity entity : level.getAllEntities()) {
                    if (!(entity instanceof ArmorStand) || !entity.getTags().contains(TAG_PROXY_MARKER)) {
                        continue;
                    }
                    String stableTag = stableKeyTag(entity);
                    if (stableTag == null) {
                        toRemove.add(entity);
                        continue;
                    }
                    Entity kept = keptByStableTag.putIfAbsent(stableTag, entity);
                    if (kept != null && kept != entity) {
                        toRemove.add(entity);
                    }
                }
            }
            discardDuplicates(toRemove);
        } finally {
            cleanupInProgress = false;
        }
    }

    private static void expireStale(MinecraftServer server, long now) {
        List<ProxyStandKey> expired = new ArrayList<>();
        for (Map.Entry<ProxyStandKey, Long> entry : LAST_SEEN_MILLIS.entrySet()) {
            if (now - entry.getValue() <= STALE_MILLIS) {
                continue;
            }
            expired.add(entry.getKey());
        }
        for (ProxyStandKey key : expired) {
            removeStand(server, key);
            MARKERS.remove(key);
            ACTIVE_STAND_IDS.remove(key);
            LAST_SEEN_MILLIS.remove(key);
            expiredRemovedSinceSummary++;
        }
    }

    private static void removeStand(MinecraftServer server, ProxyStandKey key) {
        UUID uuid = ACTIVE_STAND_IDS.get(key);
        ServerLevel level = server.getLevel(key.markerDimension());
        if (level == null) {
            return;
        }
        if (uuid != null) {
            Entity entity = level.getEntity(uuid);
            if (entity != null) {
                discardAll(List.of(entity));
                return;
            }
        }
        ArmorStand stand = findExistingStandByKey(level, key);
        if (stand != null) {
            discardAll(List.of(stand));
        }
    }

    private static void discardAll(List<Entity> entities) {
        for (Entity entity : entities) {
            if (entity != null && !entity.isRemoved()) {
                entity.discard();
                removedSinceSummary++;
            }
        }
    }

    private static void discardDuplicates(List<Entity> entities) {
        for (Entity entity : entities) {
            if (entity != null && !entity.isRemoved()) {
                entity.discard();
                duplicatesRemovedSinceSummary++;
            }
        }
    }

    private static void maybeLogSummary(long now) {
        if (now - lastSummaryMillis < 2_000L) {
            return;
        }
        lastSummaryMillis = now;
        String sampleKey = MARKERS.keySet().stream()
                .findFirst()
                .map(ProxyStandKey::stableHash)
                .orElse("-");
        Skyesight.LOGGER.info(
                "[Skyesight] PORTAL_PROXY_ARMOR_STAND_MARKERS: enabled=yes activeKeys={} standsSpawned={} standsReused={} standsTeleported={} duplicatesRemoved={} expiredRemoved={} sampleKey={}",
                MARKERS.size(),
                spawnedSinceSummary,
                reusedSinceSummary,
                teleportedSinceSummary,
                duplicatesRemovedSinceSummary,
                expiredRemovedSinceSummary,
                sampleKey
        );
        if (spawnedSinceSummary > Math.max(2, MARKERS.size()) && MARKERS.size() <= 2) {
            Skyesight.LOGGER.warn(
                    "[Skyesight] PORTAL_PROXY_ARMOR_STAND_MARKERS: reason=unstable-key-or-lost-uuid activeKeys={} standsSpawned={}",
                    MARKERS.size(),
                    spawnedSinceSummary
            );
        }
        spawnedSinceSummary = 0;
        reusedSinceSummary = 0;
        teleportedSinceSummary = 0;
        removedSinceSummary = 0;
        expiredRemovedSinceSummary = 0;
        duplicatesRemovedSinceSummary = 0;
    }

    private static ProxyStandKey stableKey(SkyesightProxyMarkerPayload payload) {
        return new ProxyStandKey(
                payload.queryDimension(),
                payload.realPlayerUuid(),
                payload.viewId(),
                payload.displayDimension(),
                payload.cameraDimension()
        );
    }

    private static String stableKeyTag(Entity entity) {
        for (String tag : entity.getTags()) {
            if (tag.startsWith(TAG_KEY_PREFIX)) {
                return tag;
            }
        }
        return null;
    }

    private static String sanitize(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        StringBuilder builder = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            builder.append(Character.isLetterOrDigit(c) ? c : '_');
        }
        return builder.toString();
    }

    private static String formatVec(Vec3 vec) {
        return String.format(Locale.ROOT, "%.2f,%.2f,%.2f", vec.x, vec.y, vec.z);
    }

    private record ProxyStandKey(
            ResourceKey<Level> markerDimension,
            UUID realPlayerUuid,
            ResourceLocation viewId,
            ResourceKey<Level> displayDimension,
            ResourceKey<Level> cameraDimension
    ) {
        String stableHash() {
            String raw = markerDimension.location()
                    + "|" + realPlayerUuid
                    + "|" + viewId
                    + "|" + displayDimension.location()
                    + "|" + cameraDimension.location();
            return Integer.toUnsignedString(raw.hashCode(), 36);
        }

        String stableTag() {
            return TAG_KEY_PREFIX + stableHash();
        }
    }

    private record ServerMarker(
            ProxyStandKey key,
            String markerName,
            Vec3 apparentPosition,
            String realPlayerName,
            String direction,
            boolean syntheticReverse,
            String variant,
            long updatedMillis
    ) {
    }
}
