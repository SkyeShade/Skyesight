package com.skyeshade.skyesight.portal;

import com.skyeshade.skyesight.api.PortalEndpoint;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PortalCollisionMathTest {
    final PortalEndpoint a = PortalEndpoint.of("a", Level.OVERWORLD, Vec3.ZERO, Direction.NORTH, 2, 3);
    final PortalEndpoint b = PortalEndpoint.of("b", Level.NETHER, new Vec3(1000, 20, 0), Direction.EAST, 2, 3);

    @Test
    void cardinalMapRoundTripsVolumes() {
        AABB box = new AABB(-.3, -1.5, -.2, .3, .3, .4);
        var mapped = PortalCollisionMath.transform(box, a, b);
        var restored = PortalCollisionMath.transform(mapped, b, a);
        assertEquals(box.minX, restored.minX, 1e-5);
        assertEquals(box.maxZ, restored.maxZ, 1e-5);
        assertEquals(box.getYsize(), mapped.getYsize(), 1e-5);
    }

    @Test
    void onlyFarSideInsideApertureIsCarved() {
        var wall = Shapes.create(new AABB(-2, -2, -1, 2, 2, 1));
        var clipped = Shapes.joinUnoptimized(wall, PortalCollisionMath.apertureHalf(a, 1, 4), BooleanOp.ONLY_FIRST);
        assertFalse(overlap(clipped, new AABB(-.2, -.2, -.8, .2, .2, -.2)));
        assertTrue(overlap(clipped, new AABB(-.2, -.2, .2, .2, .2, .8)));
        assertTrue(overlap(clipped, new AABB(1.1, -.2, -.8, 1.5, .2, -.2)));
        assertTrue(overlap(clipped, new AABB(-.2, 1.6, -.8, .2, 1.9, -.2)));
    }

    @Test
    void reverseSideCarvesOppositeHalf() {
        var half = PortalCollisionMath.apertureHalf(a, -1, 4);
        assertTrue(overlap(half, new AABB(-.2, -.2, .2, .2, .2, .8)));
        assertFalse(overlap(half, new AABB(-.2, -.2, -.8, .2, .2, -.2)));
    }

    @Test
    void destinationWallMapsIntoRemovedHalf() {
        var source = Shapes.create(new AABB(-.5, -1, -1, .5, 1, -.5));
        var target = PortalCollisionMath.transform(source, a, b);
        var restored = PortalCollisionMath.transform(target, b, a);
        assertTrue(overlap(restored, new AABB(-.2, -.2, -.8, .2, .2, -.6)));
    }

    @Test
    void oversizeBodiesDoNotFit() {
        assertTrue(PortalTraversalMath.fitsBody(a, new AABB(-.3, -1.5, -.3, .3, .3, .3)));
        assertFalse(PortalTraversalMath.fitsBody(a, new AABB(-1.1, -1.5, -.3, 1.1, .3, .3)));
        assertFalse(PortalTraversalMath.fitsBody(a, new AABB(-.3, -1.5, -.3, .3, 2, .3)));
        assertTrue(PortalCollisionMath.supported(a));
        assertTrue(PortalCollisionMath.supported(b));
    }

    private boolean overlap(VoxelShape shape, AABB box) {
        return Shapes.joinIsNotEmpty(shape, Shapes.create(box), BooleanOp.AND);
    }
}
