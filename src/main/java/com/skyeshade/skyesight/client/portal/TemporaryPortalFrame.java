package com.skyeshade.skyesight.client.portal;

import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

public record TemporaryPortalFrame(
        Vec3 position,
        Quaternionf rotation,
        float width,
        float height
) {}