package com.skyeshade.skyesight;

import com.skyeshade.skyesight.item.DebugPortalStickItem;
import com.skyeshade.skyesight.item.MaskedPortalDebugStickItem;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class SkyesightItems {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Skyesight.MODID);

    public static final DeferredItem<Item> DEBUG_PORTAL_STICK = ITEMS.register(
            "debug_portal_stick",
            () -> new DebugPortalStickItem(new Item.Properties().stacksTo(1))
    );

    public static final DeferredItem<Item> MASKED_PORTAL_DEBUG_STICK = ITEMS.register(
            "masked_portal_debug_stick",
            () -> new MaskedPortalDebugStickItem(new Item.Properties().stacksTo(1))
    );

    private SkyesightItems() {
    }

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
    }
}
