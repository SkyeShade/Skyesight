package com.skyeshade.skyesight.client.render.entity;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.entity.PartEntity;

public final class PortalMultipartPartEligibility {
    private PortalMultipartPartEligibility() {}

    public static Result evaluate(Entity parent, PartEntity<?> part) {
        if (parent == null || part == null || part.isRemoved()) {
            return Result.skip("removed");
        }

        if (part.getParent() != parent) {
            return Result.skip("parent-mismatch");
        }

        AABB box = part.getBoundingBoxForCulling();
        if (box.getXsize() <= 0.0D || box.getYsize() <= 0.0D || box.getZsize() <= 0.0D) {
            return Result.skip("invalid-box");
        }

        double distance = part.position().distanceTo(parent.position());
        boolean nearOrigin = Math.abs(part.getX()) < 0.5D
                && Math.abs(part.getY()) < 0.5D
                && Math.abs(part.getZ()) < 0.5D;
        if (nearOrigin && distance > 32.0D) {
            return Result.skip("origin-dormant");
        }

        String partClass = part.getClass().getName();
        if (partClass.equals(parent.getClass().getName())) {
            return Result.skip("parent-class-part");
        }

        String rendererClass = rendererClass(part);
        String parentRendererClass = rendererClass(parent);
        if (!rendererClass.equals("unavailable") && rendererClass.equals(parentRendererClass)) {
            return Result.skip("parent-renderer-duplicate");
        }

        double maxReasonableDistance = Math.max(32.0D, parent.getBoundingBoxForCulling().getSize() * 8.0D);
        if (distance > maxReasonableDistance) {
            return Result.skip("part-too-far");
        }

        return Result.render(rendererClass);
    }

    private static String rendererClass(Entity entity) {
        try {
            return Minecraft.getInstance()
                    .getEntityRenderDispatcher()
                    .getRenderer(entity)
                    .getClass()
                    .getName();
        } catch (RuntimeException exception) {
            return "unavailable";
        }
    }

    public record Result(boolean render, String reason, String rendererClass) {
        private static Result render(String rendererClass) {
            return new Result(true, "render", rendererClass);
        }

        private static Result skip(String reason) {
            return new Result(false, reason, "-");
        }
    }
}
