package com.skyeshade.skyesight.remote;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SecondaryParticlePolicyTest {
    @Test void mainEngineReuseRequiresBothSameLevelAndNearbyPhysicalEye() {
        var physical = new Vec3(8, 123, 4);
        assertTrue(SecondaryParticlePolicy.usesMainParticles(true, physical, physical.add(16, 0, 0)));
        assertFalse(SecondaryParticlePolicy.usesMainParticles(true, physical, physical.add(192, 0, 0)));
        assertFalse(SecondaryParticlePolicy.usesMainParticles(false, physical, physical));
        assertFalse(SecondaryParticlePolicy.usesMainParticles(true, physical, physical.add(0, 17, 0)));
    }

    @Test void interestIsBoundedInThreeDimensionsAndRejectsInvalidCoordinates() {
        var center = new Vec3(200, 123, 4);
        assertTrue(SecondaryParticlePolicy.contains(center, 32, 200, 123, 36));
        assertFalse(SecondaryParticlePolicy.contains(center, 32, 200, 156, 4));
        assertFalse(SecondaryParticlePolicy.contains(center, 32, 232, 155, 4));
        assertFalse(SecondaryParticlePolicy.contains(center, 32, Double.NaN, 123, 4));
        assertFalse(SecondaryParticlePolicy.contains(center, 32, Double.POSITIVE_INFINITY, 123, 4));
    }
}
