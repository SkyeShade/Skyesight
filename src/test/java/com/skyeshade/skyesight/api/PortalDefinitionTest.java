package com.skyeshade.skyesight.api;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PortalDefinitionTest {
    @Test void wallAndFreeStandingPoliciesAreExplicitAndIndependent() {
        for(var behavior:PortalBehavior.values()) for(var direction:PortalDirectionality.values()) {
            var wall=PortalDefinition.builder(UUID.randomUUID()).endpointA(a).endpointB(b)
                    .behavior(behavior).directionality(direction).sides(PortalSidedness.FRONT_ONLY).build();
            assertEquals(behavior,wall.settings().behavior());
            assertEquals(direction,wall.settings().directionality());
            assertFalse(wall.settings().sides(true).allows(-1));
            assertFalse(wall.settings().sides(false).allows(-1));
            var free=wall.toBuilder().sides(PortalSidedness.BOTH).build();
            assertTrue(free.settings().sides(true).allows(-1));
            assertTrue(free.settings().sides(false).allows(-1));
            assertEquals(direction,free.settings().directionality());
        }
    }
    private final PortalEndpoint a=PortalEndpoint.of("a",Vec3.ZERO,Direction.NORTH);
    private final PortalEndpoint b=PortalEndpoint.of("b",new Vec3(10,0,0),Direction.EAST);
    @Test void defaultsAndEndpointReplacementAreDeclarative() {
        var d=PortalDefinition.builder(UUID.randomUUID()).endpointA(a).endpointB(b).build();
        assertEquals(PortalBehavior.VISUAL_ONLY,d.settings().behavior());
        assertEquals(PortalDirectionality.TWO_WAY,d.settings().directionality());
        var moved=d.toBuilder().endpointB(b.withDimension(Level.NETHER)).build();
        assertEquals(d.id(),moved.id()); assertEquals(a,moved.endpointA()); assertNotEquals(d.endpointB(),moved.endpointB());
        d.endpointA().rotation().rotateY(1);
        assertEquals(a.rotation(),d.endpointA().rotation());
    }
    @Test void directionAndFacesRemainIndependentAcrossSettingsUpdates() {
        var shape=PortalAperture.grid(".#.","###");
        var d=PortalDefinition.builder(UUID.randomUUID()).endpointA(a).endpointB(b)
                .sides(PortalSidedness.BACK_ONLY,PortalSidedness.FRONT_ONLY).directionality(PortalDirectionality.A_TO_B)
                .behavior(PortalBehavior.TRAVERSABLE).aperture(shape).build();
        assertTrue(d.settings().sides(true).allows(-1)); assertFalse(d.settings().sides(true).allows(1));
        assertTrue(d.settings().sides(false).allows(1)); assertFalse(d.settings().directionality().allows(false));
        assertEquals(shape,d.settings().apertureA());
        assertEquals(PortalDirectionality.A_TO_B,d.settings().withSourceTag("test").directionality());
    }
}
