package com.skyeshade.skyesight.mixin.server;

import com.skyeshade.skyesight.server.SkyesightServerBlockEventBroadcaster;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.BlockEventData;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ServerLevel.class)
public abstract class ServerLevelBlockEventMixin {
    @Inject(method = "blockEvent", at = @At("TAIL"))
    private void skyesight$queuedVisualEvent(BlockPos pos, Block block,
            int id, int param, CallbackInfo ci) {
        var level = (ServerLevel)(Object)this;
        // Remote watches load chunks without enabling server block ticking. The native queue waits
        // there, but client animation (e.g. lid open count) must receive the event now.
        if (!level.shouldTickBlocksAt(pos) && level.getBlockState(pos).is(block))
            SkyesightServerBlockEventBroadcaster.send(level, pos, id, param);
    }
    @Inject(
            method = "doBlockEvent",
            at = @At("RETURN")
    )
    private void skyesight$onDoBlockEvent(
            BlockEventData event,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (!cir.getReturnValue()) {
            return;
        }

        SkyesightServerBlockEventBroadcaster.send(
                (ServerLevel) (Object) this,
                event.pos(),
                event.paramA(),
                event.paramB()
        );
    }
}
