package com.skyeshade.skyesight.remote;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class SkyesightRemoteRadiusPolicyTest {
    @Test void clampsUntrustedValuesAndDoesNotOverflow() {
        assertEquals(10,SkyesightRemoteRadiusPolicy.accepted(16,10));
        assertEquals(4,SkyesightRemoteRadiusPolicy.accepted(4,10));
        assertEquals(10,SkyesightRemoteRadiusPolicy.accepted(Integer.MAX_VALUE,10));
        assertEquals(0,SkyesightRemoteRadiusPolicy.accepted(Integer.MIN_VALUE,10));
    }
    @Test void preloadRingRespectsPerViewLimitWithoutChangingDefaults() {
        assertEquals(10,SkyesightRemoteRadiusPolicy.loadRadius(10,10));
        assertEquals(5,SkyesightRemoteRadiusPolicy.loadRadius(4,10));
        assertEquals(9,SkyesightRemoteRadiusPolicy.loadRadius(8,Integer.MAX_VALUE));
        assertEquals(10,SkyesightRemoteRadiusPolicy.loadRadius(Integer.MAX_VALUE,10));
    }
}
