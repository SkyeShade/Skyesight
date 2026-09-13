package com.skyeshade.skyesight.api;

import com.skyeshade.skyesight.client.render.slice.EntityClipContext;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SkyesightClipPlaneTest {
    @Test void normalizedSidesAndInvalidInputs() {
        var plane = new SkyesightClipPlane(new Vec3(10,20,30), new Vec3(2,0,0));
        assertEquals(3, plane.signedDistance(new Vec3(13,0,0)), 1e-8);
        assertTrue(plane.retains(plane.point(), ClipSide.POSITIVE));
        assertTrue(plane.retains(plane.point(), ClipSide.NEGATIVE));
        assertTrue(plane.retains(Vec3.ZERO, ClipSide.NONE));
        assertEquals(ClipSide.NEGATIVE, ClipSide.POSITIVE.opposite());
        assertThrows(IllegalArgumentException.class, () -> new SkyesightClipPlane(Vec3.ZERO, Vec3.ZERO));
        assertThrows(IllegalArgumentException.class, () -> new SkyesightClipPlane(new Vec3(Double.NaN,0,0), new Vec3(1,0,0)));
    }

    @Test void affineTransformPreservesSideWithNonuniformScaleAndRotation() {
        var plane = new SkyesightClipPlane(new Vec3(2,3,4), new Vec3(1,1,1));
        var transform = new Matrix4f().translation(10,-20,30).rotateY(.8f).scale(2,3,.5f);
        var mapped = plane.transformed(transform);
        for (int side : new int[]{-1,1}) {
            var p = transform.transformPosition(plane.point().add(plane.normal().scale(side)).toVector3f());
            assertTrue(mapped.signedDistance(new Vec3(p.x,p.y,p.z)) * side > 0);
        }
        assertThrows(IllegalArgumentException.class, () -> plane.transformed(new Matrix4f().scale(0)));
    }

    @Test void cameraRelativeEquationMatchesVertexTransformAtLargeCoordinates() {
        var camera = new Vec3(20_000_000,70,-20_000_000);
        var plane = new SkyesightClipPlane(camera.add(.5,0,0), new Vec3(1,1,0));
        var transform = new Matrix4f().rotateZ(.7f).translate(3,4,5);
        for (var side : new ClipSide[]{ClipSide.POSITIVE,ClipSide.NEGATIVE}) {
            var equation = EntityClipContext.vertexPlane(plane, side, camera, transform);
            var vertex = transform.transform(new Vector4f(.7f,.3f,-.2f,1));
            double expected = plane.signedDistance(camera.add(.7,.3,-.2)) * (side == ClipSide.POSITIVE ? 1 : -1);
            assertEquals(expected, equation.dot(vertex), 1e-6);
        }
    }
}
