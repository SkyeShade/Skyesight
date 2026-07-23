package com.skyeshade.skyesight.client.portal;

import com.skyeshade.skyesight.SkyesightClientConfig;
import com.skyeshade.skyesight.SkyesightDebugConfig;
import com.skyeshade.skyesight.api.PortalEndpoint;
import com.skyeshade.skyesight.api.RegisteredPortalView;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;

public final class PortalVisibilityCull {
    private PortalVisibilityCull() {}

    public static PortalVisibilityResult evaluate(
            Minecraft minecraft,
            RegisteredPortalView view,
            Camera camera,
            Frustum mainFrustum,
            int framebufferWidth,
            int framebufferHeight
    ) {
        if (minecraft == null || minecraft.level == null) {
            return reject(false, false, false, false, false, 0.0D, 0, 0, "missing-client-level", DebugData.empty());
        }
        if (view == null || view.source() == null) {
            return reject(false, false, false, false, false, 0.0D, 0, 0, "missing-view-source", DebugData.empty());
        }
        if (camera == null || framebufferWidth <= 0 || framebufferHeight <= 0) {
            return reject(false, false, false, false, false, 0.0D, 0, 0, "missing-camera-or-framebuffer", DebugData.empty());
        }
        boolean dimensionMatches = minecraft.level.dimension().equals(view.source().dimension());
        if (!dimensionMatches) {
            return new PortalVisibilityResult(
                    true,
                    false,
                    true,
                    false,
                    true,
                    true,
                    0.0D,
                    0,
                    0,
                    0,
                    "source-dimension-mismatch-fail-open",
                    DebugData.dimensions(minecraft.level.dimension(), view.source().dimension())
            );
        }

        Vec3 cameraPos = camera.getPosition();
        Vec3 center = view.source().center();
        double distance = cameraPos.distanceTo(center);
        double maxDistance = SkyesightClientConfig.portalRenderDistanceBlocks();
        boolean distancePass = maxDistance < 0.0D || distance <= Math.max(0.0D, maxDistance);
        PortalAperture aperture = aperture(view.source());
        Vec3[] points = aperture.corners();
        Vec3[] dotPoints = new Vec3[] {
                center,
                aperture.bottomLeft(),
                aperture.bottomRight(),
                aperture.topRight(),
                aperture.topLeft()
        };
        Vector3f cameraForward = cameraForward(camera);
        double[] behindDots = behindDots(cameraPos, cameraForward, dotPoints);
        if (!distancePass) {
            return reject(
                    true,
                    false,
                    false,
                    false,
                    false,
                    distance,
                    0,
                    0,
                    "distance",
                    DebugData.initial(minecraft.level.dimension(), view.source().dimension(), cameraPos, cameraForward, view.source(), aperture, cameraPos, center, maxDistance, behindDots)
            );
        }

        boolean behindCamera = allBehindCamera(behindDots);
        AABB aabb = apertureAabb(aperture);
        AABB inflatedAabb = aabb.inflate(0.1D);
        if (!validAabb(inflatedAabb)) {
            return new PortalVisibilityResult(
                    true,
                    true,
                    true,
                    behindCamera,
                    true,
                    true,
                    distance,
                    0,
                    0,
                    0,
                    "invalid-aabb-fail-open",
                    DebugData.frustum(minecraft.level.dimension(), view.source().dimension(), cameraPos, cameraForward, view.source(), aperture, center, maxDistance, behindDots, aabb, inflatedAabb, false, false, framebufferWidth, framebufferHeight)
            );
        }
        if (mainFrustum == null) {
            return new PortalVisibilityResult(
                    true,
                    true,
                    true,
                    behindCamera,
                    true,
                    true,
                    distance,
                    0,
                    0,
                    0,
                    "frustum-unavailable-fail-open",
                    DebugData.frustum(minecraft.level.dimension(), view.source().dimension(), cameraPos, cameraForward, view.source(), aperture, center, maxDistance, behindDots, aabb, inflatedAabb, false, true, framebufferWidth, framebufferHeight)
            );
        }

        boolean vanillaFrustumVisible;
        try {
            vanillaFrustumVisible = mainFrustum.isVisible(inflatedAabb);
        } catch (RuntimeException exception) {
            return new PortalVisibilityResult(
                    true,
                    true,
                    true,
                    behindCamera,
                    true,
                    true,
                    distance,
                    0,
                    0,
                    0,
                    "frustum-error-fail-open:" + exception.getClass().getSimpleName(),
                    DebugData.frustum(minecraft.level.dimension(), view.source().dimension(), cameraPos, cameraForward, view.source(), aperture, center, maxDistance, behindDots, aabb, inflatedAabb, false, true, framebufferWidth, framebufferHeight)
            );
        }

        boolean frustumPass = !SkyesightClientConfig.enablePortalFrustumCulling() || vanillaFrustumVisible;
        if (!frustumPass) {
            return reject(
                    true,
                    true,
                    false,
                    false,
                    false,
                    distance,
                    0,
                    0,
                    "outside-vanilla-frustum-aabb",
                    DebugData.frustum(minecraft.level.dimension(), view.source().dimension(), cameraPos, cameraForward, view.source(), aperture, center, maxDistance, behindDots, aabb, inflatedAabb, vanillaFrustumVisible, true, framebufferWidth, framebufferHeight)
            );
        }

        return new PortalVisibilityResult(
                true,
                true,
                true,
                behindCamera,
                true,
                true,
                distance,
                0,
                0,
                0,
                "visible-vanilla-frustum-aabb",
                DebugData.frustum(minecraft.level.dimension(), view.source().dimension(), cameraPos, cameraForward, view.source(), aperture, center, maxDistance, behindDots, aabb, inflatedAabb, vanillaFrustumVisible, true, framebufferWidth, framebufferHeight)
        );
    }

