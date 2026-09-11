package com.skyeshade.skyesight.network;

import com.skyeshade.skyesight.remote.SkyesightRemoteViewRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SkyesightEnvironmentCacheTest {
    private final ResourceLocation id = ResourceLocation.parse("test:reverse");
    private final Object connection = new Object();
    private final SkyesightEnvironmentCache cache = new SkyesightEnvironmentCache();
    private SkyesightEnvironmentPayload snapshot(long generation) {
        return new SkyesightEnvironmentPayload(id, Level.OVERWORLD, generation, 500, 23000,
                false, 0, 1, true, false, .6f, 0);
    }
    @AfterEach void clearRegistry() { SkyesightRemoteViewRegistry.clear(); }
    @Test void environmentCanPrecedeWorldCreationAndSurvivePhysicalDimensionChange() {
        SkyesightRemoteViewRegistry.register(id, Level.OVERWORLD, 1);
        var payload = snapshot(1);
        cache.put(connection, payload);
        assertSame(payload, cache.get(connection, id, Level.OVERWORLD));
        assertNull(cache.get(connection, id, Level.NETHER));
        assertSame(payload, cache.get(connection, id, Level.OVERWORLD));
    }
    @Test void replacementGenerationRejectsOldSnapshotAndDelayedPackets() {
        SkyesightRemoteViewRegistry.register(id, Level.OVERWORLD, 1);
        cache.put(connection, snapshot(1));
        SkyesightRemoteViewRegistry.register(id, Level.OVERWORLD, 2);
        assertNull(cache.get(connection, id, Level.OVERWORLD));
        var current = snapshot(2); cache.put(connection, current);
        cache.put(connection, snapshot(1));
        assertSame(current, cache.get(connection, id, Level.OVERWORLD));
    }
    @Test void connectionAndExplicitRetirementDiscardCachedEnvironment() {
        SkyesightRemoteViewRegistry.register(id, Level.OVERWORLD, 1);
        cache.put(connection, snapshot(1));
        assertNull(cache.get(new Object(), id, Level.OVERWORLD));
        cache.put(connection, snapshot(1)); cache.remove(id);
        assertNull(cache.get(connection, id, Level.OVERWORLD));
    }
}
