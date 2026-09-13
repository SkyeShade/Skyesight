package com.skyeshade.skyesight.api;

public enum ClipSide {
    POSITIVE, NEGATIVE, NONE;

    public ClipSide opposite() {
        return this == POSITIVE ? NEGATIVE : this == NEGATIVE ? POSITIVE : NONE;
    }
}
