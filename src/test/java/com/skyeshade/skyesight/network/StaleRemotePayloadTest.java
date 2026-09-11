package com.skyeshade.skyesight.network;

import com.skyeshade.skyesight.remote.SkyesightRemoteViewRegistry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StaleRemotePayloadTest {
    private static final ResourceLocation VIEW = ResourceLocation.parse("test:arcane_lens");
    private static final ResourceKey<Level> TARGET = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse("test:decay"));

    @AfterEach void clear() { SkyesightRemoteViewRegistry.clear(); }

    @Test void delayedUpdatesNeverEnterVisualWorldAfterReequipOrClose() {
        SkyesightRemoteViewRegistry.register(VIEW, TARGET, 2);
        rejectOldUpdates();
        assertTrue(SkyesightRemoteViewRegistry.accepts(VIEW, 2, TARGET));
        SkyesightRemoteViewRegistry.unregister(VIEW, 2);
        rejectOldUpdates();
        assertTrue(SkyesightRemoteViewRegistry.get(VIEW).isEmpty());
    }

    private static void rejectOldUpdates() {
        // No Minecraft/visual world exists here: a stale payload must return before accessing either.
        // Null bodies also ensure rejected packets cannot be consumed accidentally.
        assertDoesNotThrow(() -> SkyesightClientBlockUpdateHandler.handle(new SkyesightBlockUpdatesPayload(VIEW, 1, TARGET, null)));
        assertDoesNotThrow(() -> SkyesightClientBlockEventHandler.handle(new SkyesightBlockEventPayload(VIEW, 1, TARGET, null, 0, 0)));
        assertDoesNotThrow(() -> SkyesightClientLightDataHandler.handle(new SkyesightLightDataPayload(VIEW, 1, TARGET, 0, 0, null)));
        assertDoesNotThrow(() -> SkyesightClientEntityHandler.handle(new SkyesightEntitySnapshotPayload(VIEW, 1, TARGET, null)));
        assertDoesNotThrow(() -> SkyesightClientParticleHandler.handle(new SkyesightParticlePayload(VIEW, 1, TARGET, null, false, 0, 0, 0, 0, 0, 0, 0, 0)));
        assertDoesNotThrow(() -> SkyesightClientEnvironmentHandler.handle(new SkyesightEnvironmentPayload(
                VIEW, TARGET, 1, 0, 0, false, 0, 1, false, false, 0, 0)));
    }
}
