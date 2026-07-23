package com.skyeshade.skyesight.client.world;

import com.skyeshade.skyesight.api.RegisteredPortalView;
import com.skyeshade.skyesight.api.SkyesightPortalApi;
import com.skyeshade.skyesight.client.render.entity.PortalMultipartPartEligibility;
import com.skyeshade.skyesight.entity.PortalMultipartEntityUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.entity.PartEntity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class SkyesightMultipartEntityDebug {
    private static final Set<String> RENDER_LOGGED = new HashSet<>();
    private static volatile int traceFramesRequested;

    private SkyesightMultipartEntityDebug() {}

    public static String status() {
        traceFramesRequested = 2;
        Minecraft minecraft = Minecraft.getInstance();
        List<String> rows = new ArrayList<>();

        if (minecraft.level != null) {
            for (Entity entity : minecraft.level.entitiesForRendering()) {
                addIfMultipart(rows, "main_level_same_dim", null, entity, minecraft);
                if (rows.size() >= 8) {
                    break;
                }
            }
        }

        SkyesightVisualWorldManager.forEachWorld((viewId, world) -> {
            if (rows.size() >= 12 || world == null || world.isClosed()) {
                return;
            }
            for (SkyesightVisualEntity visualEntity : world.entityStore().entities()) {
                if (visualEntity == null) {
                    continue;
                }
                addIfMultipart(rows, "visual_world_snapshot", viewId, visualEntity.entity(), minecraft);
                if (rows.size() >= 12) {
                    break;
                }
            }
        });

        for (RegisteredPortalView view : SkyesightPortalApi.getAllPortals()) {
            if (rows.size() >= 16 || view == null) {
                continue;
            }
            for (Entity entity : SkyesightPortalEntityPool.entities(view.id(), view.target().dimension())) {
                addIfMultipart(rows, "portal_entity_pool", view.id(), entity, minecraft);
                if (rows.size() >= 16) {
                    break;
                }
            }
        }

        if (rows.isEmpty()) {
            return "multipartParents=0 " + SkyesightVisualEntityStore.entityDataDebugSummary();
        }
        return "multipartParents=" + rows.size()
                + " "
                + String.join(" | ", rows)
                + " "
                + SkyesightVisualEntityStore.entityDataDebugSummary();
    }

    private static void addIfMultipart(
            List<String> rows,
            String source,
            ResourceLocation viewId,
            Entity entity,
            Minecraft minecraft
    ) {
        if (entity == null || PortalMultipartEntityUtil.shouldSkipStandaloneVisualEntity(entity)
                || !PortalMultipartEntityUtil.isMultipartParent(entity)) {
            return;
        }
        PartEntity<?>[] parts = PortalMultipartEntityUtil.parts(entity);
        int partCount = parts == null ? 0 : parts.length;
        int backrefs = PortalMultipartEntityUtil.partsWithParentBackreference(entity);
        int partsInMainLevel = countPartsInMainLevel(parts, minecraft);
        String renderer = "-";
        try {
            renderer = minecraft.getEntityRenderDispatcher().getRenderer(entity).getClass().getName();
        } catch (RuntimeException ignored) {
            renderer = "unavailable";
        }
        rows.add(
                "source=" + source
                        + " view=" + (viewId == null ? "-" : viewId)
                        + " type=" + entity.getType()
                        + " class=" + entity.getClass().getName()
                        + " renderer=" + renderer
                        + " multipartParent=true"
                        + " parts=" + partCount
                        + " parentBackrefs=" + backrefs
                        + " partsInMainLevel=" + partsInMainLevel
                        + " tick=" + entity.tickCount
                        + " pos=" + compactPos(entity)
                        + " oldPos=" + compactOldPos(entity)
                        + " rot=" + compactRot(entity)
                        + " partSample=[" + partSample(entity, parts) + "]"
                        + " groupedParts=[" + groupedPartSample(entity, parts) + "]"
                        + " levelSourceParts=[" + levelSourcePartSummary(entity, minecraft) + "]"
                        + " eligibleParts=[" + eligiblePartSummary(entity, parts) + "]"
        );
    }

    public static void logRenderTimeSample(String source, Entity entity, float partialTick) {
        if (traceFramesRequested <= 0) {
            return;
        }
        if (entity == null || !PortalMultipartEntityUtil.isMultipartParent(entity)) {
            return;
        }
        String key = source + ":" + entity.getType();
        if (!RENDER_LOGGED.add(key)) {
            return;
        }
        PartEntity<?>[] parts = PortalMultipartEntityUtil.parts(entity);
        com.skyeshade.skyesight.Skyesight.LOGGER.info(
                "[Skyesight] MULTIPART_RENDER_SAMPLE: source={} partialTick={} type={} class={} tick={} pos={} oldPos={} rot={} parts={} sample=[{}]",
                source,
                partialTick,
                entity.getType(),
                entity.getClass().getName(),
                entity.tickCount,
                compactPos(entity),
                compactOldPos(entity),
                compactRot(entity),
                parts == null ? 0 : parts.length,
                partSample(entity, parts)
                        + " grouped=" + groupedPartSample(entity, parts)
        );
    }

    public static boolean wantsMultipartRenderTrace(Entity entity) {
        if (traceFramesRequested <= 0 || entity == null) {
            return false;
        }
        return PortalMultipartEntityUtil.isMultipartParent(entity)
                || PortalMultipartEntityUtil.isStandalonePartEntity(entity);
    }

    public static boolean diagnosticsArmed() {
        return traceFramesRequested > 0;
    }

    public static void finishMultipartRenderTraceFrame(long frameId, String viewId, List<String> entries) {
        if (entries == null || entries.isEmpty() || traceFramesRequested <= 0) {
            return;
        }
        traceFramesRequested--;
        Map<String, Integer> rendererCounts = new LinkedHashMap<>();
        Map<String, Integer> signatureCounts = new LinkedHashMap<>();
        int parents = 0;
        int parts = 0;
        for (String entry : entries) {
            if (entry.contains("standalonePart=true")) {
                parts++;
            } else {
                parents++;
            }
            String renderer = valueAfter(entry, "renderer=");
            rendererCounts.merge(renderer, 1, Integer::sum);
            String signature = valueAfter(entry, "signature=");
            signatureCounts.merge(signature, 1, Integer::sum);
        }
        List<String> duplicateSignatures = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : signatureCounts.entrySet()) {
            if (entry.getValue() > 1) {
                duplicateSignatures.add(entry.getKey() + "x" + entry.getValue());
            }
        }
        com.skyeshade.skyesight.Skyesight.LOGGER.info(
                "[Skyesight] MULTIPART_RENDER_TRACE frame={} view={} parentsRendered={} partsRendered={} rendererCounts={} duplicateSignatures={} entries=[{}]",
                frameId,
                viewId,
                parents,
                parts,
                rendererCounts,
                duplicateSignatures,
                String.join(" | ", entries)
        );
    }

    private static String valueAfter(String text, String key) {
        int start = text.indexOf(key);
        if (start < 0) {
            return "-";
        }
        start += key.length();
        int end = text.indexOf(' ', start);
        return end < 0 ? text.substring(start) : text.substring(start, end);
    }

    private static int countPartsInMainLevel(PartEntity<?>[] parts, Minecraft minecraft) {
        if (parts == null || minecraft.level == null) {
            return 0;
        }
        int count = 0;
        for (PartEntity<?> part : parts) {
            if (part == null) {
                continue;
            }
            for (Entity entity : minecraft.level.entitiesForRendering()) {
                if (entity == part) {
                    count++;
                    break;
                }
            }
        }
        return count;
    }

    private static String compactPos(Entity entity) {
        return String.format("%.2f,%.2f,%.2f", entity.getX(), entity.getY(), entity.getZ());
    }

    private static String compactOldPos(Entity entity) {
        return String.format(
                "%.2f,%.2f,%.2f/%.2f,%.2f,%.2f",
                entity.xo,
                entity.yo,
                entity.zo,
                entity.xOld,
                entity.yOld,
                entity.zOld
        );
    }

    private static String compactRot(Entity entity) {
        String base = String.format(
                "y=%.1f/%.1f x=%.1f/%.1f",
                entity.getYRot(),
                entity.yRotO,
                entity.getXRot(),
                entity.xRotO
        );
        if (entity instanceof LivingEntity livingEntity) {
            return base + String.format(
                    " body=%.1f/%.1f head=%.1f/%.1f",
                    livingEntity.yBodyRot,
                    livingEntity.yBodyRotO,
                    livingEntity.yHeadRot,
                    livingEntity.yHeadRotO
            );
        }
        return base;
    }

    private static String partSample(Entity parent, PartEntity<?>[] parts) {
        if (parts == null || parts.length == 0) {
            return "-";
        }
        StringBuilder builder = new StringBuilder();
        int limit = Math.min(5, parts.length);
        boolean allAtParent = true;
        boolean allAtOrigin = true;
        for (int i = 0; i < limit; i++) {
            PartEntity<?> part = parts[i];
            if (part == null) {
                continue;
            }
            double distance = part.position().distanceTo(parent.position());
            if (distance > 0.01D) {
                allAtParent = false;
            }
            if (part.position().length() > 0.01D) {
                allAtOrigin = false;
            }
            if (!builder.isEmpty()) {
                builder.append("; ");
            }
            AABB box = part.getBoundingBox();
            builder.append(i)
                    .append(":")
                    .append(part.getClass().getSimpleName())
                    .append("#")
                    .append(part.getId())
                    .append(" pos=")
                    .append(compactPos(part))
                    .append(" dist=")
                    .append(String.format("%.2f", distance))
                    .append(" box=")
                    .append(String.format("%.2fx%.2fx%.2f", box.getXsize(), box.getYsize(), box.getZsize()))
                    .append(" parentRef=")
                    .append(part.getParent() == parent);
        }
        builder.append(" allAtParent=").append(allAtParent)
                .append(" allAtOrigin=").append(allAtOrigin);
        return builder.toString();
    }

    private static String groupedPartSample(Entity parent, PartEntity<?>[] parts) {
        if (parts == null || parts.length == 0) {
            return "-";
        }
        Map<String, GroupSummary> groups = new LinkedHashMap<>();
        for (PartEntity<?> part : parts) {
            if (part == null) {
                continue;
            }
            String name = part.getClass().getSimpleName();
            GroupSummary summary = groups.computeIfAbsent(name, ignored -> new GroupSummary());
            summary.count++;
            double distance = part.position().distanceTo(parent.position());
            if (isNearOrigin(part)) {
                summary.nearOrigin++;
            }
            if (distance > 32.0D) {
                summary.farFromParent++;
            }
            if (part.getY() < parent.getY() - 16.0D) {
                summary.belowParent++;
            }
            if (summary.samples.size() < 3) {
                summary.samples.add(
                        compactPos(part)
                                + "/d="
                                + String.format("%.2f", distance)
                );
            }
        }
        StringBuilder builder = new StringBuilder();
        int written = 0;
        for (Map.Entry<String, GroupSummary> entry : groups.entrySet()) {
            if (written++ > 0) {
                builder.append("; ");
            }
            GroupSummary summary = entry.getValue();
            builder.append(entry.getKey())
                    .append(" count=").append(summary.count)
                    .append(" samples=").append(summary.samples)
                    .append(" nearOrigin=").append(summary.nearOrigin)
                    .append(" far=").append(summary.farFromParent)
                    .append(" below=").append(summary.belowParent);
        }
        int suspiciousParts = groups.values().stream().mapToInt(GroupSummary::suspiciousCount).sum();
        builder.append(" suspiciousParts=").append(suspiciousParts);
        return builder.toString();
    }

    private static String levelSourcePartSummary(Entity parent, Minecraft minecraft) {
        if (parent == null || minecraft.level == null) {
            return "-";
        }

        Map<String, GroupSummary> groups = new LinkedHashMap<>();
        for (Entity entity : minecraft.level.entitiesForRendering()) {
            if (!(entity instanceof PartEntity<?> part) || part.getParent() != parent) {
                continue;
            }
            String name = entity.getClass().getSimpleName();
            GroupSummary summary = groups.computeIfAbsent(name, ignored -> new GroupSummary());
            summary.count++;
            if (summary.samples.size() < 3) {
                summary.samples.add(compactPartForCompare(parent, part));
            }
        }
        return groupSummaryText(groups);
    }

    private static String eligiblePartSummary(Entity parent, PartEntity<?>[] parts) {
        if (parts == null || parts.length == 0) {
            return "-";
        }

        Map<String, GroupSummary> renderGroups = new LinkedHashMap<>();
        Map<String, Integer> skippedReasons = new LinkedHashMap<>();
        for (PartEntity<?> part : parts) {
            if (part == null) {
                skippedReasons.merge("null", 1, Integer::sum);
                continue;
            }
            PortalMultipartPartEligibility.Result eligibility =
                    PortalMultipartPartEligibility.evaluate(parent, part);
            if (!eligibility.render()) {
                skippedReasons.merge(part.getClass().getSimpleName() + ":" + eligibility.reason(), 1, Integer::sum);
                continue;
            }
            String name = part.getClass().getSimpleName();
            GroupSummary summary = renderGroups.computeIfAbsent(name, ignored -> new GroupSummary());
            summary.count++;
            if (summary.samples.size() < 3) {
                summary.samples.add(compactPartForCompare(parent, part) + " renderer=" + shortClassName(eligibility.rendererClass()));
            }
        }

        return "render={" + groupSummaryText(renderGroups) + "} skipped=" + skippedReasons;
    }

    private static String groupSummaryText(Map<String, GroupSummary> groups) {
        if (groups.isEmpty()) {
            return "-";
        }
        StringBuilder builder = new StringBuilder();
        int written = 0;
        for (Map.Entry<String, GroupSummary> entry : groups.entrySet()) {
            if (written++ > 0) {
                builder.append("; ");
            }
            builder.append(entry.getKey())
                    .append(" count=")
                    .append(entry.getValue().count)
                    .append(" samples=")
                    .append(entry.getValue().samples);
        }
        return builder.toString();
    }

    private static String compactPartForCompare(Entity parent, PartEntity<?> part) {
        return "#"
                + part.getId()
                + " pos="
                + compactPos(part)
                + " d="
                + String.format("%.2f", part.position().distanceTo(parent.position()))
                + " rot="
                + String.format("%.1f/%.1f %.1f/%.1f", part.getYRot(), part.yRotO, part.getXRot(), part.xRotO);
    }

    private static String shortClassName(String className) {
        if (className == null || className.equals("-")) {
            return "-";
        }
        int index = className.lastIndexOf('.');
        return index < 0 ? className : className.substring(index + 1);
    }

    private static boolean isNearOrigin(Entity entity) {
        return Math.abs(entity.getX()) < 0.5D
                && Math.abs(entity.getY()) < 0.5D
                && Math.abs(entity.getZ()) < 0.5D;
    }

    private static final class GroupSummary {
        private int count;
        private int nearOrigin;
        private int farFromParent;
        private int belowParent;
        private final List<String> samples = new ArrayList<>();

        private int suspiciousCount() {
            return this.nearOrigin + this.farFromParent + this.belowParent;
        }
    }
}
