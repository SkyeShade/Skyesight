package com.skyeshade.skyesight.api;

import com.skyeshade.skyesight.Skyesight;

import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

public final class SkyesightPortalRegistry {
    private static final Map<ResourceLocation, RegisteredPortalView> PORTALS = new LinkedHashMap<>();
    private static final List<PortalChangeListener> CHANGE_LISTENERS = new CopyOnWriteArrayList<>();
    private static long nextGeneration = 1L;

    private SkyesightPortalRegistry() {
    }

    public static synchronized PortalRegistrationResult register(
            ResourceLocation id,
            PortalEndpoint source,
            PortalEndpoint target,
            PortalRenderSettings renderSettings,
            boolean renderEnabled,
            String pairedId,
            String groupId,
            String sourceTag,
            boolean renderBackface,
            boolean replace
    ) {
        validate(id, source, target);
        RegisteredPortalView oldView = PORTALS.get(id);
        if (!replace && oldView != null) {
            throw new IllegalArgumentException("Portal id already registered: " + id);
        }
        if (renderSettings == null) {
            renderSettings = PortalRenderSettings.defaultsWithInternalStencilRef(nextStencilRef());
        } else if (renderSettings.stencilRef() <= 0 || stencilRefInUseByOther(id, renderSettings.stencilRef())) {
            renderSettings = renderSettings.withInternalStencilRef(nextStencilRef());
        }
        RegisteredPortalView newView = new RegisteredPortalView(
                id,
                source,
                target,
                renderSettings,
                renderEnabled,
                pairedId,
                groupId,
                sourceTag,
                renderBackface,
                nextGeneration++,
                false
        );
        PortalReplacement replacement = oldView == null
                ? new PortalReplacement("register", PortalCachePolicy.SOFT_REPLACE)
                : classifyReplacement(oldView, newView);
        PORTALS.put(id, newView);
        if (oldView != null) {
            notifyInvalidated(oldView, newView, replacement.reason(), replacement.cachePolicy());
        }
        return PortalRegistrationResult.success(List.of(id), "registered " + id);
    }

    public static synchronized boolean remove(ResourceLocation id) {
        if (id == null) {
            return false;
        }
        RegisteredPortalView removed = PORTALS.remove(id);
        if (removed != null) {
            notifyInvalidated(removed, null, "remove", PortalCachePolicy.REMOVE);
            return true;
        }
        return false;
    }

    public static synchronized boolean disableRetainingCache(ResourceLocation id) {
        if (id == null) {
            return false;
        }
        RegisteredPortalView oldView = PORTALS.get(id);
        if (oldView == null) {
            return false;
        }
        if (oldView.cacheRetainedDisabled()) {
            return true;
        }
        RegisteredPortalView disabledView = new RegisteredPortalView(
                oldView.id(),
                oldView.source(),
                oldView.target(),
                oldView.renderSettings(),
                false,
                oldView.pairedId(),
                oldView.groupId(),
                oldView.sourceTag(),
                oldView.renderBackface(),
                nextGeneration++,
                true
        );
        PORTALS.put(id, disabledView);
        notifyInvalidated(oldView, disabledView, "disable_retain_cache", PortalCachePolicy.DISABLE_RETAIN_CACHE);
        return true;
    }

    public static synchronized int removeBySourceTag(String sourceTag) {
        if (sourceTag == null || sourceTag.isBlank()) {
            return 0;
        }
        List<RegisteredPortalView> removed = new ArrayList<>();
        PORTALS.entrySet().removeIf(entry -> {
            if (sourceTag.equals(entry.getValue().sourceTag())) {
                removed.add(entry.getValue());
                return true;
            }
            return false;
        });
        for (RegisteredPortalView view : removed) {
            notifyInvalidated(view, null, "clear", PortalCachePolicy.CLEAR);
        }
        return removed.size();
    }

    public static synchronized RegisteredPortalView get(ResourceLocation id) {
        return PORTALS.get(id);
    }

    public static synchronized boolean contains(ResourceLocation id) {
        return PORTALS.containsKey(id);
    }

    public static synchronized List<RegisteredPortalView> all() {
        return List.copyOf(PORTALS.values());
    }

    public static synchronized Collection<ResourceLocation> ids() {
        return List.copyOf(PORTALS.keySet());
    }

    public static void addChangeListener(PortalChangeListener listener) {
        if (listener != null) {
            CHANGE_LISTENERS.add(listener);
        }
    }

