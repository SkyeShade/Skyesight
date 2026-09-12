package com.skyeshade.skyesight.client.world;

import net.minecraft.core.BlockPos;

import java.util.*;

/**
 * One native server stream per breaker, independent of client prediction and entity presence.
 */
public final class SecondaryDestroyProgress {
    public record Crack(BlockPos pos, int stage, long updated) {
    }

    private final Map<Integer, Crack> cracks = new HashMap<>();

    public void update(int breaker, BlockPos pos, int stage, long tick) {
        if (stage < 0 || stage > 9) {
            var old = cracks.get(breaker);
            if (old != null && old.pos().equals(pos)) cracks.remove(breaker);
        } else {
            if (!cracks.containsKey(breaker) && cracks.size() >= 4096) return;
            cracks.put(breaker, new Crack(pos.immutable(), stage, tick));
        }
    }

    public Collection<Crack> visible(long tick) {
        cracks.values().removeIf(c -> tick - c.updated() > 400);
        Map<BlockPos, Crack> blocks = new HashMap<>();
        for (var c : cracks.values()) blocks.merge(c.pos(), c, (a, b) -> a.stage() >= b.stage() ? a : b);
        return blocks.values();
    }

    public void clear() {
        cracks.clear();
    }

    public void removeBlock(BlockPos pos) {
        cracks.values().removeIf(c -> c.pos().equals(pos));
    }
}
