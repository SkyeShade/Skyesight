package com.skyeshade.skyesight.client.world;

import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;
import java.util.HashSet;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class SkyesightChunkDemandTest {
    private static final ResourceKey<Level> TARGET = ResourceKey.create(Registries.DIMENSION,ResourceLocation.parse("test:decay"));
    @Test void stationaryCameraResizesDemandWithoutReplacingItsGeneration() {
        var state = new SkyesightChunkDemand(7, TARGET);
        var center = new ChunkPos(30, -20);
        Set<ChunkPos> loaded = new HashSet<>();
        long now = 0;
        for (int drawRadius : new int[]{2, 4, 8, 16, 4, 12, 4}) {
            int loadRadius = drawRadius + 1;
            var requested = state.update(center, loadRadius, now++, (x, z) -> loaded.contains(new ChunkPos(x, z)));
            assertNotNull(requested, "A radius change must publish updated demand even if chunks are cached");
            int batches = 0;
            while (requested != null) {
                assertTrue(++batches < 20, "Stationary demand must converge");
                for (ChunkPos chunk : requested) {
                    assertTrue(state.contains(chunk, loadRadius));
                    loaded.add(chunk);
                    state.acknowledge(7, TARGET, chunk.x, chunk.z);
                }
                requested = state.update(center, loadRadius, now++, (x, z) -> loaded.contains(new ChunkPos(x, z)));
            }
            assertEquals(loadRadius, state.radius);
            assertTrue(state.pending.isEmpty());
            assertEquals(7, state.generation);
            for (int z = -loadRadius; z <= loadRadius; z++) {
                for (int x = -loadRadius; x <= loadRadius; x++) {
                    assertTrue(loaded.contains(new ChunkPos(center.x + x, center.z + z)));
                }
            }
        }
    }
    @Test void reusedViewIdReplacesGenerationStateAndLateRetirementCannotClearIt() {
        var id = ResourceLocation.parse("test:camera");
        try {
            var old = SkyesightClientChunkRequester.stateFor(id,1,TARGET);
            old.update(new ChunkPos(0,0),5,0,(x,z)->false);
            var current = SkyesightClientChunkRequester.stateFor(id,2,TARGET);
            assertNotSame(old,current);
            assertNull(current.center);
            assertTrue(current.pending.isEmpty());
            current.update(new ChunkPos(20,20),5,0,(x,z)->false);
            SkyesightClientChunkRequester.reset(id,1);
            assertSame(current,SkyesightClientChunkRequester.stateFor(id,2,TARGET));
            SkyesightClientChunkRequester.markChunkReceived(id,1,TARGET,20,20);
            assertEquals(121,current.pending.size());
            SkyesightClientChunkRequester.reset(id,2);
            assertNotSame(current,SkyesightClientChunkRequester.stateFor(id,2,TARGET));
        } finally { SkyesightClientChunkRequester.reset(id); }
    }
    @Test void firstEquipAndReequipFollowTheSameMovingCamera() {
        for (long generation : new long[]{1,2}) {
            var state = new SkyesightChunkDemand(generation,TARGET);
            int start = generation==1 ? 0 : 20;
            Set<ChunkPos> loaded = new HashSet<>();
            var first = state.update(new ChunkPos(start,start),5,0,(x,z)->loaded.contains(new ChunkPos(x,z)));
            assertEquals(121,first.size());
            loaded.addAll(first);
            assertNull(state.update(new ChunkPos(start,start),5,1,(x,z)->loaded.contains(new ChunkPos(x,z))));
            for (int step=1;step<=15;step++) {
                ChunkPos camera = new ChunkPos(start+step,start);
                var next = state.update(camera,5,step+1,(x,z)->loaded.contains(new ChunkPos(x,z)));
                assertNotNull(next);
                assertEquals(11,next.size());
                assertEquals(camera,state.center);
                loaded.addAll(next);
            }
        }
    }
    @Test void pendingResponsesRetryAndOldGenerationCannotAcknowledgeNewDemand() {
        var state = new SkyesightChunkDemand(2,TARGET);
        var center = new ChunkPos(20,20);
        assertEquals(121,state.update(center,5,0,(x,z)->false).size());
        state.acknowledge(1,TARGET,20,20);
        assertEquals(121,state.pending.size());
        assertNull(state.update(center,5,SkyesightChunkDemand.RETRY_NANOS-1,(x,z)->false));
        assertEquals(121,state.update(center,5,SkyesightChunkDemand.RETRY_NANOS,(x,z)->false).size());
        state.acknowledge(2,TARGET,20,20);
        assertEquals(120,state.pending.size());
        var moved = state.update(new ChunkPos(40,20),5,SkyesightChunkDemand.RETRY_NANOS+1,(x,z)->false);
        assertEquals(121,moved.size());
        assertEquals(121,state.pending.size());
    }
    @Test void recenterSendsWatchEvenWithNoMissingChunksAndOldCenterIsOutsideDemand() {
        var state = new SkyesightChunkDemand(2,TARGET);
        assertTrue(state.update(new ChunkPos(0,0),5,0,(x,z)->true).isEmpty());
        assertTrue(state.update(new ChunkPos(20,0),5,1,(x,z)->true).isEmpty());
        assertEquals(new ChunkPos(20,0),state.center);
        assertFalse(state.contains(new ChunkPos(0,0),state.radius+3));
        assertTrue(state.contains(new ChunkPos(20,0),state.radius+3));
    }
}
