package com.skyeshade.skyesight.client.portal;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.skyeshade.skyesight.Skyesight;
import com.skyeshade.skyesight.SkyesightDebugConfig;
import com.skyeshade.skyesight.client.compat.iris.SkyesightIrisCompat;
import com.skyeshade.skyesight.client.render.SkyesightClonedSkyRenderer;
import com.skyeshade.skyesight.mixin.client.CameraInvoker;
import com.skyeshade.skyesight.mixin.client.LevelRendererCloudInvoker;
import com.skyeshade.skyesight.mixin.client.LevelRendererSkyInvoker;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.Map;

public final class PortalSkyCaptureManager {
    // Working default: capture sky first and clouds second into one offscreen
    // environment texture, then the portal pass composites that texture opaquely
    // through the direct-stencil aperture. Clouds are currently background-only;
    // a later depth-aware implementation should use a separate/direct pass.
    private static final boolean ENABLE_PORTAL_CLOUD_CAPTURE = true;
    private static final boolean DISABLE_DARK_HORIZON_DURING_PORTAL_SKY_CAPTURE = true;
    private static final boolean FORCE_PORTAL_SKY_ALPHA_OPAQUE = true;
    private static final boolean USE_TERRAIN_PROJECTION_FOR_SKY_CAPTURE = true;
    private static final boolean FORCE_SKY_CAMERA_ABOVE_HORIZON = true;
    private static final boolean MATCH_VANILLA_SKY_CALLER_PRECONDITIONS = true;
    private static final boolean USE_PORTAL_E_FIXED_NETHER_FOG_FALLBACK = true;
    private static final RgbaSample PORTAL_E_FIXED_NETHER_FALLBACK_COLOR = new RgbaSample(42, 12, 9, 255);
    private static final ThreadLocal<Boolean> PORTAL_SKY_CAPTURE_ACTIVE = ThreadLocal.withInitial(() -> false);
    private static long lastCaptureSummaryLogNanos = 0L;

    private enum PortalCloudMode {
        DISABLED,
        OFFSCREEN_VANILLA_INVOKER
    }

    private static final PortalCloudMode PORTAL_CLOUD_MODE = PortalCloudMode.OFFSCREEN_VANILLA_INVOKER;

    public enum Mode {
        MAIN_CAMERA_COPY,
        PORTAL_CAMERA_RENDER,
        // Emergency legacy fallback only; the stable path invokes vanilla LevelRenderer.renderSky into the offscreen environment target.
        PORTAL_CAMERA_CLONED
    }

    public record Capture(
            Mode mode,
            String key,
            TextureTarget target,
            int frameId,
            String summary
    ) {
        public boolean validForFrame(int frameId) {
            return this.target != null
                    && this.target.getColorTextureId() > 0
                    && this.frameId == frameId;
        }

        public int textureId() {
            return this.target == null ? -1 : this.target.getColorTextureId();
        }
    }

    private final Map<String, TextureTarget> targets = new HashMap<>();
    private final Map<String, Capture> captures = new HashMap<>();

