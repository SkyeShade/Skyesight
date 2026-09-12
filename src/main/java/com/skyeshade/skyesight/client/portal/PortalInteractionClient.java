package com.skyeshade.skyesight.client.portal;

import com.skyeshade.skyesight.api.*;
import com.skyeshade.skyesight.client.transition.TraversalPortalClient;
import com.skyeshade.skyesight.client.world.SkyesightVisualWorldManager;
import com.skyeshade.skyesight.network.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.*;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * One-hop predicted pick. The physical hit result never contains destination coordinates.
 */
@net.neoforged.fml.common.EventBusSubscriber(modid = "skyesight", value = net.neoforged.api.distmarker.Dist.CLIENT)
public final class PortalInteractionClient {
    private static long sequence;
    private static SkyesightPortalRaycast.Link miningLink;
    private static ClientLevel miningLevel;

    public static ClientLevel interactionLevel() {
        var mc = Minecraft.getInstance();
        var context = com.skyeshade.skyesight.server.portal.PortalInteractionContext.forEntity(mc.player);
        return context != null && context.level instanceof ClientLevel client ? client : null;
    }

    public static boolean routeAction(net.minecraft.network.protocol.Packet<?> packet) {
        if (interactionLevel() == null || mining == null || !(packet instanceof net.minecraft.network.protocol.game.ServerboundPlayerActionPacket action))
            return false;
        var intent = switch (action.getAction()) {
            case START_DESTROY_BLOCK -> SkyesightPortalInteractionPayload.Action.START;
            case STOP_DESTROY_BLOCK -> SkyesightPortalInteractionPayload.Action.STOP;
            case ABORT_DESTROY_BLOCK -> SkyesightPortalInteractionPayload.Action.ABORT;
            default -> null;
        };
        if (intent == null) return false;
        ((com.skyeshade.skyesight.mixin.client.PortalCarriedItemInvoker) Minecraft.getInstance().gameMode).skyesight$syncCarriedItem();
        PacketDistributor.sendToServer(new SkyesightPortalInteractionPayload(mining.portal(), mining.revision(), ++sequence, intent, action.getPos()));
        return true;
    }


    private static SkyesightPortalRaycast.Result selected;
    private static ClientLevel selectedLevel;
    private static SkyesightPortalRaycast.Step mining;
    private static com.mojang.blaze3d.vertex.ByteBufferBuilder overlayBuffer;

    public static ClientLevel destination(SkyesightPortalRaycast.Link link) {
        var world = SkyesightVisualWorldManager.get(link.id());
        if (world != null && world.environmentReady()) return world.level();
        var physical = Minecraft.getInstance().level;
        if (physical != null && physical.dimension().equals(link.target().dimension())
                && loaded(physical, link.target().center().add(-4, -4, -4), link.target().center().add(4, 4, 4)))
            return physical;
        var level = Minecraft.getInstance().level;
        return level != null && level.dimension().equals(link.target().dimension()) ? level : null;
    }

    public static boolean loaded(ClientLevel level, Vec3 from, Vec3 to) {
        if (level == null) return false;
        int minX = net.minecraft.util.Mth.floor(Math.min(from.x, to.x)) >> 4, maxX = net.minecraft.util.Mth.floor(Math.max(from.x, to.x)) >> 4;
        int minZ = net.minecraft.util.Mth.floor(Math.min(from.z, to.z)) >> 4, maxZ = net.minecraft.util.Mth.floor(Math.max(from.z, to.z)) >> 4;
        for (int x = minX; x <= maxX; x++)
            for (int z = minZ; z <= maxZ; z++) {
                var chunk = level.getChunkSource().getChunk(x, z, false);
                if (chunk == null || chunk instanceof net.minecraft.world.level.chunk.EmptyLevelChunk) return false;
            }
        return true;
    }

    public static SkyesightPortalRaycast.Result pick() {
        return pick(1);
    }

