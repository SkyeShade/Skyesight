package com.skyeshade.skyesight.client.world;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SecondaryDestroyProgressTest {
    @Test
    void serverStagesPersistBetweenUpdatesAndClearOnAbort() {
        var state = new SecondaryDestroyProgress();
        for (int stage = 0; stage < 10; stage++) {
            state.update(123, BlockPos.ZERO, stage, stage * 10);
            assertEquals(stage, state.visible(stage * 10 + 9).iterator().next().stage());
        }
        state.update(123, BlockPos.ZERO, -1, 100);
        assertTrue(state.visible(101).isEmpty());
    }

    @Test
    void lateClearForPreviousTargetDoesNotEraseNewTarget() {
        var state = new SecondaryDestroyProgress();
        state.update(123, BlockPos.ZERO, 3, 1);
        state.update(123, BlockPos.ZERO.above(), 0, 2);
        state.update(123, BlockPos.ZERO, -1, 3);
        assertEquals(BlockPos.ZERO.above(), state.visible(4).iterator().next().pos());
    }

    @Test
    void multipleBreakersUseLargestStageWithoutSharingRemoval() {
        var state = new SecondaryDestroyProgress();
        state.update(123, BlockPos.ZERO, 3, 1);
        state.update(456, BlockPos.ZERO, 6, 1);
        assertEquals(6, state.visible(2).iterator().next().stage());
        state.update(456, BlockPos.ZERO, -1, 3);
        assertEquals(3, state.visible(4).iterator().next().stage());
        assertTrue(state.visible(402).isEmpty());
    }
}
