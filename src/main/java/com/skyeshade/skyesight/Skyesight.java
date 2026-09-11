package com.skyeshade.skyesight;


import com.mojang.logging.LogUtils;
import com.skyeshade.skyesight.api.SkyesightApi;

import com.skyeshade.skyesight.client.view.SkyesightClientApi;
import com.skyeshade.skyesight.network.SkyesightPayloads;
import com.skyeshade.skyesight.server.PortalServerViewCacheInvalidator;
import com.skyeshade.skyesight.server.portal.PortalPlayerQueryMixinTargetAudit;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import com.skyeshade.skyesight.command.SkyesightCommandRegistrar;
import org.slf4j.Logger;

@Mod(Skyesight.MODID)
public final class Skyesight {
    public static final String MODID = "skyesight";
    public static final Logger LOGGER = LogUtils.getLogger();


    public Skyesight(IEventBus modBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.CLIENT, SkyesightClientConfig.SPEC);
        modBus.addListener(Skyesight::onConfigLoading);
        SkyesightItems.register(modBus);
        PortalServerViewCacheInvalidator.register();
        modBus.addListener(SkyesightPayloads::register);
        NeoForge.EVENT_BUS.register(this);
        if (SkyesightDebugConfig.SOURCE_MAP) {
            LOGGER.info(
                    "[Skyesight] Portal observer proximity mixins: naturalSpawnerDistanceMixin=enabled targetMethod=NaturalSpawner.isRightDistanceToPlayerAndSpawnPoint(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/ChunkAccess;Lnet/minecraft/core/BlockPos$MutableBlockPos;D)Z serverLevelNearbyPlayerMixin=disabled targetMethod=EntityGetter.hasNearbyAlivePlayer(DDDD)Z"
            );
            LOGGER.info(
                    "[Skyesight] Portal spawning integration: chunkMapInvoker=disabled serverChunkCacheInjection=enabled naturalSpawnerDistanceMixin=enabled worldBootSafe=yes"
            );
            PortalPlayerQueryMixinTargetAudit.logStartupAudit();
        }
    }

    private static void onConfigLoading(ModConfigEvent.Loading event) {
        if (event.getConfig().getSpec() == SkyesightClientConfig.SPEC
                && SkyesightClientConfig.registerStartupDebugPortals()) {
            SkyesightDebugPortalRegistrations.registerDefaults();
        }
    }

    private static final SkyesightApi API = new SkyesightClientApi();


    public static SkyesightApi api() {
        return API;
    }

    @SubscribeEvent
    public void registerCommands(RegisterCommandsEvent event) {
        SkyesightCommandRegistrar.register(event);
    }
}

