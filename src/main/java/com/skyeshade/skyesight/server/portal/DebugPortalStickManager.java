package com.skyeshade.skyesight.server.portal;

import com.skyeshade.skyesight.Skyesight;
import com.skyeshade.skyesight.SkyesightDebugConfig;
import com.skyeshade.skyesight.PortalFirstUseTimeline;
import com.skyeshade.skyesight.api.PortalEndpoint;
import com.skyeshade.skyesight.api.PortalRegistrationResult;
import com.skyeshade.skyesight.api.PortalRenderSettings;
import com.skyeshade.skyesight.api.RegisteredPortalView;
import com.skyeshade.skyesight.api.SkyesightPortalApi;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@EventBusSubscriber(modid = Skyesight.MODID)
public final class DebugPortalStickManager {
    public static final String PORTAL_A_ID = "debug_stick_portal_a";
    public static final String PORTAL_B_ID = "debug_stick_portal_b";
    private static final String MARKER_TAG = "skyesight_debug_portal_stick_marker";
    private static final Map<UUID, StickState> STATES = new HashMap<>();
    private static float nextPortalWidth = PortalEndpoint.DEFAULT_WIDTH;
    private static float nextPortalHeight = PortalEndpoint.DEFAULT_HEIGHT;

    private DebugPortalStickManager() {
    }

    public static void placeEndpoint(ServerPlayer player, BlockPos clickedPos, Direction clickedFace) {
        StickState state = STATES.computeIfAbsent(player.getUUID(), ignored -> new StickState());
        if (state.endpointA != null && state.endpointB != null) {
            player.sendSystemMessage(Component.literal("Debug stick portal pair already placed; left-click to start replacement, or sneak-left-click to delete it."));
            return;
        }

        PortalEndpoint endpoint = endpointFromClick(player, clickedPos, clickedFace, state.endpointA == null ? "A" : "B");
        logEndpoint(state.endpointA == null ? "A" : "B", endpoint);
        if (state.endpointA == null) {
            state.endpointA = endpoint;
            state.clickedFaceA = clickedFace;
            state.timestampMillis = System.currentTimeMillis();
            spawnEndpointMarker(player, state, endpoint);
            player.sendSystemMessage(Component.literal(
                    "Debug portal A set at "
                            + endpoint.dimension().location()
                            + " "
                            + formatVec(endpoint.center())
                            + " facing "
                            + endpoint.facing().getName()
                            + " size "
                            + formatSize(endpoint.width(), endpoint.height())
                            + ". Right-click another block to place B."
            ));
            return;
        }

        state.endpointB = endpoint;
        state.clickedFaceB = clickedFace;
        PortalFirstUseTimeline.log(
                "debug_stick_second_click_received",
                null,
                "endpointA=" + formatVec(state.endpointA.center())
                        + " endpointB=" + formatVec(state.endpointB.center())
                        + " dimA=" + state.endpointA.dimension().location()
                        + " dimB=" + state.endpointB.dimension().location()
        );
        removeEndpointMarker(player.server, state);

        PortalRegistrationResult result = SkyesightPortalApi.registerPortalPair(
                PORTAL_A_ID,
                state.endpointA,
                PORTAL_B_ID,
                state.endpointB,
                PortalRenderSettings.defaults(),
                PortalRenderSettings.defaults(),
                true,
                true,
                "debug-stick",
                true,
                false
        );
        if (result.success()) {
            if (!validateFinalStencilRefs(player)) {
                removeRegisteredStickPair();
                player.sendSystemMessage(Component.literal("Debug stick portal registration failed: invalid final stencil refs."));
                return;
            }
            logCompactRegisteredPair();
            logBackfaceProof(result);
        }
        logRegisteredPair(state);
        player.sendSystemMessage(Component.literal(
                result.success()
                        ? "Registered debug stick portal pair debug_stick_portal_a <-> debug_stick_portal_b size "
                        + formatSize(state.endpointA.width(), state.endpointA.height())
                        + " refs " + finalStencilRef(PORTAL_A_ID) + "/" + finalStencilRef(PORTAL_B_ID) + "."
                        : "Debug stick portal registration failed: " + result.message()
        ));
    }

