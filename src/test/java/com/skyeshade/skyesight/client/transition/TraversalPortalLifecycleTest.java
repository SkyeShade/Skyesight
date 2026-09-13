package com.skyeshade.skyesight.client.transition;

import com.skyeshade.skyesight.api.PortalBehavior;
import com.skyeshade.skyesight.api.PortalEndpoint;
import com.skyeshade.skyesight.api.PortalPairSettings;
import com.skyeshade.skyesight.api.SkyesightPortalApi;
import com.skyeshade.skyesight.network.SkyesightTraversalPayload;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TraversalPortalLifecycleTest {
    private static final PortalEndpoint A = PortalEndpoint.of("a", Vec3.ZERO, Direction.NORTH);
    private static final PortalEndpoint B = PortalEndpoint.of("b", new Vec3(20, 0, 0), Direction.EAST);

    private static void define(UUID id, long revision) {
        TraversalPortalClient.receive(new SkyesightTraversalPayload(id, revision, 0,
                SkyesightTraversalPayload.DEFINE, true, A, B, Vec3.ZERO,
                PortalPairSettings.of(PortalBehavior.TRAVERSABLE)));
    }

    private static void remove(UUID id, long revision) {
        TraversalPortalClient.receive(new SkyesightTraversalPayload(id, revision, 0,
                SkyesightTraversalPayload.REMOVE, true, null, null));
    }

    @Test
    void stablePairIdSurvivesTwentyRemovalCyclesAndStalePackets() {
        UUID id = UUID.randomUUID();
        String a = SkyesightTraversalPayload.portalId(id, true);
        String b = SkyesightTraversalPayload.portalId(id, false);
        long generation = 0;
        for (int revision = 1; revision <= 20; revision++) {
            define(id, revision);
            var view = SkyesightPortalApi.getPortal(a);
            assertNotNull(view);
            assertEquals(b, view.pairedId());
            assertTrue(view.generation() > generation);
            generation = view.generation();
            remove(id, revision - 1);
            assertSame(view, SkyesightPortalApi.getPortal(a));
            remove(id, revision);
            assertNull(SkyesightPortalApi.getPortal(a));
            assertNull(SkyesightPortalApi.getPortal(b));
            define(id, revision);
            assertNull(SkyesightPortalApi.getPortal(a));
        }
    }

    @Test
    void removalBeforeDefinitionLeavesTombstone() {
        UUID id = UUID.randomUUID();
        String a = SkyesightTraversalPayload.portalId(id, true);
        remove(id, 8);
        define(id, 8);
        assertNull(SkyesightPortalApi.getPortal(a));
        define(id, 9);
        assertNotNull(SkyesightPortalApi.getPortal(a));
        remove(id, 9);
    }
}
