package com.skyeshade.skyesight.server.portal;

import com.skyeshade.skyesight.Skyesight;
import com.skyeshade.skyesight.api.*;
import com.skyeshade.skyesight.mixin.server.PortalInteractionGameModeAccessor;
import com.skyeshade.skyesight.network.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.*;

/**
 * Server-owned, one-hop interaction sessions. Every action and mining tick revalidates the ray.
 */
@EventBusSubscriber(modid = Skyesight.MODID)
public final class PortalInteractions {
    private static final Map<ServerPlayer, Session> MINING = new IdentityHashMap<>();
    private static final Map<ServerPlayer, Long> SEQUENCES = new WeakHashMap<>();
    private static final Map<ServerPlayer, Integer> ACTION_TICKS = new WeakHashMap<>();

    private record MenuAnchor(net.minecraft.world.inventory.AbstractContainerMenu menu,
                              SkyesightPortalRaycast.Link link, Vec3 position) {
    }

    private static final Map<ServerPlayer, MenuAnchor> MENUS = new WeakHashMap<>();

    private record Target(SkyesightPortalRaycast.Result ray, SkyesightPortalRaycast.Link link) {
    }

    private record Session(SkyesightPortalInteractionPayload intent, BlockHitResult hit,
                           SkyesightPortalRaycast.Link link, net.minecraft.server.level.ServerLevel destination) {
    }

    private static Target target(ServerPlayer player, SkyesightPortalInteractionPayload intent) {
        if (player.isRemoved() || !player.isAlive() || player.isSpectator() || player.isChangingDimension())
            return null;
        double reach = Math.max(player.blockInteractionRange(), player.entityInteractionRange());
        var ray = SkyesightPortalRaycast.trace(new SkyesightServerPortalRays(player.server, player,
                        TraversalPortalManager::links, e -> e != player), player.level().dimension(),
                player.getEyePosition(), player.getLookAngle(), reach, 1);
        if (!com.skyeshade.skyesight.portal.PortalInteractionPolicy.matches(ray, intent.portal(), intent.revision(),
                player.blockInteractionRange(), player.entityInteractionRange())) return null;
        var step = ray.chain().getFirst();
        var link = TraversalPortalManager.links(player.level().dimension()).stream()
                .filter(l -> l.id().equals(step.portal()) && l.revision() == step.revision()).findFirst().orElse(null);
        var level = player.server.getLevel(ray.dimension());
        if (link == null || level == null || !level.getWorldBorder().isWithinBounds(BlockPos.containing(ray.position()))
                || !level.mayInteract(player, BlockPos.containing(ray.position()))) return null;
        return new Target(ray, link);
    }

