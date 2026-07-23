package com.skyeshade.skyesight.client.portal;

import com.skyeshade.skyesight.api.SkyesightClipPlane;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

public record PortalViewPlacement(
        Vec3 cameraPosition,
        Quaternionf cameraRotation,
        SkyesightClipPlane clipPlane
) {}