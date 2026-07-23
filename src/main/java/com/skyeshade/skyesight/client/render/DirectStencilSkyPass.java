package com.skyeshade.skyesight.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.skyeshade.skyesight.client.compat.iris.SkyesightIrisCompat;
import com.skyeshade.skyesight.mixin.client.LevelRendererSkyInvoker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/**
 * Legacy direct-stencil sky experiment.
 *
 * <p>The stabilized portal environment path does not call vanilla sky from the
 * late stencil pass. It captures sky/clouds offscreen in
 * {@code PortalSkyCaptureManager} and composites the captured texture through
 * stencil. Keep this class for isolated diagnostics only.</p>
 */
public final class DirectStencilSkyPass {
    private DirectStencilSkyPass() {}

    public enum Mode {
        SIMPLE_FILL,
        CLONED_SKY,
        VANILLA_SKY_NO_CLEAR,
        VANILLA_OR_HOOKED_SKY,
        VANILLA_COMPONENTS_NO_CLEAR,
        REAL_LEVEL_RENDERER_SKY_NO_CLEAR
    }

    public static Result render(
            SecondaryViewFrame frame,
            Minecraft minecraft,
            float partialTick,
            Runnable reassertStencilRead,
            Mode mode
    ) {
        int framebufferBefore = GL30.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        int[] viewportBefore = readViewport();
        String stencilBefore = describeStencil();
        String shaderColorBefore = shaderColorString(RenderSystem.getShaderColor());
        ShaderInstance shaderBefore = RenderSystem.getShader();
        boolean depthTestBefore = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean blendBefore = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean cullBefore = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        boolean depthMaskBefore = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        int depthFuncBefore = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);

        Matrix4f previousProjection = new Matrix4f(RenderSystem.getProjectionMatrix());
        VertexSorting previousVertexSorting = RenderSystem.getVertexSorting();
        var modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushMatrix();

        boolean succeeded = false;
        String exceptionText = "";
        boolean fogRestored = false;
        boolean shaderColorRestored = false;
        boolean irisLoaded = SkyesightIrisCompat.isIrisLoaded();
        boolean shaderPackActive = SkyesightIrisCompat.isShaderPackInUse();
        boolean vanillaRenderSkyCalled = false;
        boolean neoforgeSkyHookCalled = false;

        try {
            reassertStencilRead.run();

            RenderSystem.colorMask(true, true, true, true);
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.disableBlend();
            RenderSystem.enableCull();
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

            try (SkyesightSecondaryRenderContext.Scope ignored =
                         SkyesightSecondaryRenderContext.push(
                                 minecraft.getMainRenderTarget(),
                                 frame.camera(),
                                 minecraft.getMainRenderTarget()
                         )) {
                if (mode == Mode.VANILLA_SKY_NO_CLEAR
                        || mode == Mode.VANILLA_OR_HOOKED_SKY
                        || mode == Mode.REAL_LEVEL_RENDERER_SKY_NO_CLEAR) {
                    if ((mode == Mode.VANILLA_OR_HOOKED_SKY || mode == Mode.REAL_LEVEL_RENDERER_SKY_NO_CLEAR)
                            && shaderPackActive) {
                        throw new IllegalStateException("Iris shaderpack active; real LevelRenderer sky is not portal-pipeline-safe yet");
                    }
                    renderRealLevelRendererSkyNoClear(frame, minecraft, partialTick, reassertStencilRead);
                    vanillaRenderSkyCalled = true;
                } else if (mode == Mode.VANILLA_COMPONENTS_NO_CLEAR) {
                    if (shaderPackActive) {
                        throw new IllegalStateException("Iris shaderpack active; direct component sky falls back to simple fill");
                    }
                    renderVanillaComponentsNoClear(frame, minecraft, partialTick, reassertStencilRead);
                } else {
                    SkyesightClonedSkyRenderer.renderSky(
                            minecraft.level,
                            frame.camera(),
                            frame.modelViewMatrix(),
                            frame.projectionMatrix(),
                            partialTick,
                            reassertStencilRead
                    );
                }
            }

            reassertStencilRead.run();
            succeeded = true;
        } catch (RuntimeException exception) {
            exceptionText = exception.getClass().getSimpleName() + ": " + exception.getMessage();
        } finally {
            try {
                FogRenderer.setupNoFog();
                fogRestored = true;
            } catch (RuntimeException ignored) {
                fogRestored = false;
            }

            modelViewStack.popMatrix();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(previousProjection, previousVertexSorting);

            restoreFramebuffer(minecraft, framebufferBefore);
            RenderSystem.viewport(
                    viewportBefore[0],
                    viewportBefore[1],
                    viewportBefore[2],
                    viewportBefore[3]
            );
            RenderSystem.colorMask(true, true, true, true);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            shaderColorRestored = shaderColorString(RenderSystem.getShaderColor()).equals("1.000,1.000,1.000,1.000");
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            RenderSystem.disableBlend();
            RenderSystem.enableCull();
            RenderSystem.defaultBlendFunc();
        }

