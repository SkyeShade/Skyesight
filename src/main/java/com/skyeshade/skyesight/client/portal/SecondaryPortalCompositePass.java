package com.skyeshade.skyesight.client.portal;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import com.skyeshade.skyesight.api.PortalStencilMask;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.lwjgl.opengl.GL11;

import static com.skyeshade.skyesight.client.portal.DirectStencilPortalRenderConfig.PORTAL_APERTURE_EDGE_INSET_BLOCKS;

public final class SecondaryPortalCompositePass {
    private static final float PORTAL_Z_OFFSET = 0.001F;

    private SecondaryPortalCompositePass() {}

    public static void render(
            PoseStack poseStack,
            Camera camera,
            PortalFrame portal,
            int colorTextureId
    ) {
        Vec3 cameraPosition = camera.getPosition();
        Vec3 portalPosition = portal.position();

        poseStack.pushPose();

        try {
            poseStack.translate(
                    portalPosition.x() - cameraPosition.x(),
                    portalPosition.y() - cameraPosition.y(),
                    portalPosition.z() - cameraPosition.z()
            );
            poseStack.mulPose(new Quaternionf(portal.rotation()));

            RenderSystem.setShader(GameRenderer::getPositionTexShader);
            RenderSystem.setShaderTexture(0, colorTextureId);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.disableBlend();
            RenderSystem.disableCull();

            Matrix4f matrix = poseStack.last().pose();
            float halfWidth = portal.width() * 0.5F;
            float halfHeight = portal.height() * 0.5F;

            var buffer = Tesselator.getInstance().begin(
                    VertexFormat.Mode.QUADS,
                    DefaultVertexFormat.POSITION_TEX
            );

            buffer.addVertex(matrix, -halfWidth, -halfHeight, PORTAL_Z_OFFSET).setUv(0.0F, 0.0F);
            buffer.addVertex(matrix, halfWidth, -halfHeight, PORTAL_Z_OFFSET).setUv(1.0F, 0.0F);
            buffer.addVertex(matrix, halfWidth, halfHeight, PORTAL_Z_OFFSET).setUv(1.0F, 1.0F);
            buffer.addVertex(matrix, -halfWidth, halfHeight, PORTAL_Z_OFFSET).setUv(0.0F, 1.0F);

            BufferUploader.drawWithShader(buffer.buildOrThrow());
        } finally {
            poseStack.popPose();

            RenderSystem.depthMask(true);
            RenderSystem.disableBlend();
            RenderSystem.enableCull();
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }

    public static StencilResult renderStencilScreenTexture(
            PoseStack poseStack,
            Camera camera,
            PortalFrame portal,
            int colorTextureId,
            int stencilBits
    ) {
        if (stencilBits <= 0) {
            return new StencilResult(true, false, true, stencilBits, "stencil unavailable");
        }

        Matrix4f previousProjection = new Matrix4f(RenderSystem.getProjectionMatrix());
        VertexSorting previousVertexSorting = RenderSystem.getVertexSorting();
        var modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushMatrix();

        try {
            GL11.glEnable(GL11.GL_STENCIL_TEST);
            RenderSystem.clearStencil(0);
            RenderSystem.clear(GL11.GL_STENCIL_BUFFER_BIT, Minecraft.ON_OSX);

            RenderSystem.stencilMask(0xFF);
            RenderSystem.stencilFunc(GL11.GL_ALWAYS, 1, 0xFF);
            RenderSystem.stencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_REPLACE);
            RenderSystem.colorMask(false, false, false, false);
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.disableBlend();
            RenderSystem.disableCull();
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

            drawPortalMask(poseStack, camera, portal);

            RenderSystem.colorMask(true, true, true, true);
            RenderSystem.stencilMask(0x00);
            RenderSystem.stencilFunc(GL11.GL_EQUAL, 1, 0xFF);
            RenderSystem.stencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.disableBlend();
            RenderSystem.disableCull();
            RenderSystem.setShader(GameRenderer::getPositionTexShader);
            RenderSystem.setShaderTexture(0, colorTextureId);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

            drawFullscreenTexture(colorTextureId);

            return new StencilResult(true, true, false, stencilBits, "");
        } catch (RuntimeException exception) {
            return new StencilResult(true, false, true, stencilBits, exception.getClass().getSimpleName() + ": " + exception.getMessage());
        } finally {
            GL11.glDisable(GL11.GL_STENCIL_TEST);
            RenderSystem.stencilMask(0xFF);
            RenderSystem.colorMask(true, true, true, true);

            modelViewStack.popMatrix();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(previousProjection, previousVertexSorting);

            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            RenderSystem.disableBlend();
            RenderSystem.enableCull();
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }

    public static StencilResult beginStencilAperture(
            PoseStack poseStack,
            Camera camera,
            PortalFrame portal,
            int stencilBits
    ) {
        if (stencilBits <= 0) {
            return new StencilResult(true, false, true, stencilBits, "stencil unavailable");
        }

        try {
            GL11.glEnable(GL11.GL_STENCIL_TEST);
            RenderSystem.clearStencil(0);
            RenderSystem.clear(GL11.GL_STENCIL_BUFFER_BIT, Minecraft.ON_OSX);

            RenderSystem.stencilMask(0xFF);
            RenderSystem.stencilFunc(GL11.GL_ALWAYS, 1, 0xFF);
            RenderSystem.stencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_REPLACE);
            RenderSystem.colorMask(false, false, false, false);
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.disableBlend();
            RenderSystem.disableCull();
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

            drawPortalMask(poseStack, camera, portal);

            RenderSystem.colorMask(true, true, true, true);
            RenderSystem.stencilMask(0x00);
            RenderSystem.stencilFunc(GL11.GL_EQUAL, 1, 0xFF);
            RenderSystem.stencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.disableBlend();
            RenderSystem.disableCull();
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

            return new StencilResult(true, true, false, stencilBits, "");
        } catch (RuntimeException exception) {
            restoreStencilState();
            return new StencilResult(true, false, true, stencilBits, exception.getClass().getSimpleName() + ": " + exception.getMessage());
        }
    }

