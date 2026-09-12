package com.skyeshade.skyesight.server.portal;

import com.skyeshade.skyesight.api.SkyesightPortalRaycast.Link;
import com.skyeshade.skyesight.portal.PortalTraversalMath;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * A synchronous, actor-specific interaction scope. Never moves an entity or changes world ownership.
 */
public final class PortalInteractionContext implements AutoCloseable {
    private static final ThreadLocal<PortalInteractionContext> CURRENT = new ThreadLocal<>();
    private final PortalInteractionContext previous;
    public final net.minecraft.world.entity.player.Player player;
    public final net.minecraft.world.level.Level level;
    public final net.minecraft.world.level.Level sourceLevel;
    public final net.minecraft.world.phys.Vec3 sourcePosition;
    public final PortalTraversalMath.Pose pose;
    public final net.minecraft.world.phys.AABB bounds;

    private PortalInteractionContext(net.minecraft.world.entity.player.Player player, net.minecraft.world.level.Level level, Link link) {
        this.player = player;
        this.level = level;
        this.sourceLevel = player.level();
        this.sourcePosition = player.position();
        this.pose = PortalTraversalMath.transform(link.source(), link.target(), player.position(),
                player.getDeltaMovement(), player.getYRot(), player.getXRot());
        this.bounds = com.skyeshade.skyesight.portal.PortalCollisionMath.transform(player.getBoundingBox(), link.source(), link.target());
        previous = CURRENT.get();
        CURRENT.set(this);
    }

    public static PortalInteractionContext open(net.minecraft.world.entity.player.Player player, net.minecraft.world.level.Level level, Link link) {
        return new PortalInteractionContext(player, level, link);
    }

    public static PortalInteractionContext forEntity(Entity entity) {
        var context = CURRENT.get();
        return context != null && context.player == entity ? context : null;
    }

    public static boolean active() {
        return CURRENT.get() != null;
    }

    public static boolean ownsConnection(Object listener) {
        var c = CURRENT.get();
        return c != null && c.player instanceof ServerPlayer serverPlayer && serverPlayer.connection == listener;
    }

    @Override
    public void close() {
        if (previous == null) CURRENT.remove();
        else CURRENT.set(previous);
    }
}

