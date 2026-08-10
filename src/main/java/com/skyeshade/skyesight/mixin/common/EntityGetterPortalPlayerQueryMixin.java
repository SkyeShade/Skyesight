package com.skyeshade.skyesight.mixin.common;

import com.skyeshade.skyesight.server.portal.PortalApparentQueryPlayer;
import com.skyeshade.skyesight.server.portal.PortalPathProximity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.EntityGetter;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;
import java.util.function.Predicate;

@Mixin(EntityGetter.class)
public interface EntityGetterPortalPlayerQueryMixin {
    @Inject(
            method = "getNearestPlayer(DDDDZ)Lnet/minecraft/world/entity/player/Player;",
            at = @At("RETURN"),
            cancellable = true,
            require = 1
    )
    private void skyesight$blockWrongDimensionVanillaBoolean(
            double x,
            double y,
            double z,
            double distance,
            boolean creativePlayers,
            CallbackInfoReturnable<Player> cir
    ) {
        EntityGetter getter = (EntityGetter) (Object) this;
        if (!(getter instanceof Level level) || !level.isClientSide()) {
            return;
        }
        Player vanilla = cir.getReturnValue();
        if (vanilla != null && !PortalPathProximity.isVanillaReturnValidForQueryLevel(level, vanilla)) {
            PortalPathProximity.traceCrossDimPlayerQueryGuard(
                    level,
                    vanilla,
                    vanilla,
                    null,
                    false,
                    false,
                    true,
                    "none",
                    vanilla.position(),
                    null,
                    "blocked-vanilla-wrong-dimension"
            );
            PortalPathProximity.tracePlayerQueryFinal(
                    "getNearestPlayer(DDDDZ)",
                    level,
                    false,
                    null,
                    true,
                    "blocked-vanilla-wrong-dimension"
            );
            cir.setReturnValue(null);
        }
    }

    @Inject(
            method = "getNearestPlayer(DDDDZ)Lnet/minecraft/world/entity/player/Player;",
            at = @At("HEAD"),
            cancellable = true,
            require = 1
    )
    private void skyesight$getNearestPlayerBoolean(
            double x,
            double y,
            double z,
            double distance,
            boolean creativePlayers,
            CallbackInfoReturnable<Player> cir
    ) {
        EntityGetter getter = (EntityGetter) (Object) this;

        if (!(getter instanceof Level level)) {
            return;
        }
        PortalPathProximity.tracePlayerQueryTargetHit(getter, "getNearestPlayer(DDDDZ)");
        PortalPathProximity.traceEnchantQueryStage(
                level,
                x,
                y,
                z,
                distance,
                "getNearestPlayer(DDDDZ)",
                "hook-hit"
        );

        boolean overrideAtHead = PortalPathProximity.shouldOverridePlayerQueryCompletely(getter, level);
        PortalPathProximity.tracePlayerQueryPlayersAudit(level, overrideAtHead, !overrideAtHead, overrideAtHead ? "portal-aware-head-override" : "vanilla-allowed");
        if (!overrideAtHead) {
            return;
        }

        Predicate<Entity> predicate = creativePlayers
                ? EntitySelector.NO_CREATIVE_OR_SPECTATOR
                : EntitySelector.NO_SPECTATORS;

        Optional<PortalPathProximity.PortalPlayerPathResult> result = PortalPathProximity.nearestPlayerConsideringPortals(
                level,
                x,
                y,
                z,
                distance,
                predicate
        );
        skyesight$finishNearestPlayerQuery(level, "getNearestPlayer(DDDDZ)", result, cir);
    }

    @Inject(
            method = "getNearestPlayer(DDDDLjava/util/function/Predicate;)Lnet/minecraft/world/entity/player/Player;",
            at = @At("RETURN"),
            cancellable = true,
            require = 1
    )
    private void skyesight$blockWrongDimensionVanillaPredicate(
            double x,
            double y,
            double z,
            double distance,
            Predicate<Entity> predicate,
            CallbackInfoReturnable<Player> cir
    ) {
        EntityGetter getter = (EntityGetter) (Object) this;
        if (!(getter instanceof Level level) || !level.isClientSide()) {
            return;
        }
        Player vanilla = cir.getReturnValue();
        if (vanilla != null && !PortalPathProximity.isVanillaReturnValidForQueryLevel(level, vanilla)) {
            PortalPathProximity.traceCrossDimPlayerQueryGuard(
                    level,
                    vanilla,
                    vanilla,
                    null,
                    false,
                    false,
                    true,
                    "none",
                    vanilla.position(),
                    null,
                    "blocked-vanilla-wrong-dimension"
            );
            PortalPathProximity.tracePlayerQueryFinal(
                    "getNearestPlayer(DDDDPredicate)",
                    level,
                    false,
                    null,
                    true,
                    "blocked-vanilla-wrong-dimension"
            );
            cir.setReturnValue(null);
        }
    }