    public static void clear(ServerPlayer player) {
        StickState state = STATES.remove(player.getUUID());
        if (state != null) {
            removeEndpointMarker(player.server, state);
        }
        boolean registeredPairExists = hasRegisteredStickPair();
        PortalRegistrationResult result = registeredPairExists
                ? SkyesightPortalApi.disablePortalOrPair(PORTAL_A_ID)
                : PortalRegistrationResult.failure("no registered debug stick portal pair");
        player.sendSystemMessage(Component.literal(
                result.success()
                        ? "Debug stick portal pair disabled; cache retained. Right-click two blocks to replace."
                        : "Debug stick portal state cleared."
        ));
    }

    public static void deleteRegisteredPair(ServerPlayer player) {
        StickState state = STATES.remove(player.getUUID());
        if (state != null) {
            removeEndpointMarker(player.server, state);
        }
        PortalRegistrationResult result = removeRegisteredStickPair();
        player.sendSystemMessage(Component.literal(
                result.success()
                        ? "Debug stick portal pair removed; cache cleared."
                        : "Debug stick portal state cleared."
        ));
    }

    private static boolean hasRegisteredStickPair() {
        return SkyesightPortalApi.getPortal(PORTAL_A_ID) != null || SkyesightPortalApi.getPortal(PORTAL_B_ID) != null;
    }

    private static PortalRegistrationResult removeRegisteredStickPair() {
        PortalRegistrationResult result = SkyesightPortalApi.removePortalOrPair(PORTAL_A_ID);
        if (result.success()) {
            return result;
        }
        return SkyesightPortalApi.removePortalOrPair(PORTAL_B_ID);
    }

    public static String status(ServerPlayer player) {
        StickState state = STATES.get(player.getUUID());
        if (state == null || state.endpointA == null) {
            return "no debug stick endpoint set";
        }
        if (state.endpointB == null) {
            return "A=" + state.endpointA.dimension().location() + " " + formatVec(state.endpointA.center())
                    + " facing " + state.endpointA.facing().getName() + "; B unset";
        }
        return "A=" + state.endpointA.dimension().location() + " " + formatVec(state.endpointA.center())
                + " B=" + state.endpointB.dimension().location() + " " + formatVec(state.endpointB.center());
    }

    public static String sizeStatus() {
        return "size is " + formatSize(nextPortalWidth, nextPortalHeight);
    }

    public static String setWidth(float width) {
        PortalEndpoint.validateSize(width, nextPortalHeight);
        nextPortalWidth = width;
        return "width set to " + formatFloat(nextPortalWidth) + "; " + sizeStatus();
    }

    public static String setHeight(float height) {
        PortalEndpoint.validateSize(nextPortalWidth, height);
        nextPortalHeight = height;
        return "height set to " + formatFloat(nextPortalHeight) + "; " + sizeStatus();
    }

    public static String setSize(float width, float height) {
        PortalEndpoint.validateSize(width, height);
        nextPortalWidth = width;
        nextPortalHeight = height;
        return sizeStatus();
    }

    public static String resetSize() {
        nextPortalWidth = PortalEndpoint.DEFAULT_WIDTH;
        nextPortalHeight = PortalEndpoint.DEFAULT_HEIGHT;
        return sizeStatus();
    }

    private static PortalEndpoint endpointFromClick(ServerPlayer player, BlockPos clickedPos, Direction clickedFace, String id) {
        return PortalEndpoint.fromBlockHit(
                id,
                player.serverLevel().dimension(),
                clickedPos,
                clickedFace,
                player.getDirection(),
                nextPortalWidth,
                nextPortalHeight
        );
    }