        int framebufferAfter = GL30.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        int[] viewportAfter = readViewport();
        String stencilAfter = describeStencil();
        String shaderColorAfter = shaderColorString(RenderSystem.getShaderColor());
        ShaderInstance shaderAfter = RenderSystem.getShader();

        return new Result(
                true,
                succeeded,
                exceptionText,
                stencilBefore,
                stencilAfter,
                framebufferBefore,
                framebufferAfter,
                viewportString(viewportBefore),
                viewportString(viewportAfter),
                !stencilBefore.equals(stencilAfter),
                framebufferBefore != framebufferAfter,
                !viewportString(viewportBefore).equals(viewportString(viewportAfter)),
                SkyesightClonedSkyRenderer.lastUpperSkyRendered(),
                SkyesightClonedSkyRenderer.lastDarkLowerSkyRendered(),
                SkyesightClonedSkyRenderer.lastSunMoonAttempted(),
                SkyesightClonedSkyRenderer.lastStarsAttempted(),
                SkyesightClonedSkyRenderer.lastDimensionSpecialSkyRendered(),
                mode.name(),
                shaderColorBefore,
                shaderColorAfter,
                shaderColorRestored,
                fogRestored,
                shaderName(shaderBefore),
                shaderName(shaderAfter),
                depthStateString(depthTestBefore, depthFuncBefore, depthMaskBefore, blendBefore, cullBefore),
                depthStateString(
                        GL11.glIsEnabled(GL11.GL_DEPTH_TEST),
                        GL11.glGetInteger(GL11.GL_DEPTH_FUNC),
                        GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK),
                        GL11.glIsEnabled(GL11.GL_BLEND),
                        GL11.glIsEnabled(GL11.GL_CULL_FACE)
                ),
                vanillaRenderSkyCalled,
                neoforgeSkyHookCalled,
                irisLoaded,
                shaderPackActive,
                shaderColorRestored && fogRestored && shaderColorString(RenderSystem.getShaderColor()).equals("1.000,1.000,1.000,1.000"),
                SkyesightLevelRendererSkyDebug.summary()
        );
    }

    private static void renderRealLevelRendererSkyNoClear(
            SecondaryViewFrame frame,
            Minecraft minecraft,
            float partialTick,
            Runnable reassertStencilRead
    ) {
        if (minecraft.level == null) {
            throw new IllegalStateException("no client level");
        }

        SkyesightClonedSkyRenderer.resetDiagnosticsForExternalSky();
        SkyesightLevelRendererSkyDebug.reset();

        // Match LevelRenderer.renderLevel's sky setup, excluding only the framebuffer clear.
        FogRenderer.setupColor(
                frame.camera(),
                partialTick,
                minecraft.level,
                minecraft.options.getEffectiveRenderDistance(),
                minecraft.gameRenderer.getDarkenWorldAmount(partialTick)
        );
        FogRenderer.levelFogColor();

        float renderDistance = minecraft.gameRenderer.getRenderDistance();
        boolean foggy = minecraft.level.effects().isFoggyAt(
                Mth.floor(frame.camera().getPosition().x()),
                Mth.floor(frame.camera().getPosition().y())
        ) || minecraft.gui.getBossOverlay().shouldCreateWorldFog();

        Runnable skyFogSetup = () -> {
            reassertStencilRead.run();
            FogRenderer.setupFog(
                    frame.camera(),
                    FogRenderer.FogMode.FOG_SKY,
                    renderDistance,
                    foggy,
                    partialTick
            );
            RenderSystem.setShader(GameRenderer::getPositionShader);
        };

        skyFogSetup.run();
        RenderSystem.setShader(GameRenderer::getPositionShader);
        ((LevelRendererSkyInvoker) minecraft.levelRenderer).skyesight$renderSky(
                frame.modelViewMatrix(),
                frame.projectionMatrix(),
                partialTick,
                frame.camera(),
                foggy,
                skyFogSetup
        );
    }

    private static void renderVanillaComponentsNoClear(
            SecondaryViewFrame frame,
            Minecraft minecraft,
            float partialTick,
            Runnable reassertStencilRead
    ) {
        if (minecraft.level == null) {
            throw new IllegalStateException("no client level");
        }

        SkyesightClonedSkyRenderer.resetDiagnosticsForExternalSky();
        FogRenderer.setupColor(
                frame.camera(),
                partialTick,
                minecraft.level,
                minecraft.options.getEffectiveRenderDistance(),
                minecraft.gameRenderer.getDarkenWorldAmount(partialTick)
        );
        FogRenderer.levelFogColor();

        float renderDistance = minecraft.gameRenderer.getRenderDistance();
        boolean foggy = minecraft.level.effects().isFoggyAt(
                Mth.floor(frame.camera().getPosition().x()),
                Mth.floor(frame.camera().getPosition().y())
        ) || minecraft.gui.getBossOverlay().shouldCreateWorldFog();
        FogRenderer.setupFog(
                frame.camera(),
                FogRenderer.FogMode.FOG_SKY,
                renderDistance,
                foggy,
                partialTick
        );
        reassertStencilRead.run();

        SkyesightClonedSkyRenderer.renderSky(
                minecraft.level,
                frame.camera(),
                frame.modelViewMatrix(),
                frame.projectionMatrix(),
                partialTick,
                reassertStencilRead
        );
    }

    private static void restoreFramebuffer(Minecraft minecraft, int framebufferBefore) {
        int mainFramebuffer = minecraft.getMainRenderTarget().frameBufferId;
        if (framebufferBefore == mainFramebuffer) {
            minecraft.getMainRenderTarget().bindWrite(false);
            return;
        }

        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebufferBefore);
    }

    private static int[] readViewport() {
        int[] viewport = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        return viewport;
    }

    private static String viewportString(int[] viewport) {
        return viewport[0] + "," + viewport[1] + "," + viewport[2] + "x" + viewport[3];
    }

    private static String describeStencil() {
        return "enabled=" + GL11.glIsEnabled(GL11.GL_STENCIL_TEST);
    }

    private static String shaderColorString(float[] color) {
        return String.format(
                java.util.Locale.ROOT,
                "%.3f,%.3f,%.3f,%.3f",
                color[0],
                color[1],
                color[2],
                color[3]
        );
    }

    private static String shaderName(ShaderInstance shader) {
        return shader == null ? "null" : String.valueOf(shader.getName());
    }

    private static String depthStateString(
            boolean depthTest,
            int depthFunc,
            boolean depthMask,
            boolean blend,
            boolean cull
    ) {
        return "depth="
                + depthTest
                + " func="
                + depthFunc
                + " mask="
                + depthMask
                + " blend="
                + blend
                + " cull="
                + cull;
    }

    public record Result(
            boolean attempted,
            boolean succeeded,
            String exception,
            String stencilBefore,
            String stencilAfter,
            int targetBefore,
            int targetAfter,
            String viewportBefore,
            String viewportAfter,
            boolean skyChangedStencil,
            boolean skyChangedTarget,
            boolean skyChangedViewport,
            boolean upperSkyRendered,
            boolean darkLowerSkyRendered,
            boolean sunMoonAttempted,
            boolean starsAttempted,
            boolean dimensionSpecialSkyRendered,
            String mode,
            String shaderColorBefore,
            String shaderColorAfter,
            boolean shaderColorRestored,
            boolean fogRestored,
            String shaderBefore,
            String shaderAfter,
            String renderStateBefore,
            String renderStateAfter,
            boolean vanillaRenderSkyCalled,
            boolean neoforgeSkyHookCalled,
            boolean irisLoaded,
            boolean shaderPackActive,
            boolean guiTintLeakGuardRestored,
            String levelRendererSkySummary
    ) {}
}