    public Capture captureMainCameraCopy(Minecraft minecraft, RenderLevelStageEvent event) {
        var mainTarget = minecraft.getMainRenderTarget();
        if (mainTarget == null || mainTarget.frameBufferId <= 0 || mainTarget.width <= 0 || mainTarget.height <= 0) {
            Capture capture = invalid(Mode.MAIN_CAMERA_COPY, "main", event.getRenderTick(), "invalid main target");
            this.captures.put("main", capture);
            return capture;
        }

        int framebufferBefore = GL30.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        int readFramebufferBefore = GL30.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int drawFramebufferBefore = GL30.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int[] viewportBefore = viewport();

        try {
            TextureTarget target = getOrCreate("main", mainTarget.width, mainTarget.height);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, mainTarget.frameBufferId);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, target.frameBufferId);
            GL30.glBlitFramebuffer(
                    0,
                    0,
                    mainTarget.width,
                    mainTarget.height,
                    0,
                    0,
                    target.width,
                    target.height,
                    GL11.GL_COLOR_BUFFER_BIT,
                    GL11.GL_NEAREST
            );
            Capture capture = new Capture(
                    Mode.MAIN_CAMERA_COPY,
                    "main",
                    target,
                    event.getRenderTick(),
                    "main-copy tex=" + target.getColorTextureId() + " size=" + target.width + "x" + target.height + " gl=" + drainGlErrors()
            );
            this.captures.put("main", capture);
            return capture;
        } catch (RuntimeException exception) {
            Capture capture = invalid(Mode.MAIN_CAMERA_COPY, "main", event.getRenderTick(), exception.getClass().getSimpleName() + ": " + exception.getMessage());
            this.captures.put("main", capture);
            return capture;
        } finally {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFramebufferBefore);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFramebufferBefore);
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebufferBefore);
            RenderSystem.viewport(viewportBefore[0], viewportBefore[1], viewportBefore[2], viewportBefore[3]);
        }
    }



    public Capture capturePortalCameraSky(
            Minecraft minecraft,
            RenderLevelStageEvent event,
            String key,
            ClientLevel targetLevel,
            String targetLevelSource,
            Camera camera,
            Quaternionf cameraRotation,
            Matrix4f terrainProjection,
            String terrainProjectionSummary,
            double terrainFov,
            int targetWidth,
            int targetHeight
    ) {
        ClientLevel level = targetLevel;
        var mainTarget = minecraft.getMainRenderTarget();
        if (level == null || mainTarget == null || mainTarget.width <= 0 || mainTarget.height <= 0) {
            Capture capture = invalid(Mode.PORTAL_CAMERA_RENDER, key, event.getRenderTick(), "invalid level/main target");
            this.captures.put(key, capture);
            return capture;
        }
        int captureWidth = targetWidth <= 0 ? mainTarget.width : targetWidth;
        int captureHeight = targetHeight <= 0 ? mainTarget.height : targetHeight;

        int framebufferBefore = GL30.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        int readFramebufferBefore = GL30.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int drawFramebufferBefore = GL30.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int[] viewportBefore = viewport();
        Matrix4f previousProjection = new Matrix4f(RenderSystem.getProjectionMatrix());
        VertexSorting previousSorting = RenderSystem.getVertexSorting();
        var modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushMatrix();
        boolean depthTestBefore = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean blendBefore = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean cullBefore = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        boolean depthMaskBefore = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        boolean[] colorMaskBefore = colorMask();
        float[] shaderColorBefore = RenderSystem.getShaderColor().clone();
        ShaderInstance shaderBefore = RenderSystem.getShader();
        int texture0Before = RenderSystem.getShaderTexture(0);
        RgbaSample fogColorBeforeCapture = currentClearColor();
        RgbaSample clearColorBeforeCapture = fogColorBeforeCapture;
        Vec3 cameraPositionBeforeCapture = null;
        boolean cameraPositionAdjustedForSky = false;

        try {
            TextureTarget target = getOrCreate(key, captureWidth, captureHeight);
            target.bindWrite(true);
            RenderSystem.viewport(0, 0, target.width, target.height);
            RenderSystem.colorMask(true, true, true, true);
            RenderSystem.depthMask(true);
            RenderSystem.disableBlend();
            RenderSystem.enableDepthTest();
            RenderSystem.enableCull();
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

            float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(true);
            double fallbackFov = minecraft.options.fov().get();
            Matrix4f fallbackProjection = minecraft.gameRenderer.getProjectionMatrix(fallbackFov);
            Matrix4f projection = USE_TERRAIN_PROJECTION_FOR_SKY_CAPTURE && terrainProjection != null
                    ? new Matrix4f(terrainProjection)
                    : fallbackProjection;
            Matrix4f frustum = new Matrix4f().rotation(new Quaternionf(cameraRotation).conjugate());
            Vec3 originalCameraPosition = camera.getPosition();
            cameraPositionBeforeCapture = originalCameraPosition;
            double originalCameraY = originalCameraPosition.y();
            double horizon = level.getLevelData().getHorizonHeight(level);
            boolean originalBelowHorizon = originalCameraY - horizon < 0.0D;
            boolean cameraYAdjusted = false;
            double effectiveCameraY = originalCameraY;
            if (FORCE_SKY_CAMERA_ABOVE_HORIZON && originalBelowHorizon) {
                effectiveCameraY = horizon + 8.0D;
                ((CameraInvoker) camera).skyesight$setPosition(new Vec3(
                        originalCameraPosition.x(),
                        effectiveCameraY,
                        originalCameraPosition.z()
                ));
                cameraYAdjusted = true;
                cameraPositionAdjustedForSky = true;
            }
            String dimension = String.valueOf(level.dimension().location());
            String skyType = level.effects().skyType().name();
            boolean normalSkyType = level.effects().skyType() == DimensionSpecialEffects.SkyType.NORMAL;
            boolean portalENetherTarget = "E".equals(key) && level.dimension() == net.minecraft.world.level.Level.NETHER;
            boolean disableTargetFogSetup = portalENetherTarget && USE_PORTAL_E_FIXED_NETHER_FOG_FALLBACK;
            String targetFogMode = "TARGET_LEVEL_FOG_SETUP";
            RgbaSample vanillaCallerClearColor;
            if (disableTargetFogSetup) {
                targetFogMode = "FIXED_NETHER_FALLBACK";
                vanillaCallerClearColor = PORTAL_E_FIXED_NETHER_FALLBACK_COLOR;
            } else if (MATCH_VANILLA_SKY_CALLER_PRECONDITIONS) {
                vanillaCallerClearColor = setupVanillaCallerClearState(minecraft, level, camera, partialTick);
            } else {
                targetFogMode = "MATCH_VANILLA_CALLER_DISABLED";
                Vec3 skyColor = level.getSkyColor(camera.getPosition(), partialTick);
                vanillaCallerClearColor = RgbaSample.fromUnit(skyColor.x(), skyColor.y(), skyColor.z(), 1.0D);
            }
            RgbaSample selectedBaseColor = vanillaCallerClearColor;
            String clearColorSource = disableTargetFogSetup ? "fixedNetherFallback" : "vanillaCallerFogClearColor";
            target.setClearColor(selectedBaseColor.redFloat(), selectedBaseColor.greenFloat(), selectedBaseColor.blueFloat(), 1.0F);
            target.clear(Minecraft.ON_OSX);
            target.bindWrite(true);
            RenderSystem.viewport(0, 0, target.width, target.height);

            modelViewStack.identity();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(projection, VertexSorting.DISTANCE_TO_ORIGIN);
            PORTAL_SKY_CAPTURE_ACTIVE.set(true);
            String skippedVanillaSkyReason = "-";
            String renderer;
            if (normalSkyType) {
                renderer = renderVanillaSkyCapture(minecraft, level, camera, frustum, projection, partialTick);
            } else {
                renderer = "dimensionClearOnly";
                skippedVanillaSkyReason = "skyType " + skyType + " is not NORMAL";
            }
            PORTAL_SKY_CAPTURE_ACTIVE.set(false);
            // The portal environment texture contains sky first, then first-pass clouds.
            // Clouds are background-only here and may appear behind all portal terrain;
            // depth-aware clouds would need a later separate/direct pass.
            CloudCaptureResult cloudResult = capturePortalClouds(
                    minecraft,
                    level,
                    target,
                    camera,
                    frustum,
                    projection,
                    partialTick,
                    "vanillaLevelRenderer".equals(renderer)
            );
            if (cameraYAdjusted) {
                ((CameraInvoker) camera).skyesight$setPosition(originalCameraPosition);
                cameraPositionAdjustedForSky = false;
            }
            boolean alphaFixApplied = forceCaptureAlphaOpaque(target);
            boolean vanillaInvokerUsed = "vanillaLevelRenderer".equals(renderer);
            boolean clonedFallbackUsed = renderer.startsWith("clonedFallbackAfterVanillaFailure");
            String glError = drainGlErrors();
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFramebufferBefore);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFramebufferBefore);
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebufferBefore);
            RenderSystem.viewport(viewportBefore[0], viewportBefore[1], viewportBefore[2], viewportBefore[3]);
            FogRestoreResult fogRestoreResult = restoreMainWorldFogState(minecraft, event, partialTick, key, fogColorBeforeCapture);
            String summary = "portal=" + key
                    + " captureMode=PORTAL_CAMERA_RENDER"
                    + " targetDimension=" + dimension
                    + " targetLevelSource=" + emptyDash(targetLevelSource)
                    + " renderer=" + renderer
                    + " skyType=" + skyType
                    + " targetFogMode=" + targetFogMode
                    + " usedVanillaInvoker=" + yesNo(vanillaInvokerUsed)
                    + " skippedVanillaSkyReason=" + skippedVanillaSkyReason
                    + " fallbackUsed=" + yesNo(clonedFallbackUsed)
                    + " alphaFixApplied=" + yesNo(alphaFixApplied)
                    + " clearColorSource=" + clearColorSource
                    + " fogRestored=" + yesNo(fogRestoreResult.fogRestored())
                    + " cloudsEnabled=" + yesNo(cloudResult.cloudsEnabled())
                    + " cloudStatus=" + cloudResult.cloudStatus()
                    + " cloudRenderSucceeded=" + yesNo(cloudResult.succeeded())
                    + " cloudSkippedReason=" + cloudResult.skippedReason()
                    + " tex=" + target.getColorTextureId()
                    + " size=" + target.width + "x" + target.height
                    + " glError=" + glError;
            logCaptureSummaryThrottled(summary);

            Capture capture = new Capture(
                    Mode.PORTAL_CAMERA_RENDER,
                    key,
                    target,
                    event.getRenderTick(),
                    summary
            );
            this.captures.put(key, capture);
            return capture;
        } catch (RuntimeException exception) {
            Capture capture = invalid(Mode.PORTAL_CAMERA_RENDER, key, event.getRenderTick(), exception.getClass().getSimpleName() + ": " + exception.getMessage());
            this.captures.put(key, capture);
            return capture;
        } finally {
            PORTAL_SKY_CAPTURE_ACTIVE.set(false);
            if (cameraPositionAdjustedForSky && cameraPositionBeforeCapture != null) {
                ((CameraInvoker) camera).skyesight$setPosition(cameraPositionBeforeCapture);
            }
            modelViewStack.popMatrix();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(previousProjection, previousSorting);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFramebufferBefore);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFramebufferBefore);
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebufferBefore);
            RenderSystem.viewport(viewportBefore[0], viewportBefore[1], viewportBefore[2], viewportBefore[3]);
            if (shaderBefore != null) {
                RenderSystem.setShader(() -> shaderBefore);
            }
            RenderSystem.setShaderTexture(0, texture0Before);
            RenderSystem.setShaderColor(
                    shaderColorBefore[0],
                    shaderColorBefore[1],
                    shaderColorBefore[2],
                    shaderColorBefore[3]
            );
            setClearColor(clearColorBeforeCapture);
            RenderSystem.depthMask(depthMaskBefore);
            RenderSystem.colorMask(colorMaskBefore[0], colorMaskBefore[1], colorMaskBefore[2], colorMaskBefore[3]);
            setEnabled(GL11.GL_DEPTH_TEST, depthTestBefore);
            setEnabled(GL11.GL_BLEND, blendBefore);
            setEnabled(GL11.GL_CULL_FACE, cullBefore);
        }
    }

    private static String renderVanillaSkyCapture(
            Minecraft minecraft,
            ClientLevel level,
            Camera camera,
            Matrix4f frustum,
            Matrix4f projection,
            float partialTick
    ) {
        FogRenderer.setupColor(
                camera,
                partialTick,
                level,
                minecraft.options.getEffectiveRenderDistance(),
                minecraft.gameRenderer.getDarkenWorldAmount(partialTick)
        );
        FogRenderer.levelFogColor();

        float renderDistance = minecraft.gameRenderer.getRenderDistance();
        boolean foggy = level.effects().isFoggyAt(
                Mth.floor(camera.getPosition().x()),
                Mth.floor(camera.getPosition().y())
        ) || minecraft.gui.getBossOverlay().shouldCreateWorldFog();

        Runnable skyFogSetup = () -> {
            FogRenderer.setupFog(
                    camera,
                    FogRenderer.FogMode.FOG_SKY,
                    renderDistance,
                    foggy,
                    partialTick
            );
            RenderSystem.setShader(GameRenderer::getPositionShader);
        };

        try {
            skyFogSetup.run();
            RenderSystem.setShader(GameRenderer::getPositionShader);
            ((LevelRendererSkyInvoker) minecraft.levelRenderer).skyesight$renderSky(
                    frustum,
                    projection,
                    partialTick,
                    camera,
                    foggy,
                    skyFogSetup
            );
            return "vanillaLevelRenderer";
        } catch (RuntimeException exception) {
            SkyesightClonedSkyRenderer.renderSky(level, camera, frustum, projection, partialTick, skyFogSetup);
            return "clonedFallbackAfterVanillaFailure:" + exception.getClass().getSimpleName();
        }
    }

    private static CloudCaptureResult capturePortalClouds(
            Minecraft minecraft,
            ClientLevel level,
            TextureTarget target,
            Camera camera,
            Matrix4f frustum,
            Matrix4f projection,
            float partialTick,
            boolean vanillaSkyCaptureSucceeded
    ) {
        CloudStatus cloudStatus = minecraft.options.getCloudsType();
        float cloudHeight = level.effects().getCloudHeight();
        boolean shaderPackActive = SkyesightIrisCompat.isShaderPackInUse();

        if (!ENABLE_PORTAL_CLOUD_CAPTURE) {
            return CloudCaptureResult.skipped(cloudStatus, "portal clouds disabled");
        }
        if (PORTAL_CLOUD_MODE == PortalCloudMode.DISABLED) {
            return CloudCaptureResult.skipped(cloudStatus, "cloud mode disabled");
        }
        if (cloudStatus == CloudStatus.OFF) {
            return CloudCaptureResult.skipped(cloudStatus, "minecraft clouds off");
        }
        if (Float.isNaN(cloudHeight)) {
            return CloudCaptureResult.skipped(cloudStatus, "dimension cloud height NaN");
        }
        if (!vanillaSkyCaptureSucceeded || target == null || target.frameBufferId <= 0) {
            return CloudCaptureResult.skipped(cloudStatus, "sky capture target unavailable");
        }
        if (shaderPackActive) {
            return CloudCaptureResult.skipped(cloudStatus, "shaderpack active");
        }

        try {
            target.bindWrite(true);
            RenderSystem.viewport(0, 0, target.width, target.height);
            RenderSystem.colorMask(true, true, true, true);
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
            RenderSystem.disableBlend();
            RenderSystem.enableCull();
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            RenderSystem.setProjectionMatrix(projection, VertexSorting.DISTANCE_TO_ORIGIN);
            var modelViewStack = RenderSystem.getModelViewStack();
            modelViewStack.identity();
            RenderSystem.applyModelViewMatrix();
            FogRenderer.levelFogColor();

            PoseStack poseStack = new PoseStack();
            ((LevelRendererCloudInvoker) minecraft.levelRenderer).skyesight$renderClouds(
                    poseStack,
                    frustum,
                    projection,
                    partialTick,
                    camera.getPosition().x(),
                    camera.getPosition().y(),
                    camera.getPosition().z()
            );

            return CloudCaptureResult.succeeded(
                    cloudStatus
            );
        } catch (RuntimeException exception) {
            return CloudCaptureResult.failed(
                    cloudStatus,
                    exception.getClass().getSimpleName() + ": " + exception.getMessage()
            );
        } finally {
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }

    private static RgbaSample setupVanillaCallerClearState(
            Minecraft minecraft,
            ClientLevel level,
            Camera camera,
            float partialTick
    ) {
        // Match LevelRenderer.renderLevel's pre-renderSky setup. LevelRenderer.renderSky
        // does not own every background pixel; the caller's fog/clear color remains
        // visible wherever skyBuffer/darkBuffer geometry does not cover the target.
        RenderSystem.colorMask(true, true, true, true);
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        FogRenderer.setupColor(
                camera,
                partialTick,
                level,
                minecraft.options.getEffectiveRenderDistance(),
                minecraft.gameRenderer.getDarkenWorldAmount(partialTick)
        );
        FogRenderer.levelFogColor();
        return currentClearColor();
    }

    private static FogRestoreResult restoreMainWorldFogState(
            Minecraft minecraft,
            RenderLevelStageEvent event,
            float partialTick,
            String portalKey,
            RgbaSample fogColorBeforeCapture
    ) {
        if (minecraft.level == null || event.getCamera() == null) {
            return new FogRestoreResult(false);
        }

        try {
            FogRenderer.setupColor(
                    event.getCamera(),
                    partialTick,
                    minecraft.level,
                    minecraft.options.getEffectiveRenderDistance(),
                    minecraft.gameRenderer.getDarkenWorldAmount(partialTick)
            );
            FogRenderer.levelFogColor();
            RgbaSample restoredColor = currentClearColor();
            boolean leakDetected = "E".equals(portalKey)
                    && fogColorBeforeCapture != null
                    && restoredColor.rgbDistance(fogColorBeforeCapture) > 12;
            return new FogRestoreResult(!leakDetected);
        } catch (RuntimeException exception) {
            return new FogRestoreResult(false);
        }
    }

    private static RgbaSample currentClearColor() {
        FloatBuffer buffer = BufferUtils.createFloatBuffer(4);
        GL11.glGetFloatv(GL11.GL_COLOR_CLEAR_VALUE, buffer);
        return RgbaSample.fromUnit(buffer.get(0), buffer.get(1), buffer.get(2), buffer.get(3));
    }

    private static void setClearColor(RgbaSample color) {
        if (color != null) {
            RenderSystem.clearColor(color.redFloat(), color.greenFloat(), color.blueFloat(), color.alpha / 255.0F);
        }
    }

    public Capture capture(String key) {
        return this.captures.get(key);
    }

    public static boolean skipDarkHorizonForCurrentCapture() {
        return DISABLE_DARK_HORIZON_DURING_PORTAL_SKY_CAPTURE && PORTAL_SKY_CAPTURE_ACTIVE.get();
    }

    public static boolean onDarkHorizonDrawAttempt() {
        return skipDarkHorizonForCurrentCapture();
    }

    public static boolean onSkyBufferDrawAttempt() {
        return false;
    }

    public static void onSkyBufferDrawn() {
    }

    private static boolean forceCaptureAlphaOpaque(TextureTarget target) {
        if (!FORCE_PORTAL_SKY_ALPHA_OPAQUE || target == null || target.frameBufferId <= 0) {
            return false;
        }

        target.bindWrite(true);
        RenderSystem.viewport(0, 0, target.width, target.height);
        RenderSystem.colorMask(false, false, false, true);
        RenderSystem.disableBlend();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

        Matrix4f overlayProjection = new Matrix4f().setOrtho(
                0.0F,
                target.width,
                target.height,
                0.0F,
                -1.0F,
                1.0F
        );
        RenderSystem.setProjectionMatrix(overlayProjection, VertexSorting.ORTHOGRAPHIC_Z);
        var modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.identity();
        RenderSystem.applyModelViewMatrix();

        Matrix4f matrix = new Matrix4f();
        var buffer = Tesselator.getInstance().begin(
                VertexFormat.Mode.QUADS,
                DefaultVertexFormat.POSITION_COLOR
        );
        buffer.addVertex(matrix, 0.0F, target.height, 0.0F).setColor(255, 255, 255, 255);
        buffer.addVertex(matrix, target.width, target.height, 0.0F).setColor(255, 255, 255, 255);
        buffer.addVertex(matrix, target.width, 0.0F, 0.0F).setColor(255, 255, 255, 255);
        buffer.addVertex(matrix, 0.0F, 0.0F, 0.0F).setColor(255, 255, 255, 255);
        BufferUploader.drawWithShader(buffer.buildOrThrow());

        RenderSystem.colorMask(true, true, true, true);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        return true;
    }

    private static void logCaptureSummaryThrottled(String summary) {
        if (!SkyesightDebugConfig.VERBOSE_RENDER) {
            return;
        }
        long now = System.nanoTime();
        if (now - lastCaptureSummaryLogNanos > 2_000_000_000L) {
            lastCaptureSummaryLogNanos = now;
            Skyesight.LOGGER.info("[Skyesight] Portal sky capture summary {}", summary);
        }
    }

    private static String yesNo(boolean value) {
        return value ? "yes" : "no";
    }

    private static String emptyDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private record FogRestoreResult(boolean fogRestored) {}

    private record CloudCaptureResult(
            boolean cloudsEnabled,
            String cloudStatus,
            boolean succeeded,
            String skippedReason
    ) {
        private static CloudCaptureResult skipped(
                CloudStatus cloudStatus,
                String reason
        ) {
            return new CloudCaptureResult(
                    false,
                    cloudStatus.name(),
                    false,
                    reason
            );
        }

        private static CloudCaptureResult succeeded(
                CloudStatus cloudStatus
        ) {
            return new CloudCaptureResult(
                    true,
                    cloudStatus.name(),
                    true,
                    "-"
            );
        }

        private static CloudCaptureResult failed(
                CloudStatus cloudStatus,
                String reason
        ) {
            return new CloudCaptureResult(
                    true,
                    cloudStatus.name(),
                    false,
                    reason
            );
        }
    }

    private record RgbaSample(int red, int green, int blue, int alpha) {
        private static RgbaSample fromUnit(double red, double green, double blue, double alpha) {
            return new RgbaSample(toByte(red), toByte(green), toByte(blue), toByte(alpha));
        }

        private float redFloat() {
            return this.red / 255.0F;
        }

        private float greenFloat() {
            return this.green / 255.0F;
        }

        private float blueFloat() {
            return this.blue / 255.0F;
        }

        private int rgbDistance(RgbaSample other) {
            if (other == null) {
                return -1;
            }
            return Math.abs(this.red - other.red)
                    + Math.abs(this.green - other.green)
                    + Math.abs(this.blue - other.blue);
        }

        private static int toByte(double value) {
            return Mth.clamp((int) Math.round(value * 255.0D), 0, 255);
        }

        @Override
        public String toString() {
            return "(" + this.red + "," + this.green + "," + this.blue + "," + this.alpha + ")";
        }
    }

    public void close() {
        for (TextureTarget target : this.targets.values()) {
            target.destroyBuffers();
        }
        this.targets.clear();
        this.captures.clear();
    }

    private TextureTarget getOrCreate(String key, int width, int height) {
        TextureTarget target = this.targets.get(key);
        if (target == null) {
            target = new TextureTarget(width, height, true, Minecraft.ON_OSX);
            this.targets.put(key, target);
        } else if (target.width != width || target.height != height) {
            target.resize(width, height, Minecraft.ON_OSX);
        }
        return target;
    }

    private static Capture invalid(Mode mode, String key, int frameId, String reason) {
        return new Capture(mode, key, null, frameId, "invalid " + reason);
    }

    private static int[] viewport() {
        int[] viewport = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        return viewport;
    }

    private static boolean[] colorMask() {
        ByteBuffer buffer = BufferUtils.createByteBuffer(4);
        GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK, buffer);
        return new boolean[] {
                buffer.get(0) != 0,
                buffer.get(1) != 0,
                buffer.get(2) != 0,
                buffer.get(3) != 0
        };
    }

    private static void setEnabled(int cap, boolean enabled) {
        if (enabled) {
            GL11.glEnable(cap);
        } else {
            GL11.glDisable(cap);
        }
    }

    private static String drainGlErrors() {
        StringBuilder errors = new StringBuilder();
        for (int i = 0; i < 8; i++) {
            int error = GL11.glGetError();
            if (error == GL11.GL_NO_ERROR) {
                break;
            }
            if (!errors.isEmpty()) {
                errors.append(',');
            }
            errors.append(error);
        }
        return errors.isEmpty() ? "none" : errors.toString();
    }
}
