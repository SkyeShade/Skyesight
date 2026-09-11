package com.skyeshade.skyesight.mixin.client;

import com.mojang.blaze3d.vertex.VertexBuffer;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LevelRenderer.class)
public interface LevelRendererAccessor {
    @Accessor("level")
    ClientLevel skyesight$getLevel();

    @Accessor("level")
    void skyesight$setLevel(ClientLevel level);

    @Accessor("generateClouds")
    boolean skyesight$getGenerateClouds();

    @Accessor("generateClouds")
    void skyesight$setGenerateClouds(boolean generateClouds);

    @Accessor("cloudBuffer")
    VertexBuffer skyesight$getCloudBuffer();

    @Accessor("cloudBuffer")
    void skyesight$setCloudBuffer(VertexBuffer cloudBuffer);

    @Accessor("prevCloudX")
    int skyesight$getPrevCloudX();

    @Accessor("prevCloudX")
    void skyesight$setPrevCloudX(int prevCloudX);

    @Accessor("prevCloudY")
    int skyesight$getPrevCloudY();

    @Accessor("prevCloudY")
    void skyesight$setPrevCloudY(int prevCloudY);

    @Accessor("prevCloudZ")
    int skyesight$getPrevCloudZ();

    @Accessor("prevCloudZ")
    void skyesight$setPrevCloudZ(int prevCloudZ);

    @Accessor("prevCloudColor")
    Vec3 skyesight$getPrevCloudColor();

    @Accessor("prevCloudColor")
    void skyesight$setPrevCloudColor(Vec3 prevCloudColor);

    @Accessor("prevCloudsType")
    CloudStatus skyesight$getPrevCloudsType();

    @Accessor("prevCloudsType")
    void skyesight$setPrevCloudsType(CloudStatus prevCloudsType);

}
