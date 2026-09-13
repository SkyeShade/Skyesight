package com.skyeshade.skyesight.client.render.entity;

import com.skyeshade.skyesight.api.PortalEndpoint;
import com.skyeshade.skyesight.client.render.slice.EntityClipBuffer;
import com.skyeshade.skyesight.api.SkyesightPortalRaycast;
import com.skyeshade.skyesight.portal.PortalTraversalMath;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector4f;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PortalEntityClipBufferTest {
    @Test
    void sourceKeepsFeatureGeometryOutsideApertureWithoutOverlappingPieces() {
        var quad = List.of(new float[]{-2,-1,0},new float[]{2,-1,0},new float[]{2,1,0},new float[]{-2,1,0});
        var aperture = List.of(new Vector4f(1,0,0,1),new Vector4f(-1,0,0,1));
        var remainder = EntityClipBuffer.subtract(quad, aperture);
        assertEquals(2, remainder.size());
        double area = remainder.stream().mapToDouble(PortalEntityClipBufferTest::area).sum();
        assertEquals(4,area,1e-6);
        assertTrue(remainder.stream().flatMap(List::stream).allMatch(v -> Math.abs(v[0]) >= 1));
    }

    private static double area(List<float[]> polygon) {
        double sum = 0;
        for(int i=0;i<polygon.size();i++) {
            var a=polygon.get(i); var b=polygon.get((i+1)%polygon.size());
            sum += a[0]*b[1]-b[0]*a[1];
        }
        return Math.abs(sum)/2;
    }

    @Test
    void featureBroadPhaseAllowsPartialApertureOverlapButNotWalkingBesideIt() {
        var source = PortalEndpoint.of("a",Level.OVERWORLD,
                Vec3.ZERO,Direction.SOUTH,2,3);
        var link = SkyesightPortalRaycast.Link.rectangle(ResourceLocation.parse("test:a"),1,source,source,true);
        assertTrue(PortalEntitySplicing.overlapsAperture(link,new AABB(.8,-.5,-1,2,.5,1)));
        assertFalse(PortalEntitySplicing.overlapsAperture(link,new AABB(1.1,-.5,-1,2,.5,1)));
    }

    @Test
    void oppositeClipHalvesMeetAtCanonicalTransformedPlane() {
        var source = PortalEndpoint.of("a", Level.OVERWORLD,
                new Vec3(3, 80, 4), Direction.SOUTH, 2, 3);
        for (var facing : List.of(Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST)) {
            var target = PortalEndpoint.of("b", Level.NETHER,
                    new Vec3(1000, 70, -7), facing, 2, 3);
            var link = SkyesightPortalRaycast.Link.rectangle(ResourceLocation.parse("test:pair"), 1, source, target, true);
            for (int side : new int[]{-1, 1}) {
                var split = new PortalEntitySplicing.Split(null, link, source.center(), side);
                var sourceBeyond = source.center().add(PortalTraversalMath.normal(source).scale(-side * .1));
                var mapped = PortalTraversalMath.position(source, target, sourceBeyond);
                assertEquals(target.center(), split.transformedPosition());
                assertTrue(sourceBeyond.subtract(split.sourcePlane().point()).dot(split.sourcePlane().normal()) < 0);
                assertTrue(mapped.subtract(split.destinationPlane().point()).dot(split.destinationPlane().normal()) > 0);
            }
        }
    }

    @Test
    void cutsQuadAndInterpolatesTextureCoordinates() {
        var quad = List.of(new float[]{-1, -1, 0, 0}, new float[]{1, -1, 0, 1}, new float[]{1, 1, 0, 1}, new float[]{-1, 1, 0, 0});
        var clipped = EntityClipBuffer.clip(quad, new Vector4f(1, 0, 0, 0));
        assertEquals(4, clipped.size());
        assertTrue(clipped.stream().allMatch(v -> v[0] >= 0));
        assertEquals(2, clipped.stream().filter(v -> v[0] == 0 && v[3] == .5f).count());
        assertTrue(EntityClipBuffer.clip(quad, new Vector4f(1, 0, 0, -2)).isEmpty());
    }
}
