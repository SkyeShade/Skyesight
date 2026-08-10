package com.skyeshade.skyesight.client.debug;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.skyeshade.skyesight.api.SkyesightCameraApi;
import com.skyeshade.skyesight.api.SkyesightCameraView;
import com.skyeshade.skyesight.api.SkyesightViewOutput;
import com.skyeshade.skyesight.api.SkyesightViewRenderOptions;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import org.joml.Quaternionf;

/**
 * Minimal client-side integration test for the public camera API.
 */
@EventBusSubscriber(modid = "skyesight", value = Dist.CLIENT)
public final class SkyesightDebugCameraCommands {
    private static final ResourceLocation DEBUG_CAMERA_ID =
            ResourceLocation.fromNamespaceAndPath("skyesight", "debug_camera");
    private static final int DEFAULT_WIDTH = 320;
    private static final int DEFAULT_HEIGHT = 180;
    private static final float DEFAULT_FOV = 70.0F;
    private static final int DEFAULT_RENDER_DISTANCE = 8;

    private SkyesightDebugCameraCommands() {}

    @SubscribeEvent
    public static void register(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("skyesightcamera")
                        .then(Commands.literal("create")
                                .executes(context -> createFromCurrentCamera(context.getSource(), DEFAULT_WIDTH, DEFAULT_HEIGHT))
                                .then(Commands.argument("width", IntegerArgumentType.integer(1))
                                        .then(Commands.argument("height", IntegerArgumentType.integer(1))
                                                .executes(context -> createFromCurrentCamera(
                                                        context.getSource(),
                                                        IntegerArgumentType.getInteger(context, "width"),
                                                        IntegerArgumentType.getInteger(context, "height")
                                                )))))
                        .then(Commands.literal("render")
                                .executes(context -> renderFromCurrentCamera(context.getSource())))
                        .then(Commands.literal("status")
                                .executes(context -> status(context.getSource())))
                        .then(Commands.literal("resize")
                                .then(Commands.argument("width", IntegerArgumentType.integer(1))
                                        .then(Commands.argument("height", IntegerArgumentType.integer(1))
                                                .executes(context -> resize(
                                                        context.getSource(),
                                                        IntegerArgumentType.getInteger(context, "width"),
                                                        IntegerArgumentType.getInteger(context, "height")
                                                )))))
                        .then(Commands.literal("remove")
                                .executes(context -> remove(context.getSource())))
        );
    }

    private static int createFromCurrentCamera(net.minecraft.commands.CommandSourceStack source, int width, int height) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            source.sendFailure(Component.literal("[Skyesight] No client level is loaded."));
            return 0;
        }

        Camera camera = minecraft.gameRenderer.getMainCamera();
        SkyesightCameraView view = SkyesightCameraApi.create(
                DEBUG_CAMERA_ID,
                minecraft.level.dimension(),
                camera.getPosition(),
                new Quaternionf(camera.rotation()),
                width,
                height,
                DEFAULT_FOV,
                DEFAULT_RENDER_DISTANCE,
                SkyesightViewRenderOptions.defaults()
        );
        view.camera().copyFromMainCamera();

        source.sendSuccess(() -> Component.literal(
                "[Skyesight] Created debug camera " + DEBUG_CAMERA_ID
                        + " at " + formatPosition(view.camera().position())
                        + " size=" + view.width() + "x" + view.height()
                        + " fov=" + view.fov()
        ), false);
        return 1;
    }

    private static int renderFromCurrentCamera(net.minecraft.commands.CommandSourceStack source) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            source.sendFailure(Component.literal("[Skyesight] No client level is loaded."));
            return 0;
        }

        SkyesightCameraView view = SkyesightCameraApi.get(DEBUG_CAMERA_ID).orElse(null);
        if (view == null || view.isClosed()) {
            source.sendFailure(Component.literal("[Skyesight] No debug camera exists. Run /skyesightcamera create first."));
            return 0;
        }

        view.setDimension(minecraft.level.dimension());
        view.camera().copyFromMainCamera();
        view.render(minecraft.getTimer().getGameTimeDeltaPartialTick(true));

        source.sendSuccess(() -> Component.literal("[Skyesight] Rendered debug camera: " + outputSummary(view)), false);
        return 1;
    }

    private static int status(net.minecraft.commands.CommandSourceStack source) {
        SkyesightCameraView view = SkyesightCameraApi.get(DEBUG_CAMERA_ID).orElse(null);
        if (view == null) {
            source.sendSuccess(() -> Component.literal("[Skyesight] Debug camera is not created."), false);
            return 1;
        }

        source.sendSuccess(() -> Component.literal(
                "[Skyesight] Debug camera " + DEBUG_CAMERA_ID
                        + " closed=" + view.isClosed()
                        + " dimension=" + view.dimension().location()
                        + " position=" + formatPosition(view.camera().position())
                        + " renderDistance=" + view.renderDistanceChunks()
                        + " " + outputSummary(view)
        ), false);
        return 1;
    }

    private static int resize(net.minecraft.commands.CommandSourceStack source, int width, int height) {
        SkyesightCameraView view = SkyesightCameraApi.get(DEBUG_CAMERA_ID).orElse(null);
        if (view == null || view.isClosed()) {
            source.sendFailure(Component.literal("[Skyesight] No debug camera exists. Run /skyesightcamera create first."));
            return 0;
        }

        view.resize(width, height);
        source.sendSuccess(() -> Component.literal(
                "[Skyesight] Resized debug camera " + DEBUG_CAMERA_ID + " to " + width + "x" + height
        ), false);
        return 1;
    }

    private static int remove(net.minecraft.commands.CommandSourceStack source) {
        boolean removed = SkyesightCameraApi.destroy(DEBUG_CAMERA_ID);
        source.sendSuccess(() -> Component.literal(
                removed
                        ? "[Skyesight] Removed debug camera " + DEBUG_CAMERA_ID
                        : "[Skyesight] Debug camera was not created."
        ), false);
        return 1;
    }

    private static String outputSummary(SkyesightCameraView view) {
        SkyesightViewOutput output = view.output();
        return "output="
                + output.width() + "x" + output.height()
                + " colorTexture=" + output.colorTextureId()
                + " depthTexture=" + output.depthTextureId();
    }

    private static String formatPosition(Vec3 position) {
        return String.format(
                java.util.Locale.ROOT,
                "%.2f %.2f %.2f",
                position.x(),
                position.y(),
                position.z()
        );
    }
}
