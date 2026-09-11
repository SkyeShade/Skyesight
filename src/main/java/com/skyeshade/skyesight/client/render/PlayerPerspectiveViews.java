package com.skyeshade.skyesight.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import java.util.HashSet;
import java.util.Set;

/** Internal scene semantics for views intended to become the player's main perspective. */
public final class PlayerPerspectiveViews {
    private static final Set<ResourceLocation> VIEWS = new HashSet<>();
    private PlayerPerspectiveViews() {}
    public static void register(String id) { VIEWS.add(ResourceLocation.parse(id)); }
    public static void remove(String id) { VIEWS.remove(ResourceLocation.parse(id)); }
    public static void clear() { VIEWS.clear(); }
    public static boolean contains(ResourceLocation id) { return VIEWS.contains(id); }
    public static int radius(ResourceLocation id, int independentRadius) {
        return contains(id) ? Minecraft.getInstance().options.getEffectiveRenderDistance() : independentRadius;
    }
}
