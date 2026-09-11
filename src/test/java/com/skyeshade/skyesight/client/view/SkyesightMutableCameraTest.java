package com.skyeshade.skyesight.client.view;

import com.skyeshade.skyesight.client.render.SkyesightCameraMatrices;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SkyesightMutableCameraTest {
    @Test void fullYawSweepAndVerticalPitchPreserveQuaternionBasisAndModelView() {
        var camera = new SkyesightMutableCamera();
        for (int yaw = 0; yaw <= 360; yaw += 5) {
            for (float pitch : new float[]{0, 89, 90, -89, -90}) {
                for (float roll : new float[]{0, 23, -45}) {
                    Quaternionf input = new Quaternionf().rotationYXZ(
                            (float)Math.PI - (float)Math.toRadians(yaw),
                            -(float)Math.toRadians(pitch), -(float)Math.toRadians(roll));
                    camera.setRotationPublic(input);
                    assertEquals(input, camera.rotation());
                    assertEquals(input.transform(new Vector3f(0, 0, -1)), camera.getLookVector());
                    assertEquals(input.transform(new Vector3f(0, 1, 0)), camera.getUpVector());
                    assertEquals(input.transform(new Vector3f(-1, 0, 0)), camera.getLeftVector());
                    assertEquals(new Matrix4f().rotation(new Quaternionf(input).conjugate()),
                            SkyesightCameraMatrices.createModelView(camera));
                    input.identity();
                    assertTrue(Float.isFinite(camera.getYRot()));
                    assertTrue(Float.isFinite(camera.getXRot()));
                }
            }
        }
    }

    @Test void copiesAndOffsetCopiesPreserveOrientationAndDoNotAlias() {
        var source = new SkyesightMutableCamera();
        source.setPositionPublic(new Vec3(12.25, 80.75, -100.5));
        source.setRotationPublic(42, 90, 17);
        var target = new SkyesightMutableCamera();
        target.copyFrom(source);
        assertEquals(source.rotation(), target.rotation());
        assertEquals(source.getPosition(), target.getPosition());
        target.copyFromWithOffset(source, new Vec3(1, 2, 3));
        assertEquals(source.rotation(), target.rotation());
        assertEquals(source.getPosition().add(1, 2, 3), target.getPosition());
        Quaternionf saved = new Quaternionf(target.rotation());
        source.setRotationPublic(0, 0, 0);
        assertEquals(saved, target.rotation());
        target.setRotationPublic(target.rotation());
        assertEquals(saved, target.rotation());
    }
}
