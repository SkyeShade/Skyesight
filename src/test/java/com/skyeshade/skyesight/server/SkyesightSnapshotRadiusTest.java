package com.skyeshade.skyesight.server;

import com.skyeshade.skyesight.api.PortalEndpoint;
import com.skyeshade.skyesight.api.SkyesightPortalApi;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class SkyesightSnapshotRadiusTest {
    private static final ResourceLocation VIEW = ResourceLocation.parse("test:snapshot_radius");
    @AfterEach void clear() { SkyesightPortalApi.removePortal(VIEW.toString()); }
    @Test void terrainKeepAliveAndEntityUpdatesCannotAlternateSnapshotMembershipRadius() {
        SkyesightPortalApi.registerPortal(VIEW.toString(),
                PortalEndpoint.of("source", Vec3.ZERO, Direction.NORTH),
                PortalEndpoint.of("target", Vec3.ZERO, Direction.SOUTH));
        int entityRadius = SkyesightPortalApi.getPortal(VIEW.toString()).renderSettings().entityChunkRadius();
        for (int terrainRadius : new int[]{4, 19, 4, 19, 8, 12}) {
            var watch = new SkyesightServerViewTracker.ViewWatch(VIEW, 1, Level.OVERWORLD, -59, 51, terrainRadius, Set.of());
            assertEquals(entityRadius, SkyesightServerEntitySnapshotSender.snapshotRadius(watch));
            assertEquals(terrainRadius, watch.radius());
        }
    }
    @Test void standaloneCameraWithoutPortalKeepsItsOwnSnapshotRadius() {
        var watch = new SkyesightServerViewTracker.ViewWatch(VIEW, 1, Level.OVERWORLD, 0, 0, 12, Set.of());
        assertEquals(12, SkyesightServerEntitySnapshotSender.snapshotRadius(watch));
    }
}