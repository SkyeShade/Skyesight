package com.skyeshade.skyesight.mixin.common;

import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;

@Mixin(ItemEntity.class)
public interface ItemEntityAnimationAccessor {
    @Accessor("age")
    void skyesight$setVisualAge(int age);

    @Mutable
    @Accessor("bobOffs")
    void skyesight$setVisualBob(float bob);
}