    public static StencilResult writeStencilApertureMask(
            PoseStack poseStack,
            Camera camera,
            PortalFrame portal,
            int stencilBits
    ) {
        return writeStencilApertureMask(poseStack, camera, portal, stencilBits, 1, true);
    }

    public static StencilResult writeStencilApertureMask(
            PoseStack poseStack,
            Camera camera,
            PortalFrame portal,
            int stencilBits,
            int stencilRef,
            boolean clearStencil
    ) {
        return writeStencilApertureMask(
                poseStack,
                camera,
                portal,
                stencilBits,
                stencilRef,
                clearStencil,
                true,
                false
        );
    }

    public static StencilResult writeStencilApertureMask(
            PoseStack poseStack,
            Camera camera,
            PortalFrame portal,
            int stencilBits,
            int stencilRef,
            boolean clearStencil,
            boolean depthTest,
            boolean writePortalDepth
    ) {
        return writeStencilApertureMask(
                poseStack,
                camera,
                portal,
                stencilBits,
                stencilRef,
                clearStencil,
                depthTest,
                writePortalDepth,
                null,
                null
        );
    }

    public static StencilResult writeStencilApertureMask(
            PoseStack poseStack,
            Camera camera,
            PortalFrame portal,
            int stencilBits,
            int stencilRef,
            boolean clearStencil,
            boolean depthTest,
            boolean writePortalDepth,
            PortalStencilMask stencilMask,
            ResourceLocation viewId
    ) {
        if (stencilBits <= 0) {
            return new StencilResult(true, false, true, stencilBits, "stencil unavailable");
        }

        try {
            GL11.glEnable(GL11.GL_STENCIL_TEST);

            if (clearStencil) {
                RenderSystem.clearStencil(0);
                RenderSystem.clear(GL11.GL_STENCIL_BUFFER_BIT, Minecraft.ON_OSX);
            }

            RenderSystem.stencilMask(0xFF);
            RenderSystem.stencilFunc(GL11.GL_ALWAYS, stencilRef, 0xFF);
            RenderSystem.stencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_REPLACE);
            RenderSystem.colorMask(false, false, false, false);
            if (depthTest) {
                RenderSystem.enableDepthTest();
            } else {
                RenderSystem.disableDepthTest();
            }
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
            RenderSystem.depthMask(writePortalDepth);
            RenderSystem.disableBlend();
            RenderSystem.disableCull();
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

            drawPortalMask(poseStack, camera, portal, stencilMask, viewId);

            GL11.glDisable(GL11.GL_STENCIL_TEST);
            RenderSystem.stencilMask(0xFF);
            RenderSystem.colorMask(true, true, true, true);
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            RenderSystem.disableBlend();
            RenderSystem.enableCull();
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

            return new StencilResult(true, true, false, stencilBits, "");
        } catch (RuntimeException exception) {
            restoreStencilState();
            return new StencilResult(true, false, true, stencilBits, exception.getClass().getSimpleName() + ": " + exception.getMessage());
        }
    }

    public static StencilResult beginExistingStencilApertureRead(int stencilBits) {
        return beginExistingStencilApertureRead(stencilBits, 1);
    }

    public static StencilResult beginExistingStencilApertureRead(int stencilBits, int stencilRef) {
        if (stencilBits <= 0) {
            return new StencilResult(true, false, true, stencilBits, "stencil unavailable");
        }

        try {
            GL11.glEnable(GL11.GL_STENCIL_TEST);
            RenderSystem.colorMask(true, true, true, true);
            RenderSystem.stencilMask(0x00);
            RenderSystem.stencilFunc(GL11.GL_EQUAL, stencilRef, 0xFF);
            RenderSystem.stencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.disableBlend();
            RenderSystem.disableCull();
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

            return new StencilResult(true, true, false, stencilBits, "");
        } catch (RuntimeException exception) {
            restoreStencilState();
            return new StencilResult(true, false, true, stencilBits, exception.getClass().getSimpleName() + ": " + exception.getMessage());
        }
    }

    public static void restoreStencilState() {
        GL11.glDisable(GL11.GL_STENCIL_TEST);
        RenderSystem.stencilMask(0xFF);
        RenderSystem.colorMask(true, true, true, true);
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
        RenderSystem.enableCull();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    public static void drawPortalCornerMarkers(
            PoseStack poseStack,
            Camera camera,
            PortalFrame portal
    ) {
        Vec3 cameraPosition = camera.getPosition();
        Vec3 portalPosition = portal.position();

        poseStack.pushPose();

        try {
            poseStack.translate(
                    portalPosition.x() - cameraPosition.x(),
                    portalPosition.y() - cameraPosition.y(),
                    portalPosition.z() - cameraPosition.z()
            );
            poseStack.mulPose(new Quaternionf(portal.rotation()));

            RenderSystem.setShader(GameRenderer::getPositionColorShader);
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.disableBlend();
            RenderSystem.disableCull();
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

            Matrix4f matrix = poseStack.last().pose();
            float halfWidth = portal.width() * 0.5F;
            float halfHeight = portal.height() * 0.5F;
            float marker = 0.055F;

            var buffer = Tesselator.getInstance().begin(
                    VertexFormat.Mode.QUADS,
                    DefaultVertexFormat.POSITION_COLOR
            );

            addMarker(buffer, matrix, -halfWidth, -halfHeight, marker, 255, 0, 0, 255);
            addMarker(buffer, matrix, halfWidth, -halfHeight, marker, 0, 255, 0, 255);
            addMarker(buffer, matrix, halfWidth, halfHeight, marker, 0, 64, 255, 255);
            addMarker(buffer, matrix, -halfWidth, halfHeight, marker, 255, 255, 0, 255);

            BufferUploader.drawWithShader(buffer.buildOrThrow());
        } finally {
            poseStack.popPose();

            RenderSystem.depthMask(true);
            RenderSystem.disableBlend();
            RenderSystem.enableCull();
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }

    public static void drawStencilMagentaProof() {
        Minecraft minecraft = Minecraft.getInstance();
        int width = minecraft.getWindow().getWidth();
        int height = minecraft.getWindow().getHeight();

        Matrix4f previousProjection = new Matrix4f(RenderSystem.getProjectionMatrix());
        VertexSorting previousVertexSorting = RenderSystem.getVertexSorting();
        var modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushMatrix();

        try {
            Matrix4f overlayProjection = new Matrix4f().setOrtho(
                    0.0F,
                    width,
                    height,
                    0.0F,
                    -1.0F,
                    1.0F
            );

            RenderSystem.setProjectionMatrix(overlayProjection, VertexSorting.ORTHOGRAPHIC_Z);
            modelViewStack.identity();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.disableBlend();
            RenderSystem.disableCull();
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

            Matrix4f matrix = new Matrix4f();
            var buffer = Tesselator.getInstance().begin(
                    VertexFormat.Mode.QUADS,
                    DefaultVertexFormat.POSITION_COLOR
            );

            buffer.addVertex(matrix, 0.0F, height, 0.0F).setColor(255, 0, 255, 255);
            buffer.addVertex(matrix, width, height, 0.0F).setColor(255, 0, 255, 255);
            buffer.addVertex(matrix, width, 0.0F, 0.0F).setColor(255, 0, 255, 255);
            buffer.addVertex(matrix, 0.0F, 0.0F, 0.0F).setColor(255, 0, 255, 255);

            BufferUploader.drawWithShader(buffer.buildOrThrow());
        } finally {
            modelViewStack.popMatrix();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(previousProjection, previousVertexSorting);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }

    public static void fillStencilApertureColor(float red, float green, float blue, float alpha) {
        Minecraft minecraft = Minecraft.getInstance();
        int width = minecraft.getWindow().getWidth();
        int height = minecraft.getWindow().getHeight();

        Matrix4f previousProjection = new Matrix4f(RenderSystem.getProjectionMatrix());
        VertexSorting previousVertexSorting = RenderSystem.getVertexSorting();
        var modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushMatrix();

        try {
            Matrix4f overlayProjection = new Matrix4f().setOrtho(
                    0.0F,
                    width,
                    height,
                    0.0F,
                    -1.0F,
                    1.0F
            );

            RenderSystem.setProjectionMatrix(overlayProjection, VertexSorting.ORTHOGRAPHIC_Z);
            modelViewStack.identity();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.disableBlend();
            RenderSystem.disableCull();
            RenderSystem.colorMask(true, true, true, true);
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

            Matrix4f matrix = new Matrix4f();
            var buffer = Tesselator.getInstance().begin(
                    VertexFormat.Mode.QUADS,
                    DefaultVertexFormat.POSITION_COLOR
            );

            int r = Math.round(red * 255.0F);
            int g = Math.round(green * 255.0F);
            int b = Math.round(blue * 255.0F);
            int a = Math.round(alpha * 255.0F);

            buffer.addVertex(matrix, 0.0F, height, 0.0F).setColor(r, g, b, a);
            buffer.addVertex(matrix, width, height, 0.0F).setColor(r, g, b, a);
            buffer.addVertex(matrix, width, 0.0F, 0.0F).setColor(r, g, b, a);
            buffer.addVertex(matrix, 0.0F, 0.0F, 0.0F).setColor(r, g, b, a);

            BufferUploader.drawWithShader(buffer.buildOrThrow());
        } finally {
            modelViewStack.popMatrix();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(previousProjection, previousVertexSorting);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }

    public static void clearStencilApertureDepthToFar() {
        Matrix4f previousProjection = new Matrix4f(RenderSystem.getProjectionMatrix());
        VertexSorting previousVertexSorting = RenderSystem.getVertexSorting();
        var modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushMatrix();

        try {
            Matrix4f identityProjection = new Matrix4f().identity();
            RenderSystem.setProjectionMatrix(identityProjection, VertexSorting.ORTHOGRAPHIC_Z);
            modelViewStack.identity();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.colorMask(false, false, false, false);
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(GL11.GL_ALWAYS);
            RenderSystem.depthMask(true);
            RenderSystem.disableBlend();
            RenderSystem.disableCull();
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

            Matrix4f matrix = new Matrix4f();
            var buffer = Tesselator.getInstance().begin(
                    VertexFormat.Mode.QUADS,
                    DefaultVertexFormat.POSITION_COLOR
            );

            buffer.addVertex(matrix, -1.0F, -1.0F, 1.0F).setColor(255, 255, 255, 255);
            buffer.addVertex(matrix, 1.0F, -1.0F, 1.0F).setColor(255, 255, 255, 255);
            buffer.addVertex(matrix, 1.0F, 1.0F, 1.0F).setColor(255, 255, 255, 255);
            buffer.addVertex(matrix, -1.0F, 1.0F, 1.0F).setColor(255, 255, 255, 255);

            BufferUploader.drawWithShader(buffer.buildOrThrow());
        } finally {
            RenderSystem.colorMask(true, true, true, true);
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
            RenderSystem.depthMask(true);
            modelViewStack.popMatrix();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(previousProjection, previousVertexSorting);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }

    private static void drawPortalMask(
            PoseStack poseStack,
            Camera camera,
            PortalFrame portal
    ) {
        drawPortalMask(poseStack, camera, portal, null, null);
    }

    private static void drawPortalMask(
            PoseStack poseStack,
            Camera camera,
            PortalFrame portal,
            PortalStencilMask stencilMask,
            ResourceLocation viewId
    ) {
        Vec3 cameraPosition = camera.getPosition();
        Vec3 portalPosition = portal.position();

        poseStack.pushPose();

        try {
            poseStack.translate(
                    portalPosition.x() - cameraPosition.x(),
                    portalPosition.y() - cameraPosition.y(),
                    portalPosition.z() - cameraPosition.z()
            );
            poseStack.mulPose(new Quaternionf(portal.rotation()));

            Matrix4f matrix = poseStack.last().pose();
            float halfWidth = Math.max(0.0F, portal.width() * 0.5F - PORTAL_APERTURE_EDGE_INSET_BLOCKS);
            float halfHeight = Math.max(0.0F, portal.height() * 0.5F - PORTAL_APERTURE_EDGE_INSET_BLOCKS);

            PortalStencilMaskCache.LoadedMask loadedMask = PortalStencilMaskCache.get(stencilMask);
            if (loadedMask == null) {
                drawRectangularMask(matrix, halfWidth, halfHeight);
                if (stencilMask != null) {
                    PortalStencilMaskCache.logUseIfEnabled(viewId, null, true, "mask-unavailable-rectangle-fallback");
                }
            } else {
                drawLoadedMask(matrix, halfWidth * 2.0F, halfHeight * 2.0F, loadedMask);
                PortalStencilMaskCache.logUseIfEnabled(viewId, loadedMask, false, "alpha-binary");
            }
        } finally {
            poseStack.popPose();
        }
    }

    private static void drawRectangularMask(Matrix4f matrix, float halfWidth, float halfHeight) {
        var buffer = Tesselator.getInstance().begin(
                VertexFormat.Mode.QUADS,
                DefaultVertexFormat.POSITION_COLOR
        );

        buffer.addVertex(matrix, -halfWidth, -halfHeight, PORTAL_Z_OFFSET).setColor(255, 255, 255, 255);
        buffer.addVertex(matrix, halfWidth, -halfHeight, PORTAL_Z_OFFSET).setColor(255, 255, 255, 255);
        buffer.addVertex(matrix, halfWidth, halfHeight, PORTAL_Z_OFFSET).setColor(255, 255, 255, 255);
        buffer.addVertex(matrix, -halfWidth, halfHeight, PORTAL_Z_OFFSET).setColor(255, 255, 255, 255);

        BufferUploader.drawWithShader(buffer.buildOrThrow());
    }

    private static void drawLoadedMask(Matrix4f matrix, float portalWidth, float portalHeight, PortalStencilMaskCache.LoadedMask mask) {
        var buffer = Tesselator.getInstance().begin(
                VertexFormat.Mode.QUADS,
                DefaultVertexFormat.POSITION_COLOR
        );

        float cellWidth = portalWidth / mask.width();
        float cellHeight = portalHeight / mask.height();
        float left = -portalWidth * 0.5F;
        float bottom = -portalHeight * 0.5F;

        for (int y = 0; y < mask.height(); y++) {
            for (int x = 0; x < mask.width(); x++) {
                if (!mask.isSolid(x, y)) {
                    continue;
                }
                float x0 = left + x * cellWidth;
                float x1 = x0 + cellWidth;
                float y1 = bottom + (mask.height() - y) * cellHeight;
                float y0 = y1 - cellHeight;
                buffer.addVertex(matrix, x0, y0, PORTAL_Z_OFFSET).setColor(255, 255, 255, 255);
                buffer.addVertex(matrix, x1, y0, PORTAL_Z_OFFSET).setColor(255, 255, 255, 255);
                buffer.addVertex(matrix, x1, y1, PORTAL_Z_OFFSET).setColor(255, 255, 255, 255);
                buffer.addVertex(matrix, x0, y1, PORTAL_Z_OFFSET).setColor(255, 255, 255, 255);
            }
        }

        BufferUploader.drawWithShader(buffer.buildOrThrow());
    }

    private static void addMarker(
            com.mojang.blaze3d.vertex.BufferBuilder buffer,
            Matrix4f matrix,
            float centerX,
            float centerY,
            float size,
            int red,
            int green,
            int blue,
            int alpha
    ) {
        float z = PORTAL_Z_OFFSET + 0.002F;

        buffer.addVertex(matrix, centerX - size, centerY - size, z).setColor(red, green, blue, alpha);
        buffer.addVertex(matrix, centerX + size, centerY - size, z).setColor(red, green, blue, alpha);
        buffer.addVertex(matrix, centerX + size, centerY + size, z).setColor(red, green, blue, alpha);
        buffer.addVertex(matrix, centerX - size, centerY + size, z).setColor(red, green, blue, alpha);
    }

    public static void drawFullscreenTexture(int colorTextureId) {
        Minecraft minecraft = Minecraft.getInstance();
        int width = minecraft.getWindow().getWidth();
        int height = minecraft.getWindow().getHeight();

        Matrix4f overlayProjection = new Matrix4f().setOrtho(
                0.0F,
                width,
                height,
                0.0F,
                -1.0F,
                1.0F
        );

        RenderSystem.setProjectionMatrix(overlayProjection, VertexSorting.ORTHOGRAPHIC_Z);
        var modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.identity();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.setShaderTexture(0, colorTextureId);

        Matrix4f matrix = new Matrix4f();

        var buffer = Tesselator.getInstance().begin(
                VertexFormat.Mode.QUADS,
                DefaultVertexFormat.POSITION_TEX
        );

        buffer.addVertex(matrix, 0.0F, height, 0.0F).setUv(0.0F, 0.0F);
        buffer.addVertex(matrix, width, height, 0.0F).setUv(1.0F, 0.0F);
        buffer.addVertex(matrix, width, 0.0F, 0.0F).setUv(1.0F, 1.0F);
        buffer.addVertex(matrix, 0.0F, 0.0F, 0.0F).setUv(0.0F, 1.0F);

        BufferUploader.drawWithShader(buffer.buildOrThrow());
    }

    public record StencilResult(
            boolean attempted,
            boolean succeeded,
            boolean fallbackUsed,
            int stencilBits,
            String exception
    ) {}
}
