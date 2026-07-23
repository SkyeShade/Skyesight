package com.skyeshade.skyesight.client.portal;

import com.mojang.blaze3d.platform.NativeImage;
import com.skyeshade.skyesight.Skyesight;
import com.skyeshade.skyesight.SkyesightDebugConfig;
import com.skyeshade.skyesight.api.PortalStencilMask;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class PortalStencilMaskCache {
    private static final Map<ResourceLocation, LoadedMask> CACHE = new HashMap<>();
    private static final Set<ResourceLocation> WARNED_FAILURES = new HashSet<>();

    private PortalStencilMaskCache() {
    }

    public static LoadedMask get(PortalStencilMask mask) {
        if (mask == null || mask.texture() == null) {
            return null;
        }
        ResourceLocation texture = mask.texture();
        LoadedMask cached = CACHE.get(texture);
        if (cached != null) {
            return cached;
        }
        LoadedMask loaded = load(texture);
        if (loaded != null) {
            CACHE.put(texture, loaded);
        }
        return loaded;
    }

    public static void clear() {
        CACHE.clear();
        WARNED_FAILURES.clear();
    }

    private static LoadedMask load(ResourceLocation texture) {
        ResourceLocation resourceId = resourceLocationForTexture(texture);
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            warnOnce(texture, "minecraft-unavailable");
            return null;
        }
        Optional<Resource> resource = minecraft.getResourceManager().getResource(resourceId);
        if (resource.isEmpty()) {
            warnOnce(texture, "missing-resource " + resourceId);
            return null;
        }
        try (InputStream input = resource.get().open(); NativeImage image = NativeImage.read(input)) {
            int width = image.getWidth();
            int height = image.getHeight();
            if (width <= 0 || height <= 0) {
                warnOnce(texture, "invalid-size " + width + "x" + height);
                return null;
            }
            boolean[] solid = new boolean[width * height];
            int solidPixels = 0;
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int alpha = (image.getPixelRGBA(x, y) >>> 24) & 0xFF;
                    if (alpha > 0) {
                        solid[y * width + x] = true;
                        solidPixels++;
                    }
                }
            }
            LoadedMask loaded = new LoadedMask(texture, resourceId, width, height, solid, solidPixels);
            logIfEnabled(loaded, false, "-");
            return loaded;
        } catch (IOException | RuntimeException exception) {
            warnOnce(texture, exception.getClass().getSimpleName() + ": " + exception.getMessage());
            return null;
        }
    }

    private static ResourceLocation resourceLocationForTexture(ResourceLocation texture) {
        String path = texture.getPath();
        if (path.startsWith("textures/") && path.endsWith(".png")) {
            return texture;
        }
        return ResourceLocation.fromNamespaceAndPath(texture.getNamespace(), "textures/" + path + ".png");
    }

    private static void warnOnce(ResourceLocation texture, String reason) {
        if (WARNED_FAILURES.add(texture)) {
            Skyesight.LOGGER.warn(
                    "[Skyesight] Portal stencil mask unavailable; texture={} fallback=rectangle reason={}",
                    texture,
                    reason
            );
        }
        if (SkyesightDebugConfig.shouldLogRenderTargetAudit()) {
            Skyesight.LOGGER.info(
                    "[Skyesight] PORTAL_MASK_TEXTURE: view=- texture={} size=- solidPixels=0 transparentPixels=0 fallback=yes reason={}",
                    texture,
                    reason
            );
        }
    }

    public static void logUseIfEnabled(ResourceLocation viewId, LoadedMask mask, boolean fallback, String reason) {
        if (!SkyesightDebugConfig.shouldLogRenderTargetAudit() || mask == null) {
            return;
        }
        Skyesight.LOGGER.info(
                "[Skyesight] PORTAL_MASK_TEXTURE: view={} texture={} size={}x{} solidPixels={} transparentPixels={} fallback={} reason={}",
                viewId == null ? "-" : viewId,
                mask.texture(),
                mask.width(),
                mask.height(),
                mask.solidPixels(),
                mask.transparentPixels(),
                fallback ? "yes" : "no",
                reason == null || reason.isBlank() ? "-" : reason
        );
    }

    private static void logIfEnabled(LoadedMask mask, boolean fallback, String reason) {
        logUseIfEnabled(null, mask, fallback, reason);
    }

    public record LoadedMask(
            ResourceLocation texture,
            ResourceLocation resourceId,
            int width,
            int height,
            boolean[] solid,
            int solidPixels
    ) {
        public boolean isSolid(int x, int y) {
            return x >= 0 && x < width && y >= 0 && y < height && solid[y * width + x];
        }

        public int transparentPixels() {
            return width * height - solidPixels;
        }
    }
}
