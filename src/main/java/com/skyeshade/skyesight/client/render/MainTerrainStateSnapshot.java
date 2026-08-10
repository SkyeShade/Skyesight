package com.skyeshade.skyesight.client.render;

public record MainTerrainStateSnapshot(
        int mainRenderListsIdentity,
        int mainRenderListsSize
) {
    public static MainTerrainStateSnapshot unavailable() {
        return new MainTerrainStateSnapshot(0, 0);
    }
}
