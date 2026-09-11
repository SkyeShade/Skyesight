package com.skyeshade.skyesight.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.skyeshade.skyesight.Skyesight;
import com.skyeshade.skyesight.SkyesightDebugConfig;
import com.skyeshade.skyesight.mixin.client.LevelRendererAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL30;

import java.util.HashMap;
import java.util.Map;

public final class CloudVertexNdcDiagnostics {
    private static final long LOG_INTERVAL_NANOS = 1_000_000_000L;
    private static final ThreadLocal<PortalCall> PORTAL_CALL = new ThreadLocal<>();
    private static final Map<String, Anchors> ANCHORS = new HashMap<>();
    private static final Map<String, Long> LAST_NDC_LOG = new HashMap<>();
    private static final Map<String, Translation> PREVIOUS_TRANSLATION = new HashMap<>();

    private CloudVertexNdcDiagnostics() {}

    public static Scope portalCall(String viewId, int renderFrame, int expectedTarget, LevelRenderer levelRenderer) {
        if (!SkyesightDebugConfig.SKY_CAPTURE_AUDIT) {
            return Scope.NO_OP;
        }
        PortalCall previous = PORTAL_CALL.get();
        PortalCall current = new PortalCall(viewId, renderFrame, expectedTarget, levelRenderer);
        PORTAL_CALL.set(current);
        return () -> {
            current.finish();
            if (previous == null) {
                PORTAL_CALL.remove();
            } else {
                PORTAL_CALL.set(previous);
            }
        };
    }

    public static void recordRender(
            LevelRenderer renderer,
            PoseStack poseStack,
            Matrix4f frustum,
            Matrix4f projection,
            float partialTick,
            double camX,
            double camY,
            double camZ
    ) {
        if (!SkyesightDebugConfig.SKY_CAPTURE_AUDIT) {
            return;
        }
        PortalCall portal = PORTAL_CALL.get();
        String source = portal == null ? "main" : "portal";
        String view = portal == null ? "main" : portal.viewId();
        int frame = portal == null ? renderer.getTicks() : portal.renderFrame();
        String key = source + ":" + view;

        ClientLevel level = ((LevelRendererAccessor) renderer).skyesight$getLevel();
        if (level == null || !Float.isFinite(level.effects().getCloudHeight())) {
            return;
        }

        int ticks = renderer.getTicks();
        double animation = (double) (((float) ticks + partialTick) * 0.03F);
        double cloudX = (camX + animation) / 12.0D;
        double cloudY = level.effects().getCloudHeight() - camY + 0.33F;
        double cloudZ = camZ / 12.0D + 0.33F;
        cloudX -= Mth.floor(cloudX / 2048.0D) * 2048.0D;
        cloudZ -= Mth.floor(cloudZ / 2048.0D) * 2048.0D;
        float fractionX = (float) (cloudX - Mth.floor(cloudX));
        float fractionY = (float) (cloudY / 4.0D - Mth.floor(cloudY / 4.0D)) * 4.0F;
        float fractionZ = (float) (cloudZ - Mth.floor(cloudZ));

        Vec3 camera = new Vec3(camX, camY, camZ);
        Anchors anchors = ANCHORS.computeIfAbsent(
                key,
                ignored -> createAnchors(camera, level.effects().getCloudHeight() + 0.33D, frustum)
        );
        Matrix4f entry = new Matrix4f(poseStack.last().pose());
        Matrix4f ordinaryModelView = new Matrix4f(entry).mul(frustum);
        Matrix4f cloudModelView = new Matrix4f(ordinaryModelView)
                .scale(12.0F, 1.0F, 12.0F)
                .translate(-fractionX, fractionY, -fractionZ);
        if (portal != null) {
            portal.cloudModelView = new Matrix4f(cloudModelView);
            portal.projection = new Matrix4f(projection);
        }

        Comparison center = compare(anchors.center(), camera, ordinaryModelView, cloudModelView, projection, fractionX, fractionY, fractionZ);
        Comparison left = compare(anchors.left(), camera, ordinaryModelView, cloudModelView, projection, fractionX, fractionY, fractionZ);
        Comparison right = compare(anchors.right(), camera, ordinaryModelView, cloudModelView, projection, fractionX, fractionY, fractionZ);

        long now = System.nanoTime();
        long last = LAST_NDC_LOG.getOrDefault(key, 0L);
        if (now - last < LOG_INTERVAL_NANOS) {
            return;
        }
        LAST_NDC_LOG.put(key, now);
        Skyesight.LOGGER.info(
                "[Skyesight] CLOUD_NDC_COMPARE source={} view={} frame={} camera={} cell={},{} fraction={},{} rawCenter={} expectedCenter={} cloudCenter={} expectedLeft={} cloudLeft={} expectedRight={} cloudRight={} maxDelta={} entryIdentityDelta={}",
                source,
                view,
                frame,
                compact(camera),
                Mth.floor(cloudX),
                Mth.floor(cloudZ),
                compact(fractionX, fractionZ),
                center.raw(),
                center.expected(),
                center.cloud(),
                left.expected(),
                left.cloud(),
                right.expected(),
                right.cloud(),
                Math.max(center.delta(), Math.max(left.delta(), right.delta())),
                maxIdentityDelta(entry)
        );
    }

