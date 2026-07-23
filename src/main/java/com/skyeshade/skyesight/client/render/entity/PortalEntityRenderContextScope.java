package com.skyeshade.skyesight.client.render.entity;

import com.skyeshade.skyesight.Skyesight;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

public final class PortalEntityRenderContextScope implements AutoCloseable {
    private static final ThreadLocal<State> CURRENT = new ThreadLocal<>();
    private static final Set<String> LOOKUP_LOGGED = new HashSet<>();
    private static final Set<String> LEAK_LOGGED = new HashSet<>();

    private final State previous;

    private PortalEntityRenderContextScope(State state) {
        this.previous = CURRENT.get();
        CURRENT.set(state);
    }

    public static PortalEntityRenderContextScope enter(
            ResourceLocation viewId,
            ResourceKey<Level> targetDimension,
            String source
    ) {
        return new PortalEntityRenderContextScope(new State(viewId, targetDimension, source));
    }

    public static boolean active() {
        return CURRENT.get() != null;
    }

    public static void register(Entity entity, boolean passLocalPart, int parentEntityId) {
        State state = CURRENT.get();
        if (state == null || entity == null) {
            return;
        }
        state.entitiesById.put(entity.getId(), new Entry(entity, passLocalPart, parentEntityId));
        warnIfMainLevelContains(entity);
    }

    public static Entity lookup(int entityId, String source) {
        State state = CURRENT.get();
        if (state == null) {
            return null;
        }
        Entry entry = state.entitiesById.get(entityId);
        if (entry == null) {
            logLookup(source, entityId, "miss", state);
            return null;
        }
        logLookup(source, entityId, entry.passLocalPart() ? "part-hit" : "entity-hit", state);
        return entry.entity();
    }

    public static void recordContextCall(String source) {
        State state = CURRENT.get();
        if (state == null) {
            return;
        }
        logLookup(source, -1, "context-call", state);
    }

    public static String summary() {
        State state = CURRENT.get();
        if (state == null) {
            return "portalScope=inactive";
        }
        int parts = 0;
        for (Entry entry : state.entitiesById.values()) {
            if (entry.passLocalPart()) {
                parts++;
            }
        }
        return "portalScope=active view="
                + state.viewId()
                + " dim="
                + state.targetDimension().location()
                + " source="
                + state.source()
                + " entries="
                + state.entitiesById().size()
                + " parts="
                + parts;
    }

    @Override
    public void close() {
        if (this.previous == null) {
            CURRENT.remove();
        } else {
            CURRENT.set(this.previous);
        }
    }

    private static void logLookup(String source, int entityId, String result, State state) {
        String caller = caller();
        String key = source + ":" + caller + ":" + result;
        if (!LOOKUP_LOGGED.add(key) || LOOKUP_LOGGED.size() > 64) {
            return;
        }
        Skyesight.LOGGER.info(
                "[Skyesight] PORTAL_ENTITY_SCOPE_LOOKUP source={} result={} entityId={} view={} dim={} caller={}",
                source,
                result,
                entityId,
                state.viewId(),
                state.targetDimension().location(),
                caller
        );
    }

    private static String caller() {
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        for (StackTraceElement element : stack) {
            String className = element.getClassName();
            if (className.startsWith("java.")
                    || className.startsWith("com.skyeshade.skyesight.client.render.entity.PortalEntityRenderContextScope")
                    || className.contains("ClientLevelPortalEntityRenderContextMixin")) {
                continue;
            }
            return className + "#" + element.getMethodName();
        }
        return "unknown";
    }

    private static void warnIfMainLevelContains(Entity entity) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        for (Entity mainEntity : minecraft.level.entitiesForRendering()) {
            if (mainEntity != entity) {
                continue;
            }
            String key = entity.getType() + ":" + entity.getId();
            if (!LEAK_LOGGED.add(key)) {
                return;
            }
            Skyesight.LOGGER.warn(
                    "[Skyesight] PORTAL_ENTITY_SCOPE_LEAK_GUARD entity in main level during portal scope type={} id={}",
                    entity.getType(),
                    entity.getId()
            );
            return;
        }
    }

    private record State(
            ResourceLocation viewId,
            ResourceKey<Level> targetDimension,
            String source,
            Map<Integer, Entry> entitiesById
    ) {
        private State(ResourceLocation viewId, ResourceKey<Level> targetDimension, String source) {
            this(viewId, targetDimension, source, new LinkedHashMap<>());
        }
    }

    private record Entry(Entity entity, boolean passLocalPart, int parentEntityId) {}
}
