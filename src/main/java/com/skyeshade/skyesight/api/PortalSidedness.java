package com.skyeshade.skyesight.api;

/** Front is the positive half-space of the endpoint's canonical normal. */
public enum PortalSidedness {
    FRONT_ONLY, BACK_ONLY, BOTH;

    public boolean allows(double signedDistance) {
        return this == BOTH || (this == FRONT_ONLY ? signedDistance >= 0 : signedDistance <= 0);
    }
}