    @Inject(
            method = "getNearestPlayer(DDDDLjava/util/function/Predicate;)Lnet/minecraft/world/entity/player/Player;",
            at = @At("HEAD"),
            cancellable = true,
            require = 1
    )
    private void skyesight$getNearestPlayerPredicate(
            double x,
            double y,
            double z,
            double distance,
            Predicate<Entity> predicate,
            CallbackInfoReturnable<Player> cir
    ) {
        EntityGetter getter = (EntityGetter) (Object) this;

        if (!(getter instanceof Level level)) {
            return;
        }
        PortalPathProximity.tracePlayerQueryTargetHit(getter, "getNearestPlayer(DDDDPredicate)");
        PortalPathProximity.traceEnchantQueryStage(
                level,
                x,
                y,
                z,
                distance,
                "getNearestPlayer(DDDDPredicate)",
                "hook-hit"
        );

        boolean overrideAtHead = PortalPathProximity.shouldOverridePlayerQueryCompletely(getter, level);
        PortalPathProximity.tracePlayerQueryPlayersAudit(level, overrideAtHead, !overrideAtHead, overrideAtHead ? "portal-aware-head-override" : "vanilla-allowed");
        if (!overrideAtHead) {
            return;
        }

        Optional<PortalPathProximity.PortalPlayerPathResult> result = PortalPathProximity.nearestPlayerConsideringPortals(
                level,
                x,
                y,
                z,
                distance,
                predicate
        );
        skyesight$finishNearestPlayerQuery(level, "getNearestPlayer(DDDDPredicate)", result, cir);
    }

    @Inject(
            method = "hasNearbyAlivePlayer(DDDD)Z",
            at = @At("HEAD"),
            cancellable = true,
            require = 1
    )
    private void skyesight$hasNearbyAlivePlayer(
            double x,
            double y,
            double z,
            double distance,
            CallbackInfoReturnable<Boolean> cir
    ) {
        EntityGetter getter = (EntityGetter) (Object) this;
        if (!(getter instanceof Level level)) {
            return;
        }
        PortalPathProximity.tracePlayerQueryTargetHit(getter, "hasNearbyAlivePlayer(DDDD)");
        boolean overrideAtHead = PortalPathProximity.shouldOverridePlayerQueryCompletely(getter, level);
        PortalPathProximity.tracePlayerQueryPlayersAudit(level, overrideAtHead, !overrideAtHead, overrideAtHead ? "portal-aware-head-override" : "vanilla-allowed");
        if (!overrideAtHead) {
            return;
        }

        Predicate<Entity> predicate = entity -> entity instanceof Player player
                && player.isAlive()
                && EntitySelector.NO_SPECTATORS.test(entity);
        Optional<PortalPathProximity.PortalPlayerPathResult> result = PortalPathProximity.nearestPlayerConsideringPortals(
                level,
                x,
                y,
                z,
                distance,
                predicate
        );
        Player returned = skyesight$validReturnPlayer(level, result);
        PortalPathProximity.tracePlayerQueryFinal(
                "hasNearbyAlivePlayer(DDDD)",
                level,
                true,
                returned,
                false,
                returned == null ? "portal-aware-no-candidate" : "portal-aware-candidate"
        );
        cir.setReturnValue(returned != null);
    }

    private void skyesight$finishNearestPlayerQuery(
            Level level,
            String method,
            Optional<PortalPathProximity.PortalPlayerPathResult> result,
            CallbackInfoReturnable<Player> cir
    ) {
        Player returned = skyesight$validReturnPlayer(level, result);
        if (returned == null) {
            PortalPathProximity.tracePlayerQueryReturnPath(level, null, null, false, "portal-aware-no-candidate");
            PortalPathProximity.tracePlayerQueryFinal(method, level, true, null, false, "portal-aware-no-candidate");
            cir.setReturnValue(null);
            return;
        }

        PortalPathProximity.PortalPlayerPathResult pathResult = result.orElse(null);
        boolean usesContext = pathResult != null && pathResult.throughPortal() && !level.isClientSide();
        if (usesContext) {
            PortalPathProximity.storeShortLivedApparentPlayerContext(pathResult);
        }
        String reason = pathResult == null
                ? "portal-aware-no-candidate"
                : pathResult.throughPortal()
                ? (level.isClientSide() ? "client-proxy-query-candidate" : "server-context-query-candidate")
                : "direct-dimension-valid";
        PortalPathProximity.tracePlayerQueryReturnPath(level, pathResult, returned, usesContext, reason);
        PortalPathProximity.traceCrossDimPlayerQueryGuard(
                level,
                returned,
                null,
                pathResult,
                pathResult != null && !pathResult.throughPortal() && PortalPathProximity.isDirectPlayerCandidateForQuery(level, returned),
                returned instanceof PortalApparentQueryPlayer,
                false,
                returned instanceof PortalApparentQueryPlayer
                        ? (pathResult != null && pathResult.throughPortal() ? "proxy-portal" : "proxy-direct")
                        : "real-direct",
                returned.position(),
                pathResult == null ? null : pathResult.viewId(),
                reason
        );
        PortalPathProximity.tracePlayerQueryFinal(method, level, true, returned, false, reason);
        cir.setReturnValue(returned);
    }

    private Player skyesight$validReturnPlayer(Level level, Optional<PortalPathProximity.PortalPlayerPathResult> result) {
        if (result.isEmpty()) {
            return null;
        }
        PortalPathProximity.PortalPlayerPathResult pathResult = result.get();
        Player player = pathResult.player();
        if (player == null) {
            return null;
        }
        if (pathResult.throughPortal()) {
            if (level.isClientSide()
                    && !(player instanceof PortalApparentQueryPlayer)) {
                return null;
            }
            return player;
        }
        return PortalPathProximity.isDirectPlayerCandidateForQuery(level, player) ? player : null;
    }
}
