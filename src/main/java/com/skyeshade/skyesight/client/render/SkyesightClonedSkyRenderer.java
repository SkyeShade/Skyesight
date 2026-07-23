package com.skyeshade.skyesight.client.render;

import com.mojang.blaze3d.platform.GlStateManager.DestFactor;
import com.mojang.blaze3d.platform.GlStateManager.SourceFactor;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import com.skyeshade.skyesight.mixin.client.LevelRendererEndSkyInvoker;
import com.skyeshade.skyesight.mixin.client.LevelRendererSkyBufferAccessor;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Legacy cloned/manual sky renderer retained as an emergency fallback and
 * diagnostic comparison path. Stabilized direct-stencil portals should prefer
 * offscreen vanilla {@code LevelRenderer.renderSky} capture through
 * {@code PortalSkyCaptureManager}.
 */
public final class SkyesightClonedSkyRenderer {
    private static final ResourceLocation SUN_LOCATION =
            ResourceLocation.withDefaultNamespace("textures/environment/sun.png");

    private static final ResourceLocation MOON_LOCATION =
            ResourceLocation.withDefaultNamespace("textures/environment/moon_phases.png");

    private static volatile boolean lastUpperSkyRendered;
    private static volatile boolean lastDarkLowerSkyRendered;
    private static volatile boolean lastSunMoonAttempted;
    private static volatile boolean lastStarsAttempted;
    private static volatile boolean lastDimensionSpecialSkyRendered;

    private SkyesightClonedSkyRenderer() {}

    public static void renderSky(
            ClientLevel level,
            Camera camera,
            Matrix4f frustumMatrix,
            Matrix4f projectionMatrix,
            float partialTick
    ) {
        renderSky(level, camera, frustumMatrix, projectionMatrix, partialTick, () -> {});
    }

    public static void renderSky(
            ClientLevel level,
            Camera camera,
            Matrix4f frustumMatrix,
            Matrix4f projectionMatrix,
            float partialTick,
            Runnable beforeSkyPiece
    ) {
        resetDiagnostics();

        Minecraft minecraft = Minecraft.getInstance();
        LevelRendererSkyBufferAccessor buffers =
                (LevelRendererSkyBufferAccessor) minecraft.levelRenderer;

        int ticks = buffers.skyesight$getTicks();

        if (tryRenderCustomSky(level, camera, frustumMatrix, projectionMatrix, partialTick, ticks)) {
            lastDimensionSpecialSkyRendered = true;
            restoreState();
            return;
        }

        FogRenderer.setupNoFog();

        FogType fogType = camera.getFluidInCamera();
        if (fogType == FogType.POWDER_SNOW || fogType == FogType.LAVA) {
            restoreState();
            return;
        }

        PoseStack poseStack = new PoseStack();
        poseStack.mulPose(frustumMatrix);

        DimensionSpecialEffects.SkyType skyType = level.effects().skyType();

        if (skyType == DimensionSpecialEffects.SkyType.END) {
            beforeSkyPiece.run();
            renderEndSky(poseStack);
            lastDimensionSpecialSkyRendered = true;
            restoreState();
            return;
        }

        if (skyType != DimensionSpecialEffects.SkyType.NORMAL) {
            restoreState();
            return;
        }

        renderNormalSky(level, camera, buffers, poseStack, projectionMatrix, partialTick, beforeSkyPiece);
        restoreState();
    }

    private static void resetDiagnostics() {
        lastUpperSkyRendered = false;
        lastDarkLowerSkyRendered = false;
        lastSunMoonAttempted = false;
        lastStarsAttempted = false;
        lastDimensionSpecialSkyRendered = false;
    }

