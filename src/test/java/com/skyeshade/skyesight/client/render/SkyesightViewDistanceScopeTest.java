package com.skyeshade.skyesight.client.render;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SkyesightViewDistanceScopeTest {
    @Test void viewSpecificDistanceDoesNotChangePhysicalOrOtherViews() throws Exception {
        assertEquals(19, SkyesightViewDistanceScope.resolve(19));
        try (var first = new SkyesightViewDistanceScope(38)) {
            assertEquals(38, SkyesightViewDistanceScope.resolve(19));
            try (var second = new SkyesightViewDistanceScope(8)) {
                assertEquals(8, SkyesightViewDistanceScope.resolve(19));
            }
            assertEquals(38, SkyesightViewDistanceScope.resolve(19));
            var observed = new java.util.concurrent.atomic.AtomicInteger();
            Thread t = new Thread(() -> observed.set(SkyesightViewDistanceScope.resolve(19)));
            t.start(); t.join();
            assertEquals(19, observed.get());
        }
        assertEquals(19, SkyesightViewDistanceScope.resolve(19));
    }
    @Test void exceptionRestoresPhysicalDistance() {
        assertThrows(IllegalStateException.class, () -> {
            try (var distance = new SkyesightViewDistanceScope(38)) { throw new IllegalStateException(); }
        });
        assertEquals(19, SkyesightViewDistanceScope.resolve(19));
    }
}
