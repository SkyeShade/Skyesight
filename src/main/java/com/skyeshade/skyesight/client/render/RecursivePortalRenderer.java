package com.skyeshade.skyesight.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.skyeshade.skyesight.api.*;
import com.skyeshade.skyesight.client.render.remote.PortalNetworkStreaming;
import com.skyeshade.skyesight.client.transition.TraversalPortalClient;
import com.skyeshade.skyesight.client.world.SkyesightClientChunkRequester;
import com.skyeshade.skyesight.client.world.SkyesightVisualWorldManager;
import com.skyeshade.skyesight.portal.PortalTraversalMath;
import com.skyeshade.skyesight.SkyesightClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;

import java.util.*;

/** Nested full scenes with isolated depth targets; composition inherits the parent's stencil. */
public final class RecursivePortalRenderer {
    private static final SecondaryViewContext[] TARGETS = new SecondaryViewContext[5];
    private static final ThreadLocal<PortalRecursionPath> PATH = new ThreadLocal<>();
    private static int remaining = 16;
    public static int renderedScenes, deepestScene;
    public static long frameNanos;
    private RecursivePortalRenderer() {}
    public static boolean isNestedScene() { return PATH.get()!=null; }
    public static void beginFrame() {
        remaining = 16; renderedScenes = deepestScene = 0; frameNanos = 0;
        int limit=SkyesightClientConfig.maxPortalRecursionDepth();
        for(int i=Math.max(1,limit);i<TARGETS.length;i++) if(TARGETS[i]!=null) { TARGETS[i].close(); TARGETS[i]=null; }
    }
    public static void close() {
        for (int i=0; i<TARGETS.length; i++) if(TARGETS[i]!=null) { TARGETS[i].close(); TARGETS[i]=null; }
        PATH.remove();
    }
    public static void invalidate(ResourceLocation id) {
        for (int i=0;i<TARGETS.length;i++) if(TARGETS[i]!=null && id.equals(TARGETS[i].viewId())) {
            TARGETS[i].close(); TARGETS[i]=null;
        }
    }

    static boolean isPairedExit(RegisteredPortalView entered, ResourceLocation candidate) {
        return entered != null && candidate.toString().equals(entered.pairedId());
    }

    public static void render(SecondarySceneFrame parent) {
        var inherited = PATH.get();
        var path = inherited;
        if (path == null) {
            if (parent.view().diagnostics().cameraView()) return;
            var id = parent.viewId();
            if (id == null || SkyesightPortalApi.getPortal(id.toString()) == null) return;
            path = new PortalRecursionPath(List.of(id), SkyesightClientConfig.maxPortalRecursionDepth());
        }
        if (!path.canDescend() || remaining <= 0) return;
        var camera = parent.view().camera();
        var entered = SkyesightPortalApi.getPortal(path.portals().getLast().toString());
        double distance = Math.min(parent.options().terrainRadius()*16.0, SkyesightClientConfig.PORTAL_RENDER_DISTANCE_BLOCKS.get());
        var candidates = SkyesightPortalApi.getAllPortals().stream()
                .filter(p -> p.active() && p.renderSettings().enabled() && p.renderSettings().rendersView())
                // This surface is the current scene's exit boundary, not another portal in it.
                // Keep other/repeated directions eligible so genuine tunnels remain recursive.
                .filter(p -> !isPairedExit(entered,p.id()))
                .filter(p -> p.source().dimension().equals(parent.level().dimension()))
                .filter(p -> p.source().center().distanceToSqr(camera.getPosition()) <= distance*distance)
                .filter(p -> TraversalPortalClient.sides(p).allows(PortalTraversalMath.local(p.source(),camera.getPosition()).z))
                .sorted(Comparator.comparing(p -> p.id().toString())).toList();
        try {
            for (var portal : candidates) {
                if (remaining <= 0) break;
                // Legacy PNG-only masks have no CPU aperture; never silently draw them as rectangles.
                var mask = portal.renderSettings().stencilMask();
                if (mask != null && mask.aperture() == null) continue;
                var polygons = project(parent, portal.source(), mask == null ? PortalAperture.RECTANGLE : mask.aperture());
                if (polygons.isEmpty()) continue;
                remaining--;
                PATH.set(path.through(portal.id()));
                draw(parent, portal, polygons, path.portals().size());
            }
        } finally { if(inherited == null) PATH.remove(); else PATH.set(inherited); }
    }