    private static void spawnEndpointMarker(ServerPlayer player, StickState state, PortalEndpoint endpoint) {
        removeEndpointMarker(player.server, state);
        ServerLevel level = player.server.getLevel(endpoint.dimension());
        if (level == null) {
            return;
        }
        ArmorStand stand = EntityType.ARMOR_STAND.create(level);
        if (stand == null) {
            return;
        }
        stand.setPos(endpoint.center());
        stand.setNoGravity(true);
        stand.setInvulnerable(true);
        stand.setSilent(true);
        stand.setCustomName(Component.literal("debug stick A"));
        stand.setCustomNameVisible(true);
        stand.setGlowingTag(true);
        stand.setInvisible(false);
        stand.addTag(MARKER_TAG);
        if (level.addFreshEntity(stand)) {
            state.markerDimension = endpoint.dimension();
            state.markerUuid = stand.getUUID();
        }
    }

    private static void removeEndpointMarker(MinecraftServer server, StickState state) {
        if (server == null || state == null || state.markerUuid == null || state.markerDimension == null) {
            return;
        }
        ServerLevel level = server.getLevel(state.markerDimension);
        if (level != null) {
            Entity entity = level.getEntity(state.markerUuid);
            if (entity != null && entity.getTags().contains(MARKER_TAG)) {
                entity.discard();
            }
        }
        state.markerUuid = null;
        state.markerDimension = null;
    }

    private static void logRegisteredPair(StickState state) {
        if (SkyesightDebugConfig.shouldLogDebugStickAudit()) {
            logRegisteredView(SkyesightPortalApi.parseId(PORTAL_A_ID), SkyesightPortalApi.parseId("debug_portal_a"), state.clickedFaceA);
            logRegisteredView(SkyesightPortalApi.parseId(PORTAL_B_ID), SkyesightPortalApi.parseId("debug_portal_b"), state.clickedFaceB);
        }
        if (SkyesightDebugConfig.shouldLogDebugStickAudit() || SkyesightDebugConfig.shouldLogPortalApiAudit()) {
            logApiViewCompare(
                    SkyesightPortalApi.parseId("debug_portal_a"),
                    SkyesightPortalApi.parseId("debug_portal_b"),
                    SkyesightPortalApi.parseId(PORTAL_A_ID),
                    SkyesightPortalApi.parseId(PORTAL_B_ID)
            );
        }
    }

    private static void logEndpoint(String slot, PortalEndpoint endpoint) {
        if (!SkyesightDebugConfig.shouldLogDebugStickAudit()) {
            return;
        }
        Vec3 right = basis(endpoint, 1.0F, 0.0F, 0.0F);
        Vec3 up = basis(endpoint, 0.0F, 1.0F, 0.0F);
        Vec3 forward = basis(endpoint, 0.0F, 0.0F, 1.0F);
        Skyesight.LOGGER.info(
                "[Skyesight] DEBUG_STICK_PORTAL_ENDPOINT: slot={} dimension={} pos={} facing={} basis=right:{},up:{},forward:{}",
                slot,
                endpoint.dimension().location(),
                formatVec(endpoint.center()),
                endpoint.facing().getName(),
                formatVec(right),
                formatVec(up),
                formatVec(forward)
        );
    }

