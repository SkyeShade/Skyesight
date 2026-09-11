package com.skyeshade.skyesight.client.world;

import com.skyeshade.skyesight.mixin.client.LivingEntityParticleDataAccessor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/** Only the visual branch of vanilla effect particles; no entity AI, movement or effect ticking. */
final class SecondaryEntityParticles {
    private SecondaryEntityParticles() {}
    static void tick(Entity entity) {
        if (!(entity instanceof LivingEntity living) || !(entity.level() instanceof SkyesightVisualClientLevel level)) return;
        var manager = level.skyesightParticleManager();
        if (manager == null || !manager.isActive()) return;
        var particles = living.getEntityData().get(LivingEntityParticleDataAccessor.skyesight$effectParticles());
        if (particles.isEmpty()) return;
        var random = living.getRandom();
        int chance = (living.isInvisible() ? 15 : 4)
                * (living.getEntityData().get(LivingEntityParticleDataAccessor.skyesight$effectAmbient()) ? 5 : 1);
        if (random.nextInt(chance) == 0) level.addParticle(particles.get(random.nextInt(particles.size())),
                living.getRandomX(.5), living.getRandomY(), living.getRandomZ(.5), 1, 1, 1);
    }
}
