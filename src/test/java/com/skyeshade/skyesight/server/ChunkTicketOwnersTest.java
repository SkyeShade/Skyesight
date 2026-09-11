package com.skyeshade.skyesight.server;

import org.junit.jupiter.api.Test;
import java.util.HashSet;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class ChunkTicketOwnersTest {
    private record Chunk(String dimension, int x, int z) {}
    private record Owner(String player, String view, String purpose) {}

    @Test void independentCentersThenOverlapAndRelease() {
        var tickets = new ChunkTicketOwners<Chunk, Owner>();
        Owner a = new Owner("A", "arcane_lens", "camera");
        Owner b = new Owner("B", "arcane_lens", "camera");
        Set<Chunk> aChunks = region(0, 0);
        Set<Chunk> bChunks = region(20, 20);
        for (Chunk chunk : aChunks) assertTrue(tickets.acquire(chunk, a));
        for (Chunk chunk : bChunks) assertTrue(tickets.acquire(chunk, b));
        assertEquals(18, tickets.chunks().size());
        // B moves into a partly overlapping region. Acquire before releasing old chunks.
        Set<Chunk> moved = region(1, 0);
        for (Chunk chunk : moved) assertEquals(!aChunks.contains(chunk), tickets.acquire(chunk, b));
        for (Chunk chunk : bChunks) assertTrue(tickets.release(chunk, b));
        for (Chunk chunk : aChunks) assertEquals(!moved.contains(chunk), tickets.release(chunk, a));
        assertEquals(moved, tickets.chunks());
        for (Chunk chunk : moved) assertEquals(1, tickets.count(chunk));
        for (Chunk chunk : moved) assertTrue(tickets.release(chunk, b));
        assertTrue(tickets.chunks().isEmpty());
    }

    @Test void refreshAndDuplicateCleanupAreIdempotentAcrossSubsystems() {
        var tickets = new ChunkTicketOwners<Chunk, Owner>();
        var chunk = new Chunk("decay", 0, 0);
        var camera = new Owner("A", "arcane_lens", "camera");
        var simulation = new Owner("A", "arcane_lens", "simulation");
        assertTrue(tickets.acquire(chunk, camera));
        for (int tick = 0; tick < 200; tick++) {
            assertFalse(tickets.acquire(chunk, camera));
            assertFalse(tickets.acquire(chunk, simulation));
        }
        assertEquals(2, tickets.count(chunk));
        assertFalse(tickets.release(chunk, camera));
        assertFalse(tickets.release(chunk, camera));
        assertEquals(1, tickets.count(chunk));
        assertTrue(tickets.release(chunk, simulation));
        assertFalse(tickets.release(chunk, simulation));
    }

    @Test void dimensionsAndViewsHaveIndependentOwnership() {
        var tickets = new ChunkTicketOwners<Chunk, Owner>();
        var decay = new Chunk("decay", 0, 0);
        var other = new Chunk("other_dimension", 0, 0);
        var lens = new Owner("A", "arcane_lens", "camera");
        var second = new Owner("A", "another_camera", "camera");
        assertTrue(tickets.acquire(decay, lens));
        assertFalse(tickets.acquire(decay, second));
        assertTrue(tickets.acquire(other, lens));
        assertFalse(tickets.release(decay, lens));
        assertTrue(tickets.release(other, lens));
        assertEquals(Set.of(decay), tickets.chunks());
        assertTrue(tickets.release(decay, second));
    }

    private static Set<Chunk> region(int x, int z) {
        Set<Chunk> chunks = new HashSet<>();
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            chunks.add(new Chunk("decay", x + dx, z + dz));
        }
        return chunks;
    }
}
