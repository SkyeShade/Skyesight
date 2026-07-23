package com.skyeshade.skyesight.api;

import net.minecraft.resources.ResourceLocation;

import java.util.List;

public record PortalRegistrationResult(boolean success, List<ResourceLocation> ids, String message) {
    public static PortalRegistrationResult success(List<ResourceLocation> ids, String message) {
        return new PortalRegistrationResult(true, List.copyOf(ids), message);
    }

    public static PortalRegistrationResult failure(String message) {
        return new PortalRegistrationResult(false, List.of(), message);
    }
}