    private static SkyesightPortalRaycast.Result pick(float partialTick) {
        var mc = Minecraft.getInstance();
        selected = null;
        selectedLevel = null;
        if (mc.player == null || mc.level == null || mc.gameMode == null) return null;
        var links = TraversalPortalClient.interactionLinks(mc.level.dimension());
        if (links.isEmpty()) return null;
        var access = new SkyesightPortalRaycast.WorldAccess() {
            ClientLevel current;
            int segment;

            public boolean available(ResourceKey<Level> dimension, Vec3 from, Vec3 to) {
                current = null;
                // After the portal boundary, use its watched scene first. Previously loaded main
                // chunks can still exist after a long teleport but no longer receive block updates.
                if (segment++ == 0 && dimension.equals(mc.level.dimension()) && loaded(mc.level, from, to))
                    current = mc.level;
                if (current == null) for (var link : links)
                    if (link.target().dimension().equals(dimension)) {
                        var level = destination(link);
                        if (loaded(level, from, to)) {
                            current = level;
                            break;
                        }
                    }
                if (current == null && dimension.equals(mc.level.dimension()) && loaded(mc.level, from, to))
                    current = mc.level;
                return current != null;
            }

            public Iterable<SkyesightPortalRaycast.Link> portals(ResourceKey<Level> dimension) {
                return TraversalPortalClient.interactionLinks(dimension);
            }

            public HitResult nearestHit(ResourceKey<Level> dimension, Vec3 from, Vec3 to) {
                HitResult nearest = current.clip(new ClipContext(from, to, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, mc.player));
                double distance = from.distanceToSqr(nearest.getLocation());
                var candidates = new java.util.ArrayList<>(current.getEntities(mc.player, new AABB(from, to).inflate(1), e -> e.isPickable() && !e.isSpectator()));
                for (var link : links) {
                    var world = SkyesightVisualWorldManager.get(link.id());
                    if (world != null && world.level() == current) for (var visual : world.entityStore().entities())
                        if (visual.entity().isPickable() && !visual.entity().isRemoved() && !visual.entity().getUUID().equals(mc.player.getUUID()))
                            candidates.add(visual.entity());
                }
                for (var e : candidates) {
                    var hit = e.getBoundingBox().inflate(e.getPickRadius()).clip(from, to);
                    if (hit.isPresent() && from.distanceToSqr(hit.get()) < distance) {
                        nearest = new EntityHitResult(e, hit.get());
                        distance = from.distanceToSqr(hit.get());
                    }
                }
                return nearest;
            }
        };
        var ray = SkyesightPortalRaycast.trace(access, mc.level.dimension(), mc.player.getEyePosition(partialTick), mc.player.getViewVector(partialTick),
                Math.max(mc.player.blockInteractionRange(), mc.player.entityInteractionRange()), 1);
        if (ray.chain().isEmpty()) return null;
        selected = ray;
        selectedLevel = access.current;
        return ray;
    }

    public static void updatePick(float partialTick) {
        var mc = Minecraft.getInstance();
        var ray = pick(partialTick);
        if (ray != null) {
            mc.hitResult = BlockHitResult.miss(mc.player.getEyePosition(), Direction.UP, mc.player.blockPosition());
            mc.crosshairPickEntity = ray.hit() instanceof EntityHitResult hit ? hit.getEntity() : null;
        }
    }

    public static boolean attack(boolean initial, boolean held) {
        var mc = Minecraft.getInstance();
        var ray = pick();
        if (!held || ray == null || mc.player == null || mc.player.isUsingItem() || mc.player.isSpectator()) {
            stopMining();
            return ray != null;
        }
        if (initial && mc.player.isHandsBusy()) return true;
        if (ray.end() != SkyesightPortalRaycast.End.HIT) {
            stopMining();
            mc.hitResult = BlockHitResult.miss(mc.player.getEyePosition(), Direction.UP, mc.player.blockPosition());
            mc.crosshairPickEntity = null;
            return false; // Preserve Minecraft's native miss delay and swing behavior.
        }
        if (ray.end() == SkyesightPortalRaycast.End.HIT) {
            var event = net.neoforged.neoforge.client.ClientHooks.onClickInput(0, mc.options.keyAttack, InteractionHand.MAIN_HAND);
            if (event.isCanceled()) {
                stopMining();
                if (event.shouldSwingHand()) mc.player.swing(InteractionHand.MAIN_HAND);
                return true;
            }
            if (initial && ray.hit() instanceof EntityHitResult)
                send(ray, SkyesightPortalInteractionPayload.Action.ATTACK);
            else if (ray.hit() instanceof BlockHitResult) {
                mine(ray, initial);
            }
            if (event.shouldSwingHand()) mc.player.swing(InteractionHand.MAIN_HAND);
        }
        return true;
    }

    public static boolean use() {
        var mc = Minecraft.getInstance();
        var ray = pick();
        if (ray == null) return false;
        stopMining();
        // Empty space beyond an aperture is still an ordinary use-in-air action (bow, snowball,
        // food). Clear the hidden source hit, then let vanilla create the item/projectile locally.
        if (ray.end() != SkyesightPortalRaycast.End.HIT) {
            mc.hitResult = BlockHitResult.miss(mc.player.getEyePosition(), Direction.UP, mc.player.blockPosition());
            mc.crosshairPickEntity = null;
            return false;
        }
        if (ray.end() == SkyesightPortalRaycast.End.HIT && !mc.player.isHandsBusy()) {
            var event = net.neoforged.neoforge.client.ClientHooks.onClickInput(1, mc.options.keyUse, InteractionHand.MAIN_HAND);
            if (!event.isCanceled()) send(ray, SkyesightPortalInteractionPayload.Action.USE);
            if (event.shouldSwingHand()) mc.player.swing(InteractionHand.MAIN_HAND);
        }
        return true;
    }

