package com.skyeshade.skyesight.server.portal;

import com.skyeshade.skyesight.server.PortalSimulationCoordinator;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;

public final class PortalDespawnProtection extends PortalSimulationCoordinator {
    private static final ThreadLocal<Boolean> ALLOW_PORTAL_ENTITY_DISCARD = ThreadLocal.withInitial(() -> false);

    private PortalDespawnProtection() {
    }

    public static boolean isSkyesightIntentionalDiscard() {
        return ALLOW_PORTAL_ENTITY_DISCARD.get();
    }

    public static boolean setSkyesightIntentionalDiscard(boolean allowed) {
        boolean previous = ALLOW_PORTAL_ENTITY_DISCARD.get();
        ALLOW_PORTAL_ENTITY_DISCARD.set(allowed);
        return previous;
    }

    public static boolean shouldProtectHostileInPortalRegion(Mob mob, ServerLevel level, String reason) {
        return PortalSimulationCoordinator.shouldProtectHostileInPortalRegion(mob, level, reason);
    }

    public static boolean shouldProtectPortalMobFromDespawn(Mob mob) {
        return PortalSimulationCoordinator.shouldProtectPortalMobFromDespawn(mob);
    }

    public static boolean shouldCancelPortalEntityRemoval(Entity entity, Entity.RemovalReason reason) {
        return PortalSimulationCoordinator.shouldCancelPortalEntityRemoval(entity, reason);
    }

    public static void onPortalEntityRemoved(Entity entity, Entity.RemovalReason reason, boolean cancelled) {
        PortalSimulationCoordinator.onPortalEntityRemoved(entity, reason, cancelled);
    }
}
