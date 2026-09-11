package com.skyeshade.skyesight.portal;

import com.skyeshade.skyesight.api.PortalEndpoint;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PortalTraversalMathTest {
    private final PortalEndpoint a = PortalEndpoint.of("a", Level.OVERWORLD, Vec3.ZERO, Direction.NORTH, 2, 3);
    @Test void crossingRequiresPlaneAndFullBodyFitFromEitherSide() {
        for (int side : new int[]{1, -1}) {
            assertNotNull(PortalTraversalMath.crossing(a, new Vec3(.5, -1.5, side), new Vec3(.5, -1.5, -side), .6, 1.8));
            assertNull(PortalTraversalMath.crossing(a, new Vec3(.8, -1.5, side), new Vec3(.8, -1.5, -side), .6, 1.8));
            assertNull(PortalTraversalMath.crossing(a, new Vec3(0, 0, side), new Vec3(0, 0, -side), .6, 1.8));
        }
        assertNull(PortalTraversalMath.crossing(a, new Vec3(0, -1.5, 1), new Vec3(0, -1.5, .01), .6, 1.8));
        assertNull(PortalTraversalMath.crossing(a, new Vec3(0, -1.5, 1), new Vec3(0, -1.5, 0), .6, 1.8));
        assertNull(PortalTraversalMath.crossing(a, new Vec3(0, -1.5, 0), new Vec3(0, -1.5, 0), .6, 1.8));
    }
    @Test void slowDiagonalAndJumpSegmentsUseIntersectionRatherThanEndPoint() {
        assertNotNull(PortalTraversalMath.crossing(a, new Vec3(.3, -1.5, .00001), new Vec3(.3, -1.5, -.00001), .6, 1.8));
        assertNotNull(PortalTraversalMath.crossing(a, new Vec3(-1, -1, 1), new Vec3(1, -.5, -1), .6, 1.8));
        assertNull(PortalTraversalMath.crossing(a, new Vec3(1, -1, 1), new Vec3(1.5, -.5, -1), .6, 1.8));
    }
    @Test void eyeOwnershipIncludesContactButGameplayStillRequiresCrossing() {
        for (int side : new int[]{-1, 1}) {
            var previousEye = new Vec3(0, .12, side * .01);
            var onPlane = new Vec3(0, .12, 0);
            assertNotNull(PortalTraversalMath.eyeCrossing(a, previousEye, onPlane, 1.62, .6, 1.8));
            assertNotNull(PortalTraversalMath.eyeCrossing(a, previousEye, new Vec3(0, .12, -side * .01), 1.62, .6, 1.8));
            assertNull(PortalTraversalMath.eyeCrossing(a, previousEye, new Vec3(0, .12, side * .005), 1.62, .6, 1.8));
            assertNull(PortalTraversalMath.eyeCrossing(a, previousEye.add(.8, 0, 0), onPlane.add(.8, 0, 0), 1.62, .6, 1.8));
            assertNull(PortalTraversalMath.crossing(a, previousEye.subtract(0, 1.62, 0), onPlane.subtract(0, 1.62, 0), .6, 1.8));
        }
        assertNull(PortalTraversalMath.eyeCrossing(a, new Vec3(0, .12, 0), new Vec3(0, .12, 0), 1.62, .6, 1.8));
    }
    @Test void transformsRoundTripAndPreserveMomentumForEveryUprightFacing() {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            var b = PortalEndpoint.of("b", Level.NETHER, new Vec3(1000, 50, -500), facing, 2, 3);
            var position = new Vec3(.4, -.5, .1);
            var out = PortalTraversalMath.position(a, b, position);
            assertTrue(PortalTraversalMath.position(b, a, out).distanceTo(position) < 1e-5);
            var velocity = new Vec3(.1, .42, -.25);
            var transformed = PortalTraversalMath.rotate(velocity, PortalTraversalMath.rotation(a.rotation(), b.rotation()));
            assertEquals(velocity.length(), transformed.length(), 1e-6);
            assertEquals(velocity.y, transformed.y, 1e-6);
            assertTrue(PortalTraversalMath.rotate(transformed, PortalTraversalMath.rotation(b.rotation(), a.rotation())).distanceTo(velocity) < 1e-6);
        }
    }
    @Test void reentryRequiresBodyClearanceOnEitherSideWithoutATimer() {
        for (int side : new int[]{-1, 1}) {
            assertFalse(PortalTraversalMath.clearedExit(a, new Vec3(0, -1.5, side * .005), .6));
            assertFalse(PortalTraversalMath.clearedExit(a, new Vec3(0, -1.5, side * .39), .6));
            assertTrue(PortalTraversalMath.clearedExit(a, new Vec3(0, -1.5, side * .41), .6));
        }
    }
    @Test void feetTransformProducesTheRenderedEyeForStandingAndCrouching() {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            var b = PortalEndpoint.of("b", Level.NETHER, new Vec3(4000, 102.5, -500), facing, 2, 3);
            for (double eyeHeight : new double[]{1.62, 1.27}) {
                var feet = new Vec3(.4, -1.5, -.125);
                var transformedFeet = PortalTraversalMath.position(a, b, feet);
                var renderedEye = PortalTraversalMath.position(a, b, feet.add(0, eyeHeight, 0));
                assertTrue(renderedEye.distanceTo(transformedFeet.add(0, eyeHeight, 0)) < 1e-6);
            }
        }
    }
    @Test void preservingPreviousOffsetKeepsTheWholeArrivalRenderIntervalContinuous() {
        var previous = new Vec3(.2, -.8, .15);
        var current = previous.add(.1, .3332, -.2158);
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            var b = PortalEndpoint.of("b", Level.END, new Vec3(4000, 101, 2000), facing, 2, 3);
            var rotation = PortalTraversalMath.rotation(a.rotation(), b.rotation());
            var authoritative = PortalTraversalMath.position(a, b, current);
            var restoredPrevious = authoritative.add(PortalTraversalMath.rotate(previous.subtract(current), rotation));
            for (double partial : new double[]{0, .25, .75, 1}) {
                assertTrue(restoredPrevious.lerp(authoritative, partial).distanceTo(
                        PortalTraversalMath.position(a, b, previous.lerp(current, partial))) < 1e-6);
            }
        }
    }
}