    private static void send(SkyesightPortalRaycast.Result ray, SkyesightPortalInteractionPayload.Action action) {
        ((com.skyeshade.skyesight.mixin.client.PortalCarriedItemInvoker) Minecraft.getInstance().gameMode).skyesight$syncCarriedItem();
        var step = ray.chain().getFirst();
        PacketDistributor.sendToServer(new SkyesightPortalInteractionPayload(step.portal(), step.revision(), ++sequence, action));
    }

    private static void mine(SkyesightPortalRaycast.Result ray, boolean initial) {
        var mc = Minecraft.getInstance();
        var step = ray.chain().getFirst();
        var link = TraversalPortalClient.interactionLinks(mc.level.dimension()).stream()
                .filter(l -> l.id().equals(step.portal()) && l.revision() == step.revision()).findFirst().orElse(null);
        if (link == null) {
            stopMining();
            return;
        }
        if (mining == null || !mining.portal().equals(step.portal()) || mining.revision() != step.revision() || miningLevel != selectedLevel) {
            stopMining();
            mc.gameMode.stopDestroyBlock();
            mining = step;
            miningLink = link;
            miningLevel = selectedLevel;
        }
        if (miningLevel == null) {
            stopMining();
            return;
        }
        var hit = (BlockHitResult) ray.hit();
        try (var ignored = com.skyeshade.skyesight.server.portal.PortalInteractionContext.open(mc.player, miningLevel, miningLink)) {
            if (initial) mc.gameMode.startDestroyBlock(hit.getBlockPos(), hit.getDirection());
            else mc.gameMode.continueDestroyBlock(hit.getBlockPos(), hit.getDirection());
        }
    }

    private static void stopMining() {
        var mc = Minecraft.getInstance();
        if (mining != null && miningLevel != null && mc.player != null && mc.gameMode != null) {
            try (var ignored = com.skyeshade.skyesight.server.portal.PortalInteractionContext.open(mc.player, miningLevel, miningLink)) {
                mc.gameMode.stopDestroyBlock();
            }
        }
        mining = null;
        miningLink = null;
        miningLevel = null;
    }

    public static void receive(SkyesightPortalMiningPayload payload) {
        if (!com.skyeshade.skyesight.remote.SkyesightRemoteViewRegistry.accepts(payload.viewId(), payload.generation(), payload.dimension()))
            return;
        var world = com.skyeshade.skyesight.client.world.SkyesightVisualWorldManager.getIfCurrent(payload.viewId(), payload.dimension());
        if (world != null && !world.isClosed()) world.destroyProgress().update(payload.breakerId(), payload.pos(),
                payload.stage(), (long) com.skyeshade.skyesight.client.world.SecondaryEntityClock.tickTime());
    }

    @net.neoforged.bus.api.SubscribeEvent
    public static void logout(net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) {
        selected = null;
        selectedLevel = null;
        mining = null;
        miningLevel = null;
        miningLink = null;
        sequence = 0;
        if (overlayBuffer != null) {
            overlayBuffer.close();
            overlayBuffer = null;
        }
    }

