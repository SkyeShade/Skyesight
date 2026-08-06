package com.skyeshade.skyesight.client.world;

import com.mojang.authlib.GameProfile;
import com.skyeshade.skyesight.Skyesight;
import com.skyeshade.skyesight.entity.PortalMultipartEntityUtil;
import com.skyeshade.skyesight.entity.SkyesightEntityDimensionContext;
import com.skyeshade.skyesight.mixin.common.SynchedEntityDataAccessor;
import com.skyeshade.skyesight.network.SkyesightEntitySnapshotPayload;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class SkyesightVisualEntityStore {
    private static final int PORTAL_ENTITY_STALE_GRACE_TICKS = 60;
    private static final long STALE_GRACE_MILLIS = PORTAL_ENTITY_STALE_GRACE_TICKS * 50L;
    private static final int PORTAL_ENTITY_STORE_CAP = 128;
    private static final Set<String> WARNED_ENTITY_DATA_MISMATCHES = new HashSet<>();
    private static final Map<String, EntityDataSummary> ENTITY_DATA_SUMMARIES = new HashMap<>();
    private final ClientLevel level;
    private final Map<UUID, SkyesightVisualEntity> entities = new HashMap<>();
    private final Map<UUID, Long> lastSeenMillis = new HashMap<>();

    public SkyesightVisualEntityStore(ClientLevel level) {
        this.level = level;
    }
    public SkyesightVisualEntity get(UUID uuid) {
        return this.entities.get(uuid);
    }

    public SkyesightVisualEntity getByEntityId(int entityId) {
        for (SkyesightVisualEntity visualEntity : this.entities.values()) {
            if (visualEntity != null && visualEntity.entity() != null && visualEntity.entity().getId() == entityId) {
                return visualEntity;
            }
        }
        return null;
    }
    public void applySnapshot(SkyesightEntitySnapshotPayload payload) {
        Set<UUID> seen = new HashSet<>();
        long now = System.currentTimeMillis();

        for (SkyesightEntitySnapshotPayload.Entry entry : payload.entities()) {
            try {
                seen.add(entry.uuid());
                this.lastSeenMillis.put(entry.uuid(), now);

                SkyesightVisualEntity visualEntity = this.entities.get(entry.uuid());
                if (visualEntity != null
                        && PortalMultipartEntityUtil.shouldSkipStandaloneVisualEntity(visualEntity.entity())) {
                    PortalMultipartEntityUtil.warnSkippedStandalonePart(visualEntity.entity(), "visual_snapshot_store_existing");
                    this.entities.remove(entry.uuid());
                    this.lastSeenMillis.remove(entry.uuid());
                    continue;
                }

                if (visualEntity == null) {
                    Entity entity = createEntity(entry);

                    if (entity == null) {
                        continue;
                    }
                    if (PortalMultipartEntityUtil.shouldSkipStandaloneVisualEntity(entity)) {
                        PortalMultipartEntityUtil.warnSkippedStandalonePart(entity, "visual_snapshot_store");
                        continue;
                    }

                    ((SkyesightEntityDimensionContext) entity).skyesight$setExplicitDimension(payload.dimension());
                    applyEntityData(entity, entry.entityData());
                    applyEquipment(entity, entry.equipment());

                    visualEntity = new SkyesightVisualEntity(entity, entry);
                    this.entities.put(entry.uuid(), visualEntity);
                    continue;
                }

                ((SkyesightEntityDimensionContext) visualEntity.entity()).skyesight$setExplicitDimension(payload.dimension());
                applyEntityData(visualEntity.entity(), entry.entityData());
                applyEquipment(visualEntity.entity(), entry.equipment());
                visualEntity.acceptSnapshot(entry);
            } catch (RuntimeException exception) {
                warnSnapshotEntrySkipped(entry, exception);
            }
        }

        removeStale(now);
        enforceCap();
    }

    public Iterable<SkyesightVisualEntity> entities() {
        return this.entities.values();
    }

    public TickStats tickVisualEntities(String viewId, String cameraDimension) {
        int ticked = 0;
        int skipped = 0;
        String skippedReason = "-";

        for (SkyesightVisualEntity visualEntity : this.entities.values()) {
            Entity entity = visualEntity.entity();
            if (entity == null || entity.isRemoved()) {
                skipped++;
                skippedReason = "removed";
                continue;
            }

            try {
                visualEntity.clientTick();
                ticked++;
            } catch (RuntimeException exception) {
                skipped++;
                skippedReason = exception.getClass().getSimpleName() + ": " + exception.getMessage();
            }
        }

        return new TickStats(ticked, skipped, skippedReason);
    }

    public int size() {
        return this.entities.size();
    }

    public void clear() {
        this.entities.clear();
        this.lastSeenMillis.clear();
    }

    private int removeStale(long now) {
        int removed = 0;
        Iterator<Map.Entry<UUID, SkyesightVisualEntity>> iterator = this.entities.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, SkyesightVisualEntity> entry = iterator.next();
            long lastSeen = this.lastSeenMillis.getOrDefault(entry.getKey(), now);
            if (now - lastSeen <= STALE_GRACE_MILLIS) {
                continue;
            }
            iterator.remove();
            this.lastSeenMillis.remove(entry.getKey());
            removed++;
        }
        return removed;
    }

    private int enforceCap() {
        if (this.entities.size() <= PORTAL_ENTITY_STORE_CAP) {
            return 0;
        }
        List<Map.Entry<UUID, Long>> byAge = new ArrayList<>(this.lastSeenMillis.entrySet());
        byAge.sort(Map.Entry.comparingByValue());
        int removed = 0;
        for (Map.Entry<UUID, Long> entry : byAge) {
            if (this.entities.size() <= PORTAL_ENTITY_STORE_CAP) {
                break;
            }
            if (this.entities.remove(entry.getKey()) != null) {
                this.lastSeenMillis.remove(entry.getKey());
                removed++;
            }
        }
        return removed;
    }

    private Entity createEntity(SkyesightEntitySnapshotPayload.Entry entry) {
        if (entry.type() == EntityType.PLAYER) {
            String name = entry.profileName();

            if (name == null || name.isBlank()) {
                name = "SkyesightPlayer";
            }

            return new RemotePlayer(
                    this.level,
                    new GameProfile(entry.uuid(), name)
            );
        }

        Entity entity = entry.type().create(this.level);

        if (entity == null) {
            Skyesight.LOGGER.warn(
                    "[Skyesight] Failed to create visual entity type={} uuid={}",
                    BuiltInRegistries.ENTITY_TYPE.getKey(entry.type()),
                    entry.uuid()
            );
            return null;
        }

        entity.setUUID(entry.uuid());
        return entity;
    }

    private static void applyEntityData(
            Entity entity,
            List<SynchedEntityData.DataValue<?>> values
    ) {
        if (values == null || values.isEmpty()) {
            return;
        }

        SynchedEntityData entityData = entity.getEntityData();
        SynchedEntityData.DataItem<?>[] items =
                ((SynchedEntityDataAccessor) entityData).skyesight$getItemsById();

        for (SynchedEntityData.DataValue<?> value : values) {
            if (value == null) {
                continue;
            }
            int id = value.id();
            if (id < 0 || id >= items.length || items[id] == null) {
                warnEntityDataSkipped(entity, id, "missing_id");
                continue;
            }

            SynchedEntityData.DataItem<?> item = items[id];
            if (item.getAccessor().serializer() != value.serializer()) {
                warnEntityDataSkipped(entity, id, mismatchReason(item, value));
                continue;
            }

            try {
                entityData.assignValues(List.of(value));
                recordEntityDataApplied(entity, id);
            } catch (RuntimeException exception) {
                warnEntityDataSkipped(entity, id, exception.getClass().getSimpleName());
            }
        }
    }

    public static String entityDataDebugSummary() {
        if (ENTITY_DATA_SUMMARIES.isEmpty()) {
            return "entityData={}";
        }
        StringBuilder builder = new StringBuilder("entityData={");
        int written = 0;
        for (Map.Entry<String, EntityDataSummary> entry : ENTITY_DATA_SUMMARIES.entrySet()) {
            if (written++ > 0) {
                builder.append(" | ");
            }
            if (written > 6) {
                builder.append("+").append(ENTITY_DATA_SUMMARIES.size() - written + 1);
                break;
            }
            builder.append(entry.getKey()).append(":").append(entry.getValue());
        }
        return builder.append("}").toString();
    }

    private static void warnEntityDataSkipped(Entity entity, int id, String reason) {
        recordEntityDataSkipped(entity, id, reason);
        String key = entity.getType() + ":" + id + ":" + reason;
        if (!WARNED_ENTITY_DATA_MISMATCHES.add(key)) {
            return;
        }
        Skyesight.LOGGER.warn(
                "[Skyesight] Skipped incompatible visual entity data field type={} id={} reason={}",
                entity.getType(),
                id,
                reason
        );
    }

    private static String mismatchReason(
            SynchedEntityData.DataItem<?> item,
            SynchedEntityData.DataValue<?> value
    ) {
        Object oldValue = item.getValue();
        Object newValue = value.value();
        return "serializer_mismatch old="
                + typeName(oldValue)
                + " new="
                + typeName(newValue)
                + " oldSerializer="
                + item.getAccessor().serializer().getClass().getName()
                + " newSerializer="
                + value.serializer().getClass().getName();
    }

    private static String typeName(Object value) {
        return value == null ? "null" : value.getClass().getName();
    }

    private static void recordEntityDataApplied(Entity entity, int id) {
        EntityDataSummary summary = ENTITY_DATA_SUMMARIES.computeIfAbsent(
                entity.getType().toString(),
                ignored -> new EntityDataSummary()
        );
        summary.applied++;
        summary.appliedIds.add(id);
    }

    private static void recordEntityDataSkipped(Entity entity, int id, String reason) {
        EntityDataSummary summary = ENTITY_DATA_SUMMARIES.computeIfAbsent(
                entity.getType().toString(),
                ignored -> new EntityDataSummary()
        );
        summary.skipped++;
        summary.skippedDetails.add(id + ":" + reason);
    }

    private static final class EntityDataSummary {
        private int applied;
        private int skipped;
        private final Set<Integer> appliedIds = new LinkedHashSet<>();
        private final Set<String> skippedDetails = new LinkedHashSet<>();

        @Override
        public String toString() {
            return "applied=" + applied
                    + " appliedIds=" + sample(appliedIds)
                    + " skipped=" + skipped
                    + " skippedIds=" + sample(skippedDetails);
        }

        private static String sample(Collection<?> values) {
            if (values.isEmpty()) {
                return "[]";
            }
            StringBuilder builder = new StringBuilder("[");
            int written = 0;
            for (Object value : values) {
                if (written++ > 0) {
                    builder.append(",");
                }
                if (written > 8) {
                    builder.append("+").append(values.size() - written + 1);
                    break;
                }
                builder.append(value);
            }
            return builder.append("]").toString();
        }
    }

    private static void warnSnapshotEntrySkipped(
            SkyesightEntitySnapshotPayload.Entry entry,
            RuntimeException exception
    ) {
        String key = entry.type() + ":entry:" + exception.getClass().getSimpleName();
        if (!WARNED_ENTITY_DATA_MISMATCHES.add(key)) {
            return;
        }
        Skyesight.LOGGER.warn(
                "[Skyesight] Skipped visual entity snapshot entry type={} uuid={} reason={}: {}",
                entry.type(),
                entry.uuid(),
                exception.getClass().getSimpleName(),
                exception.getMessage()
        );
    }
    private static void applyEquipment(
            Entity entity,
            List<SkyesightEntitySnapshotPayload.EquipmentEntry> equipment
    ) {
        if (!(entity instanceof LivingEntity livingEntity)) {
            return;
        }

        for (SkyesightEntitySnapshotPayload.EquipmentEntry entry : equipment) {
            ItemStack stack = entry.stack();

            livingEntity.setItemSlot(
                    entry.slot(),
                    stack.copy()
            );
        }
    }

    public record TickStats(int ticked, int skipped, String skippedReason) {
    }
}
