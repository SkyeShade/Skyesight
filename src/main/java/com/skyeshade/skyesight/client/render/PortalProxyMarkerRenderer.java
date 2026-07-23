package com.skyeshade.skyesight.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.skyeshade.skyesight.Skyesight;
import com.skyeshade.skyesight.SkyesightDebugConfig;
import com.skyeshade.skyesight.server.portal.PortalPathProximity;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

import java.util.List;

@EventBusSubscriber(
        modid = Skyesight.MODID,
        value = Dist.CLIENT
)
public final class PortalProxyMarkerRenderer {
    private static final double BOX_HALF_SIZE = 0.18D;
    private static final double AXIS_SIZE = 0.42D;

    private PortalProxyMarkerRenderer() {}

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || event.getPoseStack() == null) {
            return;
        }
        if (net.minecraft.client.Minecraft.getInstance().level == null) {
            return;
        }
        renderMarkers(
                net.minecraft.client.Minecraft.getInstance().level,
                event.getCamera(),
                event.getPoseStack(),
                true
        );
    }

    public static void renderMarkers(Level level, Camera camera, PoseStack poseStack, boolean depthTest) {
        if (!SkyesightDebugConfig.SHOW_PROXY_MARKERS || level == null || camera == null || poseStack == null) {
            return;
        }
        List<PortalPathProximity.ProxyMarker> markers = PortalPathProximity.proxyMarkersForLevel(level);
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
        RenderSystem.lineWidth(3.0F);

        Matrix4f matrix = poseStack.last().pose();
        Vec3 cameraPosition = camera.getPosition();
        var buffer = Tesselator.getInstance().begin(
                VertexFormat.Mode.DEBUG_LINES,
                DefaultVertexFormat.POSITION_COLOR
        );

        for (PortalPathProximity.ProxyMarker marker : markers) {
            drawBox(buffer, matrix, cameraPosition, marker.apparentPosition());
            drawCross(buffer, matrix, cameraPosition, marker.apparentPosition());
        }

        BufferUploader.drawWithShader(buffer.buildOrThrow());
        renderLabels(markers, camera, poseStack);

        RenderSystem.lineWidth(1.0F);
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
        RenderSystem.enableCull();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private static void renderLabels(
            List<PortalPathProximity.ProxyMarker> markers,
            Camera camera,
            PoseStack poseStack
    ) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.font == null || minecraft.renderBuffers() == null) {
            return;
        }
        MultiBufferSource.BufferSource bufferSource = minecraft.renderBuffers().bufferSource();
        Vec3 cameraPosition = camera.getPosition();
        for (PortalPathProximity.ProxyMarker marker : markers) {
            Vec3 position = marker.apparentPosition().add(0.0D, 0.65D, 0.0D).subtract(cameraPosition);
            String label = "proxy " + marker.playerName();
            String view = "view=" + (marker.viewId() == null ? "-" : marker.viewId().getPath());
            poseStack.pushPose();
            poseStack.translate(position.x(), position.y(), position.z());
            poseStack.mulPose(camera.rotation());
            poseStack.scale(-0.025F, -0.025F, 0.025F);
            drawLabelLine(minecraft.font, bufferSource, poseStack, label, 0);
            drawLabelLine(minecraft.font, bufferSource, poseStack, view, 10);
            poseStack.popPose();
        }
        bufferSource.endBatch();
    }

    private static void drawLabelLine(
            Font font,
            MultiBufferSource bufferSource,
            PoseStack poseStack,
            String text,
            int y
    ) {
        float x = -font.width(text) / 2.0F;
        font.drawInBatch(
                text,
                x,
                y,
                0xFF55FFFF,
                false,
                poseStack.last().pose(),
                bufferSource,
                Font.DisplayMode.SEE_THROUGH,
                0,
                0xF000F0
        );
    }

    private static void drawBox(
            com.mojang.blaze3d.vertex.BufferBuilder buffer,
            Matrix4f matrix,
            Vec3 cameraPosition,
            Vec3 center
    ) {
        double s = BOX_HALF_SIZE;
        Vec3 p000 = center.add(-s, -s, -s);
        Vec3 p001 = center.add(-s, -s, s);
        Vec3 p010 = center.add(-s, s, -s);
        Vec3 p011 = center.add(-s, s, s);
        Vec3 p100 = center.add(s, -s, -s);
        Vec3 p101 = center.add(s, -s, s);
        Vec3 p110 = center.add(s, s, -s);
        Vec3 p111 = center.add(s, s, s);

        drawLine(buffer, matrix, cameraPosition, p000, p001, 0, 255, 255, 255);
        drawLine(buffer, matrix, cameraPosition, p001, p101, 0, 255, 255, 255);
        drawLine(buffer, matrix, cameraPosition, p101, p100, 0, 255, 255, 255);
        drawLine(buffer, matrix, cameraPosition, p100, p000, 0, 255, 255, 255);
        drawLine(buffer, matrix, cameraPosition, p010, p011, 0, 255, 255, 255);
        drawLine(buffer, matrix, cameraPosition, p011, p111, 0, 255, 255, 255);
        drawLine(buffer, matrix, cameraPosition, p111, p110, 0, 255, 255, 255);
        drawLine(buffer, matrix, cameraPosition, p110, p010, 0, 255, 255, 255);
        drawLine(buffer, matrix, cameraPosition, p000, p010, 0, 255, 255, 255);
        drawLine(buffer, matrix, cameraPosition, p001, p011, 0, 255, 255, 255);
        drawLine(buffer, matrix, cameraPosition, p100, p110, 0, 255, 255, 255);
        drawLine(buffer, matrix, cameraPosition, p101, p111, 0, 255, 255, 255);
    }

    private static void drawCross(
            com.mojang.blaze3d.vertex.BufferBuilder buffer,
            Matrix4f matrix,
            Vec3 cameraPosition,
            Vec3 center
    ) {
        double s = AXIS_SIZE;
        drawLine(buffer, matrix, cameraPosition, center.add(-s, 0.0D, 0.0D), center.add(s, 0.0D, 0.0D), 255, 64, 255, 255);
        drawLine(buffer, matrix, cameraPosition, center.add(0.0D, -s, 0.0D), center.add(0.0D, s, 0.0D), 255, 64, 255, 255);
        drawLine(buffer, matrix, cameraPosition, center.add(0.0D, 0.0D, -s), center.add(0.0D, 0.0D, s), 255, 64, 255, 255);
    }

    private static void drawLine(
            com.mojang.blaze3d.vertex.BufferBuilder buffer,
            Matrix4f matrix,
            Vec3 cameraPosition,
            Vec3 from,
            Vec3 to,
            int red,
            int green,
            int blue,
            int alpha
    ) {
        addVertex(buffer, matrix, cameraPosition, from, red, green, blue, alpha);
        addVertex(buffer, matrix, cameraPosition, to, red, green, blue, alpha);
    }

    private static void addVertex(
            com.mojang.blaze3d.vertex.BufferBuilder buffer,
            Matrix4f matrix,
            Vec3 cameraPosition,
            Vec3 position,
            int red,
            int green,
            int blue,
            int alpha
    ) {
        buffer.addVertex(
                matrix,
                (float) (position.x() - cameraPosition.x()),
                (float) (position.y() - cameraPosition.y()),
                (float) (position.z() - cameraPosition.z())
        ).setColor(red, green, blue, alpha);
    }
}
