package com.skyeshade.skyesight.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.skyeshade.skyesight.Skyesight;
import com.skyeshade.skyesight.SkyesightDebugConfig;
import com.skyeshade.skyesight.client.portal.PortalFrame;
import com.skyeshade.skyesight.client.world.SkyesightVisualParticleManager;
import com.skyeshade.skyesight.client.world.SkyesightVisualParticleWatch;
import com.skyeshade.skyesight.mixin.client.ParticleAccessor;
import com.skyeshade.skyesight.mixin.client.ParticleEngineAccessor;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;

public final class SecondaryParticlePass {
    private static final boolean DEBUG_VERBOSE_PORTAL_PARTICLE_DIAGNOSTICS = false;
    private static final boolean DEBUG_PORTAL_PARTICLES_VERBOSE = false;
    private static final double DEFAULT_PARTICLE_RENDER_MARGIN = 16.0D;
    private static final double FOREGROUND_PORTAL_DEPTH_MARGIN = 0.75D;
    private static final double FOREGROUND_PORTAL_AABB_MARGIN = 2.0D;
    private static final ParticleRenderType[] VANILLA_PARTICLE_RENDER_ORDER = {
            ParticleRenderType.TERRAIN_SHEET,
            ParticleRenderType.PARTICLE_SHEET_OPAQUE,
            ParticleRenderType.PARTICLE_SHEET_LIT,
            ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT,
            ParticleRenderType.CUSTOM
    };
    private static long lastParticleLogMillis;
    private static long lastFramebufferMismatchLogMillis;

    private SecondaryParticlePass() {}

    public static Result render(SecondaryViewFrame frame, Minecraft minecraft, float partialTick) {
        return render(frame, minecraft, partialTick, RenderGroup.ALL);
    }

    public static Result render(
            SecondaryViewFrame frame,
            Minecraft minecraft,
            float partialTick,
            RenderGroup renderGroup
    ) {
        if (minecraft.level == null) {
            return Result.empty();
        }

        ParticleEngine engine = minecraft.particleEngine;
        ParticleEngineAccessor engineAccessor = (ParticleEngineAccessor) engine;
        Map<ParticleRenderType, Queue<Particle>> particles = engineAccessor.skyesight$getParticles();
        Vec3 cameraPos = frame.camera().getPosition();
        int radiusChunks = Math.max(0, frame.diagnostics().entityChunkRadius());
        double radiusBlocks = radiusChunks * 16.0D + DEFAULT_PARTICLE_RENDER_MARGIN;
        double radiusSquared = radiusBlocks * radiusBlocks;
        int framebuffer = GL30.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        int stencilRef = safeGetStencilRef();
        String depthBefore = captureDepthState();
        Matrix4f projectionBefore = new Matrix4f(RenderSystem.getProjectionMatrix());
        var modelViewStack = RenderSystem.getModelViewStack();
        int totalParticles = 0;
        int candidates = 0;
        int inRange = 0;
        int rendered = 0;
        int skippedOutOfRange = 0;
        int skippedWrongDim = 0;
        int skippedRenderType = 0;
        int renderTypeCount = 0;
        StringBuilder renderTypes = new StringBuilder();
        String exception = "";
        int glBefore = GL11.glGetError();
        LightTexture lightTexture = minecraft.gameRenderer.lightTexture();
        boolean lightLayerOn = false;

        modelViewStack.pushMatrix();

        try {
            RenderSystem.setProjectionMatrix(frame.projectionMatrix(), VertexSorting.DISTANCE_TO_ORIGIN);
            modelViewStack.identity();
            modelViewStack.mul(frame.modelViewMatrix());
            RenderSystem.applyModelViewMatrix();

            lightTexture.turnOnLightLayer();
            lightLayerOn = true;
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            RenderSystem.activeTexture(org.lwjgl.opengl.GL13.GL_TEXTURE2);
            RenderSystem.activeTexture(org.lwjgl.opengl.GL13.GL_TEXTURE0);

            for (Map.Entry<ParticleRenderType, Queue<Particle>> entry : particles.entrySet()) {
                ParticleRenderType renderType = entry.getKey();
                Queue<Particle> queue = entry.getValue();

                if (renderType == ParticleRenderType.NO_RENDER || queue == null || queue.isEmpty()) {
                    skippedRenderType += queue == null ? 0 : queue.size();
                    continue;
                }

                if (!supportedRenderType(renderType)) {
                    skippedRenderType += queue.size();
                    appendRenderType(renderTypes, renderType, queue.size(), 0);
                    continue;
                }
                if (!renderGroup.accepts(renderType)) {
                    skippedRenderType += queue.size();
                    appendRenderType(renderTypes, renderType, queue.size(), 0);
                    continue;
                }

                RenderSystem.setShader(GameRenderer::getParticleShader);
                BufferBuilder buffer = renderType.begin(Tesselator.getInstance(), engineAccessor.skyesight$getTextureManager());
                int renderedForType = 0;

                if (buffer == null) {
                    skippedRenderType += queue.size();
                    appendRenderType(renderTypes, renderType, queue.size(), 0);
                    continue;
                }

                for (Particle particle : queue) {
                    totalParticles++;
                    ParticleAccessor particleAccessor = (ParticleAccessor) particle;

                    if (particleAccessor.skyesight$isRemoved()) {
                        continue;
                    }

                    candidates++;
                    Vec3 position = particlePosition(particleAccessor, partialTick);
                    double horizontalDistanceSquared = horizontalDistanceSquared(position, cameraPos);

                    if (horizontalDistanceSquared > radiusSquared) {
                        logMainWatchRender(frame, particle, renderType, position, false, "out-of-range");
                        skippedOutOfRange++;
                        continue;
                    }

                    inRange++;

                    if (!frame.frustum().isVisible(particle.getRenderBoundingBox(partialTick))) {
                        logMainWatchRender(frame, particle, renderType, position, false, "frustum-rejected");
                        skippedOutOfRange++;
                        continue;
                    }

                    particle.render(buffer, frame.camera(), partialTick);
                    logMainWatchRender(frame, particle, renderType, position, true, "-");
                    rendered++;
                    renderedForType++;
                }

                MeshData mesh = buffer.build();
                if (mesh != null) {
                    BufferUploader.drawWithShader(mesh);
                }
                renderTypeCount++;
                appendRenderType(renderTypes, renderType, queue.size(), renderedForType);
            }
        } catch (RuntimeException runtimeException) {
            exception = runtimeException.getClass().getSimpleName() + ": " + runtimeException.getMessage();
        } finally {
            if (lightLayerOn) {
                lightTexture.turnOffLightLayer();
            }
            modelViewStack.popMatrix();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(projectionBefore, VertexSorting.DISTANCE_TO_ORIGIN);
            RenderSystem.disableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        }

        int glAfter = GL11.glGetError();
        Result result = new Result(
                true,
                exception,
                totalParticles,
                candidates,
                inRange,
                rendered,
                skippedOutOfRange,
                skippedWrongDim,
                skippedRenderType,
                "-",
                renderGroup.name(),
                renderTypes.isEmpty() ? "-" : renderTypes.toString(),
                framebuffer,
                stencilRef,
                depthBefore,
                captureDepthState(),
                glBefore,
                glAfter,
                renderTypeCount,
                0,
                0,
                0,
                "-"
        );
        logIfDue(frame, result);
        return result;
    }