    public static void resetDiagnosticsForExternalSky() {
        resetDiagnostics();
    }
    private static boolean tryRenderCustomSky(
            ClientLevel level,
            Camera camera,
            Matrix4f frustumMatrix,
            Matrix4f projectionMatrix,
            float partialTick,
            int ticks
    ) {
        RenderSystem.depthMask(false);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

        boolean rendered = level.effects().renderSky(
                level,
                ticks,
                partialTick,
                frustumMatrix,
                camera,
                projectionMatrix,
                false,
                FogRenderer::setupNoFog
        );

        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

        return rendered;
    }
    private static void renderEndSky(PoseStack poseStack) {
        Minecraft minecraft = Minecraft.getInstance();

        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

        ((LevelRendererEndSkyInvoker) minecraft.levelRenderer).skyesight$renderEndSky(poseStack);

        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }
    private static void renderNormalSky(
            ClientLevel level,
            Camera camera,
            LevelRendererSkyBufferAccessor buffers,
            PoseStack poseStack,
            Matrix4f projectionMatrix,
            float partialTick,
            Runnable beforeSkyPiece
    ) {
        Vec3 skyColor = level.getSkyColor(camera.getPosition(), partialTick);
        float skyR = (float) skyColor.x();
        float skyG = (float) skyColor.y();
        float skyB = (float) skyColor.z();

        FogRenderer.levelFogColor();

        Tesselator tesselator = Tesselator.getInstance();

        beforeSkyPiece.run();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionShader);
        RenderSystem.setShaderColor(skyR, skyG, skyB, 1.0F);

        ShaderInstance skyShader = RenderSystem.getShader();

        buffers.skyesight$getSkyBuffer().bind();
        buffers.skyesight$getSkyBuffer().drawWithShader(
                poseStack.last().pose(),
                projectionMatrix,
                skyShader
        );
        VertexBuffer.unbind();
        lastUpperSkyRendered = true;

        RenderSystem.enableBlend();

        beforeSkyPiece.run();
        renderSunrise(level, poseStack, tesselator, partialTick);
        beforeSkyPiece.run();
        lastSunMoonAttempted = true;
        renderSunMoon(level, poseStack, tesselator, partialTick);
        beforeSkyPiece.run();
        lastStarsAttempted = true;
        renderStars(level, buffers, poseStack, projectionMatrix, partialTick);

        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();

