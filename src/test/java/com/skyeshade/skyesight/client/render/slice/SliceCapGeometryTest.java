package com.skyeshade.skyesight.client.render.slice;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SliceCapGeometryTest {
    private static void loop(SliceCapGeometry g,double... xy) {
        for(int i=0;i<xy.length;i+=2) {
            int j=(i+2)%xy.length;
            g.add(new SliceCapGeometry.Point(xy[i],xy[i+1]),new SliceCapGeometry.Point(xy[j],xy[j+1]));
        }
    }
    private static double area(SliceCapGeometry g) {
        return g.triangles().stream().mapToDouble(t -> ((t.b().x()-t.a().x())*(t.c().y()-t.a().y())
                -(t.b().y()-t.a().y())*(t.c().x()-t.a().x()))/2).sum();
    }
    @Test void closesConcaveBoundaryWithoutFillingItsNotch() {
        var g=new SliceCapGeometry();loop(g,0,0,3,0,3,1,1,1,1,3,0,3);
        assertEquals(5,area(g),1e-8);
        assertTrue(g.triangles().stream().allMatch(t ->
                (t.a().x()+t.b().x()+t.c().x())/3 <=1 || (t.a().y()+t.b().y()+t.c().y())/3<=1));
    }
    @Test void unionsOverlappingSolidPartsAndDuplicateFeatureDraws() {
        var g=new SliceCapGeometry();loop(g,0,0,2,0,2,2,0,2);loop(g,0,0,2,0,2,2,0,2);
        loop(g,1,1,3,1,3,3,1,3);
        assertEquals(7,area(g),1e-8);
    }
    @Test void leavesSpaceBetweenDisconnectedLimbs() {
        var g=new SliceCapGeometry();loop(g,0,0,1,0,1,1,0,1);loop(g,3,0,4,0,4,1,3,1);
        assertEquals(2,area(g),1e-8);
    }
    @Test void closedPartsTouchingAtCornersStillReceiveCaps() {
        var g=new SliceCapGeometry();loop(g,0,0,1,0,1,1,0,1);loop(g,1,1,2,1,2,2,1,2);
        assertEquals(2,area(g),1e-8);
    }
    @Test void partiallySharedCollinearEdgesAreSplitBeforeFaceWalking() {
        var g=new SliceCapGeometry();loop(g,0,0,4,0,4,1,0,1);loop(g,0,1,1,1,1,4,0,4);
        loop(g,3,1,4,1,4,4,3,4);
        assertEquals(10,area(g),1e-8);
    }
    @Test void unionOfSolidWallsPreservesInteriorHole() {
        var g=new SliceCapGeometry();
        loop(g,0,0,4,0,4,1,0,1);loop(g,0,3,4,3,4,4,0,4);
        loop(g,0,.5,1,.5,1,3.5,0,3.5);loop(g,3,.5,4,.5,4,3.5,3,3.5);
        assertEquals(12,area(g),1e-8);
    }
    @Test void openAndBranchedBoundariesFallBackWithoutTriangles() {
        var g=new SliceCapGeometry();g.add(new SliceCapGeometry.Point(0,0),new SliceCapGeometry.Point(-1,-1));
        assertTrue(g.triangles().isEmpty());
        loop(g,0,0,1,0,1,1,0,1);assertTrue(g.triangles().isEmpty());
    }
    @Test void budgetAndNonfiniteInputFallBack() {
        var g=new SliceCapGeometry();loop(g,0,0,1,0,1,1,0,1);
        g.add(new SliceCapGeometry.Point(Double.NaN,0),new SliceCapGeometry.Point(0,0));
        assertTrue(g.triangles().isEmpty());
    }
}
