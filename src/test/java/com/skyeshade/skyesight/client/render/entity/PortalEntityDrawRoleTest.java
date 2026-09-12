package com.skyeshade.skyesight.client.render.entity;

import net.minecraft.resources.ResourceLocation;
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
}