    static synchronized PortalRegistrationResult registerPair(
            ResourceLocation idA,
            PortalEndpoint a,
            ResourceLocation idB,
            PortalEndpoint b,
            PortalRenderSettings settingsA,
            PortalRenderSettings settingsB,
            boolean renderA,
            boolean renderB,
            String groupId,
            String sourceTag,
            boolean renderBackface,
            boolean replace
    ) {
        if (idA.equals(idB)) {
            throw new IllegalArgumentException("Portal pair ids must be distinct");
        }
        if (!replace && (PORTALS.containsKey(idA) || PORTALS.containsKey(idB))) {
            throw new IllegalArgumentException("Portal pair id already registered: " + idA + " or " + idB);
        }
        register(idA, a, b, settingsA, renderA, idB.toString(), groupId, sourceTag, renderBackface, true);
        register(idB, b, a, settingsB, renderB, idA.toString(), groupId, sourceTag, renderBackface, true);
        return PortalRegistrationResult.success(List.of(idA, idB), "registered pair " + idA + " <-> " + idB);
    }

    private static int nextStencilRef() {
        int next = PORTALS.values().stream()
                .mapToInt(view -> view.renderSettings().stencilRef())
                .max()
                .orElse(0) + 1;
        return Math.max(1, next);
    }

    private static boolean stencilRefInUseByOther(ResourceLocation id, int stencilRef) {
        return PORTALS.entrySet().stream()
                .anyMatch(entry -> !entry.getKey().equals(id) && entry.getValue().renderSettings().stencilRef() == stencilRef);
    }

    private static PortalReplacement classifyReplacement(RegisteredPortalView oldView, RegisteredPortalView newView) {
        if (oldView == null || newView == null) {
            return new PortalReplacement("replace", PortalCachePolicy.HARD_INVALIDATE);
        }
        if (!oldView.source().dimension().equals(newView.source().dimension())) {
            return new PortalReplacement("source_dim_changed", PortalCachePolicy.HARD_INVALIDATE);
        }
        if (!oldView.target().dimension().equals(newView.target().dimension())) {
            return new PortalReplacement("target_dim_changed", PortalCachePolicy.HARD_INVALIDATE);
        }
        if (oldView.isCrossDimension() != newView.isCrossDimension()) {
            return new PortalReplacement("cross_dim_changed", PortalCachePolicy.HARD_INVALIDATE);
        }
        if (!oldView.renderSettings().equals(newView.renderSettings())
                || oldView.renderEnabled() != newView.renderEnabled()
                || oldView.renderBackface() != newView.renderBackface()) {
            return new PortalReplacement("settings_only", PortalCachePolicy.SOFT_REPLACE);
        }
        if (!oldView.source().equals(newView.source()) || !oldView.target().equals(newView.target())) {
            return new PortalReplacement("frame_only", PortalCachePolicy.SOFT_REPLACE);
        }
        return new PortalReplacement("compatible_replace", PortalCachePolicy.SOFT_REPLACE);
    }

    private static void notifyInvalidated(
            RegisteredPortalView oldView,
            RegisteredPortalView newView,
            String reason,
            PortalCachePolicy cachePolicy
    ) {
        for (PortalChangeListener listener : CHANGE_LISTENERS) {
            try {
                listener.onPortalViewInvalidated(oldView, newView, reason, cachePolicy);
            } catch (RuntimeException exception) {
                RegisteredPortalView view = oldView == null ? newView : oldView;
                Skyesight.LOGGER.warn(
                        "[Skyesight] Portal cache invalidation listener failed viewId={} reason={} policy={}: {}",
                        view == null ? "-" : view.id(),
                        reason == null ? "-" : reason,
                        cachePolicy == null ? "-" : cachePolicy,
                        exception.toString()
                );
            }
        }
    }

    public interface PortalChangeListener {
        void onPortalViewInvalidated(
                RegisteredPortalView oldView,
                RegisteredPortalView newView,
                String reason,
                PortalCachePolicy cachePolicy
        );
    }

    private record PortalReplacement(String reason, PortalCachePolicy cachePolicy) {}

    private static void validate(ResourceLocation id, PortalEndpoint source, PortalEndpoint target) {
        if (id == null) {
            throw new IllegalArgumentException("Portal id cannot be null");
        }
        if (source == null) {
            throw new IllegalArgumentException("Portal source endpoint cannot be null");
        }
        if (target == null) {
            throw new IllegalArgumentException("Portal target endpoint cannot be null");
        }
    }
}
