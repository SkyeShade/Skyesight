package com.skyeshade.skyesight.client.portal;

import com.skyeshade.skyesight.Skyesight;
import com.skyeshade.skyesight.api.SkyesightRenderMode;
import com.skyeshade.skyesight.api.SkyesightViewHandle;
import com.skyeshade.skyesight.api.SkyesightViewSpec;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

@EventBusSubscriber(
        modid = Skyesight.MODID,
        value = Dist.CLIENT
)
public final class TemporarySkyesightDebugView {
    private static final ResourceLocation VIEW_SHOWN_ON_PORTAL_A_ID =
            ResourceLocation.fromNamespaceAndPath(Skyesight.MODID, "debug_view_shown_on_portal_a");

    private static final ResourceLocation VIEW_SHOWN_ON_PORTAL_B_ID =
            ResourceLocation.fromNamespaceAndPath(Skyesight.MODID, "debug_view_shown_on_portal_b");

    private static final int RENDER_DIST = 2;
    private static final int VIEW_WIDTH = 300;
    private static final int VIEW_HEIGHT = 600;
    private static final float VIEW_FOV = 70.0F;

    private static final float PANEL_WIDTH = 1.0F;
    private static final float PANEL_HEIGHT = 2.0F;

    private static final TemporaryPortalFrame PORTAL_A =
            new TemporaryPortalFrame(
                    new Vec3(0.5D, 89.0D, 0.01D),
                    rotationFromYawPitchRoll(0.0F, 0.0F, 0.0F),
                    PANEL_WIDTH,
                    PANEL_HEIGHT
            );

    private static final TemporaryPortalFrame PORTAL_B =
            new TemporaryPortalFrame(
                    new Vec3(2.99D, 89.0D, 2.5D),
                    rotationFromYawPitchRoll(-90.0F, 0.0F, 0.0F),
                    PANEL_WIDTH,
                    PANEL_HEIGHT
            );



    private TemporarySkyesightDebugView() {}

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.level == null || minecraft.player == null || event.getCamera() == null) {
            return;
        }

        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(true);
        Camera mainCamera = event.getCamera();

        SkyesightViewHandle viewShownOnPortalA =
                ensureView(VIEW_SHOWN_ON_PORTAL_A_ID, PORTAL_B.position(), Level.OVERWORLD);

        SkyesightViewHandle viewShownOnPortalB =
                ensureView(VIEW_SHOWN_ON_PORTAL_B_ID, PORTAL_A.position(), Level.OVERWORLD);
        TemporaryPortalViewPlacement placementShownOnPortalA =
                renderPortalView(
                        viewShownOnPortalA,
                        mainCamera,
                        PORTAL_A,
                        PORTAL_B,
                        partialTick
                );

        TemporaryPortalViewPlacement placementShownOnPortalB =
                renderPortalView(
                        viewShownOnPortalB,
                        mainCamera,
                        PORTAL_B,
                        PORTAL_A,
                        partialTick
                );


        renderPortalPanel(
                mainCamera,
                PORTAL_A,
                viewShownOnPortalA
        );

        renderPortalPanel(
                mainCamera,
                PORTAL_B,
                viewShownOnPortalB
        );

        TemporaryPortalDebugRenderer.renderPortalDebug(
                mainCamera,
                PORTAL_A,
                placementShownOnPortalB
        );

        TemporaryPortalDebugRenderer.renderPortalDebug(
                mainCamera,
                PORTAL_B,
                placementShownOnPortalA
        );
    }

    private static TemporaryPortalViewPlacement renderPortalView(
            SkyesightViewHandle view,
            Camera mainCamera,
            TemporaryPortalFrame entrancePortal,
            TemporaryPortalFrame exitPortal,
            float partialTick
    ) {
        TemporaryPortalViewPlacement placement =
                TemporaryPortalMath.placeCamera(
                        mainCamera,
                        entrancePortal,
                        exitPortal
                );

        Matrix4f projection = TemporaryPortalMath.portalProjection(
                placement.cameraPosition(),
                exitPortal,
                0.05F,
                RENDER_DIST * 16.0F
        );

        view.camera().setPosition(placement.cameraPosition());
        view.camera().setRotation(placement.cameraRotation());
        view.setClipPlane(placement.clipPlane());
        view.setProjectionOverride(projection);

        try {
            view.renderNow(partialTick);
        } finally {
            view.clearClipPlane();
            view.clearProjectionOverride();
        }

        return placement;
    }

    private static void renderPortalPanel(
            Camera mainCamera,
            TemporaryPortalFrame portal,
            SkyesightViewHandle view
    ) {
        if (view.output() == null || view.output().renderTarget() == null) {
            return;
        }

        TemporarySkyesightWorldPanelRenderer.render(
                mainCamera,
                portal,
                view.output().colorTextureId()
        );
    }



    private static SkyesightViewHandle ensureView(ResourceLocation id, Vec3 initialPosition, ResourceKey<Level> dimension) {
        return Skyesight.api()
                .getView(id)
                .orElseGet(() -> Skyesight.api().createView(
                        new SkyesightViewSpec(
                                id,
                                dimension,
                                initialPosition,
                                new Quaternionf(),
                                RENDER_DIST,
                                VIEW_WIDTH,
                                VIEW_HEIGHT,
                                VIEW_FOV,
                                SkyesightRenderMode.WORLD
                        )
                ));
    }


    private static Quaternionf rotationFromYawPitchRoll(
            float yaw,
            float pitch,
            float roll
    ) {
        return new Quaternionf()
                .rotateY((float) Math.toRadians(yaw))
                .rotateX((float) Math.toRadians(pitch))
                .rotateZ((float) Math.toRadians(roll));
    }

}