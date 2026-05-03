package com.skyeshade.skyesight.client.portal;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;


public final class TemporarySkyesightWorldPanelRenderer {
    private TemporarySkyesightWorldPanelRenderer() {}

    public static void render(
            Camera mainCamera,
            TemporaryPortalFrame portal,
            int colorTextureId
    ) {
        Matrix4f matrix = createPanelMatrix(mainCamera, portal);

        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();

        var modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushMatrix();

        try {
            modelViewStack.identity();
            RenderSystem.applyModelViewMatrix();

            renderBackFaceRed(matrix, portal.width(), portal.height());
            renderFrontFaceTexture(matrix, portal.width(), portal.height(), colorTextureId);
        } finally {
            modelViewStack.popMatrix();
            RenderSystem.applyModelViewMatrix();
        }

        RenderSystem.disableBlend();
        RenderSystem.enableCull();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private static Matrix4f createPanelMatrix(
            Camera mainCamera,
            TemporaryPortalFrame portal
    ) {
        Vec3 cameraPosition = mainCamera.getPosition();

        double relativeX = portal.position().x() - cameraPosition.x();
        double relativeY = portal.position().y() - cameraPosition.y();
        double relativeZ = portal.position().z() - cameraPosition.z();

        Quaternionf inverseCameraRotation = new Quaternionf(mainCamera.rotation()).conjugate();

        return new Matrix4f()
                .rotate(inverseCameraRotation)
                .translate((float) relativeX, (float) relativeY, (float) relativeZ)
                .rotate(portal.rotation());
    }


    private static void renderFrontFaceTexture(
            Matrix4f matrix,
            float width,
            float height,
            int colorTextureId
    ) {
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, colorTextureId);

        RenderSystem.disableBlend();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);

        float halfWidth = width * 0.5F;
        float halfHeight = height * 0.5F;

        var buffer = Tesselator.getInstance().begin(
                VertexFormat.Mode.QUADS,
                DefaultVertexFormat.POSITION_TEX
        );

        buffer.addVertex(matrix, -halfWidth, -halfHeight, 0.001F).setUv(0.0F, 0.0F);
        buffer.addVertex(matrix, halfWidth, -halfHeight, 0.001F).setUv(1.0F, 0.0F);
        buffer.addVertex(matrix, halfWidth, halfHeight, 0.001F).setUv(1.0F, 1.0F);
        buffer.addVertex(matrix, -halfWidth, halfHeight, 0.001F).setUv(0.0F, 1.0F);

        BufferUploader.drawWithShader(buffer.buildOrThrow());
    }

    private static void renderBackFaceRed(
            Matrix4f matrix,
            float width,
            float height
    ) {
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.disableBlend();

        float halfWidth = width * 0.5F;
        float halfHeight = height * 0.5F;

        var buffer = Tesselator.getInstance().begin(
                VertexFormat.Mode.QUADS,
                DefaultVertexFormat.POSITION_COLOR
        );

        buffer.addVertex(matrix, -halfWidth, halfHeight, -0.001F).setColor(255, 0, 0, 255);
        buffer.addVertex(matrix, halfWidth, halfHeight, -0.001F).setColor(255, 0, 0, 255);
        buffer.addVertex(matrix, halfWidth, -halfHeight, -0.001F).setColor(255, 0, 0, 255);
        buffer.addVertex(matrix, -halfWidth, -halfHeight, -0.001F).setColor(255, 0, 0, 255);

        BufferUploader.drawWithShader(buffer.buildOrThrow());
    }
}