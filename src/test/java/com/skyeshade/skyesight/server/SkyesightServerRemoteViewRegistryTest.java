package com.skyeshade.skyesight.server;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class SkyesightServerRemoteViewRegistryTest {
    private static final ResourceLocation VIEW = ResourceLocation.parse("test:arcane_lens");
    private static final ResourceKey<Level> DECAY = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse("test:decay"));

    @AfterEach void clear() { SkyesightServerRemoteViewRegistry.clear(); }

    @Test void sameViewIdAndDifferentGenerationsRemainPlayerScoped() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        assertTrue(SkyesightServerRemoteViewRegistry.register(a, VIEW, DECAY, 10));
        assertTrue(SkyesightServerRemoteViewRegistry.register(b, VIEW, DECAY, 1));
        assertTrue(SkyesightServerRemoteViewRegistry.unregister(a, VIEW, 10).isPresent());
        assertTrue(SkyesightServerRemoteViewRegistry.register(b, VIEW, DECAY, 1));
        assertTrue(SkyesightServerRemoteViewRegistry.unregister(b, VIEW, 1).isPresent());
    }

    @Test void reequipRejectsOldRegistrationAndLateUnregister() {
        UUID a = UUID.randomUUID();
        assertTrue(SkyesightServerRemoteViewRegistry.register(a, VIEW, DECAY, 1));
        assertTrue(SkyesightServerRemoteViewRegistry.unregister(a, VIEW, 1).isPresent());
        assertFalse(SkyesightServerRemoteViewRegistry.register(a, VIEW, DECAY, 1));
        assertTrue(SkyesightServerRemoteViewRegistry.register(a, VIEW, DECAY, 2));
        assertFalse(SkyesightServerRemoteViewRegistry.register(a, VIEW, DECAY, 1));
        assertTrue(SkyesightServerRemoteViewRegistry.unregister(a, VIEW, 1).isEmpty());
        assertTrue(SkyesightServerRemoteViewRegistry.unregister(a, VIEW, 2).isPresent());
        assertFalse(SkyesightServerRemoteViewRegistry.register(a, VIEW, DECAY, 2));
    }

    @Test void dimensionChangeRequiresNewGeneration() {
        UUID a = UUID.randomUUID();
        var other = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse("test:other"));
        assertTrue(SkyesightServerRemoteViewRegistry.register(a, VIEW, DECAY, 1));
        assertFalse(SkyesightServerRemoteViewRegistry.register(a, VIEW, other, 1));
        assertTrue(SkyesightServerRemoteViewRegistry.register(a, VIEW, other, 2));
        assertEquals(other, SkyesightServerRemoteViewRegistry.unregister(a, VIEW, 2).orElseThrow().targetDimension());
    }
}