    private static void logRegisteredView(ResourceLocation id, ResourceLocation compareId, Direction clickedFace) {
        RegisteredPortalView view = SkyesightPortalApi.getPortal(id.toString());
        if (view == null) {
            Skyesight.LOGGER.warn("[Skyesight] DEBUG_STICK_PORTAL_REGISTERED: id={} missingAfterRegistration=yes", id);
            return;
        }
        if (!SkyesightDebugConfig.shouldLogDebugStickAudit()) {
            return;
        }
        RegisteredPortalView compare = SkyesightPortalApi.getPortal(compareId.toString());
        PortalEndpoint source = view.source();
        PortalEndpoint target = view.target();
        Vec3 right = basis(source, 1.0F, 0.0F, 0.0F);
        Vec3 up = basis(source, 0.0F, 1.0F, 0.0F);
        Vec3 forward = basis(source, 0.0F, 0.0F, 1.0F);
        boolean matchingKnownConvention = clickedFace != null && clickedFace.getAxis().isHorizontal()
                ? source.facing() == clickedFace.getOpposite()
                : source.facing().getAxis().isHorizontal();
        Skyesight.LOGGER.info(
                "[Skyesight] DEBUG_STICK_FRAME_COMPARE: id={} clickedFace={} storedFacing={} center={} rotation={} right={} up={} forward={} matchingKnownConvention={} reason={}",
                view.id(),
                clickedFace == null ? "unknown" : clickedFace.getName(),
                source.facing().getName(),
                formatVec(source.center()),
                formatRotation(source),
                formatVec(right),
                formatVec(up),
                formatVec(forward),
                yesNo(matchingKnownConvention),
                clickedFace != null && clickedFace.getAxis().isHorizontal()
                        ? "stored facing is clicked horizontal face opposite to match registry frame convention"
                        : "vertical click uses player horizontal facing"
        );
        Skyesight.LOGGER.info(
                "[Skyesight] DEBUG_STICK_PORTAL_REGISTERED: id={} pairedId={} group={} sourceDim={} sourceCenter={} sourceFacing={} sourceRotation={} targetDim={} targetCenter={} targetFacing={} targetRotation={} width={} height={} renderEnabled={} rendersView={} stencilRef={} flags=sky:{},terrain:{},translucent:{},entities:{},blockEntities:{},particles:{} basisRight={} basisUp={} basisForward={} corners={} compareKnownGood={} compareCenter={} compareStencilRef={}",
                view.id(),
                view.pairedId(),
                view.groupId(),
                source.dimension().location(),
                formatVec(source.center()),
                source.facing().getName(),
                formatRotation(source),
                target.dimension().location(),
                formatVec(target.center()),
                target.facing().getName(),
                formatRotation(target),
                source.width(),
                source.height(),
                view.renderEnabled(),
                view.renderSettings().rendersView(),
                view.renderSettings().stencilRef(),
                view.renderSettings().renderSky(),
                view.renderSettings().renderTerrain(),
                view.renderSettings().renderTranslucent(),
                view.renderSettings().renderEntities(),
                view.renderSettings().renderBlockEntities(),
                view.renderSettings().renderParticles(),
                formatVec(right),
                formatVec(up),
                formatVec(forward),
                formatCorners(source, right, up),
                compare == null ? "missing" : compare.id(),
                compare == null ? "-" : formatVec(compare.source().center()),
                compare == null ? "-" : compare.renderSettings().stencilRef()
        );
    }

