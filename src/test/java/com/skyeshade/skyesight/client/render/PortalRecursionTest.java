package com.skyeshade.skyesight.client.render;
import com.skyeshade.skyesight.api.PortalEndpoint;
import com.skyeshade.skyesight.api.PortalRenderSettings;
import com.skyeshade.skyesight.api.RegisteredPortalView;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector4f;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PortalRecursionTest {
    @Test void pairedExitIsNotAChildButRepeatedDirectionAndOtherPairsAre() {
        var a=ResourceLocation.parse("test:a"); var b=ResourceLocation.parse("test:b");
        var endpoint=PortalEndpoint.of("a",Vec3.ZERO,Direction.NORTH);
        var entered=new RegisteredPortalView(a,endpoint,endpoint,
                PortalRenderSettings.defaults(),true,b.toString(),"pair","test",true,1,false);
        assertTrue(RecursivePortalRenderer.isPairedExit(entered,b));
        assertFalse(RecursivePortalRenderer.isPairedExit(entered,a));
        assertFalse(RecursivePortalRenderer.isPairedExit(entered,ResourceLocation.parse("test:c")));
        assertFalse(RecursivePortalRenderer.isPairedExit(null,b));
    }
    @Test void loopsTerminateAtEveryConfiguredDepthAndHardCap() {
        var a=ResourceLocation.parse("test:a");
        for(int limit=0;limit<=9;limit++) {
            var root=new PortalRecursionPath(List.of(a),limit); var path=root;
            while(path.canDescend()) path=path.through(a);
            assertEquals(Math.max(1,Math.min(limit,5)),path.portals().size());
            assertEquals(1,root.portals().size());
            var end=path; assertThrows(IllegalStateException.class,()->end.through(a));
        }
    }
    @Test void apertureCrossingNearPlaneRemainsFiniteAndClipped() {
        var result=RecursivePortalRenderer.clip(List.of(new Vector4f(-1,-1,-2,1),new Vector4f(1,-1,0,1),new Vector4f(1,1,0,1),new Vector4f(-1,1,-2,1)));
        assertTrue(result.size()>=3);
        assertTrue(result.stream().allMatch(v->v.w>0 && v.z>=-v.w && Math.abs(v.x)<=v.w && Math.abs(v.y)<=v.w));
    }
}
