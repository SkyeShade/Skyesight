package com.skyeshade.skyesight.client.render.slice;

import org.joml.Vector4f;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class EntityClipBufferTest {
    @Test void complementaryCutsConserveAreaForAxisHorizontalAndDiagonalPlanes() {
        var quad = List.of(new float[]{-1,-1,0,0},new float[]{1,-1,0,1},new float[]{1,1,0,1},new float[]{-1,1,0,0});
        for (var plane : List.of(new Vector4f(1,0,0,0),new Vector4f(0,1,0,0),new Vector4f(1,1,1,.25f))) {
            var positive = EntityClipBuffer.clip(quad,plane);
            var negative = EntityClipBuffer.clip(quad,new Vector4f(plane).negate());
            assertEquals(4,area(positive)+area(negative),1e-6);
            assertTrue(positive.stream().allMatch(v -> distance(v,plane) >= -1e-6));
            assertTrue(negative.stream().allMatch(v -> distance(v,plane) <= 1e-6));
        }
    }

    private static float distance(float[] v,Vector4f p) { return v[0]*p.x+v[1]*p.y+v[2]*p.z+p.w; }
    private static double area(List<float[]> p) {
        double a=0;
        for(int i=0;i<p.size();i++) { var x=p.get(i);var y=p.get((i+1)%p.size());a+=x[0]*y[1]-y[0]*x[1]; }
        return Math.abs(a)/2;
    }
}
