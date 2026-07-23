package com.skyeshade.skyesight.client.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Camera;

public final class SkyesightSecondaryRenderContext {
    private static RenderTarget currentTarget;
    private static RenderTarget mainTarget;
    private static Camera currentCamera;
    private static int depth;

    private SkyesightSecondaryRenderContext() {}

    public static boolean isActive() {
        return depth > 0 && currentTarget != null;
    }

    public static RenderTarget currentTarget() {
        return currentTarget;
    }

    public static RenderTarget mainTarget() {
        return mainTarget;
    }

    public static Camera currentCamera() {
        return currentCamera;
    }

    public static Scope push(RenderTarget target, Camera camera) {
        return push(target, camera, null);
    }

    public static Scope push(RenderTarget target, Camera camera, RenderTarget realMainTarget) {
        RenderTarget previousTarget = currentTarget;
        RenderTarget previousMainTarget = mainTarget;
        Camera previousCamera = currentCamera;
        int previousDepth = depth;

        currentTarget = target;
        mainTarget = realMainTarget;
        currentCamera = camera;
        depth++;

        return new Scope(previousTarget, previousMainTarget, previousCamera, previousDepth);
    }

    public static final class Scope implements AutoCloseable {
        private final RenderTarget previousTarget;
        private final RenderTarget previousMainTarget;
        private final Camera previousCamera;
        private final int previousDepth;
        private boolean closed;

        private Scope(
                RenderTarget previousTarget,
                RenderTarget previousMainTarget,
                Camera previousCamera,
                int previousDepth
        ) {
            this.previousTarget = previousTarget;
            this.previousMainTarget = previousMainTarget;
            this.previousCamera = previousCamera;
            this.previousDepth = previousDepth;
        }

        @Override
        public void close() {
            if (this.closed) {
                return;
            }

            currentTarget = this.previousTarget;
            mainTarget = this.previousMainTarget;
            currentCamera = this.previousCamera;
            depth = this.previousDepth;
            this.closed = true;
        }
    }
}
