package com.skyeshade.skyesight.portal;

import com.skyeshade.skyesight.api.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class PortalRaycastTest {
    final PortalEndpoint a = PortalEndpoint.of("a", Level.OVERWORLD, Vec3.ZERO, Direction.SOUTH, 2, 3);
    final PortalEndpoint b = PortalEndpoint.of("b", Level.NETHER, new Vec3(100, 0, 0), Direction.EAST, 2, 3);
    final SkyesightPortalRaycast.Link link = SkyesightPortalRaycast.Link.rectangle(ResourceLocation.parse("test:a"), 7, a, b, true);
    SkyesightPortalRaycast.WorldAccess world(Double sourceZ, Double targetX) {
        return new SkyesightPortalRaycast.WorldAccess() {
            public boolean available(ResourceKey<Level> d, Vec3 from, Vec3 to) { return true; }
            public Iterable<SkyesightPortalRaycast.Link> portals(ResourceKey<Level> d) { return d.equals(Level.OVERWORLD) ? List.of(link) : List.of(); }
            public HitResult nearestHit(ResourceKey<Level> d, Vec3 from, Vec3 to) {
                Double value = d.equals(Level.OVERWORLD) ? sourceZ : targetX;
                if (value == null) return null;
                double start = d.equals(Level.OVERWORLD) ? from.z : from.x;
                double end = d.equals(Level.OVERWORLD) ? to.z : to.x;
                double t = (value - start) / (end - start);
                if (t < 0 || t > 1 || !Double.isFinite(t)) return null;
                Vec3 hit = from.lerp(to, t);
                return new BlockHitResult(hit, Direction.UP, BlockPos.containing(hit), false);
            }
        };
    }
    SkyesightPortalRaycast.Result trace(Double source, Double target, double reach) {
        return SkyesightPortalRaycast.trace(world(source, target), Level.OVERWORLD,
                new Vec3(0, 0, 2), new Vec3(0, 0, -1), reach, 4);
    }
    @Test void sourceBlockBeforePortalWins() {
        var result = trace(1., 102., 5);
        assertEquals(Level.OVERWORLD, result.dimension()); assertEquals(1, result.distance(), 1e-6);
        assertTrue(result.chain().isEmpty());
    }
    @Test void rotatedDestinationUsesRemainingReachAndBeatsSourceBackstop() {
        var result = trace(-.1, 102., 5);
        assertEquals(SkyesightPortalRaycast.End.HIT, result.end());
        assertEquals(Level.NETHER, result.dimension()); assertEquals(4, result.distance(), 1e-5);
        assertEquals(1, result.remainingDistance(), 1e-5); assertEquals(7, result.chain().getFirst().revision());
        assertEquals(1, result.direction().x, 1e-5);
    }
    @Test void reachDoesNotResetAtPortal() {
        var result = trace(null, 103., 4);
        assertEquals(SkyesightPortalRaycast.End.MISS, result.end()); assertEquals(102, result.position().x, 1e-5);
    }
    @Test void rayOutsideApertureRemainsInSource() {
        var result = SkyesightPortalRaycast.trace(world(null, null), Level.OVERWORLD,
                new Vec3(1.1, 0, 2), new Vec3(0, 0, -1), 5, 4);
        assertEquals(Level.OVERWORLD, result.dimension()); assertTrue(result.chain().isEmpty());
    }
    @Test void startingOnPlaneTransformsWithoutResettingReach() {
        var result = SkyesightPortalRaycast.trace(world(null,102.), Level.OVERWORLD,
                Vec3.ZERO, new Vec3(0,0,-1), 3, 4);
        assertEquals(SkyesightPortalRaycast.End.HIT,result.end());
        assertEquals(2,result.distance(),1e-5); assertEquals(1,result.chain().size());
    }
    @Test void reverseExitAtZeroDistanceDoesNotConsumeAnotherHop() {
        var reverse = SkyesightPortalRaycast.Link.rectangle(ResourceLocation.parse("test:b"),7,b,a,true);
        var result = SkyesightPortalRaycast.trace(new SkyesightPortalRaycast.WorldAccess() {
            public boolean available(ResourceKey<Level> d, Vec3 f, Vec3 t) { return true; }
            public Iterable<SkyesightPortalRaycast.Link> portals(ResourceKey<Level> d) { return List.of(link,reverse); }
            public HitResult nearestHit(ResourceKey<Level> d, Vec3 f, Vec3 t) { return null; }
        },Level.OVERWORLD,new Vec3(0,0,2),new Vec3(0,0,-1),5,4);
        assertEquals(SkyesightPortalRaycast.End.MISS,result.end());
        assertEquals(1,result.chain().size()); assertEquals(103,result.position().x,1e-5);
    }
    @Test void maskedHoleAndDisabledBackfaceCannotIntercept() {
        var masked = new SkyesightPortalRaycast.Link(link.id(),7,a,b,false,p -> p.x > .25);
        var result = SkyesightPortalRaycast.trace(new SkyesightPortalRaycast.WorldAccess() {
            public boolean available(ResourceKey<Level> d, Vec3 f, Vec3 t) { return true; }
            public Iterable<SkyesightPortalRaycast.Link> portals(ResourceKey<Level> d) { return List.of(masked); }
            public HitResult nearestHit(ResourceKey<Level> d, Vec3 f, Vec3 t) { return null; }
        },Level.OVERWORLD,PortalTraversalMath.normal(a).scale(2),PortalTraversalMath.normal(a).scale(-1),5,4);
        assertTrue(result.chain().isEmpty());
        var back = SkyesightPortalRaycast.Link.rectangle(link.id(),7,a,b,false);
        result = SkyesightPortalRaycast.trace(new SkyesightPortalRaycast.WorldAccess() {
            public boolean available(ResourceKey<Level> d, Vec3 f, Vec3 t) { return true; }
            public Iterable<SkyesightPortalRaycast.Link> portals(ResourceKey<Level> d) { return List.of(back); }
            public HitResult nearestHit(ResourceKey<Level> d, Vec3 f, Vec3 t) { return null; }
        },Level.OVERWORLD,PortalTraversalMath.normal(a).scale(-2),PortalTraversalMath.normal(a),5,4);
        assertTrue(result.chain().isEmpty());
    }
    @Test void hopLimitStopsAtIntersectionAndInvalidRaysReject() {
        var result = SkyesightPortalRaycast.trace(world(null, null), Level.OVERWORLD,
                new Vec3(0, 0, 2), new Vec3(0, 0, -1), 5, 0);
        assertEquals(SkyesightPortalRaycast.End.HOP_LIMIT, result.end()); assertEquals(2, result.distance(), 1e-6);
        assertThrows(IllegalArgumentException.class, () -> SkyesightPortalRaycast.trace(world(null,null), Level.OVERWORLD, Vec3.ZERO, Vec3.ZERO, 5, 1));
    }
    @Test void unavailableDestinationReturnsTransformedRayWithoutLoadingIt() {
        var access = world(null, null);
        var result = SkyesightPortalRaycast.trace(new SkyesightPortalRaycast.WorldAccess() {
            public boolean available(ResourceKey<Level> d, Vec3 f, Vec3 t) { return d.equals(Level.OVERWORLD); }
            public Iterable<SkyesightPortalRaycast.Link> portals(ResourceKey<Level> d) { return access.portals(d); }
            public HitResult nearestHit(ResourceKey<Level> d, Vec3 f, Vec3 t) { return null; }
        }, Level.OVERWORLD, new Vec3(0,0,2), new Vec3(0,0,-1), 5, 4);
        assertEquals(SkyesightPortalRaycast.End.UNAVAILABLE, result.end());
        assertEquals(Level.NETHER, result.dimension()); assertEquals(3, result.remainingDistance(), 1e-6);
    }
    @Test void genericBodyFitsAndFastSweepDoesNotNeedContactTick() {
        var state = new PortalCrossingState();
        var body = new AABB(-.45,-1.5,-.45,.45,-.1,.45);
        state.update(a, new Vec3(0,0,10), p -> PortalTraversalMath.fitsBody(a, body.move(p)));
        assertNotNull(state.update(a, new Vec3(0,0,-10), p -> PortalTraversalMath.fitsBody(a, body.move(p))));
        assertFalse(PortalTraversalMath.fitsBody(a, body.move(1,0,0)));
        assertFalse(PortalTraversalMath.fitsBody(a, body.inflate(1)));
    }
    @Test void transformPreservesSpeedAndRoundTripsPose() {
        var first = PortalTraversalMath.transform(a,b,new Vec3(.2,-1,.1),new Vec3(0,.4,-3),180,20);
        var back = PortalTraversalMath.transform(b,a,first.position(),first.velocity(),first.yaw(),first.pitch());
        assertEquals(0, back.position().distanceTo(new Vec3(.2,-1,.1)),1e-5);
        assertEquals(0, back.velocity().distanceTo(new Vec3(0,.4,-3)),1e-5);
        assertEquals(20, back.pitch(),1e-4);
    }
}
