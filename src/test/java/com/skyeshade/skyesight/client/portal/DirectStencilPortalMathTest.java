package com.skyeshade.skyesight.client.portal;

import com.skyeshade.skyesight.client.render.SkyesightProjectionMatrices;
import com.skyeshade.skyesight.client.view.SkyesightMutableCamera;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DirectStencilPortalMathTest {
    @Test void fixedMidpointOffsetsReverseTogetherForFrontAndBackEyes() {
        Quaternionf[] rotations = {
                new Quaternionf(), new Quaternionf().rotateY((float) Math.PI),
                new Quaternionf().rotateY((float) Math.PI / 2),
                new Quaternionf().rotateX((float) Math.PI / 2)
        };
        for (Quaternionf sourceRotation : rotations) {
            for (Quaternionf targetRotation : rotations) {
                var source = new PortalFrame(new Vec3(-932.5, 80.5, 824.5), sourceRotation, 5, 5);
                var target = new PortalFrame(new Vec3(12.5, 40.5, -8.5), targetRotation, 5, 5);
                for (int side : new int[]{1, -1}) {
                    for (double distance : new double[]{0.0001, 0.889, 4}) {
                        Vec3 eye = source.position().add(DirectStencilPortalMath.normal(source).scale(side * distance));
                        assertEquals(side, DirectStencilPortalMath.viewingSide(source, eye));
                        double stencilBias = DirectStencilPortalMath.apertureOffset(source, eye, 0.001);
                        assertEquals(side * 0.001, stencilBias);
                        assertTrue(eye.subtract(source.position()).dot(
                                DirectStencilPortalMath.normal(source).scale(stencilBias)) > 0);
                        Vec3 expectedNormal = DirectStencilPortalMath.normal(target).scale(side);
                        assertEquals(expectedNormal, DirectStencilPortalMath.effectiveExitNormal(source, target, eye));
                        var eyeRotation = new Quaternionf(sourceRotation).rotateY(side < 0 ? (float) Math.PI : 0);
                        var pose = DirectStencilPortalMath.transformPose(eye, eyeRotation, source, target);
                        var pushed = DirectStencilPortalMath.pushThroughExit(pose, source, target, eye, 0.005);
                        assertTrue(pushed.position().subtract(pose.position()).distanceTo(expectedNormal.scale(0.005)) < 1E-10);
                        assertEquals(pose.rotation(), pushed.rotation());
                        var clip = DirectStencilPortalMath.exitClipPlane(source, target, eye);
                        assertEquals(expectedNormal, clip.normal());
                        assertTrue(clip.point().subtract(target.position()).distanceTo(expectedNormal.scale(0.001)) < 1E-10);
                        if (distance > 0.01) {
                            var camera = new SkyesightMutableCamera();
                            camera.setPositionPublic(pushed.position());
                            camera.setRotationPublic(pushed.rotation());
                            var projection = SkyesightProjectionMatrices.applyObliqueClipPlane(
                                    SkyesightProjectionMatrices.perspective(70, 16F / 9, 0.05F, 128), camera, clip);
                            assertTrue(nearDistance(projection, camera, clip.point().add(expectedNormal.scale(0.01))) > 0);
                            assertTrue(nearDistance(projection, camera, clip.point().subtract(expectedNormal.scale(0.01))) < 0);
                        }
                    }
                }
            }
        }
    }

    @Test void reverseTransformAndObliqueHalfSpaceAreSymmetricForEveryWallFacing() {
        for (int a = 0; a < 360; a += 90) {
            for (int b = 0; b < 360; b += 90) {
                var entrance = frame(new Vec3(8.5, 122, 1.001), a);
                var exit = frame(new Vec3(40.5, 122, -0.001), b);
                checkDirection(entrance, exit);
                checkDirection(exit, entrance);
            }
        }
    }

    private static void checkDirection(PortalFrame entrance, PortalFrame exit) {
        for (float distance : new float[]{0.03F, 0.1F, 3, 4}) {
            Vec3 position = entrance.position().add(DirectStencilPortalMath.normal(entrance).scale(distance))
                    .add(DirectStencilPortalMath.right(entrance).scale(0.2));
            Quaternionf rotation = new Quaternionf(entrance.rotation()).rotateY(0.1F);
            var transformed = DirectStencilPortalMath.transformPose(position, rotation, entrance, exit);
            var roundTrip = DirectStencilPortalMath.transformPose(transformed.position(), transformed.rotation(), exit, entrance);
            assertTrue(position.distanceTo(roundTrip.position()) < 1.0E-5);
            assertEquals(1.0F, Math.abs(rotation.dot(roundTrip.rotation())), 1.0E-5F);

            var camera = new SkyesightMutableCamera();
            camera.setPositionPublic(transformed.position());
            camera.setRotationPublic(transformed.rotation());
            var plane = DirectStencilPortalMath.exitClipPlane(exit);
            Matrix4f projection = SkyesightProjectionMatrices.applyObliqueClipPlane(
                    SkyesightProjectionMatrices.perspective(70, 16F / 9, 0.05F, 128), camera, plane);
            // The destination-facing half-space must survive; geometry behind the exit must not.
            assertTrue(nearDistance(projection, camera, plane.point().add(plane.normal().scale(0.01))) > 0);
            assertTrue(nearDistance(projection, camera, plane.point().subtract(plane.normal().scale(0.01))) < 0);
        }
    }

    private static float nearDistance(Matrix4f projection, SkyesightMutableCamera camera, Vec3 point) {
        Vec3 relative = point.subtract(camera.getPosition());
        Vector3f local = new Vector3f((float) relative.x, (float) relative.y, (float) relative.z)
                .rotate(new Quaternionf(camera.rotation()).conjugate());
        Vector4f clip = projection.transform(new Vector4f(local, 1));
        return clip.z + clip.w;
    }

    private static PortalFrame frame(Vec3 position, int yaw) {
        return new PortalFrame(position, new Quaternionf().rotateY((float) Math.toRadians(yaw)), 2, 2);
    }
}