    public static void render(com.skyeshade.skyesight.client.render.SecondarySceneFrame scene) {
        renderProgress(scene);
        var ray = selected;
        if (ray == null || ray.chain().isEmpty() || !ray.chain().getFirst().portal().equals(scene.viewId())
                || !(ray.hit() instanceof BlockHitResult hit) || ray.end() != SkyesightPortalRaycast.End.HIT) return;
        var mc = Minecraft.getInstance();
        var pos = hit.getBlockPos();
        var state = scene.level().getBlockState(pos);
        var camera = scene.view().camera().getPosition();
        var projection = new org.joml.Matrix4f(com.mojang.blaze3d.systems.RenderSystem.getProjectionMatrix());
        var sorting = com.mojang.blaze3d.systems.RenderSystem.getVertexSorting();
        var model = com.mojang.blaze3d.systems.RenderSystem.getModelViewStack();
        model.pushMatrix();
        try {
            model.set(scene.view().modelViewMatrix());
            com.mojang.blaze3d.systems.RenderSystem.applyModelViewMatrix();
            com.mojang.blaze3d.systems.RenderSystem.setProjectionMatrix(scene.view().projectionMatrix(), com.mojang.blaze3d.vertex.VertexSorting.DISTANCE_TO_ORIGIN);
            var stack = new com.mojang.blaze3d.vertex.PoseStack();
            if (overlayBuffer == null) overlayBuffer = new com.mojang.blaze3d.vertex.ByteBufferBuilder(4096);
            var lines = net.minecraft.client.renderer.RenderType.lines();
            var outline = new com.mojang.blaze3d.vertex.BufferBuilder(overlayBuffer, lines.mode(), lines.format());
            com.skyeshade.skyesight.mixin.client.PortalOutlineInvoker.skyesight$shape(stack, outline,
                    state.getShape(scene.level(), pos, net.minecraft.world.phys.shapes.CollisionContext.of(mc.player)),
                    pos.getX() - camera.x, pos.getY() - camera.y, pos.getZ() - camera.z, 0, 0, 0, .4f);
            var outlineMesh = outline.build();
            if (outlineMesh != null) lines.draw(outlineMesh);

        } finally {
            model.popMatrix();
            com.mojang.blaze3d.systems.RenderSystem.applyModelViewMatrix();
            com.mojang.blaze3d.systems.RenderSystem.setProjectionMatrix(projection, sorting);
        }
    }

    private static void renderProgress(com.skyeshade.skyesight.client.render.SecondarySceneFrame scene) {
        if (scene.visualWorld() == null) return;
        var mc = Minecraft.getInstance();
        var camera = scene.view().camera().getPosition();
        var model = com.mojang.blaze3d.systems.RenderSystem.getModelViewStack();
        var projection = new org.joml.Matrix4f(com.mojang.blaze3d.systems.RenderSystem.getProjectionMatrix());
        var sorting = com.mojang.blaze3d.systems.RenderSystem.getVertexSorting();
        model.pushMatrix();
        try {
            model.set(scene.view().modelViewMatrix());
            com.mojang.blaze3d.systems.RenderSystem.applyModelViewMatrix();
            com.mojang.blaze3d.systems.RenderSystem.setProjectionMatrix(scene.view().projectionMatrix(), com.mojang.blaze3d.vertex.VertexSorting.DISTANCE_TO_ORIGIN);
            if (overlayBuffer == null) overlayBuffer = new com.mojang.blaze3d.vertex.ByteBufferBuilder(4096);
            for (var crack : scene.visualWorld().destroyProgress().visible((long) com.skyeshade.skyesight.client.world.SecondaryEntityClock.tickTime())) {
                var pos = crack.pos();
                var state = scene.level().getBlockState(pos);
                if (state.isAir() || camera.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(pos)) > 1024
                        || !scene.view().frustum().isVisible(new net.minecraft.world.phys.AABB(pos))) continue;
                var stack = new com.mojang.blaze3d.vertex.PoseStack();
                stack.translate(pos.getX() - camera.x, pos.getY() - camera.y, pos.getZ() - camera.z);
                // Flush this view's geometry while its projection/stencil are installed. Never enqueue
                // destination vertices into a main-world batch whose eventual draw owns other matrices.
                var material = net.minecraft.client.resources.model.ModelBakery.DESTROY_TYPES.get(crack.stage());
                var breaking = new com.mojang.blaze3d.vertex.BufferBuilder(overlayBuffer, material.mode(), material.format());
                var consumer = new com.mojang.blaze3d.vertex.SheetedDecalTextureGenerator(breaking, stack.last(), 1);
                mc.getBlockRenderer().renderBreakingTexture(state, pos, scene.level(), stack, consumer, net.neoforged.neoforge.client.model.data.ModelData.EMPTY);
                var mesh = breaking.build();
                if (mesh != null) {
                    // Match vanilla's view-offset layering. Polygon offset alone can lose the
                    // coplanar depth test against the secondary terrain backend's surface.
                    // Keep depth testing and the caller's portal stencil intact.
                    material.setupRenderState();
                    model.pushMatrix();
                    try {
                        model.scale(0.99975586f);
                        com.mojang.blaze3d.systems.RenderSystem.applyModelViewMatrix();
                        com.mojang.blaze3d.vertex.BufferUploader.drawWithShader(mesh);
                    } finally {
                        model.popMatrix();
                        com.mojang.blaze3d.systems.RenderSystem.applyModelViewMatrix();
                        material.clearRenderState();
                    }
                }

            }
        } finally {
            model.popMatrix();
            com.mojang.blaze3d.systems.RenderSystem.applyModelViewMatrix();
            com.mojang.blaze3d.systems.RenderSystem.setProjectionMatrix(projection, sorting);
        }
    }

}
