package com.skyeshade.skyesight.server.portal;

import com.skyeshade.skyesight.api.PortalEndpoint;
import com.skyeshade.skyesight.portal.PortalTraversalMath;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import java.util.Set;

/** Shared authoritative player transfer for finite portals and mapped regions. */
public final class PortalTransfers {
    private PortalTransfers() {}
    public static void player(ServerPlayer player, PortalEndpoint source, PortalEndpoint target, PortalTraversalMath.Pose pose) {
        var destination=player.server.getLevel(target.dimension());
        if(destination==null) throw new IllegalStateException("Destination dimension unavailable");
        var rotation=PortalTraversalMath.rotation(source.rotation(),target.rotation());
        float head=PortalTraversalMath.yaw(PortalTraversalMath.rotate(Vec3.directionFromRotation(0,player.getYHeadRot()),rotation));
        float body=PortalTraversalMath.yaw(PortalTraversalMath.rotate(Vec3.directionFromRotation(0,player.yBodyRot),rotation));
        float fall=player.fallDistance;
        var p=pose.position();
        player.teleportTo(destination,p.x,p.y,p.z,Set.of(),pose.yaw(),pose.pitch());
        player.setYHeadRot(head);player.yHeadRotO=head;player.yBodyRot=player.yBodyRotO=body;
        player.setDeltaMovement(pose.velocity());player.fallDistance=fall;
    }
}
