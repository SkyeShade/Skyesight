package com.skyeshade.skyesight.portal;

import com.skyeshade.skyesight.api.PortalEndpoint;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PortalCrossingStateTest {
    private final PortalEndpoint portal = PortalEndpoint.of("test", Level.OVERWORLD, Vec3.ZERO, Direction.NORTH, 2, 3);
    private Vec3 sample(PortalCrossingState state, double x, double z) {
        return state.update(portal, new Vec3(x, -1.5, z), .6, 1.8);
    }
    @Test void contactNoiseRetreatAndRepeatedCrossingsHaveExactlyOneEventPerExit() {
        var state = new PortalCrossingState();
        assertNull(sample(state, 0, .5));
        int events = 0;
        for (int cycle = 0; cycle < 1000; cycle++) {
            double side = cycle % 2 == 0 ? 1 : -1;
            int stable = state.stableSide();
            for (double z : new double[]{.05, .0001, 0, -.0001, .005, 0, -.005, 0, .002, -.001}) {
                assertNull(sample(state, 0, side * z));
                assertEquals(stable, state.stableSide());
            }
            assertNotNull(sample(state, 0, -side * .02)); events++;
            assertEquals(-stable, state.stableSide());
            assertNull(sample(state, 0, -side * .5));
            assertNull(sample(state, 0, -side * .5));
        }
        assertEquals(1000, events);
    }
    @Test void leavingOpeningWhileStraddlingCancelsPendingCrossing() {
        var state = new PortalCrossingState();
        sample(state, 0, .1); sample(state, 0, -.001);
        assertTrue(state.hasPendingCrossing());
        assertNull(sample(state, 1.1, -.002));
        assertFalse(state.hasPendingCrossing());
        assertNull(sample(state, 0, -.02));
    }
    @Test void actualIntersectionRatherThanDistantEndpointDeterminesFastDiagonalFit() {
        var state = new PortalCrossingState();
        sample(state, -1, 1);
        var hit = sample(state, 1, -1);
        assertNotNull(hit); assertEquals(0, hit.x, 1e-6);
    }
    @Test void bodyOverlapsVerticallyButCenterlineMustEnterTheOpening() {
        for (double x : new double[]{-.95, .95}) {
            var state = new PortalCrossingState();
            sample(state, x, .1); assertNotNull(sample(state, x, -.1));
        }
        for (double x : new double[]{-1.05, 1.05}) {
            var state = new PortalCrossingState();
            sample(state, x, .1); assertNull(sample(state, x, -.1));
        }
        for (double y : new double[]{-3.31, 1.51}) {
            var state = new PortalCrossingState();
            state.update(portal, new Vec3(0, y, .1), .6, 1.8);
            assertNull(state.update(portal, new Vec3(0, y, -.1), .6, 1.8));
        }
        for (double y : new double[]{-1.5, -.31, 0, .5}) {
            var state = new PortalCrossingState();
            state.update(portal, new Vec3(0, y, .1), .6, 1.8);
            assertNotNull(state.update(portal, new Vec3(0, y, -.1), .6, 1.8));
        }
    }
    @Test void authoritativeArrivalAllowsImmediateReturnWithoutPingPong() {
        var state = new PortalCrossingState();
        for (int i = 0; i < 100; i++) {
            double side = i % 2 == 0 ? 1 : -1;
            state.arrive(portal, new Vec3(0, -1.5, side * .005));
            assertNull(sample(state, 0, side * .005));
            assertNull(sample(state, 0, side * .02));
            assertNotNull(sample(state, 0, -side * .02));
            assertNull(sample(state, 0, -side * .02));
        }
    }
    @Test void fastAcceptedMovementAndInitialContactBandStillUseTheWholeSweep() {
        for (double start : new double[]{.005, 5, 10}) {
            var state = new PortalCrossingState();
            assertNull(sample(state, 0, start));
            assertNotNull(sample(state, 0, -start * 4));
            assertNull(sample(state, 0, -start * 5));
        }
    }
    @Test void startingOnPlaneDoesNotInventAnEntrySide() {
        var state = new PortalCrossingState();
        assertNull(sample(state, 0, 0)); assertNull(sample(state, 0, -.005));
        assertEquals(0, state.stableSide());
        assertNull(sample(state, 0, -.1));
        assertNotNull(sample(state, 0, .1));
    }
}
