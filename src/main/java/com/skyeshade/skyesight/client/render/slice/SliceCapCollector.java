package com.skyeshade.skyesight.client.render.slice;

import com.skyeshade.skyesight.api.*;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector4f;
import java.util.ArrayList;
import java.util.List;

/** Per-draw intersection data only; cap vertices are emitted to the caller's ordinary buffers. */
final class SliceCapCollector {
    private final SliceCapGeometry geometry = new SliceCapGeometry();
    private final Matrix4f toLocal, toVertices;
    private final Vector3f origin, u, v, normal;
    private final Vector4f equation;
    private final ClipSide side;
    private final SliceRenderOptions options;
    private final int light;
    private int quads;

    SliceCapCollector(SkyesightClipPlane plane, ClipSide side, Vec3 anchor, Matrix4fc transform,
                      SliceRenderOptions options, int light) {
        this.side=side; this.options=options;this.light=light;
        toVertices=new Matrix4f(transform);toLocal=new Matrix4f(transform).invert();
        origin=plane.point().subtract(anchor).toVector3f();
        var n=plane.normal().toVector3f();
        u=new Vector3f(Math.abs(n.y)<.9f ? new Vector3f(0,1,0) : new Vector3f(1,0,0)).cross(n).normalize();
        v=new Vector3f(n).cross(u).normalize();
        normal=new Matrix3f(transform).invert().transpose().transform(new Vector3f(n)
                .mul(side==ClipSide.POSITIVE ? -1 : 1)).normalize();
        equation=new Vector4f(n,-n.dot(origin));
    }

    void collect(List<float[]> quad) {
        if (++quads > 32768) return;
        var local=new ArrayList<Vector3f>(4);
        boolean positive=false,negative=false;
        for(var vertex:quad) {
            var p=toLocal.transformPosition(new Vector3f(vertex[0],vertex[1],vertex[2]));
            local.add(p);float d=distance(p);positive|=d>1e-6;negative|=d< -1e-6;
        }
        // Tangent/coplanar faces do not expose a cut.
        if(!positive || !negative) return;
        var hits=new ArrayList<Vector3f>(4);
        for(int i=0;i<4;i++) {
            var a=local.get(i);var b=local.get((i+1)%4);
            float da=distance(a),db=distance(b);
            if(Math.abs(da)<1e-6) addUnique(hits,new Vector3f(a));
            if((da>0 && db<0)||(da<0 && db>0)) addUnique(hits,new Vector3f(a).lerp(b,da/(da-db)));
        }
        if(hits.size()==2) geometry.add(project(hits.get(0)),project(hits.get(1)));
    }
    private static void addUnique(List<Vector3f> hits,Vector3f point) {
        if(hits.stream().noneMatch(p -> p.distanceSquared(point)<1e-12)) hits.add(point);
    }
    private float distance(Vector3f p) { return equation.x*p.x+equation.y*p.y+equation.z*p.z+equation.w; }
    private SliceCapGeometry.Point project(Vector3f p) {
        var d=new Vector3f(p).sub(origin);return new SliceCapGeometry.Point(d.dot(u),d.dot(v));
    }

    void render(MultiBufferSource output) {
        if (quads > 32768) return;
        List<SliceCapGeometry.Triangle> triangles;
        try { triangles=geometry.triangles(); }
        catch (RuntimeException failure) { return; } // Bad topology never breaks the clipped model.
        if(triangles.isEmpty()) return;
        var target=output.getBuffer(RenderType.entityCutoutNoCull(options.texture()));
        // Local triangles face +plane normal; reverse the positive half's exposed face.
        boolean reverse=side==ClipSide.POSITIVE;
        if(toVertices.determinant()<0) reverse=!reverse;
        for(var triangle:triangles) {
            var a=triangle.a();var b=reverse?triangle.c():triangle.b();var c=reverse?triangle.b():triangle.c();
            for(var p:List.of(a,b,c,c)) {
                var position=new Vector3f(origin).fma((float)p.x(),u).fma((float)p.y(),v);
                toVertices.transformPosition(position);
                target.addVertex(position.x,position.y,position.z).setColor(255,255,255,255)
                        .setUv((float)p.x()*options.tilesPerBlock(),(float)p.y()*options.tilesPerBlock())
                        .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(normal.x,normal.y,normal.z);
            }
        }
    }
}