    private static void logApiViewCompare(ResourceLocation... ids) {
        if (!SkyesightDebugConfig.shouldLogDebugStickAudit() && !SkyesightDebugConfig.shouldLogPortalApiAudit()) {
            return;
        }
        for (ResourceLocation id : ids) {
            RegisteredPortalView view = SkyesightPortalApi.getPortal(id.toString());
            if (view == null) {
                Skyesight.LOGGER.info("[Skyesight] PORTAL_API_VIEW_COMPARE: id={} present=no", id);
                continue;
            }
            PortalEndpoint source = view.source();
            PortalEndpoint target = view.target();
            Vec3 sourceRight = basis(source, 1.0F, 0.0F, 0.0F);
            Vec3 sourceUp = basis(source, 0.0F, 1.0F, 0.0F);
            Vec3 sourceForward = basis(source, 0.0F, 0.0F, 1.0F);
            Vec3 targetRight = basis(target, 1.0F, 0.0F, 0.0F);
            Vec3 targetUp = basis(target, 0.0F, 1.0F, 0.0F);
            Vec3 targetForward = basis(target, 0.0F, 0.0F, 1.0F);
            boolean crossDim = !source.dimension().equals(target.dimension());
            boolean farSameDim = !crossDim && source.center().distanceTo(target.center()) > 32.0D;
            String classification = crossDim ? "crossDim" : (farSameDim ? "farSameDim" : "nearSameDim");
            Skyesight.LOGGER.info(
                    "[Skyesight] PORTAL_API_VIEW_COMPARE: id={} pair={} group={} sourceDim={} targetDim={} sourceCenter={} targetCenter={} sourceRotation={} targetRotation={} sourceBasis=right:{},up:{},forward:{} targetBasis=right:{},up:{},forward:{} entrancePortalClass=PortalFrame exitPortalClass=PortalFrame enabled={} rendersView={} stencilRef={} renderOrder=registry-order terrain={} translucent={} entities={} blockEntities={} particles={} sky={} terrainRadius={} entityRadius={} blockEntityRadius={} blockUpdateRadius={} regionId={} viewId={} visualWorldId={} entityWatchId={} chunkWatchId={} particleViewId={} blockUpdateRouteId={} crossDim={} classification={}",
                    view.id(),
                    view.pairedId() == null ? "-" : view.pairedId(),
                    view.groupId() == null ? "-" : view.groupId(),
                    source.dimension().location(),
                    target.dimension().location(),
                    formatVec(source.center()),
                    formatVec(target.center()),
                    formatRotation(source),
                    formatRotation(target),
                    formatVec(sourceRight),
                    formatVec(sourceUp),
                    formatVec(sourceForward),
                    formatVec(targetRight),
                    formatVec(targetUp),
                    formatVec(targetForward),
                    yesNo(view.renderSettings().enabled()),
                    yesNo(view.renderSettings().rendersView()),
                    view.renderSettings().stencilRef(),
                    yesNo(view.renderSettings().renderTerrain()),
                    yesNo(view.renderSettings().renderTranslucent()),
                    yesNo(view.renderSettings().renderEntities()),
                    yesNo(view.renderSettings().renderBlockEntities()),
                    yesNo(view.renderSettings().renderParticles()),
                    yesNo(view.renderSettings().renderSky()),
                    view.renderSettings().terrainChunkRadius(),
                    view.renderSettings().entityChunkRadius(),
                    view.renderSettings().blockEntityChunkRadius(),
                    view.renderSettings().blockUpdateChunkRadius(),
                    view.id(),
                    view.id(),
                    view.id(),
                    view.id(),
                    view.id(),
                    view.id(),
                    view.id(),
                    yesNo(crossDim),
                    classification
            );
        }
    }

    private static void logCompactRegisteredPair() {
        RegisteredPortalView viewA = SkyesightPortalApi.getPortal(PORTAL_A_ID);
        RegisteredPortalView viewB = SkyesightPortalApi.getPortal(PORTAL_B_ID);
        if (viewA == null || viewB == null) {
            Skyesight.LOGGER.warn(
                    "[Skyesight] Debug stick portal pair registered but lookup failed: {} present={} {} present={}",
                    PORTAL_A_ID,
                    yesNo(viewA != null),
                    PORTAL_B_ID,
                    yesNo(viewB != null)
            );
            return;
        }
        PortalEndpoint sourceA = viewA.source();
        PortalEndpoint sourceB = viewB.source();
        PortalRenderSettings settings = viewA.renderSettings();
        String pipeline = viewA.isCrossDimension() || viewB.isCrossDimension() ? "cross_dim" : "same_dim";
        Skyesight.LOGGER.info(
                "[Skyesight] Debug stick portal pair registered: {} {} {} {} <-> {} {} {} {} size={} pipeline={} backface={} stencils={}/{} radius={} flags={}",
                compactId(viewA.id()),
                shortDimension(sourceA.dimension()),
                formatCompactVec(sourceA.center()),
                sourceA.facing().getName(),
                compactId(viewB.id()),
                shortDimension(sourceB.dimension()),
                formatCompactVec(sourceB.center()),
                sourceB.facing().getName(),
                formatSize(sourceA.width(), sourceA.height()),
                pipeline,
                Boolean.toString(viewA.renderBackface() || viewB.renderBackface()),
                viewA.renderSettings().stencilRef(),
                viewB.renderSettings().stencilRef(),
                settings.terrainChunkRadius(),
                compactFlags(settings)
        );
    }

