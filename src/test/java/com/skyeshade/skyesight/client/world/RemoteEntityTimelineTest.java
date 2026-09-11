package com.skyeshade.skyesight.client.world;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RemoteEntityTimelineTest {
    private static RemoteEntityTimeline.Sample sample(int tick, double x, float body) {
        return new RemoteEntityTimeline.Sample(tick, new Vec3(x, 0, 0), 179, 10, body, -179);
    }
    @Test void interpolatesAtFrameRateAndWrapsIndependentBodyYaw() {
        var timeline = new RemoteEntityTimeline();
        timeline.accept(sample(100, 0, 179), 0);
        timeline.accept(sample(102, 2, -179), 2);
        assertEquals(.25, timeline.sample(2.25).position().x, 1e-6);
        assertEquals(.75, timeline.sample(2.75).position().x, 1e-6);
        assertEquals(-180, timeline.sample(3).body(), 1e-6);
        assertEquals(-179, timeline.sample(3).head(), 1e-6);
        assertEquals(timeline.sample(3), timeline.sample(3));
    }
    @Test void arrivalJitterDoesNotRestartInterpolationAndTeleportsDoNotSweep() {
        var timeline = new RemoteEntityTimeline();
        timeline.accept(sample(100, 0, 0), 0);
        timeline.accept(sample(102, 2, 0), 2.2);
        timeline.accept(sample(104, 4, 0), 3.9);
        assertEquals(2.5, timeline.sample(4.5).position().x, 1e-6);
        assertEquals(4, timeline.sample(20).position().x, 1e-6);
        timeline.accept(sample(106, 1000, 0), 6);
        assertEquals(1000, timeline.sample(6).position().x, 1e-6);
    }
    @Test void sameTickCorrectionAndRecreatedEntityResetDoNotKeepAnOldBody() {
        var timeline = new RemoteEntityTimeline();
        timeline.accept(sample(100, 0, 0), 0);
        timeline.accept(sample(100, 1000, 0), 0);
        assertEquals(1000, timeline.sample(0).position().x);
        timeline.accept(sample(0, 10, 0), 1);
        assertEquals(10, timeline.sample(1).position().x);
    }
}
