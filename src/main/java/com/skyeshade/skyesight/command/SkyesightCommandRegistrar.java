package com.skyeshade.skyesight.command;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.skyeshade.skyesight.Skyesight;
import com.skyeshade.skyesight.SkyesightDebugConfig;
import com.skyeshade.skyesight.SkyesightDebugPortalRegistrations;
import com.skyeshade.skyesight.SkyesightItems;
import com.skyeshade.skyesight.SkyesightNativeVisualEntityRoutingDebug;
import com.skyeshade.skyesight.api.PortalEndpoint;
import com.skyeshade.skyesight.api.PortalRegistrationResult;
import com.skyeshade.skyesight.api.RegisteredPortalView;
import com.skyeshade.skyesight.api.SkyesightPortalApi;
import com.skyeshade.skyesight.client.world.SkyesightMultipartEntityDebug;
import com.skyeshade.skyesight.client.world.SkyesightVisualParticleWatch;
import com.skyeshade.skyesight.server.PortalSimulationCoordinator;
import com.skyeshade.skyesight.server.portal.DebugPortalStickManager;
import com.skyeshade.skyesight.server.portal.MaskedPortalDebugStickManager;
import com.skyeshade.skyesight.server.portal.PortalPlayerQueryMixinTargetAudit;
import com.skyeshade.skyesight.server.portal.PortalProxyArmorStandDebugManager;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.slf4j.Logger;

import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

public final class SkyesightCommandRegistrar {
    private static final Logger LOGGER = Skyesight.LOGGER;