    private static boolean validateFinalStencilRefs(ServerPlayer player) {
        RegisteredPortalView viewA = SkyesightPortalApi.getPortal(PORTAL_A_ID);
        RegisteredPortalView viewB = SkyesightPortalApi.getPortal(PORTAL_B_ID);
        int refA = viewA == null ? PortalRenderSettings.AUTO_STENCIL_REF : viewA.renderSettings().stencilRef();
        int refB = viewB == null ? PortalRenderSettings.AUTO_STENCIL_REF : viewB.renderSettings().stencilRef();
        if (refA > 0 && refB > 0) {
            return true;
        }
        Skyesight.LOGGER.error(
                "[Skyesight] Debug stick portal pair registered with invalid final stencil refs: player={} {}={} {}={}",
                player == null ? "-" : player.getGameProfile().getName(),
                PORTAL_A_ID,
                refA,
                PORTAL_B_ID,
                refB
        );
        return false;
    }

    private static int finalStencilRef(String id) {
        RegisteredPortalView view = SkyesightPortalApi.getPortal(id);
        return view == null ? PortalRenderSettings.AUTO_STENCIL_REF : view.renderSettings().stencilRef();
    }

    private static void logBackfaceProof(PortalRegistrationResult result) {
        if (!SkyesightDebugConfig.shouldLogDebugStickAudit() && !SkyesightDebugConfig.shouldLogPortalApiAudit()) {
            return;
        }
        RegisteredPortalView viewA = SkyesightPortalApi.getPortal(PORTAL_A_ID);
        RegisteredPortalView viewB = SkyesightPortalApi.getPortal(PORTAL_B_ID);
        String groupId = viewA == null ? null : viewA.groupId();
        List<ResourceLocation> views = new ArrayList<>();
        List<ResourceLocation> backfaceViews = new ArrayList<>();
        boolean renderBackface = false;
        for (RegisteredPortalView view : SkyesightPortalApi.getAllPortals()) {
            boolean sameGroup = groupId != null && groupId.equals(view.groupId());
            boolean resultId = result.ids().contains(view.id());
            if (!sameGroup && !resultId) {
                continue;
            }
            views.add(view.id());
            renderBackface = renderBackface || view.renderBackface();
            if (!view.id().toString().equals(SkyesightPortalApi.parseId(PORTAL_A_ID).toString())
                    && !view.id().toString().equals(SkyesightPortalApi.parseId(PORTAL_B_ID).toString())) {
                backfaceViews.add(view.id());
            }
        }
        int directedViewCount = views.size();
        Skyesight.LOGGER.info(
                "[Skyesight] DEBUG_STICK_BACKFACE_PROOF: pairId={} renderBackface={} logicalPortalCount={} directedViewCount={} views={} cameraCount={} visualWorldCount={} regionCount={} entityWatchCount={} chunkWatchCount={} particleViewCount={} backfaceViews={} reason=after-registration",
                groupId == null ? PORTAL_A_ID + "+" + PORTAL_B_ID : groupId,
                Boolean.toString(renderBackface),
                2,
                directedViewCount,
                views,
                directedViewCount,
                directedViewCount,
                directedViewCount,
                directedViewCount,
                directedViewCount,
                directedViewCount,
                backfaceViews
        );
    }