    public static void handle(ServerPlayer player, SkyesightPortalInteractionPayload intent) {
        if (intent.sequence() <= SEQUENCES.getOrDefault(player, -1L)) return;
        SEQUENCES.put(player, intent.sequence());
        if (intent.action() == SkyesightPortalInteractionPayload.Action.ABORT) {
            var session = MINING.get(player);
            if (session != null && session.intent.portal().equals(intent.portal()) && session.intent.revision() == intent.revision())
                stop(player);
            return;
        }
        int tick = player.server.getTickCount();
        // Native START/STOP/ABORT form an ordered protocol, not a repeated action heartbeat.
        // A network batch may contain START and STOP in the same server tick; vanilla delayed
        // destruction handles it. Dropping STOP here strands the controller indefinitely.
        if (intent.action() == SkyesightPortalInteractionPayload.Action.ATTACK || intent.action() == SkyesightPortalInteractionPayload.Action.USE) {
            if (ACTION_TICKS.getOrDefault(player, -1) == tick) return;
            ACTION_TICKS.put(player, tick);
        }
        var target = target(player, intent);
        if (target == null) {
            stop(player);
            return;
        }
        var level = player.server.getLevel(target.ray.dimension());
        player.resetLastActionTime();
        if ((intent.action() == SkyesightPortalInteractionPayload.Action.START || intent.action() == SkyesightPortalInteractionPayload.Action.STOP)
                && target.ray.hit() instanceof BlockHitResult hit) {
            if (!hit.getBlockPos().equals(intent.predictedBlock())) {
                stop(player);
                return;
            }
            var current = MINING.get(player);
            if (intent.action() == SkyesightPortalInteractionPayload.Action.STOP && (current == null
                    || !current.intent.portal().equals(intent.portal()) || current.intent.revision() != intent.revision()
                    || !current.hit.getBlockPos().equals(hit.getBlockPos()))) return;
            if (intent.action() == SkyesightPortalInteractionPayload.Action.START) {
                stop(player);
                MINING.put(player, new Session(intent, hit, target.link, level));
            }
            withMiningContext(player, MINING.get(player), () -> player.gameMode.handleBlockBreakAction(hit.getBlockPos(),
                    intent.action() == SkyesightPortalInteractionPayload.Action.START ? Action.START_DESTROY_BLOCK : Action.STOP_DESTROY_BLOCK,
                    hit.getDirection(), level.getMaxBuildHeight(), 0));
            return;
        }
        stop(player);
        var previousMenu = player.containerMenu;
        try (var scope = PortalInteractionContext.open(player, level, target.link)) {
            if (intent.action() == SkyesightPortalInteractionPayload.Action.ATTACK && target.ray.hit() instanceof EntityHitResult hit) {
                if (hit.getEntity() != player && hit.getEntity().isAttackable()
                        && !(hit.getEntity() instanceof net.minecraft.world.entity.item.ItemEntity)
                        && !(hit.getEntity() instanceof net.minecraft.world.entity.ExperienceOrb)
                        && player.getMainHandItem().isItemEnabled(level.enabledFeatures()))
                    player.attack(hit.getEntity());
            } else if (intent.action() == SkyesightPortalInteractionPayload.Action.USE) {
                for (var hand : InteractionHand.values()) {
                    if (!player.getItemInHand(hand).isItemEnabled(level.enabledFeatures())) continue;
                    var itemBefore = player.getItemInHand(hand).copy();
                    InteractionResult result = InteractionResult.PASS;
                    if (target.ray.hit() instanceof BlockHitResult hit) {
                        result = player.gameMode.useItemOn(player, level, player.getItemInHand(hand), hand, hit);
                    } else if (target.ray.hit() instanceof EntityHitResult hit) {
                        var local = hit.getLocation().subtract(hit.getEntity().position());
                        var hook = net.neoforged.neoforge.common.CommonHooks.onInteractEntityAt(player, hit.getEntity(), local, hand);
                        result = hook != null ? hook : hit.getEntity().interactAt(player, local, hand);
                        if (!result.consumesAction()) result = player.interactOn(hit.getEntity(), hand);
                        if (result.consumesAction())
                            net.minecraft.advancements.CriteriaTriggers.PLAYER_INTERACTED_WITH_ENTITY.trigger(player,
                                    result.indicateItemUse() ? itemBefore : ItemStack.EMPTY, hit.getEntity());
                    }
                    if (result.consumesAction() || result == InteractionResult.FAIL) break;
                }
                player.inventoryMenu.broadcastChanges();
            }
        }
        if (player.containerMenu != previousMenu && player.containerMenu != player.inventoryMenu)
            MENUS.put(player, new MenuAnchor(player.containerMenu, target.link, target.ray.position()));
    }

    /**
     * Preserve vanilla menu validity, but evaluate its destination distance in the same validated portal context.
     */
    public static boolean menuValid(ServerPlayer player, net.minecraft.world.inventory.AbstractContainerMenu menu) {
        var anchor = MENUS.get(player);
        if (anchor == null) return menu.stillValid(player);
        if (anchor.menu != menu) {
            MENUS.remove(player);
            return menu.stillValid(player);
        }
        var link = TraversalPortalManager.links(player.level().dimension()).stream()
                .filter(l -> l.id().equals(anchor.link.id()) && l.revision() == anchor.link.revision()).findFirst().orElse(null);
        if (link == null) {
            MENUS.remove(player);
            return false;
        }
        Vec3 virtual = com.skyeshade.skyesight.portal.PortalTraversalMath.position(link.target(), link.source(), anchor.position);
        Vec3 direction = virtual.subtract(player.getEyePosition());
        double menuReach = player.blockInteractionRange() + 4.0; // Container.stillValidBlockEntity's native allowance.
        if (direction.lengthSqr() > menuReach * menuReach || direction.lengthSqr() < 1e-12) return false;
        var ray = SkyesightPortalRaycast.trace(new SkyesightServerPortalRays(player.server, player, TraversalPortalManager::links, e -> false),
                player.level().dimension(), player.getEyePosition(), direction, menuReach, 1);
        if (ray.chain().size() != 1 || !ray.chain().getFirst().portal().equals(link.id())) return false;
        var level = player.server.getLevel(link.target().dimension());
        if (level == null) return false;
        try (var scope = PortalInteractionContext.open(player, level, link)) {
            return menu.stillValid(player);
        }
    }