    private SkyesightCommandRegistrar() {}

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("skyesight")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("debug")
                                .then(Commands.literal("proxy-marker")
                                        .then(Commands.argument("enabled", BoolArgumentType.bool())
                                                .executes(context -> {
                                                    String status = SkyesightDebugConfig.setProxyMarker(BoolArgumentType.getBool(context, "enabled"));
                                                    context.getSource().sendSuccess(() -> Component.literal("[Skyesight] Debug proxy-marker: " + status), true);
                                                    return 1;
                                                })))
                                .then(Commands.literal("proxy-armor-stands")
                                        .then(Commands.argument("enabled", BoolArgumentType.bool())
                                                .executes(context -> {
                                                    boolean enabled = BoolArgumentType.getBool(context, "enabled");
                                                    String status = SkyesightDebugConfig.setProxyArmorStands(enabled);
                                                    if (!enabled) {
                                                        PortalProxyArmorStandDebugManager.removeAll(context.getSource().getServer());
                                                    }
                                                    context.getSource().sendSuccess(() -> Component.literal("[Skyesight] Debug proxy-armor-stands: " + status), true);
                                                    return 1;
                                                })))
                                .then(Commands.literal("portal-look-marker")
                                        .then(Commands.argument("enabled", BoolArgumentType.bool())
                                                .executes(context -> {
                                                    String status = SkyesightDebugConfig.setPortalLookMarkers(BoolArgumentType.getBool(context, "enabled"));
                                                    context.getSource().sendSuccess(() -> Component.literal("[Skyesight] Debug portal-look-marker: " + status), true);
                                                    return 1;
                                                })))
                                .then(Commands.literal("entity-dim-context")
                                        .then(Commands.literal("on")
                                                .executes(context -> {
                                                    String status = SkyesightDebugConfig.setEntityDimensionContext(true);
                                                    context.getSource().sendSuccess(() -> Component.literal("[Skyesight] Debug entity-dim-context: " + status), true);
                                                    return 1;
                                                }))
                                        .then(Commands.literal("off")
                                                .executes(context -> {
                                                    String status = SkyesightDebugConfig.setEntityDimensionContext(false);
                                                    context.getSource().sendSuccess(() -> Component.literal("[Skyesight] Debug entity-dim-context: " + status), true);
                                                    return 1;
                                                })))
                                .then(Commands.literal("portal-entity-pool")
                                        .then(Commands.literal("on")
                                                .executes(context -> {
                                                    String status = SkyesightNativeVisualEntityRoutingDebug.setEnabled(true);
                                                    context.getSource().sendSuccess(() -> Component.literal("[Skyesight] Debug portal-entity-pool: " + status), true);
                                                    return 1;
                                                }))
                                        .then(Commands.literal("off")
                                                .executes(context -> {
                                                    String status = SkyesightNativeVisualEntityRoutingDebug.setEnabled(false);
                                                    context.getSource().sendSuccess(() -> Component.literal("[Skyesight] Debug portal-entity-pool: " + status), true);
                                                    return 1;
                                                }))
                                        .then(Commands.literal("status")
                                                .executes(context -> {
                                                    String status = SkyesightNativeVisualEntityRoutingDebug.status();
                                                    context.getSource().sendSuccess(() -> Component.literal("[Skyesight] Debug portal-entity-pool: " + status), false);
                                                    LOGGER.info("[Skyesight] Debug portal-entity-pool: {}", status);
                                                    return 1;
                                                })))
                                .then(Commands.literal("multipart-entities")
                                        .then(Commands.literal("status")
                                                .executes(context -> {
                                                    String status = SkyesightMultipartEntityDebug.status();
                                                    context.getSource().sendSuccess(() -> Component.literal("[Skyesight] Debug multipart-entities: " + status), false);
                                                    LOGGER.info("[Skyesight] Debug multipart-entities: {}", status);
                                                    return 1;
                                                })))
                                .then(Commands.literal("particle-watch-block")
                                        .then(Commands.literal("clear")
                                                .executes(context -> {
                                                    String status = SkyesightVisualParticleWatch.clear();
                                                    context.getSource().sendSuccess(() -> Component.literal("[Skyesight] Debug particle-watch-block: " + status), true);
                                                    LOGGER.info("[Skyesight] Debug particle-watch-block: {}", status);
                                                    return 1;
                                                }))
                                        .then(Commands.argument("viewId", StringArgumentType.word())
                                                .then(Commands.argument("x", IntegerArgumentType.integer())
                                                        .then(Commands.argument("y", IntegerArgumentType.integer())
                                                                .then(Commands.argument("z", IntegerArgumentType.integer())
                                                                        .executes(context -> {
                                                                            ResourceLocation viewId = ResourceLocation.parse(StringArgumentType.getString(context, "viewId"));
                                                                            int x = IntegerArgumentType.getInteger(context, "x");
                                                                            int y = IntegerArgumentType.getInteger(context, "y");
                                                                            int z = IntegerArgumentType.getInteger(context, "z");
                                                                            String status = SkyesightVisualParticleWatch.set(viewId, new BlockPos(x, y, z));
                                                                            context.getSource().sendSuccess(() -> Component.literal("[Skyesight] Debug particle-watch-block: " + status), true);
                                                                            LOGGER.info("[Skyesight] Debug particle-watch-block: {}", status);
                                                                            return 1;
                                                                        }))))))
                                .then(Commands.literal("logs")
                                        .then(Commands.literal("quiet")
                                                .executes(context -> {
                                                    String status = SkyesightDebugConfig.quiet();
                                                    context.getSource().sendSuccess(() -> Component.literal("[Skyesight] Debug logs quiet: " + status), true);
                                                    LOGGER.info("[Skyesight] Debug logs quiet: {}", status);
                                                    return 1;
                                                }))
                                        .then(Commands.literal("status")
                                                .executes(context -> {
                                                    String status = SkyesightDebugConfig.status();
                                                    context.getSource().sendSuccess(() -> Component.literal("[Skyesight] Debug logs status: " + status), false);
                                                    return 1;
                                                }))
                                        .then(Commands.literal("verbose-spawn")
                                                .then(Commands.argument("enabled", BoolArgumentType.bool())
                                                        .executes(context -> {
                                                            String status = SkyesightDebugConfig.setVerboseSpawn(BoolArgumentType.getBool(context, "enabled"));
                                                            context.getSource().sendSuccess(() -> Component.literal("[Skyesight] Debug logs verbose-spawn: " + status), true);
                                                            return 1;
                                                        })))
                                        .then(Commands.literal("verbose-entity")
                                                .then(Commands.argument("enabled", BoolArgumentType.bool())
                                                        .executes(context -> {
                                                            String status = SkyesightDebugConfig.setVerboseEntity(BoolArgumentType.getBool(context, "enabled"));
                                                            context.getSource().sendSuccess(() -> Component.literal("[Skyesight] Debug logs verbose-entity: " + status), true);
                                                            return 1;
                                                        })))
                                        .then(Commands.literal("verbose-render")
                                                .then(Commands.argument("enabled", BoolArgumentType.bool())
                                                        .executes(context -> {
                                                            String status = SkyesightDebugConfig.setVerboseRender(BoolArgumentType.getBool(context, "enabled"));
                                                            context.getSource().sendSuccess(() -> Component.literal("[Skyesight] Debug logs verbose-render: " + status), true);
                                                            return 1;
                                                        })))
                                        .then(Commands.literal("source-map")
                                                .then(Commands.argument("enabled", BoolArgumentType.bool())
                                                        .executes(context -> {
                                                            String status = SkyesightDebugConfig.setSourceMap(BoolArgumentType.getBool(context, "enabled"));
                                                            context.getSource().sendSuccess(() -> Component.literal("[Skyesight] Debug logs source-map: " + status), true);
                                                            return 1;
                                                        })))
                                        .then(Commands.literal("packet-debug")
                                                .then(Commands.argument("enabled", BoolArgumentType.bool())
                                                        .executes(context -> {
                                                            String status = SkyesightDebugConfig.setPacketDebug(BoolArgumentType.getBool(context, "enabled"));
                                                            context.getSource().sendSuccess(() -> Component.literal("[Skyesight] Debug logs packet-debug: " + status), true);
                                                            return 1;
                                                        })))
                                        .then(Commands.literal("verbose-proximity")
                                                .then(Commands.argument("enabled", BoolArgumentType.bool())
                                                        .executes(context -> {
                                                            String status = SkyesightDebugConfig.setVerboseProximity(BoolArgumentType.getBool(context, "enabled"));
                                                            context.getSource().sendSuccess(() -> Component.literal("[Skyesight] Debug logs verbose-proximity: " + status), true);
                                                            return 1;
                                                        })))
                                        .then(Commands.literal("watch-debug")
                                                .then(Commands.argument("enabled", BoolArgumentType.bool())
                                                        .executes(context -> {
                                                            String status = SkyesightDebugConfig.setWatchDebug(BoolArgumentType.getBool(context, "enabled"));
                                                            context.getSource().sendSuccess(() -> Component.literal("[Skyesight] Debug logs watch-debug: " + status), true);
                                                            return 1;
                                                        })))
                                        .then(Commands.literal("terrain-audit")
                                                .then(Commands.argument("enabled", BoolArgumentType.bool())
                                                        .executes(context -> {
                                                            String status = SkyesightDebugConfig.setTerrainAudit(BoolArgumentType.getBool(context, "enabled"));
                                                            context.getSource().sendSuccess(() -> Component.literal("[Skyesight] Debug logs terrain-audit: " + status), true);
                                                            return 1;
                                                        })))
                                        .then(Commands.literal("sodium-renderer-audit")
                                                .then(Commands.argument("enabled", BoolArgumentType.bool())
                                                        .executes(context -> {
                                                            String status = SkyesightDebugConfig.setSodiumRendererAudit(BoolArgumentType.getBool(context, "enabled"));
                                                            context.getSource().sendSuccess(() -> Component.literal("[Skyesight] Debug logs sodium-renderer-audit: " + status), true);
                                                            return 1;
                                                        })))
                                        .then(Commands.literal("sky-capture-audit")
                                                .then(Commands.argument("enabled", BoolArgumentType.bool())
                                                        .executes(context -> {
                                                            String status = SkyesightDebugConfig.setSkyCaptureAudit(BoolArgumentType.getBool(context, "enabled"));
                                                            context.getSource().sendSuccess(() -> Component.literal("[Skyesight] Debug logs sky-capture-audit: " + status), true);
                                                            return 1;
                                                        })))
                                        .then(Commands.literal("render-target-audit")
                                                .then(Commands.argument("enabled", BoolArgumentType.bool())
                                                        .executes(context -> {
                                                            String status = SkyesightDebugConfig.setRenderTargetAudit(BoolArgumentType.getBool(context, "enabled"));
                                                            context.getSource().sendSuccess(() -> Component.literal("[Skyesight] Debug logs render-target-audit: " + status), true);
                                                            return 1;
                                                        })))
                                        .then(Commands.literal("render-culling-audit")
                                                .then(Commands.argument("enabled", BoolArgumentType.bool())
                                                        .executes(context -> {
                                                            String status = SkyesightDebugConfig.setRenderCullingAudit(BoolArgumentType.getBool(context, "enabled"));
                                                            context.getSource().sendSuccess(() -> Component.literal("[Skyesight] Debug logs render-culling-audit: " + status), true);
                                                            return 1;
                                                        })))
                                        .then(Commands.literal("render-perf-audit")
                                                .then(Commands.argument("enabled", BoolArgumentType.bool())
                                                        .executes(context -> {
                                                            String status = SkyesightDebugConfig.setRenderPerfAudit(BoolArgumentType.getBool(context, "enabled"));
                                                            context.getSource().sendSuccess(() -> Component.literal("[Skyesight] Debug logs render-perf-audit: " + status), true);
                                                            return 1;
                                                        })))
                                        .then(Commands.literal("debug-stick-audit")
                                                .then(Commands.argument("enabled", BoolArgumentType.bool())
                                                        .executes(context -> {
                                                            String status = SkyesightDebugConfig.setDebugStickAudit(BoolArgumentType.getBool(context, "enabled"));
                                                            context.getSource().sendSuccess(() -> Component.literal("[Skyesight] Debug logs debug-stick-audit: " + status), true);
                                                            return 1;
                                                        })))
                                        .then(Commands.literal("portal-api-audit")
                                                .then(Commands.argument("enabled", BoolArgumentType.bool())
                                                        .executes(context -> {
                                                            String status = SkyesightDebugConfig.setPortalApiAudit(BoolArgumentType.getBool(context, "enabled"));
                                                            context.getSource().sendSuccess(() -> Component.literal("[Skyesight] Debug logs portal-api-audit: " + status), true);
                                                            return 1;
                                                        })))
                                        .then(Commands.literal("lifecycle-debug")
                                                .then(Commands.argument("enabled", BoolArgumentType.bool())
                                                        .executes(context -> {
                                                            String status = SkyesightDebugConfig.setLifecycleDebug(BoolArgumentType.getBool(context, "enabled"));
                                                            context.getSource().sendSuccess(() -> Component.literal("[Skyesight] Debug logs lifecycle-debug: " + status), true);
                                                            return 1;
                                                        }))))));
        event.getDispatcher().register(
                Commands.literal("skyesight")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("portal")
                                .then(Commands.literal("debug-stick")
                                        .then(Commands.literal("give")
                                                .executes(context -> {
                                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                                    ItemStack stack = new ItemStack(SkyesightItems.DEBUG_PORTAL_STICK.get());
                                                    boolean added = player.getInventory().add(stack);
                                                    if (!added) {
                                                        player.drop(stack, false);
                                                    }
                                                    context.getSource().sendSuccess(() -> Component.literal("[Skyesight] Portal debug-stick: gave debug portal stick."), true);
                                                    return 1;
                                                }))
                                        .then(Commands.literal("clear")
                                                .executes(context -> {
                                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                                    DebugPortalStickManager.clear(player);
                                                    return 1;
                                                }))
                                        .then(Commands.literal("status")
                                                .executes(context -> {
                                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                                    String status = DebugPortalStickManager.status(player);
                                                    context.getSource().sendSuccess(() -> Component.literal("[Skyesight] Portal debug-stick: " + status), false);
                                                    return 1;
                                                }))
                                        .then(Commands.literal("size")
                                                .executes(context -> {
                                                    String status = DebugPortalStickManager.sizeStatus();
                                                    context.getSource().sendSuccess(() -> Component.literal("[Skyesight] Debug stick portal " + status), false);
                                                    return 1;
                                                })
                                                .then(Commands.argument("width", FloatArgumentType.floatArg(0.001F, PortalEndpoint.MAX_SIZE))
                                                        .then(Commands.argument("height", FloatArgumentType.floatArg(0.001F, PortalEndpoint.MAX_SIZE))
                                                                .executes(context -> {
                                                                    float width = FloatArgumentType.getFloat(context, "width");
                                                                    float height = FloatArgumentType.getFloat(context, "height");
                                                                    String status = DebugPortalStickManager.setSize(width, height);
                                                                    context.getSource().sendSuccess(() -> Component.literal("[Skyesight] Debug stick portal " + status), true);
                                                                    return 1;
                                                                }))))
                                        .then(Commands.literal("width")
                                                .then(Commands.argument("width", FloatArgumentType.floatArg(0.001F, PortalEndpoint.MAX_SIZE))
                                                        .executes(context -> {
                                                            float width = FloatArgumentType.getFloat(context, "width");
                                                            String status = DebugPortalStickManager.setWidth(width);
                                                            context.getSource().sendSuccess(() -> Component.literal("[Skyesight] Debug stick portal " + status), true);
                                                            return 1;
                                                        })))
                                        .then(Commands.literal("height")
                                                .then(Commands.argument("height", FloatArgumentType.floatArg(0.001F, PortalEndpoint.MAX_SIZE))
                                                        .executes(context -> {
                                                            float height = FloatArgumentType.getFloat(context, "height");
                                                            String status = DebugPortalStickManager.setHeight(height);
                                                            context.getSource().sendSuccess(() -> Component.literal("[Skyesight] Debug stick portal " + status), true);
                                                            return 1;
                                                        })))
                                        .then(Commands.literal("reset-size")
                                                .executes(context -> {
                                                    String status = DebugPortalStickManager.resetSize();
                                                    context.getSource().sendSuccess(() -> Component.literal("[Skyesight] Debug stick portal " + status), true);
                                                    return 1;
                                                })))
                                .then(Commands.literal("masked-debug-stick")
                                        .then(Commands.literal("give")
                                                .executes(context -> {
                                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                                    ItemStack stack = new ItemStack(SkyesightItems.MASKED_PORTAL_DEBUG_STICK.get());
                                                    boolean added = player.getInventory().add(stack);
                                                    if (!added) {
                                                        player.drop(stack, false);
                                                    }
                                                    context.getSource().sendSuccess(() -> Component.literal("[Skyesight] Portal masked-debug-stick: gave masked portal debug stick."), true);
                                                    return 1;
                                                }))
                                        .then(Commands.literal("clear")
                                                .executes(context -> {
                                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                                    MaskedPortalDebugStickManager.clear(player);
                                                    return 1;
                                                }))
                                        .then(Commands.literal("status")
                                                .executes(context -> {
                                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                                    String status = MaskedPortalDebugStickManager.status(player);
                                                    context.getSource().sendSuccess(() -> Component.literal("[Skyesight] Portal masked-debug-stick: " + status), false);
                                                    return 1;
                                                })))
                                .then(Commands.literal("cleanup")
                                        .then(Commands.literal("spawned")
                                                .executes(context -> {
                                                    String result = PortalSimulationCoordinator.cleanupPortalSpawned(context.getSource().getServer());
                                                    context.getSource().sendSuccess(() -> Component.literal("[Skyesight] Portal cleanup spawned: " + result), true);
                                                    LOGGER.info("[Skyesight] Portal cleanup spawned: {}", result);
                                                    return 1;
                                                }))
                                        .then(Commands.literal("region")
                                                .then(Commands.argument("viewId", StringArgumentType.word())
                                                        .then(Commands.literal("hostile")
                                                                .executes(context -> {
                                                                    ResourceLocation viewId = ResourceLocation.parse(StringArgumentType.getString(context, "viewId"));
                                                                    String result = PortalSimulationCoordinator.cleanupActiveRegionHostiles(context.getSource().getServer(), viewId);
                                                                    context.getSource().sendSuccess(() -> Component.literal("[Skyesight] Portal cleanup region hostile: " + result), true);
                                                                    LOGGER.info("[Skyesight] Portal cleanup region hostile: {}", result);
                                                                    return 1;
                                                                }))
                                                        .then(Commands.literal("items")
                                                                .executes(context -> {
                                                                    ResourceLocation viewId = ResourceLocation.parse(StringArgumentType.getString(context, "viewId"));
                                                                    String result = PortalSimulationCoordinator.cleanupActiveRegionItems(context.getSource().getServer(), viewId);
                                                                    context.getSource().sendSuccess(() -> Component.literal("[Skyesight] Portal cleanup region items: " + result), true);
                                                                    LOGGER.info("[Skyesight] Portal cleanup region items: {}", result);
                                                                    return 1;
                                                                })))))));
        event.getDispatcher().register(
                Commands.literal("skyesight")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("portal")
                                .then(Commands.literal("spawning")
                                        .then(Commands.literal("enable")
                                                .executes(context -> {
                                                    MinecraftServer server = context.getSource().getServer();
                                                    PortalSimulationCoordinator.setPortalNaturalSpawningRuntimeEnabled(true);
                                                    String status = PortalSimulationCoordinator.portalNaturalSpawningStatus(server);
                                                    context.getSource().sendSuccess(() -> Component.literal("[Skyesight] Portal spawning enabled: " + status), true);
                                                    LOGGER.info("[Skyesight] Portal spawning enabled: {}", status);
                                                    return 1;
                                                }))
                                        .then(Commands.literal("disable")
                                                .executes(context -> {
                                                    MinecraftServer server = context.getSource().getServer();
                                                    PortalSimulationCoordinator.setPortalNaturalSpawningRuntimeEnabled(false);
                                                    String status = PortalSimulationCoordinator.portalNaturalSpawningStatus(server);
                                                    context.getSource().sendSuccess(() -> Component.literal("[Skyesight] Portal spawning disabled: " + status), true);
                                                    LOGGER.info("[Skyesight] Portal spawning disabled: {}", status);
                                                    return 1;
                                                }))
                                        .then(Commands.literal("status")
                                                .executes(context -> {
                                                    MinecraftServer server = context.getSource().getServer();
                                                    String status = PortalSimulationCoordinator.portalNaturalSpawningStatus(server);
                                                    context.getSource().sendSuccess(() -> Component.literal("[Skyesight] Portal spawning status: " + status), false);
                                                    LOGGER.info("[Skyesight] Portal spawning status: {}", status);
                                                    return 1;
                                                }))
                                        .then(Commands.literal("clear-pauses")
                                                .executes(context -> {
                                                    String status = PortalSimulationCoordinator.clearPortalSpawnPauses();
                                                    context.getSource().sendSuccess(() -> Component.literal("[Skyesight] Portal spawning pauses cleared: " + status), true);
                                                    LOGGER.info("[Skyesight] Portal spawning pauses cleared: {}", status);
                                                    return 1;
                                                }))
                                        .then(Commands.literal("set-live-cap")
                                                .then(Commands.argument("value", IntegerArgumentType.integer(0, 128))
                                                        .executes(context -> {
                                                            int value = IntegerArgumentType.getInteger(context, "value");
                                                            String status = PortalSimulationCoordinator.setPortalNaturalSpawningLiveCapOverride(value);
                                                            context.getSource().sendSuccess(() -> Component.literal("[Skyesight] Portal spawning live cap: " + status), true);
                                                            LOGGER.info("[Skyesight] Portal spawning live cap: {}", status);
                                                            return 1;
                                                        })))
                                        .then(Commands.literal("set-dim-cap")
                                                .then(Commands.argument("value", IntegerArgumentType.integer(0, 256))
                                                        .executes(context -> {
                                                            int value = IntegerArgumentType.getInteger(context, "value");
                                                            String status = PortalSimulationCoordinator.setPortalNaturalSpawningDimCapOverride(value);
                                                            context.getSource().sendSuccess(() -> Component.literal("[Skyesight] Portal spawning dim cap: " + status), true);
                                                            LOGGER.info("[Skyesight] Portal spawning dim cap: {}", status);
                                                            return 1;
                                                        })))
                                        .then(Commands.literal("set-chunks-per-view")
                                                .then(Commands.argument("value", IntegerArgumentType.integer(0, 81))
                                                        .executes(context -> {
                                                            int value = IntegerArgumentType.getInteger(context, "value");
                                                            String status = PortalSimulationCoordinator.setPortalNaturalSpawningChunksPerViewOverride(value);
                                                            context.getSource().sendSuccess(() -> Component.literal("[Skyesight] Portal spawning chunks per view: " + status), true);
                                                            LOGGER.info("[Skyesight] Portal spawning chunks per view: {}", status);
                                                            return 1;
                                                        })))
                                        .then(Commands.literal("force-center")
                                                .then(Commands.argument("enabled", BoolArgumentType.bool())
                                                        .executes(context -> {
                                                            boolean enabled = BoolArgumentType.getBool(context, "enabled");
                                                            String status = PortalSimulationCoordinator.setPortalNaturalSpawningForceCenterChunk(enabled);
                                                            context.getSource().sendSuccess(() -> Component.literal("[Skyesight] Portal spawning force center: " + status), true);
                                                            LOGGER.info("[Skyesight] Portal spawning force center: {}", status);
                                                            return 1;
                                                        })))
                                        .then(Commands.literal("include-near-same-dim")
                                                .then(Commands.argument("enabled", BoolArgumentType.bool())
                                                        .executes(context -> {
                                                            boolean enabled = BoolArgumentType.getBool(context, "enabled");
                                                            String status = PortalSimulationCoordinator.setPortalNaturalSpawningIncludeNearSameDim(enabled);
                                                            context.getSource().sendSuccess(() -> Component.literal("[Skyesight] Portal spawning include near same-dim: " + status), true);
                                                            LOGGER.info("[Skyesight] Portal spawning include near same-dim: {}", status);
                                                            return 1;
                                                        })))
                                        )));
        event.getDispatcher().register(
                Commands.literal("skyesight")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("portal")
                                .then(Commands.literal("api")
                                        .then(Commands.literal("list")
                                                .executes(context -> {
                                                    String status = portalApiList();
                                                    context.getSource().sendSuccess(() -> Component.literal("[Skyesight] Portal API: " + status), false);
                                                    return 1;
                                                }))
                                        .then(Commands.literal("remove")
                                                .then(Commands.argument("id", StringArgumentType.word())
                                                        .executes(context -> {
                                                            String id = StringArgumentType.getString(context, "id");
                                                            boolean removed = SkyesightPortalApi.removePortal(id);
                                                            String status = removed ? "removed " + id : "not found " + id;
                                                            context.getSource().sendSuccess(() -> Component.literal("[Skyesight] Portal API: " + status), true);
                                                            return removed ? 1 : 0;
                                                        })))
                                        .then(Commands.literal("remove-pair")
                                                .then(Commands.argument("idA", StringArgumentType.word())
                                                        .then(Commands.argument("idB", StringArgumentType.word())
                                                                .executes(context -> {
                                                                    String idA = StringArgumentType.getString(context, "idA");
                                                                    String idB = StringArgumentType.getString(context, "idB");
                                                                    boolean removed = SkyesightPortalApi.removePortalPair(idA, idB);
                                                                    String status = removed ? "removed pair " + idA + "/" + idB : "pair not found " + idA + "/" + idB;
                                                                    context.getSource().sendSuccess(() -> Component.literal("[Skyesight] Portal API: " + status), true);
                                                                    return removed ? 1 : 0;
                                                                }))))
                                        .then(Commands.literal("restore-defaults")
                                                .executes(context -> {
                                                    int registered = SkyesightDebugPortalRegistrations.restoreDefaults();
                                                    String status = "restored default debug portals; registered=" + registered;
                                                    context.getSource().sendSuccess(() -> Component.literal("[Skyesight] Portal API: " + status), true);
                                                    return 1;
                                                }))
                                        .then(Commands.literal("clear-defaults")
                                                .executes(context -> {
                                                    int removed = SkyesightDebugPortalRegistrations.clearDefaultsForSession();
                                                    String status = "cleared default debug portals; removed=" + removed;
                                                    context.getSource().sendSuccess(() -> Component.literal("[Skyesight] Portal API: " + status), true);
                                                    return 1;
                                                }))
                                        .then(Commands.literal("add-oneway")
                                                .then(Commands.argument("spec", StringArgumentType.greedyString())
                                                        .executes(context -> portalApiAddOneWaySpec(
                                                                context.getSource()::sendSuccess,
                                                                StringArgumentType.getString(context, "spec")
                                                        ))))
                                        .then(Commands.literal("add-oneway-sized")
                                                .then(Commands.argument("spec", StringArgumentType.greedyString())
                                                        .executes(context -> portalApiAddOneWaySizedSpec(
                                                                context.getSource()::sendSuccess,
                                                                StringArgumentType.getString(context, "spec")
                                                        ))))
                                        .then(Commands.literal("add-pair")
                                                .then(Commands.argument("spec", StringArgumentType.greedyString())
                                                        .executes(context -> portalApiAddPairSpec(
                                                                context.getSource()::sendSuccess,
                                                                StringArgumentType.getString(context, "spec")
                                                        ))))
                                        .then(Commands.literal("add-pair-sized")
                                                .then(Commands.argument("spec", StringArgumentType.greedyString())
                                                        .executes(context -> portalApiAddPairSizedSpec(
                                                                context.getSource()::sendSuccess,
                                                                StringArgumentType.getString(context, "spec")
                                                        ))))
                                        .then(Commands.literal("place-debug-pair")
                                                .then(Commands.argument("idA", StringArgumentType.word())
                                                        .then(Commands.argument("idB", StringArgumentType.word())
                                                                .executes(context -> {
                                                                    var source = context.getSource();
                                                                    Direction facingA = facingFromYaw(source.getRotation().y);
                                                                    Direction facingB = facingA.getOpposite();
                                                                    Vec3 posA = source.getPosition();
                                                                    Vec3 posB = posA.add(facingA.getStepX() * 3.0D, facingA.getStepY() * 3.0D, facingA.getStepZ() * 3.0D);
                                                                    PortalRegistrationResult result = SkyesightPortalApi.registerPortalPair(
                                                                            StringArgumentType.getString(context, "idA"),
                                                                            PortalEndpoint.of("A", source.getLevel().dimension(), posA, facingA, 1.0F, 2.0F),
                                                                            StringArgumentType.getString(context, "idB"),
                                                                            PortalEndpoint.of("B", source.getLevel().dimension(), posB, facingB, 1.0F, 2.0F),
                                                                            true,
                                                                            true,
                                                                            "command",
                                                                            true,
                                                                            false
                                                                    );
                                                                    source.sendSuccess(() -> Component.literal("[Skyesight] Portal API: " + result.message()), true);
                                                                    return result.success() ? 1 : 0;
                                                                })))))));
    }

    private interface CommandSuccessSender {
        void send(Supplier<Component> component, boolean broadcast);
    }

    private static int portalApiAddOneWaySpec(CommandSuccessSender sender, String spec) {
        String[] parts = spec.trim().split("\\s+");
        if (parts.length != 11 && parts.length != 12) {
            sender.send(() -> Component.literal("[Skyesight] Portal API add-oneway usage: <id> <sourceDim> <sourceX> <sourceY> <sourceZ> <sourceFacing> <targetDim> <targetX> <targetY> <targetZ> <targetFacing> [renderBackface]"), false);
            return 0;
        }
        boolean renderBackface = parseOptionalBoolean(parts, 11, false);
        return portalApiAddOneWay(
                sender,
                parts[0],
                parts[1],
                Double.parseDouble(parts[2]),
                Double.parseDouble(parts[3]),
                Double.parseDouble(parts[4]),
                parts[5],
                parts[6],
                Double.parseDouble(parts[7]),
                Double.parseDouble(parts[8]),
                Double.parseDouble(parts[9]),
                parts[10],
                renderBackface
        );
    }

    private static int portalApiAddPairSpec(CommandSuccessSender sender, String spec) {
        String[] parts = spec.trim().split("\\s+");
        if (parts.length != 12 && parts.length != 13) {
            sender.send(() -> Component.literal("[Skyesight] Portal API add-pair usage: <idA> <dimA> <xA> <yA> <zA> <facingA> <idB> <dimB> <xB> <yB> <zB> <facingB> [renderBackface]"), false);
            return 0;
        }
        boolean renderBackface = parseOptionalBoolean(parts, 12, false);
        return portalApiAddPair(
                sender,
                parts[0],
                parts[1],
                Double.parseDouble(parts[2]),
                Double.parseDouble(parts[3]),
                Double.parseDouble(parts[4]),
                parts[5],
                parts[6],
                parts[7],
                Double.parseDouble(parts[8]),
                Double.parseDouble(parts[9]),
                Double.parseDouble(parts[10]),
                parts[11],
                renderBackface
        );
    }

    private static int portalApiAddOneWaySizedSpec(CommandSuccessSender sender, String spec) {
        String[] parts = spec.trim().split("\\s+");
        if (parts.length != 13 && parts.length != 14) {
            sender.send(() -> Component.literal("[Skyesight] Portal API add-oneway-sized usage: <id> <sourceDim> <sourceX> <sourceY> <sourceZ> <sourceFacing> <targetDim> <targetX> <targetY> <targetZ> <targetFacing> <width> <height> [renderBackface]"), false);
            return 0;
        }
        float width = Float.parseFloat(parts[11]);
        float height = Float.parseFloat(parts[12]);
        PortalEndpoint.validateSize(width, height);
        boolean renderBackface = parseOptionalBoolean(parts, 13, false);
        return portalApiAddOneWay(
                sender,
                parts[0],
                parts[1],
                Double.parseDouble(parts[2]),
                Double.parseDouble(parts[3]),
                Double.parseDouble(parts[4]),
                parts[5],
                parts[6],
                Double.parseDouble(parts[7]),
                Double.parseDouble(parts[8]),
                Double.parseDouble(parts[9]),
                parts[10],
                width,
                height,
                renderBackface
        );
    }

    private static int portalApiAddPairSizedSpec(CommandSuccessSender sender, String spec) {
        String[] parts = spec.trim().split("\\s+");
        if (parts.length != 14 && parts.length != 15) {
            sender.send(() -> Component.literal("[Skyesight] Portal API add-pair-sized usage: <idA> <dimA> <xA> <yA> <zA> <facingA> <idB> <dimB> <xB> <yB> <zB> <facingB> <width> <height> [renderBackface]"), false);
            return 0;
        }
        float width = Float.parseFloat(parts[12]);
        float height = Float.parseFloat(parts[13]);
        PortalEndpoint.validateSize(width, height);
        boolean renderBackface = parseOptionalBoolean(parts, 14, false);
        return portalApiAddPair(
                sender,
                parts[0],
                parts[1],
                Double.parseDouble(parts[2]),
                Double.parseDouble(parts[3]),
                Double.parseDouble(parts[4]),
                parts[5],
                parts[6],
                parts[7],
                Double.parseDouble(parts[8]),
                Double.parseDouble(parts[9]),
                Double.parseDouble(parts[10]),
                parts[11],
                width,
                height,
                renderBackface
        );
    }

    private static int portalApiAddOneWay(
            CommandSuccessSender sender,
            String id,
            String sourceDim,
            double sourceX,
            double sourceY,
            double sourceZ,
            String sourceFacing,
            String targetDim,
            double targetX,
            double targetY,
            double targetZ,
            String targetFacing,
            boolean renderBackface
    ) {
        return portalApiAddOneWay(
                sender,
                id,
                sourceDim,
                sourceX,
                sourceY,
                sourceZ,
                sourceFacing,
                targetDim,
                targetX,
                targetY,
                targetZ,
                targetFacing,
                PortalEndpoint.DEFAULT_WIDTH,
                PortalEndpoint.DEFAULT_HEIGHT,
                renderBackface
        );
    }

    private static int portalApiAddOneWay(
            CommandSuccessSender sender,
            String id,
            String sourceDim,
            double sourceX,
            double sourceY,
            double sourceZ,
            String sourceFacing,
            String targetDim,
            double targetX,
            double targetY,
            double targetZ,
            String targetFacing,
            float width,
            float height,
            boolean renderBackface
    ) {
        PortalEndpoint.validateSize(width, height);
        PortalEndpoint source = PortalEndpoint.of("source", parseDimension(sourceDim), new Vec3(sourceX, sourceY, sourceZ), parseFacing(sourceFacing), width, height);
        PortalEndpoint target = PortalEndpoint.of("target", parseDimension(targetDim), new Vec3(targetX, targetY, targetZ), parseFacing(targetFacing), width, height);
        PortalRegistrationResult result = SkyesightPortalApi.registerPortal(id, source, target, true, null, "command", true, renderBackface);
        sender.send(() -> Component.literal("[Skyesight] Portal API: " + result.message()), true);
        return result.success() ? 1 : 0;
    }

    private static int portalApiAddPair(
            CommandSuccessSender sender,
            String idA,
            String dimA,
            double xA,
            double yA,
            double zA,
            String facingA,
            String idB,
            String dimB,
            double xB,
            double yB,
            double zB,
            String facingB,
            boolean renderBackface
    ) {
        return portalApiAddPair(
                sender,
                idA,
                dimA,
                xA,
                yA,
                zA,
                facingA,
                idB,
                dimB,
                xB,
                yB,
                zB,
                facingB,
                PortalEndpoint.DEFAULT_WIDTH,
                PortalEndpoint.DEFAULT_HEIGHT,
                renderBackface
        );
    }

    private static int portalApiAddPair(
            CommandSuccessSender sender,
            String idA,
            String dimA,
            double xA,
            double yA,
            double zA,
            String facingA,
            String idB,
            String dimB,
            double xB,
            double yB,
            double zB,
            String facingB,
            float width,
            float height,
            boolean renderBackface
    ) {
        PortalEndpoint.validateSize(width, height);
        PortalEndpoint a = PortalEndpoint.of("A", parseDimension(dimA), new Vec3(xA, yA, zA), parseFacing(facingA), width, height);
        PortalEndpoint b = PortalEndpoint.of("B", parseDimension(dimB), new Vec3(xB, yB, zB), parseFacing(facingB), width, height);
        PortalRegistrationResult result = SkyesightPortalApi.registerPortalPair(idA, a, idB, b, true, true, "command", true, renderBackface);
        sender.send(() -> Component.literal("[Skyesight] Portal API: " + result.message()), true);
        return result.success() ? 1 : 0;
    }

    private static boolean parseOptionalBoolean(String[] parts, int index, boolean defaultValue) {
        if (parts.length <= index) {
            return defaultValue;
        }
        if ("true".equalsIgnoreCase(parts[index])) {
            return true;
        }
        if ("false".equalsIgnoreCase(parts[index])) {
            return false;
        }
        throw new IllegalArgumentException("Expected boolean true/false for renderBackface: " + parts[index]);
    }

    private static ResourceKey<Level> parseDimension(String value) {
        ResourceLocation id = value.contains(":") ? ResourceLocation.parse(value) : ResourceLocation.withDefaultNamespace(value);
        return ResourceKey.create(Registries.DIMENSION, id);
    }

    private static Direction parseFacing(String value) {
        Direction direction = Direction.byName(value);
        if (direction == null) {
            throw new IllegalArgumentException("Invalid portal facing: " + value);
        }
        return direction;
    }

    private static Direction facingFromYaw(float yaw) {
        return yaw > 135.0F || yaw <= -135.0F
                ? Direction.NORTH
                : yaw > 45.0F
                ? Direction.WEST
                : yaw <= -45.0F
                ? Direction.EAST
                : Direction.SOUTH;
    }

    private static String portalApiList() {
        List<RegisteredPortalView> portals = SkyesightPortalApi.getAllPortals();
        if (portals.isEmpty()) {
            return "no portals registered";
        }
        StringBuilder builder = new StringBuilder();
        for (RegisteredPortalView portal : portals) {
            if (!builder.isEmpty()) {
                builder.append(" | ");
            }
            builder.append(portal.id())
                    .append(" size=")
                    .append(formatSize(portal.source().width(), portal.source().height()))
                    .append(" src=")
                    .append(portal.source().dimension().location())
                    .append("@")
                    .append(formatVec(portal.source().center()))
                    .append("/")
                    .append(portal.source().facing().getName())
                    .append(" target=")
                    .append(portal.target().dimension().location())
                    .append("@")
                    .append(formatVec(portal.target().center()))
                    .append("/")
                    .append(portal.target().facing().getName())
                    .append(" paired=")
                    .append(portal.pairedId() == null ? "-" : portal.pairedId())
                    .append(" backface=")
                    .append(portal.renderBackface())
                    .append(" mask=")
                    .append(portal.renderSettings().stencilMask() == null ? "-" : portal.renderSettings().stencilMask().texture())
                    .append(" enabled=")
                    .append(portal.renderEnabled())
                    .append(" state=")
                    .append(portal.cacheRetainedDisabled() ? "disabled_cache_retained" : (portal.active() ? "active" : "disabled"))
                    .append(" generation=")
                    .append(portal.generation());
        }
        return builder.toString();
    }

    private static String formatVec(Vec3 vec) {
        return String.format(Locale.ROOT, "%.2f,%.2f,%.2f", vec.x(), vec.y(), vec.z());
    }

    private static String formatSize(float width, float height) {
        return String.format(Locale.ROOT, "%.1fx%.1f", width, height);
    }
}

