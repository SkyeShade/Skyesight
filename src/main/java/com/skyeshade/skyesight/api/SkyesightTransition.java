package com.skyeshade.skyesight.api;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Matrix4f;

/** One bounded visual handoff. Beginning never changes player position or sends a teleport. */
public interface SkyesightTransition extends AutoCloseable {
    enum Status { PREPARING, READY, PREDICTING, PRESENTING, COMPLETE, CANCELLED, FALLBACK }
    record Destination(ResourceKey<Level> dimension, Vec3 eyePosition, Quaternionf rotation, Matrix4f projection) {
        public Destination { rotation = new Quaternionf(rotation); projection = new Matrix4f(projection); }
        @Override public Quaternionf rotation() { return new Quaternionf(rotation); }
        @Override public Matrix4f projection() { return new Matrix4f(projection); }
    }
    Status status();
    /** Null until a valid destination frame has been captured. */
    Destination destination();
    /** Freeze the prepared image immediately before requesting the server-authoritative teleport. */
    boolean begin();
    /** Present the warmed scene live. Also confirms an existing predicted crossing. */
    default boolean beginLive() { return begin(); }
    /** Bounded visual prediction only; an authoritative traversal must subsequently confirm it. */
    default boolean predictCrossing() { return false; }
    /** Cancels presentation and releases the owned image. Never closes the selected view. */
    @Override void close();
}