    public static List<ServerPlayer> remoteOpeners(net.minecraft.world.level.Level level, BlockPos pos,
                                                   java.util.function.Predicate<net.minecraft.world.entity.player.Player> owns) {
        if (!(level instanceof net.minecraft.server.level.ServerLevel)) return List.of();
        var result = new ArrayList<ServerPlayer>();
        for (var entry : List.copyOf(MENUS.entrySet())) {
            var player = entry.getKey();
            var anchor = entry.getValue();
            if (player.hasDisconnected() || player.containerMenu != anchor.menu
                    || !anchor.link.target().dimension().equals(level.dimension())
                    || anchor.position.distanceToSqr(Vec3.atCenterOf(pos)) > 16 || !menuValid(player, anchor.menu))
                continue;
            try (var ignored = PortalInteractionContext.open(player, level, anchor.link)) {
                if (owns.test(player)) result.add(player);
            }
        }
        return result;
    }

    private static void withMiningContext(ServerPlayer player, Session session, Runnable action) {
        var mode = player.gameMode;
        var oldLevel = ((PortalInteractionGameModeAccessor) mode).skyesight$level();
        try (var ignored = PortalInteractionContext.open(player, session.destination, session.link)) {
            mode.setLevel(session.destination);
            action.run();
        } finally {
            mode.setLevel(oldLevel);
        }
    }

    /**
     * The real game mode ticks exactly once, at its normal vanilla call site.
     */
    public static void tickGameMode(ServerPlayer player, Runnable vanillaTick) {
        var session = MINING.get(player);
        if (session == null) {
            vanillaTick.run();
            return;
        }
        var target = target(player, session.intent);
        if (target == null || !(target.ray.hit() instanceof BlockHitResult hit)
                || !hit.getBlockPos().equals(session.hit.getBlockPos())) {
            stop(player);
            vanillaTick.run();
            return;
        }
        withMiningContext(player, session, vanillaTick);
        var mode = (PortalInteractionGameModeAccessor) player.gameMode;
        int stage = mode.skyesight$destroying() || mode.skyesight$delayed() ? mode.skyesight$progress() : -1;
        if (stage < 0) MINING.remove(player);
    }

    private static void stop(ServerPlayer player) {
        var session = MINING.remove(player);
        if (session == null) return;
        withMiningContext(player, session, () -> player.gameMode.handleBlockBreakAction(session.hit.getBlockPos(),
                Action.ABORT_DESTROY_BLOCK, session.hit.getDirection(), session.destination.getMaxBuildHeight(), 0));
        // Cancellation also retires vanilla's delayed completion, including after portal invalidation.
        var mode = (PortalInteractionGameModeAccessor) player.gameMode;
        mode.skyesight$destroying(false);
        mode.skyesight$delayed(false);
        session.destination.destroyBlockProgress(player.getId(), session.hit.getBlockPos(), -1);
    }

    public static void beforePhysicalAction(ServerPlayer player) {
        // Retire the destination controller before vanilla installs a new source-world target.
        // Aborting it on the next tick would otherwise erase the newly started physical mine.
        stop(player);
    }

    @SubscribeEvent
    public static void stopped(net.neoforged.neoforge.event.server.ServerStoppedEvent event) {
        MINING.clear();
        SEQUENCES.clear();
        ACTION_TICKS.clear();
        MENUS.clear();
    }

    @SubscribeEvent
    public static void logout(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            stop(player);
            MENUS.remove(player);
            SEQUENCES.remove(player);
            ACTION_TICKS.remove(player);
        }
    }
}
