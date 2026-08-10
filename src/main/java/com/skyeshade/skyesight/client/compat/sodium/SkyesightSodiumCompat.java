package com.skyeshade.skyesight.client.compat.sodium;

import net.neoforged.fml.ModList;

public final class SkyesightSodiumCompat {
    public static final String SODIUM_MOD_ID = "sodium";

    private SkyesightSodiumCompat() {}

    public static boolean isLoaded() {
        return ModList.get().isLoaded(SODIUM_MOD_ID);
    }
}
