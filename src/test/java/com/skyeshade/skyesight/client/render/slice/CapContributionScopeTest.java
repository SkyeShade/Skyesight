package com.skyeshade.skyesight.client.render.slice;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CapContributionScopeTest {
    @Test void nestedScopesRestoreAfterException() {
        assertTrue(CapContributionScope.producesCap());
        try(var outer=CapContributionScope.clipOnly()) {
            assertFalse(CapContributionScope.producesCap());
            assertThrows(IllegalStateException.class,() -> {
                try(var inner=CapContributionScope.clipOnly()) { throw new IllegalStateException(); }
            });
            assertFalse(CapContributionScope.producesCap());
        }
        assertTrue(CapContributionScope.producesCap());
    }
}
