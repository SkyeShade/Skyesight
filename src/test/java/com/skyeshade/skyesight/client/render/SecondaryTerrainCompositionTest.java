package com.skyeshade.skyesight.client.render;

import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SecondaryTerrainCompositionTest {
    @Test void destinationCannotReenterPrimaryCompositionAndTranslucencyFollowsIt() {
        var draws=new ArrayList<String>();
        try(var scope=new SecondaryTerrainComposition(()-> {
            draws.add("portal");
            SecondaryTerrainComposition.beforeTranslucent();
            try(var destination=new SecondaryTerrainComposition(null)) {
                SecondaryTerrainComposition.beforeTranslucent();
            }
        })) {
            draws.add("cutout");SecondaryTerrainComposition.beforeTranslucent();
            draws.add("translucent");SecondaryTerrainComposition.beforeTranslucent();
        }
        SecondaryTerrainComposition.beforeTranslucent();
        assertEquals(java.util.List.of("cutout","portal","translucent"),draws);
    }
    @Test void failedSceneCannotLeakItsCompositionIntoFinitePortalOrNextFrame() {
        var calls=new java.util.concurrent.atomic.AtomicInteger();
        assertThrows(IllegalStateException.class,()-> {
            try(var scope=new SecondaryTerrainComposition(calls::incrementAndGet)) {throw new IllegalStateException();}
        });
        SecondaryTerrainComposition.beforeTranslucent();assertEquals(0,calls.get());
    }
}
