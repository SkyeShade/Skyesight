package com.skyeshade.skyesight.server.portal;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * Query-only player used by isolated visual worlds. It is never added to a
 * real level and exists only as a nearest-player return value.
 */
public final class PortalApparentQueryPlayer extends Player {
    private final Player realPlayer;

    public PortalApparentQueryPlayer(Level level, Player realPlayer) {
        super(level, BlockPos.containing(realPlayer.position()), realPlayer.getYRot(), profile(realPlayer));
        this.realPlayer = realPlayer;
        this.setUUID(realPlayer.getUUID());
    }

    public Player realPlayer() {
        return this.realPlayer;
    }

    public void updateApparentPosition(Vec3 apparentPosition) {
        this.setPos(apparentPosition);
        this.xo = apparentPosition.x();
        this.yo = apparentPosition.y();
        this.zo = apparentPosition.z();
        this.xOld = apparentPosition.x();
        this.yOld = apparentPosition.y();
        this.zOld = apparentPosition.z();
        this.setYRot(this.realPlayer.getYRot());
        this.setXRot(this.realPlayer.getXRot());
        this.yRotO = this.realPlayer.yRotO;
        this.xRotO = this.realPlayer.xRotO;
        this.tickCount = this.realPlayer.tickCount;
    }

    @Override
    public boolean isSpectator() {
        return this.realPlayer.isSpectator();
    }

    @Override
    public boolean isCreative() {
        return this.realPlayer.isCreative();
    }

    private static GameProfile profile(Player realPlayer) {
        GameProfile profile = realPlayer.getGameProfile();
        UUID uuid = realPlayer.getUUID();
        String name = profile == null || profile.getName() == null ? "SkyesightProxy" : profile.getName();
        return new GameProfile(uuid, name);
    }
}
