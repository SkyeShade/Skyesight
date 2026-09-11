package com.skyeshade.skyesight.client.world;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RemotePlayerBodyTest {
    @Test void walkingBackwardKeepsBodyFacingForwardAndSamplingDoesNotAdvanceIt() {
        var body = new RemotePlayerBody(Vec3.ZERO, 180);
        for (int tick = 1; tick < 20; tick++) body.tick(new Vec3(0, 0, tick * .2), 180, false, 50);
        assertEquals(180, Math.abs(body.sample(1)), .001);
        float value = body.sample(.4F);
        for (int frame = 0; frame < 100; frame++) assertEquals(value, body.sample(.4F));
    }
    @Test void forwardMotionCatchesUpAfterStrafingAndIdleLookKeepsTorsoSeparation() {
        var body = new RemotePlayerBody(Vec3.ZERO, 130);
        for (int tick = 1; tick <= 20; tick++) body.tick(new Vec3(0, 0, -tick * .2), 180, false, 50);
        assertTrue(Math.abs(Math.abs(body.sample(1)) - 180) < .1);
        body.tick(new Vec3(0, 0, -4), 140, false, 50);
        assertTrue(Math.abs(body.sample(1) - 140) > 30);
    }
}
