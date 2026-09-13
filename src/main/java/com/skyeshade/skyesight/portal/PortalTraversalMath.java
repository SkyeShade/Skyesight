package com.skyeshade.skyesight.portal;

import com.skyeshade.skyesight.api.PortalEndpoint;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Quaternionf;
import org.joml.Vector3d;

/** Common-side rigid portal transform, also consumed by the scene renderer. */
public final class PortalTraversalMath {
    private PortalTraversalMath() {}
    public static Quaternionf rotation(Quaternionf source, Quaternionf target) {
        return new Quaternionf(target).rotateY((float) Math.PI)
                .mul(new Quaternionf(source).conjugate()).normalize();
    }
    public static Vec3 rotate(Vec3 vector, Quaternionf rotation) {
        Vector3d result = new Vector3d(vector.x, vector.y, vector.z).rotate(new Quaterniond(rotation));
        return new Vec3(result.x, result.y, result.z);
    }
    public static Vec3 local(PortalEndpoint endpoint, Vec3 point) {
        return rotate(point.subtract(endpoint.center()), new Quaternionf(endpoint.rotation()).conjugate());
    }
    public static Vec3 normal(PortalEndpoint endpoint) {
        return rotate(new Vec3(0, 0, 1), endpoint.rotation());
    }
    public static boolean clearedExit(PortalEndpoint endpoint, Vec3 feet, double playerWidth) {
        return Math.abs(local(endpoint, feet).z) > playerWidth / 2 + .1;
    }
    /** Camera ownership includes the degenerate on-plane sample, unlike gameplay crossing. */
    public static Vec3 eyeCrossing(PortalEndpoint source, Vec3 previousEye, Vec3 eye,
                                   double eyeHeight, double width, double height) {
        double previousDistance = local(source, previousEye).z;
        double distance = local(source, eye).z;
        if (Math.abs(previousDistance) <= 1e-4) return null;
        Vec3 end = Math.abs(distance) <= 1e-4
                ? eye.add(normal(source).scale(-Math.signum(previousDistance) * 2e-4)) : eye;
        return crossing(source, previousEye.subtract(0, eyeHeight, 0), end.subtract(0, eyeHeight, 0), width, height);
    }
    public static Vec3 position(PortalEndpoint source, PortalEndpoint target, Vec3 point) {
        return target.center().add(rotate(point.subtract(source.center()), rotation(source.rotation(), target.rotation())));
    }
    public record Pose(Vec3 position, Vec3 velocity, float yaw, float pitch) {}
    public static Pose transform(PortalEndpoint source, PortalEndpoint target, Vec3 position,
                                 Vec3 velocity, float yaw, float pitch) {
        var rotation = rotation(source.rotation(), target.rotation());
        double y = Math.toRadians(yaw), p = Math.toRadians(pitch);
        var look = rotate(new Vec3(-Math.sin(y) * Math.cos(p), -Math.sin(p), Math.cos(y) * Math.cos(p)), rotation);
        return new Pose(position(source, target, position), rotate(velocity, rotation), yaw(look), pitch(look));
    }
    /** Conservative full AABB containment projected into the aperture basis. */
    public static boolean fitsBody(PortalEndpoint source, AABB box) {
        for (double x : new double[]{box.minX, box.maxX})
            for (double y : new double[]{box.minY, box.maxY})
                for (double z : new double[]{box.minZ, box.maxZ}) {
                    var p = local(source, new Vec3(x, y, z));
                    if (Math.abs(p.x) > source.width() / 2 + 1e-6
                            || Math.abs(p.y) > source.height() / 2 + 1e-6) return false;
                }
        return true;
    }
    /** Upright aperture: segment follows feet; the entire player's width and height must fit. */
    public static Vec3 crossing(PortalEndpoint source, Vec3 previous, Vec3 current, double width, double height) {
        Vec3 a = local(source, previous), b = local(source, current);
        if (a.z == 0 || b.z == 0 || a.z * b.z > 0 || a.z == b.z) return null;
        double t = a.z / (a.z - b.z);
        Vec3 hit = a.lerp(b, t);
        if (Math.abs(hit.x) + width / 2 > source.width() / 2
                || hit.y < -source.height() / 2 - 0.001
                || hit.y + height > source.height() / 2 + 0.001) return null;
        return previous.lerp(current, t);
    }
    public static float yaw(Vec3 direction) {
        return (float) Math.toDegrees(Math.atan2(-direction.x, direction.z));
    }
    public static float pitch(Vec3 direction) {
        return (float) Math.toDegrees(Math.atan2(-direction.y, Math.hypot(direction.x, direction.z)));
    }
}