    private static String compactFlags(PortalRenderSettings settings) {
        StringBuilder builder = new StringBuilder();
        appendFlag(builder, settings.renderSky(), "sky");
        appendFlag(builder, settings.renderTerrain(), "terrain");
        appendFlag(builder, settings.renderTranslucent(), "translucent");
        appendFlag(builder, settings.renderEntities(), "entities");
        appendFlag(builder, settings.renderBlockEntities(), "blockEntities");
        appendFlag(builder, settings.renderParticles(), "particles");
        return builder.length() == 0 ? "-" : builder.toString();
    }

    private static void appendFlag(StringBuilder builder, boolean enabled, String name) {
        if (!enabled) {
            return;
        }
        if (builder.length() > 0) {
            builder.append(',');
        }
        builder.append(name);
    }

    private static String compactId(ResourceLocation id) {
        return Skyesight.MODID.equals(id.getNamespace()) ? id.getPath() : id.toString();
    }

    private static String compactRemovedIds(List<ResourceLocation> ids) {
        return ids.stream()
                .map(DebugPortalStickManager::compactId)
                .collect(java.util.stream.Collectors.joining("/"));
    }

    private static String shortDimension(ResourceKey<Level> dimension) {
        return dimension.location().getPath();
    }

    private static String formatCompactVec(Vec3 vec) {
        return String.format(java.util.Locale.ROOT, "(%.2f,%.2f,%.2f)", vec.x(), vec.y(), vec.z());
    }

    private static String formatSize(float width, float height) {
        return formatFloat(width) + " x " + formatFloat(height);
    }

    private static String formatFloat(float value) {
        return String.format(java.util.Locale.ROOT, "%.1f", value);
    }

    private static Vec3 basis(PortalEndpoint endpoint, float x, float y, float z) {
        Vector3f vector = new Vector3f(x, y, z);
        vector.rotate(endpoint.rotation()).normalize();
        return new Vec3(vector.x(), vector.y(), vector.z());
    }

    private static String formatCorners(PortalEndpoint endpoint, Vec3 right, Vec3 up) {
        Vec3 halfRight = right.scale(endpoint.width() * 0.5D);
        Vec3 halfUp = up.scale(endpoint.height() * 0.5D);
        Vec3 center = endpoint.center();
        return "bl=" + formatVec(center.subtract(halfRight).subtract(halfUp))
                + ",br=" + formatVec(center.add(halfRight).subtract(halfUp))
                + ",tr=" + formatVec(center.add(halfRight).add(halfUp))
                + ",tl=" + formatVec(center.subtract(halfRight).add(halfUp));
    }

    private static String formatRotation(PortalEndpoint endpoint) {
        return String.format(
                java.util.Locale.ROOT,
                "%.4f,%.4f,%.4f,%.4f",
                endpoint.rotation().x,
                endpoint.rotation().y,
                endpoint.rotation().z,
                endpoint.rotation().w
        );
    }

    private static String yesNo(boolean value) {
        return value ? "yes" : "no";
    }

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!isDebugStick(player.getMainHandItem()) && !isDebugStick(player.getOffhandItem())) {
            return;
        }
        if (player.isShiftKeyDown()) {
            deleteRegisteredPair(player);
        } else {
            clear(player);
        }
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        for (StickState state : STATES.values()) {
            removeEndpointMarker(event.getServer(), state);
        }
        STATES.clear();
    }

    private static boolean isDebugStick(net.minecraft.world.item.ItemStack stack) {
        return stack.is(com.skyeshade.skyesight.SkyesightItems.DEBUG_PORTAL_STICK.get());
    }

    private static String formatVec(Vec3 vec) {
        return String.format(java.util.Locale.ROOT, "%.2f %.2f %.2f", vec.x(), vec.y(), vec.z());
    }

    private static final class StickState {
        private PortalEndpoint endpointA;
        private PortalEndpoint endpointB;
        private Direction clickedFaceA;
        private Direction clickedFaceB;
        private ResourceKey<Level> markerDimension;
        private UUID markerUuid;
        private long timestampMillis;
    }
}
