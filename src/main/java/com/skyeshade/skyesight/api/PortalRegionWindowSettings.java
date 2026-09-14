package com.skyeshade.skyesight.api;

/** Block units. Warm margin includes bounded movement look-ahead and a cell-boundary dead band. */
public record PortalRegionWindowSettings(int visibleSize, int warmMargin, int lookAhead, int hysteresis) {
    public static final PortalRegionWindowSettings DEFAULT = new PortalRegionWindowSettings(128,32,16,8);
    public PortalRegionWindowSettings {
        if(visibleSize<32 || visibleSize>256 || warmMargin<16 || warmMargin>128
                || lookAhead<0 || hysteresis<1 || hysteresis>16 || lookAhead+hysteresis+8>warmMargin)
            throw new IllegalArgumentException("Invalid buffered region window");
    }
    public int minimumChunkRadius() { return (int)Math.ceil((visibleSize*.5+warmMargin)/16)+1; }
}
