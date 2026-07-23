package com.skyeshade.skyesight.server.portal;

import com.skyeshade.skyesight.Skyesight;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.EntityGetter;
import net.minecraft.world.level.Level;

import java.lang.reflect.Method;
import java.util.Arrays;

public final class PortalPlayerQueryMixinTargetAudit {
    private PortalPlayerQueryMixinTargetAudit() {
    }

    public static void logStartupAudit() {
        boolean levelNearby = declares(Level.class, "hasNearbyAlivePlayer", double.class, double.class, double.class, double.class);
        boolean levelPredicate = declaresByName(Level.class, "getNearestPlayer", 5);
        boolean levelBoolean = declares(Level.class, "getNearestPlayer", double.class, double.class, double.class, double.class, boolean.class);
        boolean serverOverride = declaresAny(ServerLevel.class, "getNearestPlayer", "hasNearbyAlivePlayer");
        boolean clientOverride = declaresClientLevelAny();
        boolean entityGetterDefault = declaresAny(EntityGetter.class, "getNearestPlayer", "hasNearbyAlivePlayer");
        boolean likelyApplies = levelNearby || levelPredicate || levelBoolean;
        Skyesight.LOGGER.info(
                "[Skyesight] PORTAL_PLAYER_QUERY_MIXIN_TARGET_AUDIT: Level.hasNearbyAlivePlayer={} Level.getNearestPlayerPredicate={} Level.getNearestPlayerBoolean={} ServerLevel.override={} ClientLevel.override={} EntityGetterDefaultMethod={} mixinStrategy={} currentMixinLikelyApplies={} reason={}",
                present(levelNearby),
                present(levelPredicate),
                present(levelBoolean),
                present(serverOverride),
                present(clientOverride),
                present(entityGetterDefault),
                "callsite-redirect",
                likelyApplies ? "yes" : "no",
                likelyApplies
                        ? "Level declares queried methods"
                        : "player-query methods are EntityGetter interface defaults; Level mixin removed to avoid require=0 no-op"
        );
    }

    private static boolean declares(Class<?> type, String name, Class<?>... params) {
        try {
            type.getDeclaredMethod(name, params);
            return true;
        } catch (NoSuchMethodException ignored) {
            return false;
        }
    }

    private static boolean declaresByName(Class<?> type, String name, int parameterCount) {
        return Arrays.stream(type.getDeclaredMethods())
                .filter(method -> method.getName().equals(name))
                .anyMatch(method -> method.getParameterCount() == parameterCount);
    }

    private static boolean declaresAny(Class<?> type, String... names) {
        for (Method method : type.getDeclaredMethods()) {
            for (String name : names) {
                if (method.getName().equals(name)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean declaresClientLevelAny() {
        try {
            Class<?> clientLevel = Class.forName("net.minecraft.client.multiplayer.ClientLevel");
            return declaresAny(clientLevel, "getNearestPlayer", "hasNearbyAlivePlayer");
        } catch (ClassNotFoundException ignored) {
            return false;
        }
    }

    private static String present(boolean present) {
        return present ? "present" : "missing";
    }
}
