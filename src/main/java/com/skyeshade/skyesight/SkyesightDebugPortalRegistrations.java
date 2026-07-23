package com.skyeshade.skyesight;

import com.skyeshade.skyesight.api.PortalEndpoint;
import com.skyeshade.skyesight.api.PortalCachePolicy;
import com.skyeshade.skyesight.api.PortalRenderSettings;
import com.skyeshade.skyesight.api.RegisteredPortalView;
import com.skyeshade.skyesight.api.SkyesightPortalApi;
import com.skyeshade.skyesight.api.SkyesightPortalRegistry;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class SkyesightDebugPortalRegistrations {
    private static final List<String> DEFAULT_PORTAL_IDS = List.of(
            "debug_portal_a",
            "debug_portal_b",
            "debug_portal_c",
            "debug_portal_d",
            "debug_portal_e",
            "debug_portal_g"
    );
    private static final Set<ResourceLocation> removedDefaultPortalIdsThisSession = new HashSet<>();
    private static boolean defaultsRegisteredThisSession;
    private static boolean loggedAlreadyRegisteredSkip;
    private static boolean lifecycleListenerRegistered;

    private SkyesightDebugPortalRegistrations() {
    }

    public static void registerDefaults() {
        registerLifecycleListener();
        if (defaultsRegisteredThisSession) {
            if (!loggedAlreadyRegisteredSkip) {
                loggedAlreadyRegisteredSkip = true;
                logDefaultRegistration("startup", 1, removedDefaultPortalIdsThisSession.size(), 0);
            }
            return;
        }

        int registered = registerMissingDefaults("startup");
        defaultsRegisteredThisSession = true;
        logDefaultRegistration("startup", 0, removedDefaultPortalIdsThisSession.size(), registered);
    }

    public static int restoreDefaults() {
        registerLifecycleListener();
        removedDefaultPortalIdsThisSession.clear();
        defaultsRegisteredThisSession = true;
        loggedAlreadyRegisteredSkip = false;
        int registered = registerMissingDefaults("restore-command");
        logDefaultRegistration("restore-command", 0, 0, registered);
        return registered;
    }

    public static int clearDefaultsForSession() {
        registerLifecycleListener();
        int removed = 0;
        for (String id : DEFAULT_PORTAL_IDS) {
            ResourceLocation parsed = SkyesightPortalApi.parseId(id);
            removedDefaultPortalIdsThisSession.add(parsed);
            if (SkyesightPortalApi.removePortal(id)) {
                removed++;
            }
        }
        logDefaultRegistration("clear-command", 0, removedDefaultPortalIdsThisSession.size(), -removed);
        return removed;
    }

    private static void recordRemovedIfDefault(ResourceLocation id) {
        if (isDefaultPortalId(id)) {
            removedDefaultPortalIdsThisSession.add(id);
        }
    }

    private static void registerLifecycleListener() {
        if (lifecycleListenerRegistered) {
            return;
        }
        lifecycleListenerRegistered = true;
        SkyesightPortalRegistry.addChangeListener(SkyesightDebugPortalRegistrations::recordDefaultRemoval);
    }

    private static void recordDefaultRemoval(
            RegisteredPortalView oldView,
            RegisteredPortalView newView,
            String reason,
            PortalCachePolicy cachePolicy
    ) {
        if (oldView == null || (cachePolicy != PortalCachePolicy.REMOVE && cachePolicy != PortalCachePolicy.CLEAR)) {
            return;
        }
        recordRemovedIfDefault(oldView.id());
    }

    public static boolean isDefaultPortalId(ResourceLocation id) {
        if (id == null) {
            return false;
        }
        for (String defaultId : DEFAULT_PORTAL_IDS) {
            if (SkyesightPortalApi.parseId(defaultId).equals(id)) {
                return true;
            }
        }
        return false;
    }

    private static int registerMissingDefaults(String reason) {
        PortalEndpoint a = new PortalEndpoint("A", Level.OVERWORLD, new Vec3(0.5D, 89.0D, 0.001D), Direction.NORTH, new Quaternionf(), 1.0F, 2.0F);
        PortalEndpoint b = new PortalEndpoint("B", Level.OVERWORLD, new Vec3(2.999D, 89.0D, 2.5D), Direction.EAST, PortalEndpoint.rotationFromYawPitchRoll(-90.0F, 0.0F, 0.0F), 1.0F, 2.0F);
        PortalEndpoint c = new PortalEndpoint("C", Level.OVERWORLD, new Vec3(4.5D, 89.0D, 0.001D), Direction.NORTH, new Quaternionf(a.rotation()), 1.0F, 2.0F);
        PortalEndpoint d = new PortalEndpoint("D", Level.OVERWORLD, new Vec3(502.0D, 66.0D, -1.501D), Direction.EAST, new Quaternionf(b.rotation()), 1.0F, 2.0F);
        PortalEndpoint e = new PortalEndpoint("E", Level.OVERWORLD, new Vec3(6.5D, 89.0D, 0.001D), Direction.NORTH, new Quaternionf(a.rotation()), 1.0F, 2.0F);
        PortalEndpoint f = new PortalEndpoint("F", Level.NETHER, new Vec3(0.5D, 84.0D, 0.5D), Direction.EAST, new Quaternionf(b.rotation()), 1.0F, 2.0F);
        PortalEndpoint g = new PortalEndpoint("G", Level.OVERWORLD, new Vec3(8.5D, 89.0D, 0.01D), Direction.NORTH, new Quaternionf(a.rotation()), 1.0F, 2.0F);
        PortalEndpoint h = new PortalEndpoint("H", Level.END, new Vec3(0.5D, 80.0D, 0.5D), Direction.SOUTH, PortalEndpoint.rotationFromYawPitchRoll(180.0F, 0.0F, 0.0F), 1.0F, 2.0F);

        PortalRenderSettings settingsA = new PortalRenderSettings(true, true, 1, 4, 4, 4, 2, true, true, true, true, true, true);
        PortalRenderSettings settingsB = new PortalRenderSettings(true, true, 2, 4, 4, 4, 2, true, true, true, true, true, true);
        PortalRenderSettings settingsC = new PortalRenderSettings(true, true, 3, 4, 4, 5, 4, true, true, true, true, true, true);
        PortalRenderSettings settingsD = new PortalRenderSettings(true, false, 4, 4, 4, 5, 4, true, true, true, true, true, true);
        PortalRenderSettings settingsE = new PortalRenderSettings(
                true,
                true,
                5,
                4,
                4,
                0,
                0,
                true,
                true,
                true,
                true,
                true,
                true
        );
        PortalRenderSettings settingsG = new PortalRenderSettings(
                true,
                true,
                6,
                4,
                4,
                0,
                0,
                true,
                true,
                true,
                true,
                true,
                true
        );

        int registered = 0;
        registered += registerDefaultPair("debug_portal_a", a, "debug_portal_b", b, settingsA, settingsB, true, true);
        registered += registerDefaultPair("debug_portal_c", c, "debug_portal_d", d, settingsC, settingsD, true, false);
        registered += registerDefaultOneWay("debug_portal_e", e, f, settingsE, true, "F");
        registered += registerDefaultOneWay("debug_portal_g", g, h, settingsG, true, "H");
        return registered;
    }

    private static int registerDefaultPair(
            String idA,
            PortalEndpoint a,
            String idB,
            PortalEndpoint b,
            PortalRenderSettings settingsA,
            PortalRenderSettings settingsB,
            boolean renderA,
            boolean renderB
    ) {
        ResourceLocation parsedA = SkyesightPortalApi.parseId(idA);
        ResourceLocation parsedB = SkyesightPortalApi.parseId(idB);
        if (removedDefaultPortalIdsThisSession.contains(parsedA) || removedDefaultPortalIdsThisSession.contains(parsedB)) {
            return 0;
        }
        if (SkyesightPortalApi.containsPortal(idA) || SkyesightPortalApi.containsPortal(idB)) {
            return 0;
        }
        SkyesightPortalApi.registerPortalPair(idA, a, idB, b, settingsA, settingsB, renderA, renderB, "debug-default", true, true);
        return 2;
    }

    private static int registerDefaultOneWay(
            String id,
            PortalEndpoint source,
            PortalEndpoint target,
            PortalRenderSettings settings,
            boolean render,
            String pairedId
    ) {
        ResourceLocation parsed = SkyesightPortalApi.parseId(id);
        if (removedDefaultPortalIdsThisSession.contains(parsed) || SkyesightPortalApi.containsPortal(id)) {
            return 0;
        }
        SkyesightPortalApi.registerPortal(id, source, target, settings, render, pairedId, "debug-default", true, true);
        return 1;
    }

    private static void logDefaultRegistration(String reason, int skippedAlreadyRegistered, int skippedRemovedThisSession, int registered) {
        Skyesight.LOGGER.info(
                "[Skyesight] PORTAL_DEFAULT_REGISTRATION: attempt=debug-defaults reason={} skippedAlreadyRegistered={} skippedRemovedThisSession={} registered={} callsite=SkyesightDebugPortalRegistrations",
                reason,
                skippedAlreadyRegistered,
                skippedRemovedThisSession,
                registered
        );
    }
}
