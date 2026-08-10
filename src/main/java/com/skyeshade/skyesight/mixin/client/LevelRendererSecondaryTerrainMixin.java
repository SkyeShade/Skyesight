package com.skyeshade.skyesight.mixin.client;

import com.skyeshade.skyesight.client.render.vanilla.LevelRendererSecondaryTerrainBridge;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.SectionOcclusionGraph;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererSecondaryTerrainMixin implements LevelRendererSecondaryTerrainBridge {
    @Shadow
    private Minecraft minecraft;
    @Shadow
    private ClientLevel level;
    @Shadow
    private SectionOcclusionGraph sectionOcclusionGraph;
    @Shadow
    private ObjectArrayList<SectionRenderDispatcher.RenderSection> visibleSections;
    @Shadow
    private ViewArea viewArea;
    @Shadow
    private SectionRenderDispatcher sectionRenderDispatcher;
    @Shadow
    private int lastCameraSectionX;
    @Shadow
    private int lastCameraSectionY;
    @Shadow
    private int lastCameraSectionZ;
    @Shadow
    private double prevCamX;
    @Shadow
    private double prevCamY;
    @Shadow
    private double prevCamZ;
    @Shadow
    private double prevCamRotX;
    @Shadow
    private double prevCamRotY;
    @Shadow
    private int lastViewDistance;

    @Shadow
    public abstract void allChanged();

    @Shadow
    private void applyFrustum(Frustum frustum) {
    }

    @Shadow
    private void compileSections(Camera camera) {
    }

    @Shadow
    private void renderSectionLayer(
            RenderType renderType,
            double x,
            double y,
            double z,
            Matrix4f frustumMatrix,
            Matrix4f projectionMatrix
    ) {
    }

    @Override
    public void skyesight$setupSecondaryTerrain(Camera camera, Frustum frustum, boolean spectator) {
        if (this.level == null || camera == null || frustum == null) {
            return;
        }
        if (this.minecraft.options.getEffectiveRenderDistance() != this.lastViewDistance
                || this.viewArea == null
                || this.sectionRenderDispatcher == null) {
            this.allChanged();
        }
        if (this.viewArea == null || this.sectionRenderDispatcher == null) {
            return;
        }

        Vec3 cameraPosition = camera.getPosition();
        int sectionX = SectionPos.posToSectionCoord(cameraPosition.x());
        int sectionY = SectionPos.posToSectionCoord(cameraPosition.y());
        int sectionZ = SectionPos.posToSectionCoord(cameraPosition.z());
        if (this.lastCameraSectionX != sectionX
                || this.lastCameraSectionY != sectionY
                || this.lastCameraSectionZ != sectionZ) {
            this.lastCameraSectionX = sectionX;
            this.lastCameraSectionY = sectionY;
            this.lastCameraSectionZ = sectionZ;
            this.viewArea.repositionCamera(cameraPosition.x(), cameraPosition.z());
        }

        this.sectionRenderDispatcher.setCamera(cameraPosition);
        double cameraCellX = Math.floor(cameraPosition.x / 8.0);
        double cameraCellY = Math.floor(cameraPosition.y / 8.0);
        double cameraCellZ = Math.floor(cameraPosition.z / 8.0);
        if (cameraCellX != this.prevCamX || cameraCellY != this.prevCamY || cameraCellZ != this.prevCamZ) {
            this.sectionOcclusionGraph.invalidate();
        }

        this.prevCamX = cameraCellX;
        this.prevCamY = cameraCellY;
        this.prevCamZ = cameraCellZ;

        boolean smartCull = this.minecraft.smartCull;
        BlockPos cameraBlock = camera.getBlockPosition();
        if (spectator && this.level.getBlockState(cameraBlock).isSolidRender(this.level, cameraBlock)) {
            smartCull = false;
        }

        Entity.setViewScale(
                Mth.clamp((double)this.minecraft.options.getEffectiveRenderDistance() / 8.0, 1.0, 2.5)
                        * this.minecraft.options.entityDistanceScaling().get()
        );
        this.sectionOcclusionGraph.update(smartCull, camera, frustum, this.visibleSections);
        double cameraRotX = Math.floor((double)(camera.getXRot() / 2.0F));
        double cameraRotY = Math.floor((double)(camera.getYRot() / 2.0F));
        if (this.sectionOcclusionGraph.consumeFrustumUpdate()
                || cameraRotX != this.prevCamRotX
                || cameraRotY != this.prevCamRotY) {
            this.applyFrustum(LevelRenderer.offsetFrustum(frustum));
            this.prevCamRotX = cameraRotX;
            this.prevCamRotY = cameraRotY;
        }
    }

    @Override
    public void skyesight$compileSecondarySections(Camera camera) {
        this.compileSections(camera);
    }

    @Override
    public void skyesight$renderSecondarySectionLayer(
            RenderType renderType,
            double cameraX,
            double cameraY,
            double cameraZ,
            Matrix4f modelView,
            Matrix4f projection
    ) {
        this.renderSectionLayer(renderType, cameraX, cameraY, cameraZ, modelView, projection);
    }
}
