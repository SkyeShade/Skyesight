package com.skyeshade.skyesight.portal;

import com.skyeshade.skyesight.api.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class PortalRegionTest {
    private static PortalRegionDefinition region(int length) {
        var q=new Quaternionf().rotateX((float)Math.PI/2);
        return new PortalRegionDefinition(ResourceLocation.parse("test:region"),
                new PortalRegionFrame(Level.OVERWORLD,Vec3.ZERO,q),
                new PortalRegionFrame(Level.NETHER,new Vec3(0,100,0),new Quaternionf(q).rotateY((float)Math.PI)),
                PortalRegionShape.rectangle(-length/2,-24,length/2,24),PortalSidedness.BACK_ONLY,true,64,8,4);
    }
    @Test void horizontalMappingDoesNotRotateOrMirrorAndPreservesFallingVelocity() {
        var d=region(2000); var p=new Vec3(713,12,-11); var mapped=d.mapPosition(p);
        assertEquals(0,mapped.distanceTo(p.add(0,100,0)),.001);
        assertEquals(0,d.mapVelocity(new Vec3(0,-3,0)).distanceTo(new Vec3(0,-3,0)),1e-5);
        assertEquals(0,d.inversePosition(mapped).distanceTo(p),.001);
        assertEquals(0,d.reversed().mapPosition(mapped).distanceTo(p),.001);
        assertEquals(0,d.source().normal().distanceTo(new Vec3(0,-1,0)),1e-6);
    }
    @Test void horizontalCrossingUsesMembershipAndArrivalSideWithoutWallAssumptions() {
        var d=region(2000);
        assertNotNull(d.crossing(new Vec3(700,2,10),new Vec3(700,-5,10)));
        assertNull(d.crossing(new Vec3(700,2,30),new Vec3(700,-5,30)));
        assertNull(d.crossing(new Vec3(700,-2,10),new Vec3(700,5,10)));
        assertNotNull(d.reversed().crossing(new Vec3(700,98,10),new Vec3(700,105,10)));
    }
    @Test void totalLengthDoesNotChangeLocalMaskOrWatchBudget() {
        PortalRegionWindow previous=null;
        for(int length:new int[]{128,512,2000}) {
            var d=region(length); var window=PortalRegionWindow.at(d,new Vec3(0,4,0));
            assertNotNull(window); assertEquals(32,window.activeCells());
            assertEquals(289,com.skyeshade.skyesight.server.portal.PortalRegionTracker.chunkCountForRadius(d.renderRadiusChunks()));
            if(previous!=null) assertEquals(previous.rectangles(),window.rectangles());
            previous=window;
        }
    }
    @Test void stationaryAndCellMovementKeepWatchSetDifferencesBounded() {
        var a=com.skyeshade.skyesight.server.portal.PortalRegionTracker.buildChunkSet(0,0,8);
        var stationary=com.skyeshade.skyesight.server.portal.PortalRegionTracker.buildChunkSet(0,0,8);
        assertEquals(a,stationary);
        var moving=com.skyeshade.skyesight.server.portal.PortalRegionTracker.buildChunkSet(1,0,8);
        moving.removeAll(a); assertEquals(17,moving.size());
        var d=region(2000); var first=PortalRegionWindow.at(d,new Vec3(-700,4,0));
        var second=PortalRegionWindow.at(d,new Vec3(700,4,0));
        assertEquals(first.activeCells(),second.activeCells());
        assertTrue(second.centerU()-first.centerU()>1300);
    }
    @Test void stripUnionPreservesHolesAndTrueEdges() {
        var shape=new PortalRegionShape(List.of(new PortalRegionShape.Strip(-100,-20,100,-5),
                new PortalRegionShape.Strip(-100,5,100,20)));
        assertFalse(shape.contains(0,0)); assertTrue(shape.contains(0,10)); assertFalse(shape.contains(101,10));
        var d=region(2000); assertNull(PortalRegionWindow.at(d,new Vec3(1100,4,0)));
        assertNull(PortalRegionWindow.at(d,new Vec3(0,100,0)));
    }
    @Test void arbitraryOrientationRoundTripsAndFramesAreImmutable() {
        var q=new Quaternionf().rotateXYZ(.4f,.7f,-.2f);
        var f=new PortalRegionFrame(Level.OVERWORLD,new Vec3(8,9,10),q); q.identity(); f.rotation().identity();
        var p=new Vec3(5,6,7); var local=f.local(p);
        assertEquals(0,f.world(local.x,local.y,local.z).distanceTo(p),1e-5);
        assertEquals(0,f.tangentU().cross(f.tangentV()).distanceTo(f.normal()),1e-5);
    }
    @Test void movingWindowPreservesGenerationAndRejectsMappingChanges() {
        var d=region(2000); var id=ResourceLocation.parse("test:window");
        var a=PortalRegionWindow.at(d,new Vec3(0,4,0)); var b=PortalRegionWindow.at(d,new Vec3(48,4,0));
        try {
            SkyesightPortalRegistry.register(id,d.source().endpoint("a",a.center(d),64),
                    d.destination().endpoint("b",d.mapPosition(a.center(d)),64),PortalRenderSettings.defaults(),
                    true,null,null,"portal_region",true,false);
            long generation=SkyesightPortalRegistry.get(id).generation();
            var source=d.source().endpoint("a",b.center(d),64);
            var target=d.destination().endpoint("b",d.mapPosition(b.center(d)),64);
            SkyesightPortalRegistry.moveRegionWindow(id,source,target,PortalStencilMask.rectangles(b.rectangles()));
            assertEquals(generation,SkyesightPortalRegistry.get(id).generation());
            assertEquals(b.center(d),SkyesightPortalRegistry.get(id).source().center());
            assertThrows(IllegalArgumentException.class,()->SkyesightPortalRegistry.moveRegionWindow(id,source,
                    d.destination().endpoint("b",target.center().add(1,0,0),64),PortalStencilMask.rectangles(b.rectangles())));
        } finally { SkyesightPortalRegistry.remove(id); }
    }
    @Test void regionDefinitionAndRemovalRoundTripOnWire() {
        for(var p:List.of(new com.skyeshade.skyesight.network.SkyesightPortalRegionPayload(region(2000).id(),7,region(2000)),
                new com.skyeshade.skyesight.network.SkyesightPortalRegionPayload(region(2000).id(),8,null))) {
            var buffer=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),net.minecraft.core.RegistryAccess.EMPTY);
            try {
                var codec=com.skyeshade.skyesight.network.SkyesightPortalRegionPayload.STREAM_CODEC;
                codec.encode(buffer,p); assertEquals(p,codec.decode(buffer)); assertEquals(0,buffer.readableBytes());
            } finally { buffer.release(); }
        }
    }
    @Test void visibleMaskMovesContinuouslyWhileWarmCenterHasHysteresis() {
        var d=region(2000);var start=PortalRegionWindow.at(d,new Vec3(0,4,0));
        for(double x:new double[]{7.9,8.1,7.8,8.2}) {
            var next=PortalRegionWindow.at(d,new Vec3(x,4,0),Vec3.ZERO,start);
            assertEquals(x,next.centerU(),1e-4);assertEquals(start.warmU(),next.warmU());
            assertEquals(start.warmCells(),next.warmCells());start=next;
        }
        var moved=PortalRegionWindow.at(d,new Vec3(17,4,0),Vec3.ZERO,start);
        assertEquals(16,moved.warmU());assertTrue(moved.warmCells().size()>=moved.activeCells());
    }
    @Test void velocityLookAheadIsBoundedAndVisibleMaskDoesNotFollowIt() {
        var d=region(2000);var w=PortalRegionWindow.at(d,new Vec3(0,20,0),new Vec3(100,-4,0),null);
        assertEquals(0,w.centerU(),1e-5);assertTrue(Math.abs(w.warmU())<=32);
        assertTrue(w.warmCells().size()<=196);
    }
    @Test void horizontalArrivalRequiresWholeBodyClearance() {
        var exit=region(2000).destination().endpoint("exit",new Vec3(0,100,0),64);
        assertFalse(PortalTraversalMath.clearedExit(exit,new net.minecraft.world.phys.AABB(-.3,99,-.3,.3,100.8,.3)));
        assertTrue(PortalTraversalMath.clearedExit(exit,new net.minecraft.world.phys.AABB(-.3,97,-.3,.3,98.8,.3)));
    }
    @Test void independentObserversNeverGrowASpanningWarmWindow() {
        var d=region(2000);
        var a=PortalRegionWindow.at(d,new Vec3(-500,4,0));
        var b=PortalRegionWindow.at(d,new Vec3(500,4,0));
        for(int i=0;i<40;i++) {
            a=PortalRegionWindow.at(d,new Vec3(-500+i*.4,4,0),new Vec3(.4,0,0),a);
            assertEquals(48,a.warmCells().size());assertEquals(48,b.warmCells().size());
            assertTrue(java.util.Collections.disjoint(a.warmCells(),b.warmCells()));
            assertEquals(496,b.warmU());
        }
    }
    @Test void warmMarginContainsVisibleWindowEvenAtLookAheadAndHysteresisExtremes() {
        var d=region(2000);PortalRegionWindow previous=null;
        for(int i=-100;i<=100;i++) {
            double x=i*.7;var w=PortalRegionWindow.at(d,new Vec3(x,4,0),new Vec3(i%2==0?3:-3,0,0),previous);
            assertTrue(Math.abs(w.warmU()-w.centerU())<=d.windowSettings().warmMargin()+1e-5);
            previous=w;
        }
        assertThrows(IllegalArgumentException.class,()->new PortalRegionWindowSettings(128,16,8,8));
    }
}