    private static PortalVisibilityResult reject(
            boolean sourceDimensionMatches,
            boolean distancePass,
            boolean behindCamera,
            boolean frustumPass,
            boolean projectedSizePass,
            double distanceBlocks,
            int projectedWidth,
            int projectedHeight,
            String reason,
            DebugData debugData
    ) {
        return new PortalVisibilityResult(
                false,
                sourceDimensionMatches,
                distancePass,
                behindCamera,
                frustumPass,
                projectedSizePass,
                distanceBlocks,
                projectedWidth,
                projectedHeight,
                Math.max(0, projectedWidth) * Math.max(0, projectedHeight),
                reason == null || reason.isBlank() ? "culled" : reason,
                debugData == null ? DebugData.empty() : debugData
        );
    }

    private static PortalAperture aperture(PortalEndpoint endpoint) {
        Vec3 center = endpoint.center();
        Vec3 right = rotate(endpoint.rotation(), 1.0F, 0.0F, 0.0F);
        Vec3 up = rotate(endpoint.rotation(), 0.0F, 1.0F, 0.0F);
        Vec3 normal = rotate(endpoint.rotation(), 0.0F, 0.0F, 1.0F);
        double halfWidth = endpoint.width() * 0.5D;
        double halfHeight = endpoint.height() * 0.5D;
        Vec3 bottomLeft = center.subtract(right.scale(halfWidth)).subtract(up.scale(halfHeight));
        Vec3 bottomRight = center.add(right.scale(halfWidth)).subtract(up.scale(halfHeight));
        Vec3 topRight = center.add(right.scale(halfWidth)).add(up.scale(halfHeight));
        Vec3 topLeft = center.subtract(right.scale(halfWidth)).add(up.scale(halfHeight));
        return new PortalAperture(
                center,
                right,
                up,
                normal,
                endpoint.width(),
                endpoint.height(),
                bottomLeft,
                bottomRight,
                topRight,
                topLeft
        );
    }

