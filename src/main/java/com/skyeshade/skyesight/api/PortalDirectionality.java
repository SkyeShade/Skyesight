package com.skyeshade.skyesight.api;

/** Travel/view direction is independent of the usable faces of either endpoint. */
public enum PortalDirectionality {
    A_TO_B, TWO_WAY;

    public boolean allows(boolean fromA) { return fromA || this == TWO_WAY; }
}