        beforeSkyPiece.run();
        lastDarkLowerSkyRendered = renderDarkHorizon(level, camera, buffers, poseStack, projectionMatrix, skyShader);

        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.depthMask(true);
    }

    private static void renderSunrise(
            ClientLevel level,
            PoseStack poseStack,
            Tesselator tesselator,
            float partialTick
    ) {
        float[] sunriseColor = level.effects().getSunriseColor(
                level.getTimeOfDay(partialTick),
                partialTick
        );

        if (sunriseColor == null) {
            return;
        }

        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

        poseStack.pushPose();

        poseStack.mulPose(Axis.XP.rotationDegrees(90.0F));

        float flip = Mth.sin(level.getSunAngle(partialTick)) < 0.0F
                ? 180.0F
                : 0.0F;

        poseStack.mulPose(Axis.ZP.rotationDegrees(flip));
        poseStack.mulPose(Axis.ZP.rotationDegrees(90.0F));

        Matrix4f matrix = poseStack.last().pose();

        BufferBuilder buffer = tesselator.begin(
                VertexFormat.Mode.TRIANGLE_FAN,
                DefaultVertexFormat.POSITION_COLOR
        );

        buffer.addVertex(matrix, 0.0F, 100.0F, 0.0F)
                .setColor(
                        sunriseColor[0],
                        sunriseColor[1],
                        sunriseColor[2],
                        sunriseColor[3]
                );

        for (int i = 0; i <= 16; i++) {
            float angle = (float) i * ((float) Math.PI * 2.0F) / 16.0F;
            float sin = Mth.sin(angle);
            float cos = Mth.cos(angle);

            buffer.addVertex(
                            matrix,
                            sin * 120.0F,
                            cos * 120.0F,
                            -cos * 40.0F * sunriseColor[3]
                    )
                    .setColor(
                            sunriseColor[0],
                            sunriseColor[1],
                            sunriseColor[2],
                            0.0F
                    );
        }

        BufferUploader.drawWithShader(buffer.buildOrThrow());

        poseStack.popPose();
    }

    private static void renderSunMoon(
            ClientLevel level,
            PoseStack poseStack,
            Tesselator tesselator,
            float partialTick
    ) {
        RenderSystem.blendFuncSeparate(
                SourceFactor.SRC_ALPHA,
                DestFactor.ONE,
                SourceFactor.ONE,
                DestFactor.ZERO
        );

        poseStack.pushPose();

        float rainFade = 1.0F - level.getRainLevel(partialTick);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, rainFade);

        poseStack.mulPose(Axis.YP.rotationDegrees(-90.0F));
        poseStack.mulPose(Axis.XP.rotationDegrees(level.getTimeOfDay(partialTick) * 360.0F));

        Matrix4f matrix = poseStack.last().pose();

        RenderSystem.setShader(GameRenderer::getPositionTexShader);

        float sunSize = 30.0F;
        RenderSystem.setShaderTexture(0, SUN_LOCATION);

        BufferBuilder buffer = tesselator.begin(
                VertexFormat.Mode.QUADS,
                DefaultVertexFormat.POSITION_TEX
        );

        buffer.addVertex(matrix, -sunSize, 100.0F, -sunSize).setUv(0.0F, 0.0F);
        buffer.addVertex(matrix, sunSize, 100.0F, -sunSize).setUv(1.0F, 0.0F);
        buffer.addVertex(matrix, sunSize, 100.0F, sunSize).setUv(1.0F, 1.0F);
        buffer.addVertex(matrix, -sunSize, 100.0F, sunSize).setUv(0.0F, 1.0F);

        BufferUploader.drawWithShader(buffer.buildOrThrow());

        float moonSize = 20.0F;
        RenderSystem.setShaderTexture(0, MOON_LOCATION);

        int moonPhase = level.getMoonPhase();
        int phaseX = moonPhase % 4;
        int phaseY = moonPhase / 4 % 2;

        float u0 = (float) phaseX / 4.0F;
        float v0 = (float) phaseY / 2.0F;
        float u1 = (float) (phaseX + 1) / 4.0F;
        float v1 = (float) (phaseY + 1) / 2.0F;

        buffer = tesselator.begin(
                VertexFormat.Mode.QUADS,
                DefaultVertexFormat.POSITION_TEX
        );

        buffer.addVertex(matrix, -moonSize, -100.0F, moonSize).setUv(u1, v1);
        buffer.addVertex(matrix, moonSize, -100.0F, moonSize).setUv(u0, v1);
        buffer.addVertex(matrix, moonSize, -100.0F, -moonSize).setUv(u0, v0);
        buffer.addVertex(matrix, -moonSize, -100.0F, -moonSize).setUv(u1, v0);

        BufferUploader.drawWithShader(buffer.buildOrThrow());

        poseStack.popPose();
    }

    private static void renderStars(
            ClientLevel level,
            LevelRendererSkyBufferAccessor buffers,
            PoseStack poseStack,
            Matrix4f projectionMatrix,
            float partialTick
    ) {
        float rainFade = 1.0F - level.getRainLevel(partialTick);
        float starBrightness = level.getStarBrightness(partialTick) * rainFade;

        if (starBrightness <= 0.0F) {
            return;
        }

        RenderSystem.setShaderColor(starBrightness, starBrightness, starBrightness, starBrightness);
        FogRenderer.setupNoFog();

        buffers.skyesight$getStarBuffer().bind();
        buffers.skyesight$getStarBuffer().drawWithShader(
                poseStack.last().pose(),
                projectionMatrix,
                GameRenderer.getPositionShader()
        );
        VertexBuffer.unbind();

        FogRenderer.setupNoFog();
    }

    private static boolean renderDarkHorizon(
            ClientLevel level,
            Camera camera,
            LevelRendererSkyBufferAccessor buffers,
            PoseStack poseStack,
            Matrix4f projectionMatrix,
            ShaderInstance skyShader
    ) {
        double cameraRelativeHorizon =
                camera.getPosition().y() - level.getLevelData().getHorizonHeight(level);

        if (cameraRelativeHorizon >= 0.0D) {
            return false;
        }

        RenderSystem.setShaderColor(0.0F, 0.0F, 0.0F, 1.0F);

        poseStack.pushPose();
        poseStack.translate(0.0F, 12.0F, 0.0F);

        buffers.skyesight$getDarkBuffer().bind();
        buffers.skyesight$getDarkBuffer().drawWithShader(
                poseStack.last().pose(),
                projectionMatrix,
                skyShader
        );
        VertexBuffer.unbind();

        poseStack.popPose();
        return true;
    }

    private static void restoreState() {
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    public static boolean lastUpperSkyRendered() {
        return lastUpperSkyRendered;
    }

    public static boolean lastDarkLowerSkyRendered() {
        return lastDarkLowerSkyRendered;
    }

    public static boolean lastSunMoonAttempted() {
        return lastSunMoonAttempted;
    }

    public static boolean lastStarsAttempted() {
        return lastStarsAttempted;
    }

    public static boolean lastDimensionSpecialSkyRendered() {
        return lastDimensionSpecialSkyRendered;
    }
}
