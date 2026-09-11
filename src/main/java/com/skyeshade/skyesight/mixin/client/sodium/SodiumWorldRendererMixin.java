package com.skyeshade.skyesight.mixin.client.sodium;

import com.skyeshade.skyesight.client.render.sodium.SkyesightSodiumRenderContext;
import net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer;
import net.caffeinemc.mods.sodium.client.render.chunk.map.ChunkTracker;
import net.caffeinemc.mods.sodium.client.render.chunk.map.ChunkTrackerHolder;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = SodiumWorldRenderer.class, remap = false)
public abstract class SodiumWorldRendererMixin {
    @Redirect(
            method = {"processChunkEvents", "initRenderer"},
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/map/ChunkTrackerHolder;get(Lnet/minecraft/client/multiplayer/ClientLevel;)Lnet/caffeinemc/mods/sodium/client/render/chunk/map/ChunkTracker;"
            )
    )
    private ChunkTracker skyesight$useSkyesightTracker(ClientLevel level) {
        ChunkTracker tracker = SkyesightSodiumRenderContext.currentTracker();

        if (tracker != null) {
            return tracker;
        }

        return ChunkTrackerHolder.get(level);
    }
}
