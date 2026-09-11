package com.skyeshade.skyesight.client.world;

import com.skyeshade.skyesight.Skyesight;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid = Skyesight.MODID, value = Dist.CLIENT)
public final class SecondaryEntityClock {
    private static long ticks;
    private SecondaryEntityClock() {}
    @SubscribeEvent public static void tick(ClientTickEvent.Pre event) {
        if (!Minecraft.getInstance().isPaused()) ticks++;
    }
    public static double now() {
        return (double) ticks + Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true);
    }
    public static double tickTime() { return ticks; }
}
