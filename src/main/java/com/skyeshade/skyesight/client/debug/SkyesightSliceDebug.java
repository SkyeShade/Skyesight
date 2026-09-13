package com.skyeshade.skyesight.client.debug;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.skyeshade.skyesight.api.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import org.joml.Matrix4f;

import java.lang.ref.WeakReference;
import java.util.UUID;

/** Opt-in, bounded client visual fixture. Never removes, damages, ticks or spawns gameplay entities. */
@EventBusSubscriber(modid = "skyesight", value = Dist.CLIENT)
public final class SkyesightSliceDebug {
    private static FrozenEntityVisual visual;
    private static SkyesightClipPlane plane;
    private static UUID hidden;
    private static WeakReference<ClientLevel> level = new WeakReference<>(null);
    private static long expires;
    private static boolean verified;
    private static SliceRenderOptions capOptions = SliceRenderOptions.ENERGY_CAP;

    private SkyesightSliceDebug() {}

    @SubscribeEvent public static void commands(RegisterClientCommandsEvent event) {
        var root = Commands.literal("skyesightslice")
                .then(Commands.literal("caps")
                        .then(Commands.literal("on").executes(c -> { capOptions=SliceRenderOptions.ENERGY_CAP;return 1; }))
                        .then(Commands.literal("off").executes(c -> { capOptions=SliceRenderOptions.UNCAPPED;return 1; })))
                .then(Commands.literal("verify").executes(c -> {
                    try {
                        String result = SkyesightSliceVerifier.verify();
                        c.getSource().sendSuccess(() -> Component.literal(result), false);
                        return 1;
                    } catch (RuntimeException | AssertionError e) {
                        c.getSource().sendFailure(Component.literal("Slice verification failed: " + e.getMessage()));
                        return 0;
                    }
                }))
                .then(Commands.literal("clear").executes(c -> { clear(); return 1; }));
        for (String target : new String[]{"target", "self"}) {
            root.then(Commands.literal(target).then(Commands.argument("plane", StringArgumentType.word())
                    .suggests((c, b) -> { for (String p : new String[]{"x", "z", "horizontal", "diagonal"}) b.suggest(p); return b.buildFuture(); })
                    .executes(c -> {
                        var mc = Minecraft.getInstance();
                        Entity entity = target.equals("self") ? mc.player : mc.hitResult instanceof EntityHitResult hit ? hit.getEntity() : null;
                        if (entity == null || mc.level == null) {
                            c.getSource().sendFailure(Component.literal("Look at an entity, or use /skyesightslice self <plane>."));
                            return 0;
                        }
                        Vec3 normal = switch (StringArgumentType.getString(c, "plane")) {
                            case "x" -> new Vec3(1,0,0);
                            case "z" -> new Vec3(0,0,1);
                            case "horizontal" -> new Vec3(0,1,0);
                            case "diagonal" -> new Vec3(1,1,1);
                            default -> null;
                        };
                        if (normal == null) { c.getSource().sendFailure(Component.literal("Planes: x, z, horizontal, diagonal")); return 0; }
                        try {
                            var captured = SkyesightEntitySliceApi.capture(entity, mc.getTimer().getGameTimeDeltaPartialTick(false));
                            clear();
                            visual = captured;
                            plane = new SkyesightClipPlane(visual.bounds().getCenter(), normal);
                            hidden = entity.getUUID();
                            level = new WeakReference<>(mc.level);
                            expires = System.nanoTime() + 60_000_000_000L;
                            c.getSource().sendSuccess(() -> Component.literal("Captured " + captured.entityType()
                                    + "; live draw hidden for 60s. Halves stay frozen if the source despawns. /skyesightslice clear restores drawing."), false);
                            return 1;
                        } catch (RuntimeException e) {
                            c.getSource().sendFailure(Component.literal("Capture failed: " + e.getMessage()));
                            return 0;
                        }
                    })));
        }
        event.getDispatcher().register(root);
    }

    public static boolean hides(Entity entity) {
        return visual != null && System.nanoTime() < expires && level.get() == Minecraft.getInstance().level
                && entity.getUUID().equals(hidden);
    }

    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if (!verified && event.getStage() == RenderLevelStageEvent.Stage.AFTER_ENTITIES
                && "1".equals(System.getenv("SKYESIGHT_SLICE_VERIFY"))) {
            verified = true;
            try {
                String result = SkyesightSliceVerifier.verify();
                com.skyeshade.skyesight.Skyesight.LOGGER.info("Slice verification passed: {}", result);
            } catch (RuntimeException | AssertionError e) {
                com.skyeshade.skyesight.Skyesight.LOGGER.error("Slice verification failed", e);
            }
        }
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || visual == null) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null || level.get() != mc.level || System.nanoTime() >= expires) { clear(); return; }
        if (event.getPoseStack() == null) return;
        var n = plane.normal().scale(.35);
        var buffer = mc.renderBuffers().bufferSource();
        try {
            visual.renderHalves(event.getPoseStack(), buffer, 0xF000F0, event.getCamera().getPosition(), plane,
                    new Matrix4f().translation((float)n.x, (float)n.y, (float)n.z),
                    new Matrix4f().translation((float)-n.x, (float)-n.y, (float)-n.z).rotateY(.25f), capOptions);
        } finally { buffer.endBatch(); }
    }

    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { clear(); }

    private static void clear() {
        if (visual != null) visual.close();
        visual = null;
        plane = null;
        hidden = null;
        level.clear();
    }
}
