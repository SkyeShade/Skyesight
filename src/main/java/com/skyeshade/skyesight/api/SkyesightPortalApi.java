package com.skyeshade.skyesight.api;

import com.skyeshade.skyesight.Skyesight;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public final class SkyesightPortalApi {
    private SkyesightPortalApi() {
    }

    public static PortalRegistrationResult registerPortal(String id, PortalEndpoint source, PortalEndpoint target) {
        return registerPortal(id, source, target, null, true, null, "api", false, false);
    }

    public static PortalRegistrationResult registerPortal(
            String id,
            PortalEndpoint source,
            PortalEndpoint target,
            boolean renderEnabled,
            String pairedId,
            String sourceTag,
            boolean replace
    ) {
        return registerPortal(id, source, target, null, renderEnabled, pairedId, sourceTag, replace, false);
    }

    public static PortalRegistrationResult registerPortal(
            String id,
            PortalEndpoint source,
            PortalEndpoint target,
            boolean renderEnabled,
            String pairedId,
            String sourceTag,
            boolean replace,
            boolean renderBackface
    ) {
        return registerPortal(id, source, target, null, renderEnabled, pairedId, sourceTag, replace, renderBackface);
    }

    public static PortalRegistrationResult registerPortal(
            String id,
            PortalEndpoint source,
            PortalEndpoint target,
            PortalRenderSettings renderSettings,
            boolean renderEnabled,
            String pairedId,
            String sourceTag,
            boolean replace
    ) {
        return registerPortal(id, source, target, renderSettings, renderEnabled, pairedId, sourceTag, replace, false);
    }

    public static PortalRegistrationResult registerPortal(
            String id,
            PortalEndpoint source,
            PortalEndpoint target,
            PortalRenderSettings renderSettings,
            boolean renderEnabled,
            String pairedId,
            String sourceTag,
            boolean replace,
            boolean renderBackface
    ) {
        ResourceLocation parsedId = parseId(id);
        PortalRegistrationResult result = SkyesightPortalRegistry.register(
                parsedId,
                source,
                target,
                renderSettings,
                renderEnabled,
                pairedId,
                pairedId,
                sourceTag,
                renderBackface,
                replace
        );
        return result;
    }

    public static PortalRegistrationResult registerPortalPair(String idA, PortalEndpoint a, String idB, PortalEndpoint b) {
        return registerPortalPair(idA, a, idB, b, null, null, true, true, "api", false, false);
    }

    public static PortalRegistrationResult registerPortalPair(
            String idA,
            PortalEndpoint a,
            String idB,
            PortalEndpoint b,
            boolean renderA,
            boolean renderB,
            String sourceTag,
            boolean replace
    ) {
        return registerPortalPair(idA, a, idB, b, null, null, renderA, renderB, sourceTag, replace, false);
    }

    public static PortalRegistrationResult registerPortalPair(
            String idA,
            PortalEndpoint a,
            String idB,
            PortalEndpoint b,
            boolean renderA,
            boolean renderB,
            String sourceTag,
            boolean replace,
            boolean renderBackface
    ) {
        return registerPortalPair(idA, a, idB, b, null, null, renderA, renderB, sourceTag, replace, renderBackface);
    }

    public static PortalRegistrationResult registerPortalPair(
            String idA,
            PortalEndpoint a,
            String idB,
            PortalEndpoint b,
            PortalRenderSettings settingsA,
            PortalRenderSettings settingsB,
            boolean renderA,
            boolean renderB,
            String sourceTag,
            boolean replace
    ) {
        return registerPortalPair(idA, a, idB, b, settingsA, settingsB, renderA, renderB, sourceTag, replace, false);
    }

    public static PortalRegistrationResult registerPortalPair(
            String idA,
            PortalEndpoint a,
            String idB,
            PortalEndpoint b,
            PortalRenderSettings settingsA,
            PortalRenderSettings settingsB,
            boolean renderA,
            boolean renderB,
            String sourceTag,
            boolean replace,
            boolean renderBackface
    ) {
        ResourceLocation parsedA = parseId(idA);
        ResourceLocation parsedB = parseId(idB);
        return SkyesightPortalRegistry.registerPair(
                parsedA,
                a,
                parsedB,
                b,
                settingsA,
                settingsB,
                renderA,
                renderB,
                parsedA + "+" + parsedB,
                sourceTag,
                renderBackface,
                replace
        );
    }

    public static boolean removePortal(String id) {
        ResourceLocation parsed = parseId(id);
        return SkyesightPortalRegistry.remove(parsed);
    }

    public static PortalRegistrationResult removePortalOrPair(String id) {
        ResourceLocation parsed = parseId(id);
        RegisteredPortalView view = SkyesightPortalRegistry.get(parsed);
        if (view == null) {
            return PortalRegistrationResult.failure("portal not found: " + parsed);
        }

        List<ResourceLocation> idsToRemove = new ArrayList<>();
        idsToRemove.add(parsed);

        ResourceLocation paired = parseOptionalId(view.pairedId());
        boolean pairedMissing = false;
        if (paired != null) {
            if (SkyesightPortalRegistry.contains(paired)) {
                if (!idsToRemove.contains(paired)) {
                    idsToRemove.add(paired);
                }
            } else {
                pairedMissing = true;
            }
        } else if (view.groupId() != null && !view.groupId().isBlank()) {
            for (RegisteredPortalView candidate : SkyesightPortalRegistry.all()) {
                if (view.groupId().equals(candidate.groupId())
                        && !idsToRemove.contains(candidate.id())) {
                    idsToRemove.add(candidate.id());
                }
            }
        }

        List<ResourceLocation> removed = new ArrayList<>();
        for (ResourceLocation removeId : idsToRemove) {
            if (SkyesightPortalRegistry.remove(removeId)) {
                removed.add(removeId);
            }
        }

        if (removed.isEmpty()) {
            return PortalRegistrationResult.failure("portal not removed: " + parsed);
        }
        if (removed.size() > 1) {
            return PortalRegistrationResult.success(removed, "removed portal pair " + joinIds(removed));
        }
        return PortalRegistrationResult.success(
                removed,
                pairedMissing
                        ? "removed portal " + parsed + "; paired portal missing"
                        : "removed portal " + parsed
        );
    }

    public static boolean disablePortal(String id) {
        return SkyesightPortalRegistry.disableRetainingCache(parseId(id));
    }

    public static PortalRegistrationResult disablePortalOrPair(String id) {
        ResourceLocation parsed = parseId(id);
        RegisteredPortalView view = SkyesightPortalRegistry.get(parsed);
        if (view == null) {
            return PortalRegistrationResult.failure("portal not found: " + parsed);
        }

        List<ResourceLocation> idsToDisable = new ArrayList<>();
        idsToDisable.add(parsed);

        ResourceLocation paired = parseOptionalId(view.pairedId());
        boolean pairedMissing = false;
        if (paired != null) {
            if (SkyesightPortalRegistry.contains(paired)) {
                if (!idsToDisable.contains(paired)) {
                    idsToDisable.add(paired);
                }
            } else {
                pairedMissing = true;
            }
        } else if (view.groupId() != null && !view.groupId().isBlank()) {
            for (RegisteredPortalView candidate : SkyesightPortalRegistry.all()) {
                if (view.groupId().equals(candidate.groupId())
                        && !idsToDisable.contains(candidate.id())) {
                    idsToDisable.add(candidate.id());
                }
            }
        }

        List<ResourceLocation> disabled = new ArrayList<>();
        for (ResourceLocation disableId : idsToDisable) {
            if (SkyesightPortalRegistry.disableRetainingCache(disableId)) {
                disabled.add(disableId);
            }
        }

        if (disabled.isEmpty()) {
            return PortalRegistrationResult.failure("portal not disabled: " + parsed);
        }
        if (disabled.size() > 1) {
            return PortalRegistrationResult.success(disabled, "disabled portal pair " + joinIds(disabled));
        }
        return PortalRegistrationResult.success(
                disabled,
                pairedMissing
                        ? "disabled portal " + parsed + "; paired portal missing"
                        : "disabled portal " + parsed
        );
    }

    public static boolean removePortalPair(String idA, String idB) {
        return removePortal(idA) | removePortal(idB);
    }

    public static RegisteredPortalView getPortal(String id) {
        return SkyesightPortalRegistry.get(parseId(id));
    }

    public static List<RegisteredPortalView> getAllPortals() {
        return SkyesightPortalRegistry.all();
    }

    public static boolean containsPortal(String id) {
        return SkyesightPortalRegistry.contains(parseId(id));
    }

    public static int clearPortalsBySource(String sourceTag) {
        return SkyesightPortalRegistry.removeBySourceTag(sourceTag);
    }

    public static ResourceLocation parseId(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Portal id cannot be null or blank");
        }
        return id.contains(":")
                ? ResourceLocation.parse(id)
                : ResourceLocation.fromNamespaceAndPath(Skyesight.MODID, id);
    }

    private static ResourceLocation parseOptionalId(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        return parseId(id);
    }

    private static String joinIds(List<ResourceLocation> ids) {
        return ids.stream()
                .map(ResourceLocation::toString)
                .collect(java.util.stream.Collectors.joining("/"));
    }
}