    private static AABB apertureAabb(PortalAperture aperture) {
        double minX = Math.min(Math.min(aperture.bottomLeft().x(), aperture.bottomRight().x()), Math.min(aperture.topRight().x(), aperture.topLeft().x()));
        double minY = Math.min(Math.min(aperture.bottomLeft().y(), aperture.bottomRight().y()), Math.min(aperture.topRight().y(), aperture.topLeft().y()));
        double minZ = Math.min(Math.min(aperture.bottomLeft().z(), aperture.bottomRight().z()), Math.min(aperture.topRight().z(), aperture.topLeft().z()));
        double maxX = Math.max(Math.max(aperture.bottomLeft().x(), aperture.bottomRight().x()), Math.max(aperture.topRight().x(), aperture.topLeft().x()));
        double maxY = Math.max(Math.max(aperture.bottomLeft().y(), aperture.bottomRight().y()), Math.max(aperture.topRight().y(), aperture.topLeft().y()));
        double maxZ = Math.max(Math.max(aperture.bottomLeft().z(), aperture.bottomRight().z()), Math.max(aperture.topRight().z(), aperture.topLeft().z()));
        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static boolean validAabb(AABB aabb) {
        if (aabb == null) {
            return false;
        }
        return Double.isFinite(aabb.minX)
                && Double.isFinite(aabb.minY)
                && Double.isFinite(aabb.minZ)
                && Double.isFinite(aabb.maxX)
                && Double.isFinite(aabb.maxY)
                && Double.isFinite(aabb.maxZ)
                && aabb.maxX >= aabb.minX
                && aabb.maxY >= aabb.minY
                && aabb.maxZ >= aabb.minZ
                && aabb.getXsize() > 0.0D
                && aabb.getYsize() > 0.0D
                && aabb.getZsize() > 0.0D;
    }

    private static Vec3 rotate(Quaternionf rotation, float x, float y, float z) {
        Vector3f vector = new Vector3f(x, y, z);
        vector.rotate(rotation).normalize();
        return new Vec3(vector.x(), vector.y(), vector.z());
    }

    private static Vector3f cameraForward(Camera camera) {
        Vector3f look = new Vector3f(0.0F, 0.0F, 1.0F);
        look.rotate(camera.rotation()).normalize();
        return look;
    }

    private static double[] behindDots(Vec3 cameraPos, Vector3f look, Vec3[] points) {
        double[] dots = new double[points.length];
        for (int i = 0; i < points.length; i++) {
            Vec3 toPoint = points[i].subtract(cameraPos);
            dots[i] = toPoint.x() * look.x() + toPoint.y() * look.y() + toPoint.z() * look.z();
        }
        return dots;
    }

    private static boolean allBehindCamera(double[] dots) {
        for (double dot : dots) {
            if (dot >= 0.0D) {
                return false;
            }
        }
        return true;
    }

    private static Vector4f projectClip(Matrix4f viewProjection, Vec3 cameraRelativePosition) {
        return new Vector4f(
                (float) cameraRelativePosition.x(),
                (float) cameraRelativePosition.y(),
                (float) cameraRelativePosition.z(),
                1.0F
        ).mul(viewProjection);
    }

    private static boolean validClip(Vector4f point) {
        return point != null
                && Float.isFinite(point.x)
                && Float.isFinite(point.y)
                && Float.isFinite(point.z)
                && Float.isFinite(point.w)
                && point.w > 1.0E-5F;
    }

    private static ClipSides clipSides(Vector4f[] points) {
        return new ClipSides(
                outsideCount(points, Plane.LEFT),
                outsideCount(points, Plane.RIGHT),
                outsideCount(points, Plane.BOTTOM),
                outsideCount(points, Plane.TOP),
                outsideCount(points, Plane.NEAR),
                outsideCount(points, Plane.FAR),
                allOutside(points, Plane.LEFT),
                allOutside(points, Plane.RIGHT),
                allOutside(points, Plane.BOTTOM),
                allOutside(points, Plane.TOP),
                allOutside(points, Plane.NEAR),
                allOutside(points, Plane.FAR)
        );
    }

    private static int outsideCount(Vector4f[] points, Plane plane) {
        int count = 0;
        for (Vector4f point : points) {
            if (outside(point, plane)) {
                count++;
            }
        }
        return count;
    }

    private static boolean allOutside(Vector4f[] points, Plane plane) {
        for (Vector4f point : points) {
            if (!outside(point, plane)) {
                return false;
            }
        }
        return true;
    }

    private static boolean outside(Vector4f point, Plane plane) {
        return switch (plane) {
            case LEFT -> validClip(point) && point.x < -point.w;
            case RIGHT -> validClip(point) && point.x > point.w;
            case BOTTOM -> validClip(point) && point.y < -point.w;
            case TOP -> validClip(point) && point.y > point.w;
            case NEAR -> validClip(point) && point.z < -point.w;
            case FAR -> validClip(point) && point.z > point.w;
        };
    }

    private static ScreenBounds screenBounds(Vector4f[] clipPoints, int framebufferWidth, int framebufferHeight) {
        float minX = 1.0F;
        float minY = 1.0F;
        float maxX = -1.0F;
        float maxY = -1.0F;
        int valid = 0;
        for (Vector4f clip : clipPoints) {
            if (Math.abs(clip.w) < 1.0E-5F || clip.w <= 0.0F) {
                continue;
            }
            float ndcX = clip.x / clip.w;
            float ndcY = clip.y / clip.w;
            if (!Float.isFinite(ndcX) || !Float.isFinite(ndcY)) {
                continue;
            }
            minX = Math.min(minX, ndcX);
            maxX = Math.max(maxX, ndcX);
            minY = Math.min(minY, ndcY);
            maxY = Math.max(maxY, ndcY);
            valid++;
        }
        if (valid == 0) {
            return ScreenBounds.invalid();
        }
        int x0 = clamp((int) Math.floor((minX * 0.5F + 0.5F) * framebufferWidth), 0, framebufferWidth);
        int y0 = clamp((int) Math.floor((minY * 0.5F + 0.5F) * framebufferHeight), 0, framebufferHeight);
        int x1 = clamp((int) Math.ceil((maxX * 0.5F + 0.5F) * framebufferWidth), 0, framebufferWidth);
        int y1 = clamp((int) Math.ceil((maxY * 0.5F + 0.5F) * framebufferHeight), 0, framebufferHeight);
        return new ScreenBounds(x0, y0, Math.max(0, x1 - x0), Math.max(0, y1 - y0), true);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private enum Plane {
        LEFT,
        RIGHT,
        BOTTOM,
        TOP,
        NEAR,
        FAR
    }

    private record ScreenBounds(int x, int y, int width, int height, boolean valid) {
        private static ScreenBounds invalid() {
            return new ScreenBounds(0, 0, 0, 0, false);
        }

        private int pixels() {
            return Math.max(0, this.width) * Math.max(0, this.height);
        }
    }

    private record PortalAperture(
            Vec3 center,
            Vec3 right,
            Vec3 up,
            Vec3 normal,
            float width,
            float height,
            Vec3 bottomLeft,
            Vec3 bottomRight,
            Vec3 topRight,
            Vec3 topLeft
    ) {
        private Vec3[] corners() {
            return new Vec3[] {
                    this.bottomLeft,
                    this.bottomRight,
                    this.topRight,
                    this.topLeft
            };
        }
    }

    private record ClipSides(
            int outsideLeftCount,
            int outsideRightCount,
            int outsideBottomCount,
            int outsideTopCount,
            int outsideNearCount,
            int outsideFarCount,
            boolean outsideLeftAll,
            boolean outsideRightAll,
            boolean outsideBottomAll,
            boolean outsideTopAll,
            boolean outsideNearAll,
            boolean outsideFarAll
    ) {
        private boolean anyAllOutside() {
            return this.outsideLeftAll
                    || this.outsideRightAll
                    || this.outsideBottomAll
                    || this.outsideTopAll
                    || this.outsideNearAll
                    || this.outsideFarAll;
        }
    }

    public record DebugData(
            ResourceKey<Level> clientDimension,
            ResourceKey<Level> sourceDimension,
            Vec3 cameraPos,
            String cameraForward,
            Vec3 sourceCenter,
            double sourceWidth,
            double sourceHeight,
            String sourceRight,
            String sourceUp,
            String sourceNormal,
            String cornerWorldBL,
            String cornerWorldBR,
            String cornerWorldTR,
            String cornerWorldTL,
            String cornerClipBL,
            String cornerClipBR,
            String cornerClipTR,
            String cornerClipTL,
            double maxDistance,
            double behindDotCenter,
            String behindDotsCorners,
            String cornersWorld,
            String cornersCameraRelative,
            String clipCoords,
            String ndcCoords,
            String viewport,
            String screenBounds,
            String aabb,
            String inflatedAabb,
            boolean frustumAvailable,
            boolean vanillaFrustumVisible,
            int outsideLeftCount,
            int outsideRightCount,
            int outsideBottomCount,
            int outsideTopCount,
            int behindNearCount,
            boolean allBehindNear,
            boolean outsideLeftAll,
            boolean outsideRightAll,
            boolean outsideBottomAll,
            boolean outsideTopAll,
            boolean outsideNearAll,
            boolean outsideFarAll,
            boolean projectionBoundsValid
    ) {
        private static DebugData empty() {
            return new DebugData(null, null, null, "-", null, 0.0D, 0.0D, "-", "-", "-", "-", "-", "-", "-", "-", "-", "-", "-", 0.0D, 0.0D, "-", "-", "-", "-", "-", "-", "-", "-", "-", false, false, 0, 0, 0, 0, 0, false, false, false, false, false, false, false, false);
        }

        private static DebugData dimensions(ResourceKey<Level> clientDimension, ResourceKey<Level> sourceDimension) {
            return new DebugData(clientDimension, sourceDimension, null, "-", null, 0.0D, 0.0D, "-", "-", "-", "-", "-", "-", "-", "-", "-", "-", "-", 0.0D, 0.0D, "-", "-", "-", "-", "-", "-", "-", "-", "-", false, false, 0, 0, 0, 0, 0, false, false, false, false, false, false, false, false);
        }

        private static DebugData initial(
                ResourceKey<Level> clientDimension,
                ResourceKey<Level> sourceDimension,
                Vec3 cameraPos,
                Vector3f cameraForward,
                PortalEndpoint source,
                PortalAperture aperture,
                Vec3 cameraPosition,
                Vec3 sourceCenter,
                double maxDistance,
                double[] dots
        ) {
            return new DebugData(
                    clientDimension,
                    sourceDimension,
                    cameraPos,
                    format(cameraForward),
                    sourceCenter,
                    source == null ? 0.0D : source.width(),
                    source == null ? 0.0D : source.height(),
                    aperture == null ? "-" : formatVec(aperture.right()),
                    aperture == null ? "-" : formatVec(aperture.up()),
                    aperture == null ? "-" : formatVec(aperture.normal()),
                    aperture == null ? "-" : formatVec(aperture.bottomLeft()),
                    aperture == null ? "-" : formatVec(aperture.bottomRight()),
                    aperture == null ? "-" : formatVec(aperture.topRight()),
                    aperture == null ? "-" : formatVec(aperture.topLeft()),
                    "-",
                    "-",
                    "-",
                    "-",
                    maxDistance,
                    dots.length > 0 ? dots[0] : 0.0D,
                    formatCornerDots(dots),
                    "-",
                    "-",
                    "-",
                    "-",
                    "-",
                    "-",
                    "-",
                    "-",
                    false,
                    false,
                    0,
                    0,
                    0,
                    0,
                    0,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false
            );
        }

        private static DebugData full(
                ResourceKey<Level> clientDimension,
                ResourceKey<Level> sourceDimension,
                Vec3 cameraPos,
                Vector3f cameraForward,
                PortalEndpoint source,
                PortalAperture aperture,
                Vec3 cameraPosition,
                Vec3 sourceCenter,
                double maxDistance,
                double[] dots,
                Vec3[] worldPoints,
                Vec3[] relativePoints,
                Vector4f[] clip,
                ClipSides sides,
                ScreenBounds bounds,
                int framebufferWidth,
                int framebufferHeight
        ) {
            return new DebugData(
                    clientDimension,
                    sourceDimension,
                    cameraPos,
                    format(cameraForward),
                    sourceCenter,
                    source == null ? 0.0D : source.width(),
                    source == null ? 0.0D : source.height(),
                    aperture == null ? "-" : formatVec(aperture.right()),
                    aperture == null ? "-" : formatVec(aperture.up()),
                    aperture == null ? "-" : formatVec(aperture.normal()),
                    aperture == null ? "-" : formatVec(aperture.bottomLeft()),
                    aperture == null ? "-" : formatVec(aperture.bottomRight()),
                    aperture == null ? "-" : formatVec(aperture.topRight()),
                    aperture == null ? "-" : formatVec(aperture.topLeft()),
                    formatClipPoint(clip, 0),
                    formatClipPoint(clip, 1),
                    formatClipPoint(clip, 2),
                    formatClipPoint(clip, 3),
                    maxDistance,
                    dots.length > 0 ? dots[0] : 0.0D,
                    formatCornerDots(dots),
                    formatVecs(worldPoints),
                    formatVecs(relativePoints),
                    formatClip(clip),
                    formatNdc(clip),
                    framebufferWidth + "x" + framebufferHeight,
                    formatBounds(bounds),
                    "-",
                    "-",
                    false,
                    false,
                    sides.outsideLeftCount(),
                    sides.outsideRightCount(),
                    sides.outsideBottomCount(),
                    sides.outsideTopCount(),
                    sides.outsideNearCount(),
                    sides.outsideNearAll(),
                    sides.outsideLeftAll(),
                    sides.outsideRightAll(),
                    sides.outsideBottomAll(),
                    sides.outsideTopAll(),
                    sides.outsideNearAll(),
                    sides.outsideFarAll(),
                    bounds.valid()
            );
        }

        private static DebugData frustum(
                ResourceKey<Level> clientDimension,
                ResourceKey<Level> sourceDimension,
                Vec3 cameraPos,
                Vector3f cameraForward,
                PortalEndpoint source,
                PortalAperture aperture,
                Vec3 sourceCenter,
                double maxDistance,
                double[] dots,
                AABB aabb,
                AABB inflatedAabb,
                boolean vanillaFrustumVisible,
                boolean frustumAvailable,
                int framebufferWidth,
                int framebufferHeight
        ) {
            return new DebugData(
                    clientDimension,
                    sourceDimension,
                    cameraPos,
                    format(cameraForward),
                    sourceCenter,
                    source == null ? 0.0D : source.width(),
                    source == null ? 0.0D : source.height(),
                    aperture == null ? "-" : formatVec(aperture.right()),
                    aperture == null ? "-" : formatVec(aperture.up()),
                    aperture == null ? "-" : formatVec(aperture.normal()),
                    aperture == null ? "-" : formatVec(aperture.bottomLeft()),
                    aperture == null ? "-" : formatVec(aperture.bottomRight()),
                    aperture == null ? "-" : formatVec(aperture.topRight()),
                    aperture == null ? "-" : formatVec(aperture.topLeft()),
                    "-",
                    "-",
                    "-",
                    "-",
                    maxDistance,
                    dots.length > 0 ? dots[0] : 0.0D,
                    formatCornerDots(dots),
                    aperture == null ? "-" : formatVecs(aperture.corners()),
                    "-",
                    "-",
                    "-",
                    framebufferWidth + "x" + framebufferHeight,
                    "-",
                    formatAabb(aabb),
                    formatAabb(inflatedAabb),
                    frustumAvailable,
                    vanillaFrustumVisible,
                    0,
                    0,
                    0,
                    0,
                    0,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false
            );
        }
    }

    private static String format(Vector3f value) {
        return String.format(java.util.Locale.ROOT, "%.3f,%.3f,%.3f", value.x(), value.y(), value.z());
    }

    private static String formatVec(Vec3 point) {
        if (point == null) {
            return "-";
        }
        return String.format(java.util.Locale.ROOT, "%.2f,%.2f,%.2f", point.x(), point.y(), point.z());
    }

    private static String formatCornerDots(double[] dots) {
        if (dots.length <= 1) {
            return "-";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 1; i < dots.length; i++) {
            if (!builder.isEmpty()) {
                builder.append(',');
            }
            builder.append(String.format(java.util.Locale.ROOT, "%.3f", dots[i]));
        }
        return builder.toString();
    }

    private static String formatVecs(Vec3[] points) {
        if (points == null || points.length == 0) {
            return "-";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < points.length; i++) {
            if (i > 0) {
                builder.append(';');
            }
            Vec3 point = points[i];
            builder.append(point == null
                    ? "null"
                    : String.format(java.util.Locale.ROOT, "%.2f,%.2f,%.2f", point.x(), point.y(), point.z()));
        }
        return builder.toString();
    }

    private static String formatBounds(ScreenBounds bounds) {
        if (bounds == null || !bounds.valid()) {
            return "invalid";
        }
        return bounds.x() + "," + bounds.y() + " " + bounds.width() + "x" + bounds.height();
    }

    private static String formatAabb(AABB value) {
        if (value == null) {
            return "-";
        }
        return String.format(
                java.util.Locale.ROOT,
                "%.2f,%.2f,%.2f -> %.2f,%.2f,%.2f",
                value.minX,
                value.minY,
                value.minZ,
                value.maxX,
                value.maxY,
                value.maxZ
        );
    }

    private static String formatClip(Vector4f[] clip) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < clip.length; i++) {
            if (i > 0) {
                builder.append(';');
            }
            Vector4f point = clip[i];
            builder.append(String.format(java.util.Locale.ROOT, "%.2f,%.2f,%.2f,%.2f", point.x, point.y, point.z, point.w));
        }
        return builder.toString();
    }

    private static String formatClipPoint(Vector4f[] clip, int index) {
        if (clip == null || index < 0 || index >= clip.length || clip[index] == null) {
            return "-";
        }
        Vector4f point = clip[index];
        return String.format(java.util.Locale.ROOT, "%.2f,%.2f,%.2f,%.2f", point.x, point.y, point.z, point.w);
    }

    private static String formatNdc(Vector4f[] clip) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < clip.length; i++) {
            if (i > 0) {
                builder.append(';');
            }
            Vector4f point = clip[i];
            if (Math.abs(point.w) < 1.0E-5F || point.w <= 0.0F) {
                builder.append("invalid");
            } else {
                builder.append(String.format(java.util.Locale.ROOT, "%.2f,%.2f,%.2f", point.x / point.w, point.y / point.w, point.z / point.w));
            }
        }
        return builder.toString();
    }

    public record PortalVisibilityResult(
            boolean visibleEnoughForHeavyRender,
            boolean sourceDimensionMatches,
            boolean distancePass,
            boolean behindCamera,
            boolean frustumPass,
            boolean projectedSizePass,
            double distanceBlocks,
            int projectedWidth,
            int projectedHeight,
            int projectedPixels,
            String reason,
            DebugData debugData
    ) {}
}
