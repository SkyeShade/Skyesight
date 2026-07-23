package com.skyeshade.skyesight.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;

public final class SkyesightClientThreading {
    private SkyesightClientThreading() {}

    public static boolean runOnRenderThread(Runnable task) {
        if (task == null) {
            return false;
        }
        if (RenderSystem.isOnRenderThreadOrInit()) {
            task.run();
            return true;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return false;
        }
        minecraft.execute(task);
        return true;
    }
}