    public static void recordDraw(
            VertexBuffer vertexBuffer,
            Matrix4f modelView,
            Matrix4f projection,
            ShaderInstance shader
    ) {
        if (!SkyesightDebugConfig.SKY_CAPTURE_AUDIT) {
            return;
        }
        PortalCall portal = PORTAL_CALL.get();
        if (portal == null || portal.cloudModelView == null || portal.projection == null || portal.drawLogged) {
            return;
        }
        VertexBuffer expectedCloudBuffer = ((LevelRendererAccessor) portal.levelRenderer()).skyesight$getCloudBuffer();
        ShaderInstance expectedCloudShader = GameRenderer.getRendertypeCloudsShader();
        boolean bufferMatch = expectedCloudBuffer != null && vertexBuffer == expectedCloudBuffer;
        boolean shaderMatch = expectedCloudShader != null && shader == expectedCloudShader;
        if (!bufferMatch || !shaderMatch) {
            return;
        }
        portal.drawLogged = true;
        MatrixDelta modelDelta = matrixDelta(modelView, portal.cloudModelView);
        Translation actualTranslation = Translation.from(modelView);
        Translation expectedTranslation = Translation.from(portal.cloudModelView);
        Translation previousTranslation = PREVIOUS_TRANSLATION.put(portal.viewId(), actualTranslation);
        String deltaTranslation = previousTranslation == null
                ? "n/a"
                : actualTranslation.subtract(previousTranslation).toString();
        Skyesight.LOGGER.info(
                "[Skyesight] CLOUD_GPU_STATE view={} frame={} shader={} vertexBuffer={} expectedCloudBuffer={} bufferMatch={} shaderMatch={} framebuffer={} expectedTarget={} shaderTransparency={} maxElement={} actualValue={} expectedValue={} explicitModelDelta={} actualTranslation={} expectedTranslation={} deltaTranslation={} explicitProjectionDelta={} globalProjectionDelta={} globalModelDelta={}",
                portal.viewId(),
                portal.renderFrame(),
                identity(shader, shader.getName()),
                identity(vertexBuffer, "VertexBuffer"),
                identity(expectedCloudBuffer, "VertexBuffer"),
                bufferMatch,
                shaderMatch,
                GL30.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING),
                portal.expectedTarget(),
                Minecraft.useShaderTransparency(),
                modelDelta.element(),
                modelDelta.actual(),
                modelDelta.expected(),
                modelDelta.delta(),
                actualTranslation,
                expectedTranslation,
                deltaTranslation,
                maxDelta(projection, portal.projection),
                maxDelta(RenderSystem.getProjectionMatrix(), portal.projection),
                maxDelta(RenderSystem.getModelViewMatrix(), portal.cloudModelView)
        );
    }

    public static void remove(String viewId) {
        ANCHORS.remove("portal:" + viewId);
        LAST_NDC_LOG.remove("portal:" + viewId);
        PREVIOUS_TRANSLATION.remove(viewId);
    }

    public static void clear() {
        ANCHORS.clear();
        LAST_NDC_LOG.clear();
        PREVIOUS_TRANSLATION.clear();
        PORTAL_CALL.remove();
    }

    private static Anchors createAnchors(Vec3 camera, double cloudY, Matrix4f frustum) {
        Matrix4f cameraToWorld = new Matrix4f(frustum).invert();
        Vector3f forwardVector = cameraToWorld.transformDirection(new Vector3f(0.0F, 0.0F, -1.0F));
        Vector3f rightVector = cameraToWorld.transformDirection(new Vector3f(1.0F, 0.0F, 0.0F));
        Vec3 forward = new Vec3(forwardVector.x(), 0.0D, forwardVector.z()).normalize();
        Vec3 right = new Vec3(rightVector.x(), 0.0D, rightVector.z()).normalize();
        Vec3 center = new Vec3(camera.x(), cloudY, camera.z()).add(forward.scale(128.0D));
        return new Anchors(center, center.subtract(right.scale(40.0D)), center.add(right.scale(40.0D)));
    }

    private static Comparison compare(
            Vec3 world,
            Vec3 camera,
            Matrix4f ordinaryModelView,
            Matrix4f cloudModelView,
            Matrix4f projection,
            float fractionX,
            float fractionY,
            float fractionZ
    ) {
        Vec3 relative = world.subtract(camera);
        Vector4f raw = new Vector4f(
                (float) (relative.x() / 12.0D) + fractionX,
                (float) relative.y() - fractionY,
                (float) (relative.z() / 12.0D) + fractionZ,
                1.0F
        );
        Ndc expected = ndc(new Vector4f((float) relative.x(), (float) relative.y(), (float) relative.z(), 1.0F), ordinaryModelView, projection);
        Ndc cloud = ndc(new Vector4f(raw), cloudModelView, projection);
        return new Comparison(compact(raw.x, raw.y, raw.z), expected, cloud, expected.maxDelta(cloud));
    }

    private static Ndc ndc(Vector4f point, Matrix4f modelView, Matrix4f projection) {
        modelView.transform(point);
        projection.transform(point);
        if (point.w == 0.0F) {
            return new Ndc(Float.NaN, Float.NaN);
        }
        return new Ndc(point.x / point.w, point.y / point.w);
    }

    private static float maxDelta(Matrix4f left, Matrix4f right) {
        return matrixDelta(left, right).delta();
    }

    private static MatrixDelta matrixDelta(Matrix4f actual, Matrix4f expected) {
        float[] a = elements(actual);
        float[] b = elements(expected);
        int maxIndex = 0;
        float max = 0.0F;
        for (int i = 0; i < 16; i++) {
            float delta = Math.abs(a[i] - b[i]);
            if (delta > max) {
                max = delta;
                maxIndex = i;
            }
        }
        return new MatrixDelta(elementName(maxIndex), a[maxIndex], b[maxIndex], max);
    }

    private static float[] elements(Matrix4f matrix) {
        return new float[]{
                matrix.m00(), matrix.m01(), matrix.m02(), matrix.m03(),
                matrix.m10(), matrix.m11(), matrix.m12(), matrix.m13(),
                matrix.m20(), matrix.m21(), matrix.m22(), matrix.m23(),
                matrix.m30(), matrix.m31(), matrix.m32(), matrix.m33()
        };
    }

    private static String elementName(int index) {
        return "m" + (index / 4) + (index % 4);
    }

    private static String identity(Object value, String name) {
        return value == null
                ? "null"
                : name + "@" + Integer.toHexString(System.identityHashCode(value));
    }

    private static float maxIdentityDelta(Matrix4f matrix) {
        return maxDelta(matrix, new Matrix4f());
    }

    private static String compact(Vec3 value) {
        return compact((float) value.x(), (float) value.y(), (float) value.z());
    }

    private static String compact(float x, float y) {
        return String.format(java.util.Locale.ROOT, "(%.6f,%.6f)", x, y);
    }

    private static String compact(float x, float y, float z) {
        return String.format(java.util.Locale.ROOT, "(%.4f,%.4f,%.4f)", x, y, z);
    }

    public interface Scope extends AutoCloseable {
        Scope NO_OP = () -> {};

        @Override
        void close();
    }

    private record Anchors(Vec3 center, Vec3 left, Vec3 right) {}

    private record Comparison(String raw, Ndc expected, Ndc cloud, float delta) {}

    private record MatrixDelta(String element, float actual, float expected, float delta) {}

    private record Translation(float x, float y, float z) {
        private static Translation from(Matrix4f matrix) {
            return new Translation(matrix.m30(), matrix.m31(), matrix.m32());
        }

        private Translation subtract(Translation previous) {
            return new Translation(this.x - previous.x, this.y - previous.y, this.z - previous.z);
        }

        @Override
        public String toString() {
            return compact(this.x, this.y, this.z);
        }
    }

    private record Ndc(float x, float y) {
        private float maxDelta(Ndc other) {
            return Math.max(Math.abs(this.x - other.x), Math.abs(this.y - other.y));
        }

        @Override
        public String toString() {
            return compact(this.x, this.y);
        }
    }

    private static final class PortalCall {
        private final String viewId;
        private final int renderFrame;
        private final int expectedTarget;
        private final LevelRenderer levelRenderer;
        private Matrix4f cloudModelView;
        private Matrix4f projection;
        private boolean drawLogged;

        private PortalCall(String viewId, int renderFrame, int expectedTarget, LevelRenderer levelRenderer) {
            this.viewId = viewId;
            this.renderFrame = renderFrame;
            this.expectedTarget = expectedTarget;
            this.levelRenderer = levelRenderer;
        }

        private String viewId() {
            return this.viewId;
        }

        private int renderFrame() {
            return this.renderFrame;
        }

        private int expectedTarget() {
            return this.expectedTarget;
        }

        private LevelRenderer levelRenderer() {
            return this.levelRenderer;
        }

        private void finish() {
            if (this.cloudModelView == null || this.drawLogged) {
                return;
            }
            VertexBuffer expectedBuffer = ((LevelRendererAccessor) this.levelRenderer).skyesight$getCloudBuffer();
            Skyesight.LOGGER.info(
                    "[Skyesight] CLOUD_GPU_STATE_MISSING view={} frame={} expectedBuffer={}",
                    this.viewId,
                    this.renderFrame,
                    identity(expectedBuffer, "VertexBuffer")
            );
        }
    }
}
