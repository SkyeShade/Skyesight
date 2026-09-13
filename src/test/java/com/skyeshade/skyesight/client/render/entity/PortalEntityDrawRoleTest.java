package com.skyeshade.skyesight.client.render.entity;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PortalEntityDrawRoleTest {
    private static final ResourceLocation A = ResourceLocation.fromNamespaceAndPath("skyesight", "a");
    private static final ResourceLocation B = ResourceLocation.fromNamespaceAndPath("skyesight", "b");

    @Test
    void cornerViewKeepsNativeBodyButSuppressesEntryDuplicate() {
        assertFalse(PortalEntityDrawRole.SOURCE.suppressEntrySelf(true, true, A, A));
        assertTrue(PortalEntityDrawRole.TRANSFORMED.suppressEntrySelf(true, true, A, A));
    }

    @Test
    void otherGeneratorsAndObserverViewsRemainVisible() {
        assertFalse(PortalEntityDrawRole.TRANSFORMED.suppressEntrySelf(true, true, A, B));
        assertFalse(PortalEntityDrawRole.TRANSFORMED.suppressEntrySelf(true, false, B, A));
        assertFalse(PortalEntityDrawRole.TRANSFORMED.suppressEntrySelf(true, true, null, A));
    }

    @Test
    void thirdPersonAndOtherPlayersRemainVisible() {
        assertFalse(PortalEntityDrawRole.TRANSFORMED.suppressEntrySelf(true, false, A, A));
        assertFalse(PortalEntityDrawRole.TRANSFORMED.suppressEntrySelf(false, true, A, A));
    }

    @Test
    void nestedObserverSeesEntryDuplicateEvenWhenPhysicalPlayerIsEnteringThatPortal() {
        for (int depth = 2; depth <= 5; depth++) {
            assertFalse(PortalEntityDrawRole.TRANSFORMED.suppressEntrySelf(true, true, A, A, depth > 1));
            assertFalse(PortalEntityDrawRole.SOURCE.suppressEntrySelf(true, true, A, A, depth > 1));
        }
        assertTrue(PortalEntityDrawRole.TRANSFORMED.suppressEntrySelf(true, true, A, A, false));
    }

    @Test
    void nestedEntityScopeRestoresParentVisibilityAfterExceptionalExit() {
        assertFalse(PortalEntityRenderContextScope.active());
        try (var parent = PortalEntityRenderContextScope.enter(A, Level.OVERWORLD, "parent")) {
            assertFalse(PortalEntityRenderContextScope.nestedObserver());
            assertThrows(IllegalStateException.class, () -> {
                try (var child = PortalEntityRenderContextScope.enter(B, Level.NETHER, "child", true)) {
                    assertTrue(PortalEntityRenderContextScope.nestedObserver());
                    throw new IllegalStateException("test exit");
                }
            });
            assertFalse(PortalEntityRenderContextScope.nestedObserver());
            assertTrue(PortalEntityRenderContextScope.summary().contains("view=" + A));
        }
        assertFalse(PortalEntityRenderContextScope.active());
    }
}
