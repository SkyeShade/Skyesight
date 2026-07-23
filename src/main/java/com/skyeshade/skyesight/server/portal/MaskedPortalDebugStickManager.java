package com.skyeshade.skyesight.server.portal;

import com.skyeshade.skyesight.Skyesight;
import com.skyeshade.skyesight.api.PortalEndpoint;
import com.skyeshade.skyesight.api.PortalRegistrationResult;
import com.skyeshade.skyesight.api.PortalRenderSettings;
import com.skyeshade.skyesight.api.PortalStencilMask;
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

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@EventBusSubscriber(modid = Skyesight.MODID)
public final class MaskedPortalDebugStickManager {
    public static final String PORTAL_A_ID = "debug_masked_stick_portal_a";
    public static final String PORTAL_B_ID = "debug_masked_stick_portal_b";
    public static final ResourceLocation DEBUG_MASK_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Skyesight.MODID, "stencil/debug_mask");
    private static final String MARKER_TAG = "skyesight_debug_masked_portal_stick_marker";
    private static final float MASKED_PORTAL_WIDTH = 2.0F;
    private static final float MASKED_PORTAL_HEIGHT = 2.0F;
    private static final Map<UUID, StickState> STATES = new HashMap<>();

    private MaskedPortalDebugStickManager() {
    }

    public static void placeEndpoint(ServerPlayer player, BlockPos clickedPos, Direction clickedFace) {
        StickState state = STATES.computeIfAbsent(player.getUUID(), ignored -> new StickState());
        if (state.endpointA != null && state.endpointB != null) {
            player.sendSystemMessage(Component.literal("Masked debug portal pair already placed; left-click to start replacement, or sneak-left-click to delete it."));
            return;
        }

        PortalEndpoint endpoint = PortalEndpoint.fromBlockHit(
                state.endpointA == null ? "A" : "B",
                player.serverLevel().dimension(),
                clickedPos,
                clickedFace,
                player.getDirection(),
                MASKED_PORTAL_WIDTH,
                MASKED_PORTAL_HEIGHT
        );
        if (state.endpointA == null) {
            state.endpointA = endpoint;
            spawnEndpointMarker(player, state, endpoint);
            player.sendSystemMessage(Component.literal(
                    "Masked debug portal A set at "
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
        removeEndpointMarker(player.server, state);

        PortalRenderSettings settings = PortalRenderSettings.defaultsAutoStencil()
                .withStencilMask(PortalStencilMask.alphaBinary(DEBUG_MASK_TEXTURE));
        PortalRegistrationResult result = SkyesightPortalApi.registerPortalPair(
                PORTAL_A_ID,
                state.endpointA,
                PORTAL_B_ID,
                state.endpointB,
                settings,
                settings,
                true,
                true,
                "debug-masked-stick",
                true,
                false
        );
        if (result.success()) {
            logCompactRegisteredPair();
        }
        player.sendSystemMessage(Component.literal(
                result.success()
                        ? "Registered masked debug portal pair "
                        + PORTAL_A_ID
                        + " <-> "
                        + PORTAL_B_ID
                        + " size "
                        + formatSize(MASKED_PORTAL_WIDTH, MASKED_PORTAL_HEIGHT)
                        + " mask="
                        + DEBUG_MASK_TEXTURE
                        + "."
                        : "Masked debug portal registration failed: " + result.message()
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
                : PortalRegistrationResult.failure("no registered masked debug stick portal pair");
        player.sendSystemMessage(Component.literal(
                result.success()
                        ? "Masked debug portal pair disabled; cache retained. Right-click two blocks to replace."
                        : "Masked debug portal state cleared."
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
                        ? "Masked debug portal pair removed; cache cleared."
                        : "Masked debug portal state cleared."
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
            return "no masked debug stick endpoint set";
        }
        if (state.endpointB == null) {
            return "A=" + state.endpointA.dimension().location() + " " + formatVec(state.endpointA.center())
                    + " facing " + state.endpointA.facing().getName() + "; B unset";
        }
        return "A=" + state.endpointA.dimension().location() + " " + formatVec(state.endpointA.center())
                + " B=" + state.endpointB.dimension().location() + " " + formatVec(state.endpointB.center())
                + " size=" + formatSize(MASKED_PORTAL_WIDTH, MASKED_PORTAL_HEIGHT)
                + " mask=" + DEBUG_MASK_TEXTURE;
    }

    private static void logCompactRegisteredPair() {
        RegisteredPortalView viewA = SkyesightPortalApi.getPortal(PORTAL_A_ID);
        RegisteredPortalView viewB = SkyesightPortalApi.getPortal(PORTAL_B_ID);
        if (viewA == null || viewB == null) {
            Skyesight.LOGGER.warn(
                    "[Skyesight] Masked debug portal pair registered but lookup failed: {} present={} {} present={}",
                    PORTAL_A_ID,
                    viewA != null,
                    PORTAL_B_ID,
                    viewB != null
            );
            return;
        }
        Skyesight.LOGGER.info(
                "[Skyesight] Masked debug portal pair registered: {} <-> {} size={} mask={} refs={}/{} backface=false",
                PORTAL_A_ID,
                PORTAL_B_ID,
                formatSize(viewA.source().width(), viewA.source().height()),
                DEBUG_MASK_TEXTURE,
                viewA.renderSettings().stencilRef(),
                viewB.renderSettings().stencilRef()
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
        stand.setCustomName(Component.literal("masked debug stick A"));
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

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!isMaskedStick(player.getMainHandItem()) && !isMaskedStick(player.getOffhandItem())) {
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

    private static boolean isMaskedStick(net.minecraft.world.item.ItemStack stack) {
        return stack.is(com.skyeshade.skyesight.SkyesightItems.MASKED_PORTAL_DEBUG_STICK.get());
    }

    private static String formatVec(Vec3 vec) {
        return String.format(Locale.ROOT, "%.2f %.2f %.2f", vec.x(), vec.y(), vec.z());
    }

    private static String formatSize(float width, float height) {
        return String.format(Locale.ROOT, "%.1fx%.1f", width, height);
    }

    private static String compactRemovedIds(List<ResourceLocation> ids) {
        return ids.stream()
                .map(id -> Skyesight.MODID.equals(id.getNamespace()) ? id.getPath() : id.toString())
                .collect(java.util.stream.Collectors.joining("/"));
    }

    private static final class StickState {
        private PortalEndpoint endpointA;
        private PortalEndpoint endpointB;
        private ResourceKey<Level> markerDimension;
        private UUID markerUuid;
    }
}
