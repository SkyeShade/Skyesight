package com.skyeshade.skyesight.client.render;

import net.minecraft.client.Camera;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.world.level.material.FogType;

public final class SkyesightLevelRendererSkyDebug {
    private static volatile boolean renderSkyEntered;
    private static volatile boolean renderSkyReturned;
    private static volatile boolean upperSkyDraw;
    private static volatile boolean sunriseDraw;
    private static volatile boolean sunDraw;
    private static volatile boolean moonDraw;
    private static volatile boolean starsDraw;
    private static volatile boolean darkSkyDraw;
    private static volatile boolean isFoggy;
    private static volatile String skyType = "";
    private static volatile String fogType = "";
    private static volatile String cameraPosition = "";

    private SkyesightLevelRendererSkyDebug() {}

    public static void reset() {
        renderSkyEntered = false;
        renderSkyReturned = false;
        upperSkyDraw = false;
        sunriseDraw = false;
        sunDraw = false;
        moonDraw = false;
        starsDraw = false;
        darkSkyDraw = false;
        isFoggy = false;
        skyType = "";
        fogType = "";
        cameraPosition = "";
    }

    public static void enter(Camera camera, boolean foggy, DimensionSpecialEffects.SkyType type) {
        renderSkyEntered = true;
        isFoggy = foggy;
        skyType = type == null ? "null" : type.name();
        FogType cameraFog = camera.getFluidInCamera();
        fogType = cameraFog == null ? "null" : cameraFog.name();
        cameraPosition = String.format(
                java.util.Locale.ROOT,
                "%.2f,%.2f,%.2f",
                camera.getPosition().x(),
                camera.getPosition().y(),
                camera.getPosition().z()
        );
    }

    public static void returned() {
        renderSkyReturned = true;
    }

    public static void upperSkyDraw() {
        upperSkyDraw = true;
    }

    public static void sunriseDraw() {
        sunriseDraw = true;
    }

    public static void sunDraw() {
        sunDraw = true;
    }

    public static void moonDraw() {
        moonDraw = true;
    }

    public static void starsDraw() {
        starsDraw = true;
    }

    public static void darkSkyDraw() {
        darkSkyDraw = true;
    }

    public static String summary() {
        return "entered="
                + renderSkyEntered
                + " returned="
                + renderSkyReturned
                + " foggy="
                + isFoggy
                + " fogType="
                + fogType
                + " skyType="
                + skyType
                + " camera="
                + cameraPosition
                + " upper="
                + upperSkyDraw
                + " sunrise="
                + sunriseDraw
                + " sun="
                + sunDraw
                + " moon="
                + moonDraw
                + " stars="
                + starsDraw
                + " dark="
                + darkSkyDraw;
    }

    public static boolean upperSkyDrawn() {
        return upperSkyDraw;
    }

    public static boolean sunriseDrawn() {
        return sunriseDraw;
    }

    public static boolean sunDrawn() {
        return sunDraw;
    }

    public static boolean moonDrawn() {
        return moonDraw;
    }

    public static boolean starsDrawn() {
        return starsDraw;
    }

    public static boolean darkSkyDrawn() {
        return darkSkyDraw;
    }
}
