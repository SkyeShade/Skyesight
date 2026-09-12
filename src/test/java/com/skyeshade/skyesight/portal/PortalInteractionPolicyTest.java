package com.skyeshade.skyesight.portal;

import com.skyeshade.skyesight.api.SkyesightPortalRaycast.*;
import net.minecraft.core.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PortalInteractionPolicyTest {
    final ResourceLocation id = ResourceLocation.parse("test:portal");
    final Step step = new Step(id, 4, Level.OVERWORLD, Level.NETHER, Vec3.ZERO, Vec3.ZERO, new Vec3(0, 0, 1), 2);

    Result result(End end, double distance, List<Step> chain) {
        return new Result(end, Level.NETHER, Vec3.ZERO, new Vec3(0, 0, 1), distance, 0,
                new BlockHitResult(Vec3.ZERO, Direction.UP, BlockPos.ZERO, false), chain);
    }

    boolean allowed(Result result) {
        return PortalInteractionPolicy.matches(result, id, 4, 4.5, 3);
    }

    @Test
    void totalReachIsNotResetAtExit() {
        assertTrue(allowed(result(End.HIT, 4, List.of(step))));
        assertFalse(allowed(result(End.HIT, 4.50001, List.of(step))));
    }

    @Test
    void staleRevisionAndUnrelatedPortalAreRejected() {
        var ray = result(End.HIT, 3, List.of(step));
        assertFalse(PortalInteractionPolicy.matches(ray, id, 3, 4.5, 3));
        assertFalse(PortalInteractionPolicy.matches(ray, ResourceLocation.parse("test:other"), 4, 4.5, 3));
    }

    @Test
    void SourceHitAndRecursivePathAreRejected() {
        assertFalse(allowed(result(End.HIT, 1, List.of())));
        assertFalse(allowed(result(End.HIT, 3, List.of(step, step))));
    }

    @Test
    void IncompleteAndInvalidResultsCannotAuthorizeAnAction() {
        for (var end : List.of(End.MISS, End.HOP_LIMIT, End.UNAVAILABLE))
            assertFalse(allowed(result(end, 3, List.of(step))));
        assertFalse(allowed(result(End.HIT, Double.NaN, List.of(step))));
        assertFalse(allowed(result(End.HIT, -1, List.of(step))));
    }
}