    public static Result renderVisualWorldParticles(
            SecondaryViewFrame frame,
            Minecraft minecraft,
            ClientLevel visualLevel,
            SkyesightVisualParticleManager particleManager,
            float partialTick,
            RenderGroup renderGroup,
            int expectedFramebufferId,
            int stencilRef
    ) {
        if (frame == null || frame.camera() == null || minecraft == null || visualLevel == null || particleManager == null) {
            return Result.empty();
        }

        Vec3 cameraPos = frame.camera().getPosition();
        ParticleEngine engine = minecraft.particleEngine;
        ParticleEngineAccessor engineAccessor = (ParticleEngineAccessor) engine;
        ClientLevel previousEngineLevel = engineAccessor.skyesight$getLevel();
        int framebufferBefore = GL30.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        String depthBefore = captureDepthState();
        Matrix4f projectionBefore = new Matrix4f(RenderSystem.getProjectionMatrix());
        var modelViewStack = RenderSystem.getModelViewStack();
        int totalParticles = 0;
        int candidates = 0;
        int inRange = 0;
        int rendered = 0;
        int skippedOutOfRange = 0;
        int skippedRenderType = 0;
        int glBefore = GL11.glGetError();
        String exception = "";
        StringBuilder sample = new StringBuilder();
        StringBuilder renderTypes = new StringBuilder();
        double radiusBlocks = 96.0D;
        double radiusSquared = radiusBlocks * radiusBlocks;
        Map<ParticleRenderType, List<Particle>> particlesByRenderType = new LinkedHashMap<>();
        Map<Particle, SkyesightVisualParticleManager.VisualParticle> visualParticleByParticle = new IdentityHashMap<>();
        Map<Particle, Boolean> particleCreatedThisFrame = new IdentityHashMap<>();
        int renderTypeCount = 0;
        int fallbackMarkers = 0;
        int instancesCreated = 0;
        int instancesReused = 0;
        int instancesRemoved = 0;
        StringBuilder particleSample = new StringBuilder();
        LightTexture lightTexture = minecraft.gameRenderer.lightTexture();
        boolean lightLayerOn = false;
        boolean renderDebugMarkers = SkyesightDebugConfig.VERBOSE_RENDER || SkyesightDebugConfig.WATCH_DEBUG;

        modelViewStack.pushMatrix();

        try {
            if (expectedFramebufferId > 0 && GL30.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING) != expectedFramebufferId) {
                logFramebufferMismatchIfDue(expectedFramebufferId, GL30.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING));
                GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, expectedFramebufferId);
            }

            RenderSystem.setProjectionMatrix(frame.projectionMatrix(), VertexSorting.DISTANCE_TO_ORIGIN);
            modelViewStack.identity();
            modelViewStack.mul(frame.modelViewMatrix());
            RenderSystem.applyModelViewMatrix();
            lightTexture.turnOnLightLayer();
            lightLayerOn = true;
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
            RenderSystem.depthMask(false);
            RenderSystem.disableCull();
            RenderSystem.colorMask(true, true, true, true);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            RenderSystem.activeTexture(org.lwjgl.opengl.GL13.GL_TEXTURE2);
            RenderSystem.activeTexture(org.lwjgl.opengl.GL13.GL_TEXTURE0);
            engineAccessor.skyesight$setLevel(visualLevel);

            for (SkyesightVisualParticleManager.VisualParticle particle : particleManager.particles()) {
                totalParticles++;

                ParticleOptions particleOptions = particle.particleOptions();
                if (particleOptions == null) {
                    logWatchRenderFlow(frame, particle, null, false, false, "null-particle-options");
                    skippedRenderType++;
                    continue;
                }

                candidates++;
                Particle renderParticle = particle.clientParticle();
                boolean createdThisFrame = false;

                if (renderParticle != null && !renderParticle.isAlive()) {
                    instancesRemoved++;
                    logWatchRenderFlow(frame, particle, renderParticle, false, false, "client-particle-dead");
                    skippedRenderType++;
                    continue;
                }

                Vec3 position = renderParticle == null
                        ? particle.renderPosition(partialTick)
                        : particlePosition((ParticleAccessor) renderParticle, partialTick);
                if (horizontalDistanceSquared(position, cameraPos) > radiusSquared) {
                    logWatchRenderFlow(frame, particle, renderParticle, false, false, "out-of-range");
                    skippedOutOfRange++;
                    continue;
                }

                inRange++;

                if (renderParticle == null) {
                    renderParticle = engineAccessor.skyesight$makeParticle(
                            particleOptions,
                            position.x(),
                            position.y(),
                            position.z(),
                            particle.velocity().x(),
                            particle.velocity().y(),
                            particle.velocity().z()
                    );
                    createdThisFrame = true;
                    if (renderParticle != null) {
                        particle.attachClientParticle(renderParticle);
                        instancesCreated++;
                    }
                } else {
                    instancesReused++;
                }

                if (renderParticle == null) {
                    logWatchRenderFlow(frame, particle, null, false, false, "provider-missing");
                    if (renderDebugMarkers) {
                        fallbackMarkers++;
                    }
                    skippedRenderType++;
                    continue;
                }

                ParticleRenderType particleRenderType = renderParticle.getRenderType();
                appendParticleSampleIfNeeded(particleSample, particle, renderParticle, createdThisFrame, particleRenderType, partialTick);
                if (particleRenderType == ParticleRenderType.NO_RENDER || !supportedRenderType(particleRenderType) || !renderGroup.accepts(particleRenderType)) {
                    logWatchRenderFlow(frame, particle, renderParticle, createdThisFrame, false, "render-type-rejected:" + shortRenderType(particleRenderType));
                    skippedRenderType++;
                    continue;
                }

                if (!frame.frustum().isVisible(renderParticle.getRenderBoundingBox(partialTick))) {
                    logWatchRenderFlow(frame, particle, renderParticle, createdThisFrame, false, "frustum-rejected");
                    skippedOutOfRange++;
                    continue;
                }

                particlesByRenderType.computeIfAbsent(particleRenderType, ignored -> new ArrayList<>()).add(renderParticle);
                visualParticleByParticle.put(renderParticle, particle);
                particleCreatedThisFrame.put(renderParticle, createdThisFrame);

                if (sample.length() < 240) {
                    if (sample.length() > 0) {
                        sample.append(';');
                    }
                    sample.append(formatVec(position));
                }
            }

        for (ParticleRenderType particleRenderType : VANILLA_PARTICLE_RENDER_ORDER) {
                List<Particle> renderParticles = particlesByRenderType.get(particleRenderType);
                if (renderParticles == null || renderParticles.isEmpty()) {
                    continue;
                }

                RenderSystem.setShader(GameRenderer::getParticleShader);
                BufferBuilder buffer = particleRenderType.begin(Tesselator.getInstance(), engineAccessor.skyesight$getTextureManager());
                int renderedForType = 0;

                if (buffer == null) {
                    skippedRenderType += renderParticles.size();
                    appendRenderType(renderTypes, particleRenderType, renderParticles.size(), 0);
                    continue;
                }

                for (Particle renderParticle : renderParticles) {
                    renderParticle.render(buffer, frame.camera(), partialTick);
                    logWatchRenderFlow(frame, visualParticleByParticle.get(renderParticle), renderParticle, particleCreatedThisFrame.getOrDefault(renderParticle, false), true, "-");
                    rendered++;
                    renderedForType++;
                }

                MeshData mesh = buffer.build();
                if (mesh != null) {
                    BufferUploader.drawWithShader(mesh);
                }
                renderTypeCount++;
                appendRenderType(renderTypes, particleRenderType, renderParticles.size(), renderedForType);
            }

            if (fallbackMarkers > 0 && renderDebugMarkers) {
                renderVisualParticleMarkers(frame, particleManager, partialTick, renderGroup, cameraPos, radiusSquared);
                appendDebugRenderType(renderTypes, "debugMarkers", fallbackMarkers, fallbackMarkers);
            }
        } catch (RuntimeException runtimeException) {
            exception = runtimeException.getClass().getSimpleName() + ": " + runtimeException.getMessage();
        } finally {
            engineAccessor.skyesight$setLevel(previousEngineLevel);
            if (lightLayerOn) {
                lightTexture.turnOffLightLayer();
            }
            modelViewStack.popMatrix();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(projectionBefore, VertexSorting.DISTANCE_TO_ORIGIN);
            RenderSystem.disableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            if (expectedFramebufferId > 0 && GL30.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING) != expectedFramebufferId) {
                GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, expectedFramebufferId);
            }
        }

        int glAfter = GL11.glGetError();
        return new Result(
                true,
                exception,
                totalParticles,
                candidates,
                inRange,
                rendered,
                skippedOutOfRange,
                0,
                skippedRenderType,
                sample.length() == 0 ? "-" : sample.toString(),
                renderGroup.name(),
                (renderTypes.isEmpty() ? "properParticles=" + rendered + "/" + totalParticles : renderTypes.toString())
                        + ";debugMarkers=" + fallbackMarkers,
                expectedFramebufferId > 0 ? expectedFramebufferId : framebufferBefore,
                stencilRef,
                depthBefore,
                captureDepthState(),
                glBefore,
                glAfter,
                renderTypeCount,
                instancesCreated,
                instancesReused,
                instancesRemoved,
                particleSample.length() == 0 ? "-" : particleSample.toString()
        );
    }

    private static void renderVisualParticleMarkers(
            SecondaryViewFrame frame,
            SkyesightVisualParticleManager particleManager,
            float partialTick,
            RenderGroup renderGroup,
            Vec3 cameraPos,
            double radiusSquared
    ) {
        Quaternionf cameraRotation = new Quaternionf(frame.camera().rotation());
        Vector3f right = new Vector3f(1.0F, 0.0F, 0.0F).rotate(cameraRotation);
        Vector3f up = new Vector3f(0.0F, 1.0F, 0.0F).rotate(cameraRotation);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        for (SkyesightVisualParticleManager.VisualParticle particle : particleManager.particles()) {
            if (renderGroup == RenderGroup.OPAQUE && particle.translucent()) {
                continue;
            }
            if (renderGroup == RenderGroup.TRANSLUCENT && !particle.translucent()) {
                continue;
            }

            Vec3 position = particle.renderPosition(partialTick);
            if (horizontalDistanceSquared(position, cameraPos) > radiusSquared) {
                continue;
            }

            appendVisualParticleQuad(buffer, position.subtract(cameraPos), right, up, particle);
        }

        MeshData mesh = buffer.build();
        if (mesh != null) {
            BufferUploader.drawWithShader(mesh);
        }
    }

    public static Result renderMainCameraForegroundOverlay(
            Minecraft minecraft,
            Camera mainCamera,
            Matrix4f projectionMatrix,
            Matrix4f modelViewMatrix,
            PortalFrame portal,
            float partialTick,
            int stencilRef
    ) {
        if (minecraft.level == null || mainCamera == null || portal == null) {
            return Result.empty();
        }

        ParticleEngine engine = minecraft.particleEngine;
        ParticleEngineAccessor engineAccessor = (ParticleEngineAccessor) engine;
        Map<ParticleRenderType, Queue<Particle>> particles = engineAccessor.skyesight$getParticles();
        Vec3 cameraPos = mainCamera.getPosition();
        PortalPlaneFilter portalFilter = PortalPlaneFilter.create(portal, cameraPos);
        int framebuffer = GL30.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        String depthBefore = captureDepthState();
        Matrix4f projectionBefore = new Matrix4f(RenderSystem.getProjectionMatrix());
        var modelViewStack = RenderSystem.getModelViewStack();
        int totalParticles = 0;
        int candidates = 0;
        int inRange = 0;
        int rendered = 0;
        int skippedOutOfRange = 0;
        int skippedBehindPortal = 0;
        int skippedRenderType = 0;
        int renderTypeCount = 0;
        StringBuilder renderTypes = new StringBuilder();
        String exception = "";
        int glBefore = GL11.glGetError();
        LightTexture lightTexture = minecraft.gameRenderer.lightTexture();
        boolean lightLayerOn = false;

        modelViewStack.pushMatrix();

        try {
            RenderSystem.setProjectionMatrix(projectionMatrix, VertexSorting.DISTANCE_TO_ORIGIN);
            modelViewStack.identity();
            modelViewStack.mul(modelViewMatrix);
            RenderSystem.applyModelViewMatrix();

            lightTexture.turnOnLightLayer();
            lightLayerOn = true;
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.disableCull();
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            RenderSystem.activeTexture(org.lwjgl.opengl.GL13.GL_TEXTURE2);
            RenderSystem.activeTexture(org.lwjgl.opengl.GL13.GL_TEXTURE0);

            for (Map.Entry<ParticleRenderType, Queue<Particle>> entry : particles.entrySet()) {
                ParticleRenderType renderType = entry.getKey();
                Queue<Particle> queue = entry.getValue();

                if (renderType == ParticleRenderType.NO_RENDER || queue == null || queue.isEmpty()) {
                    skippedRenderType += queue == null ? 0 : queue.size();
                    continue;
                }

                if (!supportedRenderType(renderType)) {
                    skippedRenderType += queue.size();
                    appendRenderType(renderTypes, renderType, queue.size(), 0);
                    continue;
                }

                RenderSystem.setShader(GameRenderer::getParticleShader);
                BufferBuilder buffer = renderType.begin(Tesselator.getInstance(), engineAccessor.skyesight$getTextureManager());
                int renderedForType = 0;

                if (buffer == null) {
                    skippedRenderType += queue.size();
                    appendRenderType(renderTypes, renderType, queue.size(), 0);
                    continue;
                }

                for (Particle particle : queue) {
                    totalParticles++;
                    ParticleAccessor particleAccessor = (ParticleAccessor) particle;

                    if (particleAccessor.skyesight$isRemoved()) {
                        continue;
                    }

                    Vec3 position = particlePosition(particleAccessor, partialTick);

                    ForegroundParticleTest foreground = portalFilter.test(position);

                    if (!foreground.insidePortalAperture()) {
                        skippedOutOfRange++;
                        continue;
                    }

                    candidates++;

                    if (!foreground.inFrontOfPortalPlane()) {
                        skippedBehindPortal++;
                        continue;
                    }

                    inRange++;
                    particle.render(buffer, mainCamera, partialTick);
                    rendered++;
                    renderedForType++;
                }

                MeshData mesh = buffer.build();
                if (mesh != null) {
                    BufferUploader.drawWithShader(mesh);
                }
                renderTypeCount++;
                appendRenderType(renderTypes, renderType, queue.size(), renderedForType);
            }
        } catch (RuntimeException runtimeException) {
            exception = runtimeException.getClass().getSimpleName() + ": " + runtimeException.getMessage();
        } finally {
            if (lightLayerOn) {
                lightTexture.turnOffLightLayer();
            }
            modelViewStack.popMatrix();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(projectionBefore, VertexSorting.DISTANCE_TO_ORIGIN);
            RenderSystem.disableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        }

        int glAfter = GL11.glGetError();
        return new Result(
                true,
                exception,
                totalParticles,
                candidates,
                inRange,
                rendered,
                skippedOutOfRange,
                skippedBehindPortal,
                skippedRenderType,
                portalFilter.sampleSummary(),
                RenderGroup.FOREGROUND_OVERLAY.name(),
                renderTypes.isEmpty() ? "-" : renderTypes.toString(),
                framebuffer,
                stencilRef,
                depthBefore,
                captureDepthState(),
                glBefore,
                glAfter,
                renderTypeCount,
                0,
                0,
                0,
                "-"
        );
    }

    private static void appendParticleSampleIfNeeded(
            StringBuilder sample,
            SkyesightVisualParticleManager.VisualParticle visualParticle,
            Particle renderParticle,
            boolean createdThisFrame,
            ParticleRenderType renderType,
            float partialTick
    ) {
        if (!SkyesightDebugConfig.VERBOSE_RENDER || sample.length() >= 900) {
            return;
        }

        ParticleAccessor accessor = (ParticleAccessor) renderParticle;
        Vec3 pos = particlePosition(accessor, partialTick);
        Vec3 prev = new Vec3(accessor.skyesight$getXo(), accessor.skyesight$getYo(), accessor.skyesight$getZo());

        if (sample.length() > 0) {
            sample.append(" | ");
        }

        sample.append("type=")
                .append(visualParticle.particleId())
                .append(" key=")
                .append(visualParticle.cacheKey())
                .append(" createdThisFrame=")
                .append(yesNo(createdThisFrame))
                .append(" age=")
                .append(accessor.skyesight$getAge())
                .append(" lifetime=")
                .append(accessor.skyesight$getLifetime())
                .append(" quadSize=")
                .append(renderParticle instanceof SingleQuadParticle singleQuadParticle
                        ? String.format(java.util.Locale.ROOT, "%.3f", singleQuadParticle.getQuadSize(partialTick))
                        : "n/a")
                .append(" sprite=provider-stable")
                .append(" pos=")
                .append(formatVec(pos))
                .append(" prevPos=")
                .append(formatVec(prev))
                .append(" renderType=")
                .append(shortRenderType(renderType));
    }

    private static void logWatchRenderFlow(
            SecondaryViewFrame frame,
            SkyesightVisualParticleManager.VisualParticle visualParticle,
            Particle renderParticle,
            boolean providerCreated,
            boolean rendered,
            String rejectReason
    ) {
        if (visualParticle == null || !visualParticle.watched()) {
            return;
        }

        var viewId = frame == null || frame.diagnostics() == null
                ? null
                : frame.diagnostics().entityWatchRegionId();
        if (!SkyesightVisualParticleWatch.matches(viewId)) {
            return;
        }

        ParticleRenderType renderType = renderParticle == null ? null : renderParticle.getRenderType();
        Vec3 pos = renderParticle == null
                ? visualParticle.renderPosition(1.0F)
                : renderParticle.getPos();
        SkyesightVisualParticleWatch.recordRender(viewId, providerCreated, rendered);
        Skyesight.LOGGER.info(
                "[Skyesight] PORTAL_PARTICLE_WATCH_RENDER: viewId={} type={} pos={} instanceExists={} providerCreated={} renderType={} rendered={} rejectReason={}",
                viewId == null ? "-" : viewId,
                visualParticle.particleId(),
                formatVec(pos),
                renderParticle == null ? "no" : "yes",
                yesNo(providerCreated),
                renderType == null ? "-" : shortRenderType(renderType),
                yesNo(rendered),
                rejectReason == null || rejectReason.isBlank() ? "-" : rejectReason
        );
    }

    private static void logMainWatchRender(
            SecondaryViewFrame frame,
            Particle particle,
            ParticleRenderType renderType,
            Vec3 position,
            boolean rendered,
            String rejectReason
    ) {
        var viewId = frame == null || frame.diagnostics() == null
                ? null
                : frame.diagnostics().entityWatchRegionId();
        if (!SkyesightVisualParticleWatch.near(viewId, position, 4.0D)) {
            return;
        }

        SkyesightVisualParticleWatch.recordRender(viewId, false, rendered);
        Skyesight.LOGGER.info(
                "[Skyesight] PORTAL_PARTICLE_WATCH_RENDER: viewId={} type={} pos={} instanceExists=yes providerCreated=no renderType={} rendered={} rejectReason={}",
                viewId == null ? "-" : viewId,
                particle == null ? "unknown" : particle.getClass().getName(),
                formatVec(position),
                renderType == null ? "-" : shortRenderType(renderType),
                yesNo(rendered),
                rejectReason == null || rejectReason.isBlank() ? "-" : rejectReason
        );
    }

    private static Vec3 particlePosition(ParticleAccessor particle, float partialTick) {
        return new Vec3(
                Mth.lerp(partialTick, particle.skyesight$getXo(), particle.skyesight$getX()),
                Mth.lerp(partialTick, particle.skyesight$getYo(), particle.skyesight$getY()),
                Mth.lerp(partialTick, particle.skyesight$getZo(), particle.skyesight$getZ())
        );
    }

    private static double horizontalDistanceSquared(Vec3 particlePosition, Vec3 cameraPosition) {
        double dx = particlePosition.x() - cameraPosition.x();
        double dz = particlePosition.z() - cameraPosition.z();
        return dx * dx + dz * dz;
    }

    private static void appendVisualParticleQuad(
            BufferBuilder buffer,
            Vec3 center,
            Vector3f right,
            Vector3f up,
            SkyesightVisualParticleManager.VisualParticle particle
    ) {
        float size = particle.size();
        SkyesightVisualParticleManager.ParticleColor color = particle.color();
        int red = colorByte(color.red());
        int green = colorByte(color.green());
        int blue = colorByte(color.blue());
        int alpha = colorByte(color.alpha());
        Vector3f r = new Vector3f(right).mul(size);
        Vector3f u = new Vector3f(up).mul(size);
        float cx = (float) center.x();
        float cy = (float) center.y();
        float cz = (float) center.z();
        addVisualParticleVertex(buffer, cx - r.x - u.x, cy - r.y - u.y, cz - r.z - u.z, red, green, blue, alpha);
        addVisualParticleVertex(buffer, cx + r.x - u.x, cy + r.y - u.y, cz + r.z - u.z, red, green, blue, alpha);
        addVisualParticleVertex(buffer, cx + r.x + u.x, cy + r.y + u.y, cz + r.z + u.z, red, green, blue, alpha);
        addVisualParticleVertex(buffer, cx - r.x + u.x, cy - r.y + u.y, cz - r.z + u.z, red, green, blue, alpha);
    }

    private static void addVisualParticleVertex(BufferBuilder buffer, float x, float y, float z, int red, int green, int blue, int alpha) {
        buffer.addVertex(x, y, z).setColor(red, green, blue, alpha);
    }

    private static int colorByte(float value) {
        return Mth.clamp((int) (value * 255.0F), 0, 255);
    }

    private static String formatVec(Vec3 value) {
        return String.format(java.util.Locale.ROOT, "%.2f,%.2f,%.2f", value.x(), value.y(), value.z());
    }

    private static boolean supportedRenderType(ParticleRenderType renderType) {
        return renderType == ParticleRenderType.PARTICLE_SHEET_OPAQUE
                || renderType == ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT
                || renderType == ParticleRenderType.PARTICLE_SHEET_LIT
                || renderType == ParticleRenderType.TERRAIN_SHEET;
    }

    private static void appendRenderType(StringBuilder builder, ParticleRenderType type, int total, int rendered) {
        if (builder.length() > 0) {
            builder.append(";");
        }

        builder.append(shortRenderType(type))
                .append("=")
                .append(rendered)
                .append("/")
                .append(total);
    }

    private static String shortRenderType(ParticleRenderType type) {
        if (type == null) {
            return "unknown";
        }

        String text = type.toString();
        int dot = text.lastIndexOf('.');
        return dot >= 0 ? text.substring(dot + 1) : text;
    }

    private static void appendDebugRenderType(StringBuilder builder, String type, int total, int rendered) {
        if (builder.length() > 0) {
            builder.append(";");
        }

        builder.append(type)
                .append("=")
                .append(rendered)
                .append("/")
                .append(total);
    }

    private static int safeGetStencilRef() {
        try {
            return GL11.glGetInteger(GL11.GL_STENCIL_REF);
        } catch (RuntimeException ignored) {
            return -1;
        }
    }

    private static String captureDepthState() {
        return "depthTest=" + yesNo(GL11.glIsEnabled(GL11.GL_DEPTH_TEST))
                + " depthFunc=" + GL11.glGetInteger(GL11.GL_DEPTH_FUNC)
                + " depthMask=" + yesNo(GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK))
                + " stencil=" + yesNo(GL11.glIsEnabled(GL11.GL_STENCIL_TEST))
                + " stencilRef=" + safeGetStencilRef();
    }

    private static void logIfDue(SecondaryViewFrame frame, Result result) {
        if (!DEBUG_PORTAL_PARTICLES_VERBOSE && !DEBUG_VERBOSE_PORTAL_PARTICLE_DIAGNOSTICS) {
            return;
        }

        long now = System.currentTimeMillis();

        if (now - lastParticleLogMillis < 2000L) {
            return;
        }

        lastParticleLogMillis = now;
        Skyesight.LOGGER.info("[Skyesight] Portal particles: {}", result.summary(frame));
    }

    private static void logFramebufferMismatchIfDue(int expectedFramebufferId, int actualFramebufferId) {
        long now = System.currentTimeMillis();

        if (!DEBUG_VERBOSE_PORTAL_PARTICLE_DIAGNOSTICS && now - lastFramebufferMismatchLogMillis < 5_000L) {
            return;
        }

        lastFramebufferMismatchLogMillis = now;
        Skyesight.LOGGER.error(
                "[Skyesight] Cross-dim portal particles framebuffer mismatch expected={} actual={} rebinding visible portal framebuffer",
                expectedFramebufferId,
                actualFramebufferId
        );
    }

    private static String yesNo(boolean value) {
        return value ? "yes" : "no";
    }

    public record Result(
            boolean attempted,
            String exception,
            int totalParticles,
            int candidates,
            int inRange,
            int rendered,
            int skippedOutOfRange,
            int skippedWrongDim,
            int skippedRenderType,
            String sample,
            String renderGroup,
            String renderTypes,
            int framebuffer,
            int stencilRef,
            String depthStateBefore,
            String depthStateAfter,
            int glBefore,
            int glAfter,
            int renderTypeCount,
            int instancesCreated,
            int instancesReused,
            int instancesRemoved,
            String particleSample
    ) {
        private static Result empty() {
            return new Result(false, "", 0, 0, 0, 0, 0, 0, 0, "-", "ALL", "-", -1, -1, "n/a", "n/a", GL11.GL_NO_ERROR, GL11.GL_NO_ERROR, 0, 0, 0, 0, "-");
        }

        public String summary(SecondaryViewFrame frame) {
            return "enabled=yes"
                    + " source=sameDimParticleEngine"
                    + " sameDimOnly=yes"
                    + " renderGroup=" + this.renderGroup
                    + " totalParticles=" + this.totalParticles
                    + " candidates=" + this.candidates
                    + " inRange=" + this.inRange
                    + " rendered=" + this.rendered
                    + " skippedOutOfRange=" + this.skippedOutOfRange
                    + " skippedWrongDim=" + this.skippedWrongDim
                    + " skippedRenderType=" + this.skippedRenderType
                    + " sample=" + this.sample
                    + " renderTypes=" + this.renderTypes
                    + " instancesCreated=" + this.instancesCreated
                    + " instancesReused=" + this.instancesReused
                    + " instancesRemoved=" + this.instancesRemoved
                    + " framebuffer=" + this.framebuffer
                    + " stencilRef=" + this.stencilRef
                    + " depthStateBefore='" + this.depthStateBefore + "'"
                    + " depthStateAfter='" + this.depthStateAfter + "'"
                    + " camera=" + (frame == null || frame.camera() == null ? "n/a" : frame.camera().getPosition())
                    + " glBefore=" + (this.glBefore == GL11.GL_NO_ERROR ? "none" : this.glBefore)
                    + " glError=" + (this.glAfter == GL11.GL_NO_ERROR ? "none" : this.glAfter)
                    + " exception='" + this.exception + "'";
        }

        public String foregroundSummary() {
            return "enabled=yes"
                    + " mode=afterPortalOverlay"
                    + " renderGroup=" + this.renderGroup
                    + " mainParticlesTotal=" + this.totalParticles
                    + " candidates=" + this.candidates
                    + " rendered=" + this.rendered
                    + " skippedBehindPortal=" + this.skippedWrongDim
                    + " skippedOutsideAperture=" + this.skippedOutOfRange
                    + " skippedRenderType=" + this.skippedRenderType
                    + " sample=" + this.sample
                    + " renderTypes=" + this.renderTypes
                    + " instancesCreated=" + this.instancesCreated
                    + " instancesReused=" + this.instancesReused
                    + " instancesRemoved=" + this.instancesRemoved
                    + " framebuffer=" + this.framebuffer
                    + " stencilRef=" + this.stencilRef
                    + " depthStateBefore='" + this.depthStateBefore + "'"
                    + " depthStateAfter='" + this.depthStateAfter + "'"
                    + " glBefore=" + (this.glBefore == GL11.GL_NO_ERROR ? "none" : this.glBefore)
                    + " glError=" + (this.glAfter == GL11.GL_NO_ERROR ? "none" : this.glAfter)
                    + " exception='" + this.exception + "'";
        }
    }

    public enum RenderGroup {
        ALL,
        OPAQUE,
        TRANSLUCENT,
        FOREGROUND_OVERLAY;

        private boolean accepts(ParticleRenderType renderType) {
            return switch (this) {
                case ALL, FOREGROUND_OVERLAY -> true;
                case OPAQUE -> renderType == ParticleRenderType.PARTICLE_SHEET_OPAQUE
                        || renderType == ParticleRenderType.TERRAIN_SHEET;
                case TRANSLUCENT -> renderType == ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT
                        || renderType == ParticleRenderType.PARTICLE_SHEET_LIT;
            };
        }
    }

    private record PortalPlaneFilter(
            PortalFrame portal,
            double cameraSide,
            double cameraDistanceToPlane,
            double halfWidth,
            double halfHeight,
            Quaternionf inverseRotation,
            StringBuilder sample
    ) {
        private static PortalPlaneFilter create(PortalFrame portal, Vec3 cameraPosition) {
            Quaternionf inverseRotation = new Quaternionf(portal.rotation()).conjugate();
            Vector3f cameraLocal = toLocal(portal, inverseRotation, cameraPosition);
            double cameraSide = Math.abs(cameraLocal.z()) < 1.0E-4D
                    ? 1.0D
                    : Math.signum(cameraLocal.z());
            return new PortalPlaneFilter(
                    portal,
                    cameraSide,
                    Math.abs(cameraLocal.z()) + FOREGROUND_PORTAL_DEPTH_MARGIN,
                    portal.width() * 0.5D + FOREGROUND_PORTAL_AABB_MARGIN,
                    portal.height() * 0.5D + FOREGROUND_PORTAL_AABB_MARGIN,
                    inverseRotation,
                    new StringBuilder()
            );
        }

        private ForegroundParticleTest test(Vec3 particlePosition) {
            Vector3f local = toLocal(this.portal, this.inverseRotation, particlePosition);
            double side = Math.abs(local.z()) < 1.0E-4D ? this.cameraSide : Math.signum(local.z());
            boolean insideAperture = Math.abs(local.x()) <= this.halfWidth
                    && Math.abs(local.y()) <= this.halfHeight;
            boolean inFront = side == this.cameraSide
                    && Math.abs(local.z()) <= this.cameraDistanceToPlane;
            double signedDistance = local.z();

            if (this.sample.length() < 420) {
                if (this.sample.length() > 0) {
                    this.sample.append(";");
                }
                this.sample.append("pos=")
                        .append(formatVec(particlePosition))
                        .append(" local=")
                        .append(formatVec(local))
                        .append(" planeDist=")
                        .append(String.format(java.util.Locale.ROOT, "%.3f", signedDistance))
                        .append(" inFrontOfPortalPlane=")
                        .append(insideAperture && inFront ? "yes" : "no")
                        .append(" insidePortalAperture=")
                        .append(insideAperture ? "yes" : "no");
            }

            return new ForegroundParticleTest(insideAperture, inFront, signedDistance);
        }

        private String sampleSummary() {
            return this.sample.length() == 0 ? "-" : this.sample.toString();
        }

        private static Vector3f toLocal(PortalFrame portal, Quaternionf inverseRotation, Vec3 position) {
            Vector3f local = new Vector3f(
                    (float) (position.x() - portal.position().x()),
                    (float) (position.y() - portal.position().y()),
                    (float) (position.z() - portal.position().z())
            );
            inverseRotation.transform(local);
            return local;
        }

        private static String formatVec(Vec3 vec) {
            return String.format(java.util.Locale.ROOT, "%.2f,%.2f,%.2f", vec.x(), vec.y(), vec.z());
        }

        private static String formatVec(Vector3f vec) {
            return String.format(java.util.Locale.ROOT, "%.2f,%.2f,%.2f", vec.x(), vec.y(), vec.z());
        }
    }

    private record ForegroundParticleTest(
            boolean insidePortalAperture,
            boolean inFrontOfPortalPlane,
            double signedDistanceToPortalPlane
    ) {
    }
}
