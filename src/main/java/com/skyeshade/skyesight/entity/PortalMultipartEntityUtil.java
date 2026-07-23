package com.skyeshade.skyesight.entity;

import com.skyeshade.skyesight.Skyesight;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.common.extensions.IEntityExtension;
import net.neoforged.neoforge.entity.PartEntity;

import java.util.HashSet;
import java.util.Set;

public final class PortalMultipartEntityUtil {
    private static final Set<String> WARNED_PART_TYPES = new HashSet<>();

    private PortalMultipartEntityUtil() {}

    public static boolean isStandalonePartEntity(Entity entity) {
        return entity instanceof PartEntity<?>;
    }

    public static Entity parentOfPart(Entity entity) {
        if (entity instanceof PartEntity<?> partEntity) {
            return partEntity.getParent();
        }
        return null;
    }

    public static boolean shouldSkipStandaloneVisualEntity(Entity entity) {
        return isStandalonePartEntity(entity);
    }

    public static boolean isMultipartParent(Entity entity) {
        return entity instanceof IEntityExtension extension && extension.isMultipartEntity();
    }

    public static PartEntity<?>[] parts(Entity entity) {
        if (!(entity instanceof IEntityExtension extension) || !extension.isMultipartEntity()) {
            return null;
        }
        return extension.getParts();
    }

    public static int partCount(Entity entity) {
        PartEntity<?>[] parts = parts(entity);
        return parts == null ? 0 : parts.length;
    }

    public static int partsWithParentBackreference(Entity entity) {
        PartEntity<?>[] parts = parts(entity);
        if (parts == null) {
            return 0;
        }
        int count = 0;
        for (PartEntity<?> part : parts) {
            if (part != null && part.getParent() == entity) {
                count++;
            }
        }
        return count;
    }

    public static void refreshMultipartParent(Entity parent, String source) {
        // Generic multipart support does not call mod-specific update methods.
        // Parts are read from the entity's public multipart API and rendered pass-locally.
    }

    public static void warnSkippedStandalonePart(Entity entity, String source) {
        if (entity == null) {
            return;
        }
        String key = source + ":" + entity.getType();
        if (!WARNED_PART_TYPES.add(key)) {
            return;
        }
        Entity parent = parentOfPart(entity);
        Skyesight.LOGGER.warn(
                "[Skyesight] Skipping standalone multipart visual entity source={} type={} id={} parentType={} parentId={}; parent-only rendering is used",
                source,
                entity.getType(),
                entity.getId(),
                parent == null ? "-" : parent.getType().toString(),
                parent == null ? -1 : parent.getId()
        );
    }
}
