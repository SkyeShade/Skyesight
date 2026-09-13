package com.skyeshade.skyesight.client.render;

import com.skyeshade.skyesight.api.*;
import com.skyeshade.skyesight.portal.PortalTraversalMath;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4d;
import org.joml.Quaternionf;
import org.joml.Quaterniond;
import org.joml.Vector3f;
import org.joml.Vector3d;
import org.joml.Vector4f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RecursiveProjectionTest {
    @Test void childUsesEffectiveRenderLensNotIndependentCullLensAndDoesNotAccumulateEffects() {
        var lens=SkyesightProjectionMatrices.perspective(90,16f/9,.05f,576)
                .rotateZ(.03f).translate(.01f,-.02f,0);
        var cull=SkyesightProjectionMatrices.perspective(70,16f/9,.05f,144);
        var frame=new SecondaryViewFrame(null,null,960,540,new Matrix4f(lens).m22(-3),new Matrix4f(),cull,null,lens);
        for(int depth=2;depth<=5;depth++) {
            var base=frame.baseProjectionMatrix();
            assertMatrix(lens,base,1e-6f);
            var clipped=new Matrix4f(base).m22(-depth);
            frame=new SecondaryViewFrame(null,null,960,540,clipped,new Matrix4f(),cull,null,base);
            base.identity(); // Neither caller mutation nor child's exit row changes the stored lens.
            assertMatrix(lens,frame.baseProjectionMatrix(),1e-6f);
        }
        assertNotEquals(cull.m11(),frame.baseProjectionMatrix().m11(),.1f);
    }

    @Test void rotatedChainsMatchDirectCameraAndIndependentViewSpacePlane() {
        for(var turn:new Direction[]{Direction.EAST,Direction.SOUTH,Direction.WEST})
        for(double distance:new double[]{.5,2,14}) {
            var a=endpoint("a",new Vec3(.5,81.5,.5),Direction.SOUTH);
            var b=endpoint("b",new Vec3(8.5,83.5,-7.5),turn);
            var c=endpoint("c",new Vec3(12.5,83.5,-7.5),Direction.EAST);
            var d=endpoint("d",new Vec3(24.5,90.5,.5),Direction.SOUTH);
            var root=new Vec3(.5,81.62,.5+distance); var q0=new Quaternionf().rotationX(.21f);
            var p1=PortalTraversalMath.position(a,b,root);
            var q1=PortalTraversalMath.rotation(a.rotation(),b.rotation()).mul(q0,new Quaternionf());
            var p2=PortalTraversalMath.position(c,d,p1);
            var q2=PortalTraversalMath.rotation(c.rotation(),d.rotation()).mul(q1,new Quaternionf());
            var direct=transform(c,d).mul(transform(a,b));
            var reference=direct.transformPosition(new Vector3d(root.x,root.y,root.z));
            assertEquals(p2.x,reference.x,1e-5); assertEquals(p2.y,reference.y,1e-5); assertEquals(p2.z,reference.z,1e-5);
            var basis=new Matrix4d().rotation(new Quaterniond(q0));
            var expectedBasis=new Matrix4d(direct).mul(basis);
            var actual=new Matrix4d().rotation(new Quaterniond(q2));
            for(var axis:new Vector3d[]{new Vector3d(1,0,0),new Vector3d(0,1,0),new Vector3d(0,0,-1)})
                assertTrue(expectedBasis.transformDirection(new Vector3d(axis)).distance(actual.transformDirection(new Vector3d(axis)))<1e-5);

            var lens=SkyesightProjectionMatrices.perspective(90,16f/9,.05f,576);
            var exit=new SkyesightClipPlane(d.center(),PortalTraversalMath.normal(d));
            var child=SkyesightProjectionMatrices.applyObliqueClipPlane(lens,p2,q2,exit);
            // Independent inverse-transpose world-plane reference, including full camera translation.
            var view=new Matrix4f().rotation(new Quaternionf(q2).conjugate()).translate((float)-p2.x,(float)-p2.y,(float)-p2.z);
            var n=exit.normal();
            var plane=new Vector4f((float)n.x,(float)n.y,(float)n.z,(float)-n.dot(exit.point()));
            new Matrix4f(view).invert().transpose().transform(plane);
            if(plane.w>0) plane.negate();
            var corner=new Matrix4f(lens).invert().transform(new Vector4f(plane.x>=0?1:-1,plane.y>=0?1:-1,1,1));
            plane.mul(2/plane.dot(corner));
            var expected=new Matrix4f(lens).setRow(2,plane.sub(lens.getRow(3,new Vector4f())));
            assertMatrix(expected,child,2e-4f);

            var sourcePoint=c.center().add(PortalTraversalMath.rotate(new Vec3(.3,.2,0),c.rotation()));
            var targetPoint=PortalTraversalMath.position(c,d,sourcePoint);
            var before=project(lens,p1,q1,sourcePoint); var after=project(child,p2,q2,targetPoint);
            assertEquals(before.x,after.x,2e-4); assertEquals(before.y,after.y,2e-4);
            assertEquals(-1,after.z,2e-4); // This child's exit, not the parent's, is the near plane.
        }
    }
    private static PortalEndpoint endpoint(String id,Vec3 p,Direction d) {return PortalEndpoint.of(id,p,d);}
    private static Matrix4d transform(PortalEndpoint a,PortalEndpoint b) {
        return new Matrix4d().translation(b.center().x,b.center().y,b.center().z)
                .rotate(new Quaterniond(b.rotation())).rotateY(Math.PI).rotate(new Quaterniond(a.rotation()).conjugate())
                .translate(-a.center().x,-a.center().y,-a.center().z);
    }
    private static Vector3f project(Matrix4f p,Vec3 camera,Quaternionf q,Vec3 point) {
        var relative=point.subtract(camera);
        var v=new Vector3f((float)relative.x,(float)relative.y,(float)relative.z).rotate(new Quaternionf(q).conjugate());
        return p.transformProject(v);
    }
    private static void assertMatrix(Matrix4f a,Matrix4f b,float epsilon) {
        for(int c=0;c<4;c++)for(int r=0;r<4;r++)assertEquals(a.get(c,r),b.get(c,r),epsilon,"matrix["+c+","+r+"]");
    }
}
