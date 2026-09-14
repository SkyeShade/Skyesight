package com.skyeshade.skyesight.api;

/** Block units. Warm margin includes bounded movement look-ahead and a cell-boundary dead band. */
public record PortalRegionWindowSettings(int visibleSize, int warmMargin, int lookAhead, int hysteresis) {
    /** Allocation budget per observer, independent of total region area (up to about 16K blocks wide). */
    public static final int MAX_WINDOW_CELLS = 1_048_576;
    public static final PortalRegionWindowSettings DEFAULT = new PortalRegionWindowSettings(128,32,16,8);
    public PortalRegionWindowSettings {
        if(visibleSize<1 || warmMargin<1 || lookAhead<0 || hysteresis<0
                || (long)lookAhead+hysteresis+8>warmMargin)
            throw new IllegalArgumentException("Invalid buffered region window");
        // One extra cell accommodates an unaligned window boundary. Use long before adding/multiplying.
        long cellsPerAxis = ((long)visibleSize + 2L*warmMargin + 15)/16 + 1;
        if(cellsPerAxis > 1024 || cellsPerAxis*cellsPerAxis > MAX_WINDOW_CELLS)
            throw new IllegalArgumentException("Region warm window exceeds the 1,048,576-cell allocation budget");
    }
    public int minimumChunkRadius() { return (int)Math.ceil((visibleSize*.5+warmMargin)/16)+1; }
}
