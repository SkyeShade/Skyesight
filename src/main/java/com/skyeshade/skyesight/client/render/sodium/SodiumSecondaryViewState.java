package com.skyeshade.skyesight.client.render.sodium;

import com.skyeshade.skyesight.Skyesight;
import com.skyeshade.skyesight.client.render.SecondaryViewContext;
import com.skyeshade.skyesight.client.world.SameLevelSkyesightChunkSource;
import net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer;
import net.caffeinemc.mods.sodium.client.render.chunk.map.ChunkTracker;
import net.minecraft.client.multiplayer.ClientLevel;

public final class SodiumSecondaryViewState {
    private final ChunkTracker chunkTracker = new ChunkTracker();
    private final SameLevelSkyesightChunkSource chunkSource = new SameLevelSkyesightChunkSource();
    private SodiumWorldRenderer renderer;
    private ClientLevel rendererLevel;

    private SodiumSecondaryViewState() {
    }

    public static SodiumSecondaryViewState getOrCreate(SecondaryViewContext context) {
        Object state = context.sodiumState();
        if (state instanceof SodiumSecondaryViewState sodiumState) {
            return sodiumState;
        }

        SodiumSecondaryViewState sodiumState = new SodiumSecondaryViewState();
        context.setSodiumState(sodiumState);
        return sodiumState;
    }

    public static SodiumSecondaryViewState get(SecondaryViewContext context) {
        Object state = context == null ? null : context.sodiumState();
        return state instanceof SodiumSecondaryViewState sodiumState ? sodiumState : null;
    }

    public static void close(SecondaryViewContext context) {
        SodiumSecondaryViewState state = get(context);
        if (state != null) {
            state.close();
            context.setSodiumState(null);
        }
    }

    public ChunkTracker chunkTracker() {
        return this.chunkTracker;
    }

    public SameLevelSkyesightChunkSource chunkSource() {
        return this.chunkSource;
    }

    public SodiumWorldRenderer renderer() {
        return this.renderer;
    }

    public void setRenderer(SodiumWorldRenderer renderer) {
        this.renderer = renderer;
    }

    public ClientLevel rendererLevel() {
        return this.rendererLevel;
    }

    public void setRendererLevel(ClientLevel rendererLevel) {
        this.rendererLevel = rendererLevel;
    }

    private void close() {
        if (this.renderer != null) {
            try {
                this.renderer.setLevel(null);
            } catch (RuntimeException exception) {
                Skyesight.LOGGER.warn("[Skyesight] Failed to release secondary Sodium renderer during context close", exception);
            }
            this.renderer = null;
            this.rendererLevel = null;
        }
    }
}
