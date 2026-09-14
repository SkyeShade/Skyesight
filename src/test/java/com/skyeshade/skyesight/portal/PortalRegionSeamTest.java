package com.skyeshade.skyesight.portal;

import com.skyeshade.skyesight.api.*;
import com.skyeshade.skyesight.client.portal.DirectStencilPortalMath;
import com.skyeshade.skyesight.client.portal.PortalFrame;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PortalRegionSeamTest {
    private static PortalRegionDefinition seam(int visible,int margin) {
        var q=new Quaternionf().rotateX((float)Math.PI/2);
        var windows=new PortalRegionWindowSettings(visible,margin,16,8);
        return new PortalRegionDefinition(ResourceLocation.parse("test:seam"),
                new PortalRegionFrame(Level.OVERWORLD,new Vec3(0,10.5,0),q),
                new PortalRegionFrame(Level.NETHER,new Vec3(0,80.5,0),new Quaternionf(q).mul(new Quaternionf(0,1,0,0))),
                PortalRegionShape.rectangle(-30_000_000,-30_000_000,30_000_000,30_000_000),PortalSidedness.BACK_ONLY,true,
                1024,windows.minimumChunkRadius(),0,0,PortalBehavior.TRAVERSABLE,windows);
    }
    @Test void feetCrossFirstButOnlySweptEyesTriggerDownwardTraversal() {
        var d=seam(128,32);var source=d.source().endpoint("source",d.source().origin(),64);
        var state=new PortalCrossingState();
        assertNull(state.updateEye(source,new Vec3(0,12.62,0),d::containsWorld));
        assertNull(state.updateEye(source,new Vec3(0,11.62,0),d::containsWorld)); // Feet already at 10.
        var eye=new Vec3(.2,10.42,.3);var hit=state.updateEye(source,eye,d::containsWorld);
        assertNotNull(hit);assertEquals(10.5,hit.y,1e-10);
        var pose=PortalTraversalMath.transformAtEyeCrossing(source,d.destination().endpoint("target",d.destination().origin(),64),
                hit,eye,new Vec3(0,1.62,0),new Vec3(.2,-1.2,.3),37,-12);
        assertEquals(0,pose.position().add(0,1.62,0).distanceTo(d.mapPosition(eye)),1e-10);
        assertEquals(78.8,pose.position().y,1e-10);
        assertEquals(37,pose.yaw(),1e-4);assertEquals(-12,pose.pitch(),1e-4);
        assertEquals(-1.2,pose.velocity().y,1e-10);
        assertNull(state.updateEye(source,eye.add(0,-.01,0),d::containsWorld));
    }
    @Test void upwardPillarSweepMapsResidualBeforeRootCrosses() {
        var d=seam(128,32).reversed();var source=d.source().endpoint("source",d.source().origin(),64);
        for(double height:new double[]{1.62,1.27,.4}) {
            var state=new PortalCrossingState();
            Vec3 before=new Vec3(0,79.9,0),eye=new Vec3(0,80.6,0);
            state.updateEye(source,before,d::containsWorld);
            var hit=state.updateEye(source,eye,d::containsWorld);
            assertNotNull(hit);assertEquals(80.5,hit.y,1e-10);
            var pose=PortalTraversalMath.transformAtEyeCrossing(source,d.destination().endpoint("exit",d.destination().origin(),64),
                    hit,eye,new Vec3(0,height,0),new Vec3(0,.7,0),-50,0);
            assertEquals(10.6-height,pose.position().y,1e-10);
            assertEquals(0,d.inversePosition(pose.position().add(0,height,0)).distanceTo(eye),1e-10);
        }
    }
    @Test void poseChangeAloneCanCrossAndDoesNotInventRootVelocity() {
        var d=seam(128,32);var source=d.source().endpoint("source",d.source().origin(),64);
        var root=new Vec3(0,9,0);var state=new PortalCrossingState();
        state.updateEye(source,root.add(0,1.62,0),d::containsWorld);
        var eye=root.add(0,1.27,0);var hit=state.updateEye(source,eye,d::containsWorld);
        assertNotNull(hit);
        var pose=PortalTraversalMath.transformAtEyeCrossing(source,d.destination().endpoint("exit",d.destination().origin(),64),
                hit,eye,eye.subtract(root),Vec3.ZERO,0,0);
        assertEquals(79,pose.position().y,1e-10);assertEquals(Vec3.ZERO,pose.velocity());
    }
    @Test void eyeSweepChecksIntersectionMembershipNotEndPoint() {
        var d=seam(128,32);var source=d.source().endpoint("source",d.source().origin(),64);
        var state=new PortalCrossingState();
        java.util.function.Predicate<Vec3> opening=p->Math.abs(p.x)<1;
        state.updateEye(source,new Vec3(-2,11.5,0),opening);
        assertNotNull(state.updateEye(source,new Vec3(2,9.5,0),opening));
        state=new PortalCrossingState();state.updateEye(source,new Vec3(2,11.5,0),opening);
        assertNull(state.updateEye(source,new Vec3(2,9.5,0),opening));
    }
    @Test void tiltedMappingRecoversUprightRootFromTransformedEye() {
        var d=seam(128,32);var source=d.source().endpoint("source",d.source().origin(),64);
        var target=new PortalRegionFrame(Level.NETHER,new Vec3(4,80.5,8),new Quaternionf().rotateXYZ(.3f,.7f,.2f))
                .endpoint("target",new Vec3(4,80.5,8),64);
        var eye=new Vec3(2,10.2,3);var offset=new Vec3(0,1.27,0);
        var pose=PortalTraversalMath.transformAtEyeCrossing(source,target,new Vec3(2,10.5,3),eye,offset,new Vec3(0,-.8,0),12,34);
        assertEquals(0,pose.position().add(offset).distanceTo(PortalTraversalMath.position(source,target,eye)),1e-10);
        assertTrue(pose.position().distanceTo(PortalTraversalMath.position(source,target,eye.subtract(offset)))>.1);
    }
    @Test void subCentimeterCrossingSharesClientThresholdAndOnPlaneContact() {
        var d=seam(128,32);var source=d.source().endpoint("source",d.source().origin(),64);
        var state=new PortalCrossingState();
        state.updateEye(source,new Vec3(0,10.5005,0),d::containsWorld);
        assertNull(state.updateEye(source,new Vec3(0,10.5,0),d::containsWorld));
        assertTrue(state.hasPendingCrossing());
        assertNotNull(state.updateEye(source,new Vec3(0,10.4995,0),d::containsWorld));
    }
    @Test void halfBlockPlaneAndRenderCameraRemainPreciseFarFromOrigin() {
        var d=seam(384,64);var eye=new Vec3(20_000_000,10.7,12_000_000);
        var window=PortalRegionWindow.at(d,eye);assertNotNull(window);
        assertEquals(10.5,window.center(d).y,1e-8);
        var a=new PortalFrame(window.center(d),d.source().rotation(),384,384);
        var b=new PortalFrame(d.mapPosition(window.center(d)),d.destination().rotation(),384,384);
        var camera=DirectStencilPortalMath.transformPose(eye,new Quaternionf(),a,b);
        assertEquals(0,camera.position().distanceTo(d.mapPosition(eye)),1e-8);
        assertEquals(80.7,camera.position().y,1e-8);
        var clip=DirectStencilPortalMath.exitClipPlane(a,b,eye);
        assertEquals(.001,Math.abs(clip.point().y-80.5),1e-8);
        assertEquals(10.5,d.crossing(eye,eye.add(0,-1,0)).y,1e-8);
    }
    @Test void largeWindowsAndWireRoundTripDoNotInheritFiniteCaps() {
        for(int size:new int[]{1,128,192,256,384,1024,4096}) {
            var d=seam(size,64);var window=PortalRegionWindow.at(d,new Vec3(0,12,0));
            assertNotNull(window);assertEquals(size,window.visibleSize());
            assertTrue(window.warmCells().size()>=window.activeCells());
            var buffer=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),net.minecraft.core.RegistryAccess.EMPTY);
            try {
                var payload=new com.skyeshade.skyesight.network.SkyesightPortalRegionPayload(d.id(),1,d);
                var codec=com.skyeshade.skyesight.network.SkyesightPortalRegionPayload.STREAM_CODEC;
                codec.encode(buffer,payload);assertEquals(payload,codec.decode(buffer));
            } finally {buffer.release();}
        }
        assertThrows(IllegalArgumentException.class,()->new PortalRegionWindowSettings(Integer.MAX_VALUE,32,16,8));
        assertThrows(IllegalArgumentException.class,()->new PortalRegionWindowSettings(128,Integer.MAX_VALUE,Integer.MAX_VALUE,8));
        assertThrows(IllegalArgumentException.class,()->new PortalRegionWindowSettings(128,32,Integer.MAX_VALUE,Integer.MAX_VALUE));
        assertThrows(IllegalArgumentException.class,()->new PortalRegionWindowSettings(0,32,16,8));
    }
    @Test void stripCountCanExceedOldLimitAndStillHasAPacketBudget() {
        var strips=java.util.stream.IntStream.range(0,1024).mapToObj(i->new PortalRegionShape.Strip(i*2,0,i*2+1,1)).toList();
        assertEquals(1024,new PortalRegionShape(strips).strips().size());
        assertThrows(IllegalArgumentException.class,()->new PortalRegionShape(java.util.Collections.nCopies(PortalRegionShape.MAX_STRIPS+1,strips.getFirst())));
    }
}
