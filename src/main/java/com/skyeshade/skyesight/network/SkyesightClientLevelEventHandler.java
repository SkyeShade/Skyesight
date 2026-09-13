package com.skyeshade.skyesight.network;

import com.skyeshade.skyesight.client.transition.TraversalPortalClient;
import com.skyeshade.skyesight.client.world.*;
import com.skyeshade.skyesight.portal.PortalTraversalMath;
import com.skyeshade.skyesight.remote.SkyesightRemoteViewRegistry;
import com.skyeshade.skyesight.Skyesight;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.extensions.common.IClientBlockExtensions;

import java.util.LinkedHashSet;

/**
 * Native block-break effect in the watched world; no independent debris algorithm.
 */
@EventBusSubscriber(modid = Skyesight.MODID, value = Dist.CLIENT)
public final class SkyesightClientLevelEventHandler {
    private static final LinkedHashSet<String> MAIN_EVENTS = new LinkedHashSet<>();

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        MAIN_EVENTS.clear();
    }

    public static void handle(SkyesightLevelEventPayload payload) {
        if (!SkyesightRemoteViewRegistry.accepts(payload.viewId(), payload.generation(), payload.dimension()))
            return;
        var world = SkyesightVisualWorldManager.getIfCurrent(payload.viewId(), payload.dimension());
        if (world == null || world.isClosed() || payload.eventId() != 2001) return;
        world.destroyProgress().removeBlock(payload.pos());
        var state = Block.stateById(payload.eventParam());
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        var particles = SecondaryParticleViews.get(payload.viewId());
        boolean main = particles != null && particles.usesMainParticles();
        // Delivery to the physical engine and to an independent watched engine are separate
        // ownership decisions. A nearby physical event does not populate a visual-world manager.
        if (!payload.vanillaDelivered() && mc.level.dimension().equals(payload.dimension())
                && (main || mc.player.position().distanceToSqr(Vec3.atCenterOf(payload.pos())) < 64 * 64)) {
            String key = payload.dimension().location() + ":" + payload.eventSequence();
            if (MAIN_EVENTS.add(key)) mc.level.addDestroyBlockEffect(payload.pos(), state);
            if (MAIN_EVENTS.size() > 256) MAIN_EVENTS.remove(MAIN_EVENTS.iterator().next());
        }
        if (!main) try (var ignored = SecondaryParticleCapture.push(world.level(), world.particles())) {
            world.level().addDestroyBlockEffect(payload.pos(), state);
        }
        if (payload.vanillaDelivered()) return; // Physical vanilla event already supplied the sound.
        // Only audible gameplay portals route sound; ordinary camera feeds remain silent.
        var link = TraversalPortalClient.interactionLinks(mc.level.dimension()).stream()
                .filter(l -> l.id().equals(payload.viewId())).findFirst().orElse(null);
        if (link == null) return;
        var soundPos = PortalTraversalMath.position(link.target(), link.source(), Vec3.atCenterOf(payload.pos()));
        if (soundPos.distanceToSqr(mc.player.position()) > 64) return;
        if (!state.isAir() && !IClientBlockExtensions.of(state).playBreakSound(state, world.level(), payload.pos())) {
            var sound = state.getSoundType(world.level(), payload.pos(), null);
            mc.level.playLocalSound(soundPos.x, soundPos.y, soundPos.z, sound.getBreakSound(), SoundSource.BLOCKS,
                    (sound.getVolume() + 1) / 2, sound.getPitch() * .8f, false);
        }
    }
}
