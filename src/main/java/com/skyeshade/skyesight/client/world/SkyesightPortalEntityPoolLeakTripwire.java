package com.skyeshade.skyesight.client.world;

import com.skyeshade.skyesight.Skyesight;
import com.skyeshade.skyesight.SkyesightNativeVisualEntityRoutingDebug;
import com.skyeshade.skyesight.SkyesightPortalEntityPoolConfig;
import com.skyeshade.skyesight.api.RegisteredPortalView;
import com.skyeshade.skyesight.api.SkyesightPortalApi;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

import java.util.HashSet;
import java.util.Set;

public final class SkyesightPortalEntityPoolLeakTripwire {
    private static final Set<String> WARNED = new HashSet<>();
    private static int rawAddProofLogs;

    private SkyesightPortalEntityPoolLeakTripwire() {}

    public static Snapshot beforeWrappedPacket(
            ResourceLocation viewId,
            ResourceKey<Level> targetDimension,
            String packetKind,
            int entityId
    ) {
        return Snapshot.capture(viewId, targetDimension, packetKind, entityId);
    }

    public static void afterWrappedPacket(Snapshot before) {
        if (before == null || !SkyesightPortalEntityPoolConfig.portalEntityPoolPopulationEnabled()) {
            return;
        }
        Snapshot after = Snapshot.capture(
                before.viewId,
                before.targetDimension,
                before.packetKind,
                before.entityId
        );
        if (after.mainLevelContainsTarget && !before.mainLevelContainsTarget) {
            recordLeak(
                    "LEAK_MAIN_LEVEL_ENTITY_ID",
                    before.viewId,
                    before.targetDimension,
                    before.packetKind,
                    before.entityId,
                    after.targetEntityType,
                    after.mainLevelClass,
                    after.targetLevelClass,
                    after.targetLevelIsMain
            );
        }
    }

