package com.skyeshade.skyesight.client.world;

import com.skyeshade.skyesight.mixin.common.LivingEntityAnimationAccessor;
import com.skyeshade.skyesight.mixin.common.LivingEntityWalkAnimationAccessor;
import com.skyeshade.skyesight.mixin.common.WalkAnimationStateAccessor;
import com.skyeshade.skyesight.client.render.entity.PortalVisualEntityAnimationUpdater;
import com.skyeshade.skyesight.network.SkyesightEntitySnapshotPayload;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.WalkAnimationState;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;


public final class SkyesightVisualEntity {
    private static final long DEFAULT_SNAPSHOT_INTERVAL_MS = 100L;

    private final Entity entity;
    private final RemoteEntityTimeline timeline = new RemoteEntityTimeline();
    private final RemotePlayerBody playerBody;

    private Vec3 previousPosition;
    private Vec3 currentPosition;

    private int tickCount;

    private float walkPosition;
    private float walkSpeed;
    private float walkSpeedOld;

    private long snapshotStartMs;
    private long snapshotEndMs;
    private long animationSnapshotMs;
    private Vec3 lastSnapshotPosition;

    private Vec3 deltaMovement;
    private boolean onGround;
    private float fallDistance;

    private float playerTargetWalkSpeed;
    private float playerWalkSpeed;
    private float playerWalkPosition;
    private long playerWalkAnimationMs;

    private int hurtTime;
    private int hurtDuration;
    private int deathTime;

    private boolean localSwinging;
    private InteractionHand localSwingingArm = InteractionHand.MAIN_HAND;
    private long localSwingStartMs;
    private long localSwingLastMs;
    private int localSwingDurationTicks = 6;
    private float localAttackAnim;
    private float localOAttackAnim;
    public SkyesightVisualEntity(
            Entity entity,
            SkyesightEntitySnapshotPayload.Entry entry
    ) {
        long now = animationTimeMillis();

        this.entity = entity;
        this.playerBody = new RemotePlayerBody(entry.position(), entry.yBodyRot());
        acceptPose(entry);

        this.previousPosition = entry.position();
        this.currentPosition = entry.position();
        this.deltaMovement = entry.deltaMovement();
        this.onGround = entry.onGround();
        this.fallDistance = entry.fallDistance();

        this.snapshotStartMs = now;
        this.snapshotEndMs = now + DEFAULT_SNAPSHOT_INTERVAL_MS;
        this.animationSnapshotMs = now;
        this.lastSnapshotPosition = entry.position();

        this.playerTargetWalkSpeed = 0.0F;
        this.playerWalkSpeed = 0.0F;
        this.playerWalkPosition = entry.walkPosition();
        this.playerWalkAnimationMs = now;
        acceptAnimation(entry);
        updatePlayerMovementAnimation(entry.position(), entry.position());
        applyInterpolated();
    }

    public Entity entity() {
        return this.entity;
    }

    public void clientTick() {
        if (this.entity instanceof Player player) {
            var pose = this.timeline.sample(SecondaryEntityClock.tickTime());
            this.playerBody.tick(pose.position(), pose.yaw(), this.localSwinging, player.isBlocking() ? 15F : 50F);
        }
        applyInterpolated();
        SecondaryEntityParticles.tick(this.entity);
        PortalVisualEntityAnimationUpdater.updateForRender(this.entity, 0.0F, "visual_snapshot_client_tick");
    }

    public void prepareForRender() {
        applyInterpolated();
        PortalVisualEntityAnimationUpdater.updateForRender(this.entity, debugInterpolationAlpha(), "visual_snapshot_prepare_render");
    }

    public void applyRenderStateTo(Entity target, float partialTick, String source) {
        if (target == null) {
            return;
        }
        applyInterpolated();
        copyRenderState(this.entity, target);
        PortalVisualEntityAnimationUpdater.updateForRender(
                target,
                partialTick,
                source
        );
    }

    private static long animationTimeMillis() { return (long)(SecondaryEntityClock.now() * 50); }

