package com.skyeshade.skyesight.mixin.common;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LivingEntity.class)
public interface LivingEntityAnimationAccessor {
    @Accessor("attackStrengthTicker")
    int skyesight$getAttackStrengthTicker();
    @Accessor("attackStrengthTicker")
    void skyesight$setAttackStrengthTicker(int ticks);
    @Accessor("attackAnim")
    float skyesight$getAttackAnim();

    @Accessor("attackAnim")
    void skyesight$setAttackAnim(float attackAnim);

    @Accessor("oAttackAnim")
    float skyesight$getOAttackAnim();

    @Accessor("oAttackAnim")
    void skyesight$setOAttackAnim(float oAttackAnim);

    @Accessor("swinging")
    boolean skyesight$isSwinging();

    @Accessor("swinging")
    void skyesight$setSwinging(boolean swinging);

    @Accessor("swingingArm")
    InteractionHand skyesight$getSwingingArm();

    @Accessor("swingingArm")
    void skyesight$setSwingingArm(InteractionHand swingingArm);

    @Accessor("swingTime")
    int skyesight$getSwingTime();

    @Accessor("swingTime")
    void skyesight$setSwingTime(int swingTime);
}
