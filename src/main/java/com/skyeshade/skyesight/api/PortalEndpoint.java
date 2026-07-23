package com.skyeshade.skyesight.api;

import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

public record PortalEndpoint(
        String id,
        ResourceKey<Level> dimension,
        Vec3 center,
        Direction facing,
        Quaternionf rotation,
        float width,
        float height
) {
    public static final float DEFAULT_WIDTH = 1.0F;
    public static final float DEFAULT_HEIGHT = 2.0F;
    public static final float MAX_SIZE = 64.0F;

    public PortalEndpoint {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Portal endpoint id cannot be null or blank");
        }
        if (dimension == null) {
            throw new IllegalArgumentException("Portal endpoint dimension cannot be null");
        }
        if (center == null) {
            throw new IllegalArgumentException("Portal endpoint center cannot be null");
        }
        if (facing == null) {
            throw new IllegalArgumentException("Portal endpoint facing cannot be null");
        }
        if (rotation == null) {
            rotation = rotationForFacing(facing);
        } else {
            rotation = new Quaternionf(rotation);
        }
        validateSize(width, height);
    }

    public PortalEndpoint(String id, ResourceKey<Level> dimension, Vec3 center, Direction facing, Quaternionf rotation) {
        this(id, dimension, center, facing, rotation, DEFAULT_WIDTH, DEFAULT_HEIGHT);
    }

    public PortalEndpoint(String id, Vec3 center, Direction facing, Quaternionf rotation, float width, float height) {
        this(id, Level.OVERWORLD, center, facing, rotation, width, height);
    }

    public PortalEndpoint(String id, Vec3 center, Direction facing, Quaternionf rotation) {
        this(id, Level.OVERWORLD, center, facing, rotation, DEFAULT_WIDTH, DEFAULT_HEIGHT);
    }

    public static PortalEndpoint of(String id, ResourceKey<Level> dimension, Vec3 center, Direction facing) {
        return of(id, dimension, center, facing, DEFAULT_WIDTH, DEFAULT_HEIGHT);
    }

    public static PortalEndpoint of(String id, ResourceKey<Level> dimension, Vec3 center, Direction facing, float width, float height) {
        return new PortalEndpoint(id, dimension, center, facing, rotationForFacing(facing), width, height);
    }

    public static PortalEndpoint of(String id, Vec3 center, Direction facing) {
        return of(id, Level.OVERWORLD, center, facing, DEFAULT_WIDTH, DEFAULT_HEIGHT);
    }

    public static PortalEndpoint of(String id, Vec3 center, Direction facing, float width, float height) {
        return of(id, Level.OVERWORLD, center, facing, width, height);
    }

    public PortalEndpoint size(float width, float height) {
        return new PortalEndpoint(this.id, this.dimension, this.center, this.facing, this.rotation, width, height);
    }

    public PortalEndpoint width(float width) {
        return size(width, this.height);
    }

    public PortalEndpoint height(float height) {
        return size(this.width, height);
    }

    public PortalEndpoint withDimension(ResourceKey<Level> dimension) {
        return new PortalEndpoint(this.id, dimension, this.center, this.facing, this.rotation, this.width, this.height);
    }

    public static PortalEndpoint fromBlockHit(
            String id,
            ResourceKey<Level> dimension,
            BlockPos clickedPos,
            Direction clickedFace,
            Direction playerFacing,
            float width,
            float height
    ) {
        Direction facing = clickedFace.getAxis().isHorizontal() ? clickedFace.getOpposite() : playerFacing;
        if (facing == null || !facing.getAxis().isHorizontal()) {
            facing = Direction.NORTH;
        }

        Vec3 center;
        if (clickedFace.getAxis().isHorizontal()) {
            center = new Vec3(
                    clickedPos.getX() + 0.5D + clickedFace.getStepX() * 0.501D,
                    clickedPos.getY() + height * 0.5D,
                    clickedPos.getZ() + 0.5D + clickedFace.getStepZ() * 0.501D
            );
        } else {
            center = new Vec3(
                    clickedPos.getX() + 0.5D,
                    clickedPos.getY() + (clickedFace == Direction.UP ? 1.0D : 0.0D) + height * 0.5D,
                    clickedPos.getZ() + 0.5D
            );
        }
        return of(id, dimension, center, facing, width, height);
    }

    public static void validateSize(float width, float height) {
        if (!Float.isFinite(width) || !Float.isFinite(height)) {
            throw new IllegalArgumentException("Portal endpoint width/height must be finite");
        }
        if (width <= 0.0F || height <= 0.0F) {
            throw new IllegalArgumentException("Portal endpoint width/height must be positive");
        }
        if (width > MAX_SIZE || height > MAX_SIZE) {
            throw new IllegalArgumentException("Portal endpoint width/height must be <= " + MAX_SIZE);
        }
    }

    public static Quaternionf rotationForFacing(Direction facing) {
        return switch (facing) {
            case NORTH -> new Quaternionf();
            case EAST -> rotationFromYawPitchRoll(-90.0F, 0.0F, 0.0F);
            case SOUTH -> rotationFromYawPitchRoll(180.0F, 0.0F, 0.0F);
            case WEST -> rotationFromYawPitchRoll(90.0F, 0.0F, 0.0F);
            case UP -> rotationFromYawPitchRoll(0.0F, -90.0F, 0.0F);
            case DOWN -> rotationFromYawPitchRoll(0.0F, 90.0F, 0.0F);
        };
    }

    public static Quaternionf rotationFromYawPitchRoll(float yawDegrees, float pitchDegrees, float rollDegrees) {
        return new Quaternionf()
                .rotateY((float) Math.toRadians(yawDegrees))
                .rotateX((float) Math.toRadians(pitchDegrees))
                .rotateZ((float) Math.toRadians(rollDegrees));
    }
}
