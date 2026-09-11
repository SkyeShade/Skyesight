package com.skyeshade.skyesight.item;

import com.skyeshade.skyesight.server.portal.TraversalPortalManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

public final class TraversalPortalStickItem extends Item {
    public TraversalPortalStickItem(Properties properties) { super(properties); }
    @Override public void appendHoverText(ItemStack stack, TooltipContext context,
            java.util.List<net.minecraft.network.chat.Component> lines, net.minecraft.world.item.TooltipFlag flag) {
        lines.add(net.minecraft.network.chat.Component.translatable("item.skyesight.traversal_portal_stick.help"));
    }
    @Override public InteractionResult useOn(UseOnContext context) {
        if (context.getPlayer() instanceof ServerPlayer player)
            TraversalPortalManager.select(player, context.getClickedPos());
        return InteractionResult.sidedSuccess(context.getLevel().isClientSide());
    }
    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (player.isShiftKeyDown()) {
            if (player instanceof ServerPlayer serverPlayer) TraversalPortalManager.clear(serverPlayer);
            return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide());
        }
        return InteractionResultHolder.pass(player.getItemInHand(hand));
    }
}