    public static void pooledEntityStored(
            ResourceLocation viewId,
            ResourceKey<Level> targetDimension,
            String packetKind,
            int entityId,
            Entity entity
    ) {
        if (!SkyesightPortalEntityPoolConfig.portalEntityPoolPopulationEnabled() || entity == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        for (Entity mainLevelEntity : minecraft.level.entitiesForRendering()) {
            if (mainLevelEntity != entity) {
                continue;
            }
            recordLeak(
                    "LEAK_MAIN_LEVEL_ENTITY_IDENTITY",
                    viewId,
                    targetDimension,
                    packetKind,
                    entityId,
                    entity.getType().toString(),
                    minecraft.level.getClass().getName(),
                    entity.level().getClass().getName(),
                    entity.level() == minecraft.level
            );
            return;
        }
    }

    public static void rawVanillaPacketReachedMainClient(String packetClass, int entityId) {
        if (!SkyesightPortalEntityPoolConfig.portalEntityPoolPopulationEnabled()
                || !hasActiveCrossDimView()
                || !SkyesightPortalEntityPool.containsEntityId(entityId)) {
            return;
        }
        recordLeak(
                "RAW_VANILLA_ENTITY_PACKET_REACHED_MAIN_CLIENT",
                null,
                null,
                packetClass,
                entityId,
                "-",
                Minecraft.getInstance().level == null ? "-" : Minecraft.getInstance().level.getClass().getName(),
                "-",
                true
        );
    }

    public static void rawVanillaAddPacketHandled(
            String packetClass,
            int entityId,
            String entityType,
            double x,
            double y,
            double z
    ) {
        if (!SkyesightPortalEntityPoolConfig.ENABLE_PORTAL_ENTITY_POOL_POPULATION
                || rawAddProofLogs >= 10
                || !hasActiveCrossDimView()) {
            return;
        }
        rawAddProofLogs++;
        Minecraft minecraft = Minecraft.getInstance();
        Skyesight.LOGGER.warn(
                "[Skyesight] RAW_VANILLA_ADD_ENTITY_PACKET_HANDLED: packet={} entityId={} entityType={} clientDim={} pos=({}, {}, {}) proofIndex={} stack={}",
                packetClass,
                entityId,
                entityType == null || entityType.isBlank() ? "-" : entityType,
                minecraft.level == null ? "-" : minecraft.level.dimension().location(),
                x,
                y,
                z,
                rawAddProofLogs,
                stackTraceTop()
        );
    }

    private static boolean hasActiveCrossDimView() {
        for (RegisteredPortalView view : SkyesightPortalApi.getAllPortals()) {
            if (view != null && view.active() && view.isCrossDimension()) {
                return true;
            }
        }
        return false;
    }

    private static void recordLeak(
            String reason,
            ResourceLocation viewId,
            ResourceKey<Level> targetDimension,
            String packetKind,
            int entityId,
            String entityType,
            String mainLevelClass,
            String targetLevelClass,
            boolean targetLevelIsMain
    ) {
        String key = reason + ":" + entityId + ":" + packetKind;
        if (!WARNED.add(key)) {
            return;
        }
        SkyesightPortalEntityPoolConfig.disablePopulationForSession();
        SkyesightNativeVisualEntityRoutingDebug.clientDrop(viewId, reason);
        Skyesight.LOGGER.warn(
                "[Skyesight] {}: packetKind={} viewId={} targetDim={} entityId={} entityType={} mainLevelClass={} targetLevelClass={} targetLevelIsMain={} stack={}",
                reason,
                packetKind == null ? "-" : packetKind,
                viewId == null ? "-" : viewId,
                targetDimension == null ? "-" : targetDimension.location(),
                entityId,
                entityType == null || entityType.isBlank() ? "-" : entityType,
                mainLevelClass == null || mainLevelClass.isBlank() ? "-" : mainLevelClass,
                targetLevelClass == null || targetLevelClass.isBlank() ? "-" : targetLevelClass,
                targetLevelIsMain,
                stackTraceTop()
        );
    }

    private static String stackTraceTop() {
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        StringBuilder builder = new StringBuilder();
        int written = 0;
        for (StackTraceElement element : stackTrace) {
            String className = element.getClassName();
            if (className.equals(Thread.class.getName())
                    || className.equals(SkyesightPortalEntityPoolLeakTripwire.class.getName())) {
                continue;
            }
            if (written++ > 0) {
                builder.append(" <- ");
            }
            builder.append(element);
            if (written >= 8) {
                break;
            }
        }
        return builder.toString();
    }

    public record Snapshot(
            ResourceLocation viewId,
            ResourceKey<Level> targetDimension,
            String packetKind,
            int entityId,
            int mainLevelEntityCount,
            boolean mainLevelContainsTarget,
            String targetEntityType,
            String mainLevelClass,
            String targetLevelClass,
            boolean targetLevelIsMain
    ) {
        private static Snapshot capture(
                ResourceLocation viewId,
                ResourceKey<Level> targetDimension,
                String packetKind,
                int entityId
        ) {
            Minecraft minecraft = Minecraft.getInstance();
            int count = 0;
            boolean contains = false;
            Entity targetEntity = null;
            String mainLevelClass = "-";
            if (minecraft.level != null) {
                mainLevelClass = minecraft.level.getClass().getName();
                for (Entity entity : minecraft.level.entitiesForRendering()) {
                    count++;
                    if (entity != null && entity.getId() == entityId) {
                        contains = true;
                        targetEntity = entity;
                    }
                }
            }
            return new Snapshot(
                    viewId,
                    targetDimension,
                    packetKind,
                    entityId,
                    count,
                    contains,
                    targetEntity == null ? "-" : targetEntity.getType().toString(),
                    mainLevelClass,
                    targetEntity == null ? "-" : targetEntity.level().getClass().getName(),
                    targetEntity != null && targetEntity.level() == minecraft.level
            );
        }
    }
}