    private static void draw(SecondarySceneFrame parent, RegisteredPortalView portal, List<List<Vector4f>> polygons, int depth) {
        long started = System.nanoTime();
        var mc = Minecraft.getInstance();
        var p = PortalTraversalMath.position(portal.source(), portal.target(), parent.view().camera().getPosition());
        var rotation = PortalTraversalMath.rotation(portal.source().rotation(), portal.target().rotation())
                .mul(parent.view().camera().rotation(), new Quaternionf());
        var settings = portal.renderSettings();
        // This backend is shared with root draws. Changing its radius per recursion level
        // makes Sodium rebuild its section manager every frame (including compiled terrain).
        int radius = PlayerPerspectiveViews.radius(portal.id(), settings.terrainChunkRadius());
        var block = BlockPos.containing(p);
        if (!PortalNetworkStreaming.ensure(mc,portal.id(),portal.target().dimension())) return;
        SkyesightClientChunkRequester.requestChunksFor(
                portal.id(),portal.target().dimension(),new ChunkPos(block),radius);
        var world = SkyesightVisualWorldManager.getOrCreate(portal.id(), portal.target().dimension());
        if (world == null || !world.environmentReady()) return;
        var saved = SecondarySceneOutputState.capture();
        try {
            var context = TARGETS[depth];
            if (context == null) TARGETS[depth] = context = new SecondaryViewContext();
            context.setViewId(portal.id());
            var target = context.getOrCreateRenderTarget(parent.output().width, parent.output().height);
            target.bindWrite(true);
            GL11.glDisable(GL11.GL_STENCIL_TEST);
            RenderSystem.disableScissor();
            RenderSystem.depthMask(true);
            target.clear(Minecraft.ON_OSX);
            target.bindWrite(true);
            var camera = context.camera();
            camera.setup(world.level(), mc.getCameraEntity(), false, false, parent.partialTick());
            camera.setPositionPublic(p); camera.setRotationPublic(rotation);
            var model = SkyesightCameraMatrices.createModelView(camera);
            // Full-frame targets keep the root aspect. Copy the effective rendering lens
            // before any exit clipping; the independent culling lens can have another FOV.
            // Vanilla zoom/bob/hurt effects are already in this lens, applied exactly once.
            var base = parent.view().baseProjectionMatrix();
            var projection = SkyesightProjectionMatrices.applyObliqueClipPlane(base, camera,
                    new SkyesightClipPlane(portal.target().center(), PortalTraversalMath.normal(portal.target())));
            var frustum = new Frustum(model, base); frustum.prepare(p.x,p.y,p.z);
            var frame = new SecondaryViewFrame(camera,target,target.width,target.height,projection,model,base,frustum,base);
            frame.diagnostics().setPortalInstanceId(portal.id().toString());
            frame.diagnostics().setEntityWatchRegionId(portal.id());
            frame.diagnostics().setRenderToCurrentTarget(true);
            frame.diagnostics().setTerrainChunkRadius(radius);
            frame.diagnostics().setEntityChunkRadius(settings.entityChunkRadius());
            frame.diagnostics().setBlockEntityChunkRadius(settings.blockEntityChunkRadius());
            var options = new SecondarySceneOptions(radius,settings.entityChunkRadius(),settings.blockEntityChunkRadius(),
                    settings.renderTerrain(),settings.renderTranslucent(),settings.renderBlockEntities(),settings.renderEntities(),settings.renderParticles());
            var scene = new SecondarySceneFrame(portal.id(),world.level(),world,frame,context,parent.partialTick(),target,()->{},options);
            // Sky keeps the ordinary lens, so an exit plane cannot slice a sunrise fan.
            var skyFrame = new SecondaryViewFrame(camera,target,target.width,target.height,base,model,base,frustum);
            if(settings.renderSky()) SecondarySceneEnvironmentRenderer.renderBackground(
                    new SecondarySceneFrame(portal.id(),world.level(),world,skyFrame,context,parent.partialTick(),target,()->{},options),context.clouds());
            RenderSystem.setProjectionMatrix(projection,VertexSorting.DISTANCE_TO_ORIGIN);
            RenderSystem.getModelViewStack().identity(); RenderSystem.applyModelViewMatrix();
            SecondarySceneRenderer.renderContents(scene);
            renderedScenes++; deepestScene=Math.max(deepestScene,depth+1);
            saved.restore();
            parent.output().bindWrite(false);
            parent.prepareOutput().run();
            composite(target.getColorTextureId(), polygons);
        } finally { saved.restore(); if(depth==1) frameNanos+=System.nanoTime()-started; }
    }