    private void acceptPose(SkyesightEntitySnapshotPayload.Entry entry) {
        this.timeline.accept(new RemoteEntityTimeline.Sample(entry.tickCount(), entry.position(),
                entry.yRot(), entry.xRot(), entry.yBodyRot(), entry.yHeadRot()), SecondaryEntityClock.now());
    }
    public void acceptSnapshot(SkyesightEntitySnapshotPayload.Entry entry) {
        long now = animationTimeMillis();
        this.previousPosition = this.currentPosition;
        this.currentPosition = entry.position();
        this.lastSnapshotPosition = entry.position();
        this.deltaMovement = entry.deltaMovement();
        this.onGround = entry.onGround();
        this.fallDistance = entry.fallDistance();
        this.snapshotStartMs = now;
        this.snapshotEndMs = now + DEFAULT_SNAPSHOT_INTERVAL_MS;
        if (this.previousPosition.distanceToSqr(entry.position()) > 256)
            this.playerBody.reset(entry.position(), entry.yBodyRot());
        updatePlayerMovementAnimation(this.previousPosition, entry.position());
        acceptPose(entry);
        acceptAnimation(entry);
    }
    public void applyInterpolated() {

        var pose = this.timeline.sample(SecondaryEntityClock.now());
        Vec3 position = pose.position();

        float yRot = pose.yaw();
        float xRot = pose.pitch();

        float elapsedTicks = elapsedAnimationTicks();

        this.entity.tickCount = this.tickCount + Mth.floor(elapsedTicks);

        this.entity.setPos(position);
        this.entity.setDeltaMovement(this.deltaMovement);
        this.entity.setOnGround(this.onGround);
        this.entity.fallDistance = this.fallDistance;
        Vec3 previousPosition = position; // Already sampled at render time; avoid a second partial-tick interpolation.
        this.entity.xOld = previousPosition.x();
        this.entity.yOld = previousPosition.y();
        this.entity.zOld = previousPosition.z();
        this.entity.xo = previousPosition.x();
        this.entity.yo = previousPosition.y();
        this.entity.zo = previousPosition.z();

        this.entity.setYRot(yRot);
        this.entity.setXRot(xRot);
        this.entity.yRotO = yRot;
        this.entity.xRotO = xRot;

        if (this.entity instanceof LivingEntity livingEntity) {
            livingEntity.yBodyRot = livingEntity.yBodyRotO = this.entity instanceof Player
                    ? this.playerBody.sample(net.minecraft.client.Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true)) : pose.body();
            livingEntity.yHeadRot = livingEntity.yHeadRotO = pose.head();
            applyLivingAnimationState(livingEntity);
            applyWalkAnimation(livingEntity, elapsedTicks);
        }
    }
    private void applyLivingAnimationState(LivingEntity livingEntity) {
        float elapsedTicks = elapsedAnimationTicks();
        int extrapolatedTicks = Mth.floor(elapsedTicks);

        livingEntity.hurtDuration = this.hurtDuration;
        livingEntity.hurtTime = Math.max(0, this.hurtTime - extrapolatedTicks);
        livingEntity.deathTime = this.deathTime > 0
                ? this.deathTime + extrapolatedTicks
                : 0;

        LivingEntityAnimationAccessor accessor =
                (LivingEntityAnimationAccessor) livingEntity;

        if (this.localSwinging) {
            applyLocalSwingAnimation(accessor);
            return;
        }

        accessor.skyesight$setSwinging(false);
        accessor.skyesight$setSwingTime(0);
        accessor.skyesight$setOAttackAnim(0.0F);
        accessor.skyesight$setAttackAnim(0.0F);
    }
    private void applyLocalSwingAnimation(LivingEntityAnimationAccessor accessor) {
        long now = animationTimeMillis();

        float elapsedTicks =
                (now - this.localSwingStartMs) / 50.0F;

        float previousElapsedTicks =
                (this.localSwingLastMs - this.localSwingStartMs) / 50.0F;

        this.localSwingLastMs = now;

        float progress = Mth.clamp(
                elapsedTicks / (float) this.localSwingDurationTicks,
                0.0F,
                1.0F
        );

        float previousProgress = Mth.clamp(
                previousElapsedTicks / (float) this.localSwingDurationTicks,
                0.0F,
                1.0F
        );

        this.localOAttackAnim = previousProgress;
        this.localAttackAnim = progress;

        accessor.skyesight$setSwinging(progress < 1.0F);
        accessor.skyesight$setSwingingArm(this.localSwingingArm);
        accessor.skyesight$setSwingTime(Mth.floor(elapsedTicks));
        accessor.skyesight$setOAttackAnim(this.localOAttackAnim);
        accessor.skyesight$setAttackAnim(this.localAttackAnim);

        if (progress >= 1.0F) {
            this.localSwinging = false;
            accessor.skyesight$setSwinging(false);
            accessor.skyesight$setSwingTime(0);
            accessor.skyesight$setOAttackAnim(0.0F);
            accessor.skyesight$setAttackAnim(0.0F);
        }
    }
    private void applyWalkAnimation(LivingEntity livingEntity, float elapsedTicks) {
        WalkAnimationState walkAnimation =
                ((LivingEntityWalkAnimationAccessor) livingEntity).skyesight$getWalkAnimation();

        WalkAnimationStateAccessor accessor =
                (WalkAnimationStateAccessor) walkAnimation;

        if (this.entity instanceof Player) {
            float oldSpeed = this.playerWalkSpeed;

            stepPlayerWalkAnimation();

            accessor.skyesight$setPosition(this.playerWalkPosition);
            accessor.skyesight$setSpeed(this.playerWalkSpeed);
            accessor.skyesight$setSpeedOld(oldSpeed);
            return;
        }

        float extrapolatedWalkPosition =
                this.walkPosition + this.walkSpeed * elapsedTicks;

        accessor.skyesight$setPosition(extrapolatedWalkPosition);
        accessor.skyesight$setSpeed(this.walkSpeed);
        accessor.skyesight$setSpeedOld(this.walkSpeedOld);
    }
    private void stepPlayerWalkAnimation() {
        long now = animationTimeMillis();
        float elapsedTicks = Math.min((now - this.playerWalkAnimationMs) / 50.0F, 4.0F);

        if (elapsedTicks <= 0.0F) {
            return;
        }

        this.playerWalkAnimationMs = now;

        float smoothing = 1.0F - (float) Math.pow(0.6F, elapsedTicks);

        this.playerWalkSpeed = Mth.lerp(
                smoothing,
                this.playerWalkSpeed,
                this.playerTargetWalkSpeed
        );

        this.playerWalkPosition += this.playerWalkSpeed * elapsedTicks;
    }

    private void acceptAnimation(SkyesightEntitySnapshotPayload.Entry entry) {
        this.animationSnapshotMs = animationTimeMillis();

        this.tickCount = entry.tickCount();

        this.walkPosition = entry.walkPosition();
        this.walkSpeed = entry.walkSpeed();
        this.walkSpeedOld = entry.walkSpeedOld();
        this.hurtTime = entry.hurtTime();
        this.hurtDuration = entry.hurtDuration();
        this.deathTime = entry.deathTime();
        if (entry.swinging()) {
            long swingStartMs = this.animationSnapshotMs - Math.max(0, entry.swingTime()) * 50L;
            if (!this.localSwinging || entry.swingingArm() != this.localSwingingArm) {
                this.localSwingStartMs = swingStartMs;
                this.localSwingLastMs = this.animationSnapshotMs;
                this.localAttackAnim = entry.attackAnim();
                this.localOAttackAnim = entry.oAttackAnim();
            }
            this.localSwinging = true;
            this.localSwingingArm = entry.swingingArm() == null ? InteractionHand.MAIN_HAND : entry.swingingArm();
        }
    }

    private void updatePlayerMovementAnimation(Vec3 from, Vec3 to) {
        double dx = to.x() - from.x();
        double dz = to.z() - from.z();

        double velocityDx = this.deltaMovement.x();
        double velocityDz = this.deltaMovement.z();

        double motionX = Math.abs(dx) > Math.abs(velocityDx) ? dx : velocityDx;
        double motionZ = Math.abs(dz) > Math.abs(velocityDz) ? dz : velocityDz;

        float horizontalDistance = Mth.sqrt((float) (motionX * motionX + motionZ * motionZ));

        float snapshotTicks = DEFAULT_SNAPSHOT_INTERVAL_MS / 50.0F;
        float blocksPerTick = horizontalDistance / Math.max(1.0F, snapshotTicks);

        float targetSpeed = Mth.clamp(blocksPerTick * 4.0F, 0.0F, 1.0F);

        this.playerTargetWalkSpeed = Mth.lerp(
                0.35F,
                this.playerTargetWalkSpeed,
                targetSpeed
        );

    }
    private float interpolationAlpha(long nowMs) {
        long duration = Math.max(1L, this.snapshotEndMs - this.snapshotStartMs);
        return Mth.clamp((float) (nowMs - this.snapshotStartMs) / (float) duration, 0.0F, 1.0F);
    }

    private float elapsedAnimationTicks() {
        long now = animationTimeMillis();
        long elapsedMs = Math.max(0L, now - this.animationSnapshotMs);

        return elapsedMs / 50.0F;
    }

    public Vec3 debugPreviousPosition() {
        return this.previousPosition;
    }

    public Vec3 debugCurrentPosition() {
        return this.currentPosition;
    }

    public Vec3 debugLastSnapshotPosition() {
        return this.lastSnapshotPosition;
    }

    public long debugSnapshotStartMs() {
        return this.snapshotStartMs;
    }

    public long debugSnapshotEndMs() {
        return this.snapshotEndMs;
    }

    public float debugInterpolationAlpha() {
        return interpolationAlpha(animationTimeMillis());
    }

    private static float lerpDegrees(float alpha, float from, float to) {
        float delta = Mth.wrapDegrees(to - from);
        return wrapDegrees(from + alpha * delta);
    }

    private static float wrapDegrees(float degrees) {
        return Mth.wrapDegrees(degrees);
    }

    private static void copyRenderState(Entity source, Entity target) {
        target.tickCount = source.tickCount;
        target.setPos(source.position());
        target.xo = source.xo;
        target.yo = source.yo;
        target.zo = source.zo;
        target.xOld = source.xOld;
        target.yOld = source.yOld;
        target.zOld = source.zOld;
        target.setDeltaMovement(source.getDeltaMovement());
        target.setYRot(source.getYRot());
        target.setXRot(source.getXRot());
        target.yRotO = source.yRotO;
        target.xRotO = source.xRotO;
        target.setOnGround(source.onGround());
        target.fallDistance = source.fallDistance;

        if (source instanceof LivingEntity sourceLiving && target instanceof LivingEntity targetLiving) {
            targetLiving.yBodyRot = sourceLiving.yBodyRot;
            targetLiving.yBodyRotO = sourceLiving.yBodyRotO;
            targetLiving.yHeadRot = sourceLiving.yHeadRot;
            targetLiving.yHeadRotO = sourceLiving.yHeadRotO;
            targetLiving.hurtTime = sourceLiving.hurtTime;
            targetLiving.hurtDuration = sourceLiving.hurtDuration;
            targetLiving.deathTime = sourceLiving.deathTime;

            WalkAnimationState sourceWalk =
                    ((LivingEntityWalkAnimationAccessor) sourceLiving).skyesight$getWalkAnimation();
            WalkAnimationState targetWalk =
                    ((LivingEntityWalkAnimationAccessor) targetLiving).skyesight$getWalkAnimation();
            WalkAnimationStateAccessor sourceAccessor = (WalkAnimationStateAccessor) sourceWalk;
            WalkAnimationStateAccessor targetAccessor = (WalkAnimationStateAccessor) targetWalk;
            targetAccessor.skyesight$setPosition(sourceAccessor.skyesight$getPosition());
            targetAccessor.skyesight$setSpeed(sourceAccessor.skyesight$getSpeed());
            targetAccessor.skyesight$setSpeedOld(sourceAccessor.skyesight$getSpeedOld());
        }
    }
}
