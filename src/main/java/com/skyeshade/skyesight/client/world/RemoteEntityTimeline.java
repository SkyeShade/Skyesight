package com.skyeshade.skyesight.client.world;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayDeque;

/** Bounded authoritative samples, played two server ticks behind the client frame clock. */
public final class RemoteEntityTimeline {
    public record Sample(int tick, Vec3 position, float yaw, float pitch, float body, float head) {}
    private final ArrayDeque<Sample> samples = new ArrayDeque<>();
    private double offset;

    public void accept(Sample sample, double clientTick) {
        var last = samples.peekLast();
        if (last != null && sample.equals(last)) return;
        if (last == null || sample.tick < last.tick || last.position.distanceToSqr(sample.position) > 256) {
            samples.clear();
            offset = sample.tick - clientTick;
        }
        if (!samples.isEmpty() && samples.getLast().tick == sample.tick) samples.removeLast();
        samples.addLast(sample);
        while (samples.size() > 8) samples.removeFirst();
        // Rebase only after a genuine clock discontinuity, not each packet's delivery jitter.
        if (Math.abs(sample.tick - (clientTick + offset)) > 10) offset = sample.tick - clientTick;
    }

    public Sample sample(double clientTick) {
        if (samples.isEmpty()) throw new IllegalStateException("No authoritative sample");
        double target = clientTick + offset - 2;
        var previous = samples.getFirst();
        for (var next : samples) {
            if (next.tick >= target) {
                float alpha = next.tick == previous.tick ? 0 : Mth.clamp((float)((target - previous.tick) / (next.tick - previous.tick)), 0, 1);
                return new Sample(next.tick, previous.position.lerp(next.position, alpha),
                        angle(previous.yaw, next.yaw, alpha), angle(previous.pitch, next.pitch, alpha),
                        angle(previous.body, next.body, alpha), angle(previous.head, next.head, alpha));
            }
            previous = next;
        }
        return previous; // Hold on packet loss; never extrapolate gameplay movement.
    }

    private static float angle(float from, float to, float alpha) {
        return Mth.wrapDegrees(from + Mth.wrapDegrees(to - from) * alpha);
    }
}
