package com.skyeshade.skyesight.api;

import com.skyeshade.skyesight.network.SkyesightTraversalPayload;
import io.netty.buffer.Unpooled;
import net.minecraft.core.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PortalPairSettingsTest {
    @Test
    void reverseIdentityDoesNotDependOnSharedEndpointGeometry() {
        UUID id = UUID.randomUUID();
        var a = SkyesightPortalApi.portalDirectionId(id, true);
        var b = SkyesightPortalApi.portalDirectionId(id, false);
        assertEquals(b, SkyesightTraversalPayload.pairedPortalId(a));
        assertEquals(a, SkyesightTraversalPayload.pairedPortalId(b));
        assertNull(SkyesightTraversalPayload.pairedPortalId(ResourceLocation.parse("other:visual")));
    }

    @Test
    void rectangleDecompositionPreservesSteppedOpeningWithoutPerCellDraws() {
        var shape = PortalAperture.grid(".###.", "#####", "#####", "#####", ".###.");
        assertEquals(3, shape.rectangles().size());
        assertEquals(21.0 / 25, shape.rectangles().stream().mapToDouble(r -> (r.right() - r.left()) * (r.bottom() - r.top())).sum(), 1e-9);
        assertEquals(1, PortalAperture.grid("###", "###", "###").rectangles().size());
    }

    @Test
    void gameplayIsExplicitAndFullDefinitionSurvivesNetwork() {
        assertEquals(PortalBehavior.VISUAL_ONLY, PortalPairSettings.defaults().behavior());
        var shape = PortalAperture.grid(".###.", "#####", "#####", "#####", ".###.");
        var a = PortalEndpoint.of("a", Level.OVERWORLD, new Vec3(13, 81, -9), Direction.EAST, 5, 5);
        var b = PortalEndpoint.of("b", Level.NETHER, new Vec3(1000, 70, 4), Direction.NORTH, 5, 5);
        var settings = PortalPairSettings.of(PortalBehavior.TRAVERSABLE).withApertures(shape, shape)
                .withSides(PortalSidedness.BACK_ONLY, PortalSidedness.FRONT_ONLY)
                .withDirectionality(PortalDirectionality.A_TO_B);
        var p = new SkyesightTraversalPayload(UUID.randomUUID(), 47, 0, 0, true, a, b, Vec3.ZERO, settings);
        var buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            SkyesightTraversalPayload.STREAM_CODEC.encode(buf, p);
            assertEquals(p, SkyesightTraversalPayload.STREAM_CODEC.decode(buf));
            assertEquals(0, buf.readableBytes());
        } finally {
            buf.release();
        }
    }

    @Test
    void closedCellsInsideBodyCannotBeSkippedByCornerTests() {
        var shape = PortalAperture.grid("###", "#.#", "###");
        var e = PortalEndpoint.of("e", Level.OVERWORLD, Vec3.ZERO, Direction.SOUTH, 3, 3);
        assertFalse(shape.contains(e, Vec3.ZERO));
        assertFalse(shape.fits(e, new AABB(-1.4, -1.4, -.1, 1.4, 1.4, .1)));
        assertTrue(shape.fits(e, new AABB(-1.4, -1.4, -.1, -.6, 1.4, .1)));
        assertFalse(shape.fits(e, new AABB(-2, -1, -.1, 0, 1, .1)));
    }
}
