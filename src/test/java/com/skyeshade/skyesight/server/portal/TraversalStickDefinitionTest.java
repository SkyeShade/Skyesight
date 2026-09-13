package com.skyeshade.skyesight.server.portal;

import com.skyeshade.skyesight.api.*;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class TraversalStickDefinitionTest {
    @Test void debugToolKeepsPreRedesignFreeStandingPolicy() {
        var a=PortalEndpoint.of("traversal",Level.OVERWORLD,new Vec3(.5,81.5,.5),Direction.SOUTH,2,3);
        var b=PortalEndpoint.of("traversal",Level.NETHER,new Vec3(7.5,81.5,-7.5),Direction.EAST,2,3);
        var id=UUID.randomUUID();
        var definition=TraversalPortalManager.debugDefinition(id,a,b);
        assertEquals(id,definition.id());
        assertEquals(a,definition.endpointA()); assertEquals(b,definition.endpointB());
        assertEquals(PortalBehavior.TRAVERSABLE,definition.settings().behavior());
        assertEquals(PortalDirectionality.TWO_WAY,definition.settings().directionality());
        assertEquals(PortalSidedness.BOTH,definition.settings().sidednessA());
        assertEquals(PortalSidedness.BOTH,definition.settings().sidednessB());
        assertTrue(definition.settings().renderBackface());
        assertEquals(PortalAperture.RECTANGLE,definition.settings().apertureA());
        assertEquals(PortalAperture.RECTANGLE,definition.settings().apertureB());
        assertEquals(PortalPairSettings.of(PortalBehavior.TRAVERSABLE),definition.settings());
    }
}
