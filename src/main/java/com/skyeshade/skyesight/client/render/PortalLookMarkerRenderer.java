package com.skyeshade.skyesight.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.skyeshade.skyesight.Skyesight;
import com.skyeshade.skyesight.SkyesightDebugConfig;
import com.skyeshade.skyesight.client.portal.PortalLookMarkerDebugData;
import com.skyeshade.skyesight.client.portal.PortalRenderDebugStatus;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;

@EventBusSubscriber(
        modid = Skyesight.MODID,
        value = Dist.CLIENT
)
public final class PortalLookMarkerRenderer {
    private static final double CROSS_SIZE = 0.75D;
    private static final double VERTICAL_HALF_HEIGHT = 3.0D;
    private static final double BASIS_SIZE = 1.25D;

    private PortalLookMarkerRenderer() {}

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || event.getPoseStack() == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        renderMarkers(minecraft.level, event.getCamera(), event.getPoseStack(), true);
    }

    public static void renderMarkers(Level level, Camera camera, PoseStack poseStack, boolean depthTest) {
        if (!SkyesightDebugConfig.SHOW_PORTAL_LOOK_MARKERS || level == null || camera == null || poseStack == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        List<PortalLookMarkerDebugData.PortalLookDebugMarker> markers = markersFor(level, camera);
        if (markers.isEmpty()) {
            return;
        }

        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        if (depthTest) {
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
        } else {
            RenderSystem.disableDepthTest();
        }
        RenderSystem.depthMask(false);
        RenderSystem.lineWidth(4.0F);

        Matrix4f matrix = poseStack.last().pose();
        Vec3 cameraPosition = camera.getPosition();
        var buffer = Tesselator.getInstance().begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);

        for (PortalLookMarkerDebugData.PortalLookDebugMarker marker : markers) {
            drawMarker(buffer, matrix, cameraPosition, marker);
        }

        BufferUploader.drawWithShader(buffer.buildOrThrow());
        renderLabels(minecraft, markers, camera, poseStack);

        RenderSystem.lineWidth(1.0F);
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
        RenderSystem.enableCull();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private static List<PortalLookMarkerDebugData.PortalLookDebugMarker> markersFor(Level level, Camera camera) {
        Minecraft minecraft = Minecraft.getInstance();
        ResourceKey<Level> dimension = level.dimension();
        List<PortalLookMarkerDebugData.PortalLookDebugMarker> result = new ArrayList<>();
        for (PortalLookMarkerDebugData.PortalLookDebugMarker marker :
                PortalRenderDebugStatus.portalLookDebugMarkers(minecraft, camera)) {
            if (dimension.equals(marker.dimension())) {
                result.add(marker);
            }
        }
        return result;
    }

    private static void drawMarker(
            com.mojang.blaze3d.vertex.BufferBuilder buffer,
            Matrix4f matrix,
            Vec3 cameraPosition,
            PortalLookMarkerDebugData.PortalLookDebugMarker marker
    ) {
        Vec3 center = marker.position();
        int[] color = color(marker.kind());
        drawLine(buffer, matrix, cameraPosition, center.add(-CROSS_SIZE, 0.0D, 0.0D), center.add(CROSS_SIZE, 0.0D, 0.0D), color);
        drawLine(buffer, matrix, cameraPosition, center.add(0.0D, -VERTICAL_HALF_HEIGHT, 0.0D), center.add(0.0D, VERTICAL_HALF_HEIGHT, 0.0D), color);
        drawLine(buffer, matrix, cameraPosition, center.add(0.0D, 0.0D, -CROSS_SIZE), center.add(0.0D, 0.0D, CROSS_SIZE), color);
        drawLine(buffer, matrix, cameraPosition, center, center.add(marker.right().scale(BASIS_SIZE)), new int[] {255, 64, 64, 255});
        drawLine(buffer, matrix, cameraPosition, center, center.add(marker.up().scale(BASIS_SIZE)), new int[] {64, 255, 64, 255});
        drawLine(buffer, matrix, cameraPosition, center, center.add(marker.forward().scale(BASIS_SIZE)), new int[] {64, 128, 255, 255});
        if (marker.lineStart() != null) {
            drawLine(buffer, matrix, cameraPosition, marker.lineStart(), center, new int[] {255, 255, 64, 255});
        }
    }

    private static void renderLabels(
            Minecraft minecraft,
            List<PortalLookMarkerDebugData.PortalLookDebugMarker> markers,
            Camera camera,
            PoseStack poseStack
    ) {
        if (minecraft.font == null || minecraft.renderBuffers() == null) {
            return;
        }
        MultiBufferSource.BufferSource bufferSource = minecraft.renderBuffers().bufferSource();
        Vec3 cameraPosition = camera.getPosition();
        for (PortalLookMarkerDebugData.PortalLookDebugMarker marker : markers) {
            Vec3 position = marker.position().add(0.0D, 3.35D, 0.0D).subtract(cameraPosition);
            poseStack.pushPose();
            poseStack.translate(position.x(), position.y(), position.z());
            poseStack.mulPose(camera.rotation());
            poseStack.scale(-0.025F, -0.025F, 0.025F);
            drawLabelLine(minecraft.font, bufferSource, poseStack, marker.kind() + " " + marker.portalName(), 0);
            drawLabelLine(minecraft.font, bufferSource, poseStack, "view=" + marker.viewId().getPath(), 10);
            drawLabelLine(minecraft.font, bufferSource, poseStack, "src=" + marker.source(), 20);
            poseStack.popPose();
        }
        bufferSource.endBatch();
    }

    private static void drawLabelLine(Font font, MultiBufferSource bufferSource, PoseStack poseStack, String text, int y) {
        float x = -font.width(text) / 2.0F;
        font.drawInBatch(
                text,
                x,
                y,
                0xFFFFFF55,
                false,
                poseStack.last().pose(),
                bufferSource,
                Font.DisplayMode.SEE_THROUGH,
                0,
                0xF000F0
        );
    }

    private static int[] color(String kind) {
        if ("render-camera-look-center".equals(kind)) {
            return new int[] {255, 255, 32, 255};
        }
        if ("target-plane-center".equals(kind)) {
            return new int[] {255, 96, 255, 255};
        }
        return new int[] {64, 255, 255, 255};
    }

    private static void drawLine(
            com.mojang.blaze3d.vertex.BufferBuilder buffer,
            Matrix4f matrix,
            Vec3 cameraPosition,
            Vec3 from,
            Vec3 to,
            int[] color
    ) {
        addVertex(buffer, matrix, cameraPosition, from, color);
        addVertex(buffer, matrix, cameraPosition, to, color);
    }

    private static void addVertex(
            com.mojang.blaze3d.vertex.BufferBuilder buffer,
            Matrix4f matrix,
            Vec3 cameraPosition,
            Vec3 position,
            int[] color
    ) {
        buffer.addVertex(
                matrix,
                (float) (position.x() - cameraPosition.x()),
                (float) (position.y() - cameraPosition.y()),
                (float) (position.z() - cameraPosition.z())
        ).setColor(color[0], color[1], color[2], color[3]);
    }
}