    /** Clip aperture polygons in homogeneous space before dividing, including the near plane. */
    static List<Vector4f> clip(List<Vector4f> input) {
        var result = input;
        for (int plane=0; plane<6; plane++) {
            var next = new ArrayList<Vector4f>();
            for(int i=0;i<result.size();i++) {
                var a=result.get(i); var b=result.get((i+1)%result.size());
                float da=distance(a,plane), db=distance(b,plane);
                if(da>=0) next.add(a);
                if((da>=0)!=(db>=0)) next.add(new Vector4f(a).lerp(b,da/(da-db)));
            }
            result=next;
        }
        return result.stream().filter(v -> v.w>1e-6f).toList();
    }
    private static float distance(Vector4f v,int plane) {
        return v.w + (plane%2==0?1:-1)*(plane<2?v.x:plane<4?v.y:v.z);
    }
    private static List<List<Vector4f>> project(SecondarySceneFrame scene,PortalEndpoint endpoint,PortalAperture aperture) {
        var matrix=scene.view().projectionMatrix().mul(scene.view().modelViewMatrix());
        var result=new ArrayList<List<Vector4f>>();
        for(var r:aperture.rectangles()) {
            var polygon=new ArrayList<Vector4f>();
            for(double[] uv:new double[][]{{r.left(),r.top()},{r.right(),r.top()},{r.right(),r.bottom()},{r.left(),r.bottom()}}) {
                var local=new Vec3((uv[0]-.5)*endpoint.width(),(.5-uv[1])*endpoint.height(),0);
                var p=endpoint.center().add(PortalTraversalMath.rotate(local,endpoint.rotation())).subtract(scene.view().camera().getPosition());
                polygon.add(matrix.transform(new Vector4f((float)p.x,(float)p.y,(float)p.z,1)));
            }
            var clipped=clip(polygon); if(clipped.size()>=3) result.add(clipped);
        }
        return result;
    }
    private static void composite(int texture,List<List<Vector4f>> polygons) {
        RenderSystem.setProjectionMatrix(new Matrix4f(),VertexSorting.ORTHOGRAPHIC_Z);
        RenderSystem.getModelViewStack().identity(); RenderSystem.applyModelViewMatrix();
        RenderSystem.setShader(GameRenderer::getPositionTexShader); RenderSystem.setShaderTexture(0,texture);
        RenderSystem.setShaderColor(1,1,1,1); RenderSystem.disableBlend(); RenderSystem.disableCull();
        RenderSystem.enableDepthTest(); RenderSystem.depthFunc(GL11.GL_LEQUAL); RenderSystem.depthMask(true);
        var buffer=Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES,DefaultVertexFormat.POSITION_TEX);
        for(var polygon:polygons) for(int i=1;i<polygon.size()-1;i++) for(var v:List.of(polygon.getFirst(),polygon.get(i),polygon.get(i+1))) {
            float x=v.x/v.w,y=v.y/v.w,z=v.z/v.w;
            buffer.addVertex(x,y,z).setUv((x+1)*.5f,(y+1)*.5f);
        }
        BufferUploader.drawWithShader(buffer.buildOrThrow());
    }
}
