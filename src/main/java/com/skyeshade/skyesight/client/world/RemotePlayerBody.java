package com.skyeshade.skyesight.client.world;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** LivingEntity.tick's visual body alignment, without AI, movement or gameplay ticking. */
public final class RemotePlayerBody {
    private Vec3 position;
    private float previous, current;
    public RemotePlayerBody(Vec3 position, float yaw) { reset(position, yaw); }
    public void reset(Vec3 position, float yaw) { this.position = position; previous = current = yaw; }
    public void tick(Vec3 nextPosition, float yaw, boolean attacking, float maxHeadDifference) {
        double dx = nextPosition.x - position.x, dz = nextPosition.z - position.z;
        position = nextPosition;
        previous = current;
        float target = current;
        if (dx * dx + dz * dz > 0.0025000002F) {
            float movement = (float)Mth.atan2(dz, dx) * (180F / (float)Math.PI) - 90F;
            float difference = Math.abs(Mth.wrapDegrees(yaw) - movement);
            target = difference > 95F && difference < 265F ? movement - 180F : movement;
        }
        if (attacking) target = yaw;
        current += Mth.wrapDegrees(target - current) * .3F;
        float headDifference = Mth.wrapDegrees(yaw - current);
        if (Math.abs(headDifference) > maxHeadDifference)
            current += headDifference - Math.signum(headDifference) * maxHeadDifference;
        current = Mth.wrapDegrees(current);
    }
    public float sample(float partialTick) { return Mth.wrapDegrees(previous + Mth.wrapDegrees(current - previous) * partialTick); }
}
