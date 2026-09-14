package com.skyeshade.skyesight.server.portal;

import com.skyeshade.skyesight.Skyesight;
import com.skyeshade.skyesight.api.*;
import com.skyeshade.skyesight.network.SkyesightPortalRegionPayload;
import com.skyeshade.skyesight.server.SkyesightRemoteViewLifecycleHandler;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;

@EventBusSubscriber(modid=Skyesight.MODID)
public final class PortalRegionManager {
    private static final Map<ResourceLocation,SkyesightPortalRegionPayload> DEFINITIONS=new LinkedHashMap<>();
    private static long revision;
    private static final List<Direction> DIRECTIONS=new ArrayList<>();
    public record Direction(long revision, PortalRegionDefinition definition, ResourceLocation view) {
        public PortalEndpoint source(){return definition.source().endpoint(view.toString(),definition.source().origin(),64);}
        public PortalEndpoint target(){return definition.destination().endpoint(SkyesightPortalRegionPayload.viewId(definition.id(),!view.getPath().endsWith("/b")).toString(),definition.destination().origin(),64);}
        public SkyesightPortalRaycast.Link link(){return new SkyesightPortalRaycast.Link(view,revision,source(),target(),true,
                p->definition.shape().contains(p.x,p.y),definition.sidedness());}
    }
    private static void rebuild() {
        DIRECTIONS.clear();
        for(var p:DEFINITIONS.values()) {
            if(p.definition().behavior()!=PortalBehavior.TRAVERSABLE) continue;
            DIRECTIONS.add(new Direction(p.revision(),p.definition(),SkyesightPortalRegionPayload.viewId(p.id(),false)));
            if(p.definition().bidirectional()) DIRECTIONS.add(new Direction(p.revision(),p.definition().reversed(),SkyesightPortalRegionPayload.viewId(p.id(),true)));
        }
    }
    public static List<Direction> directions(){return List.copyOf(DIRECTIONS);}
    public static List<SkyesightPortalRaycast.Link> links(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension){
        return DIRECTIONS.stream().filter(d->d.definition.source().dimension().equals(dimension)).map(Direction::link).toList();
    }
    private PortalRegionManager() {}
    private static void check(MinecraftServer server) {
        if(!server.isSameThread()) throw new IllegalStateException("Portal regions require the server thread");
    }
    public static Optional<PortalRegionDefinition> get(MinecraftServer server,ResourceLocation id) {
        check(server); var p=DEFINITIONS.get(id); return Optional.ofNullable(p==null?null:p.definition());
    }
    public static void put(MinecraftServer server,PortalRegionDefinition d) {
        check(server);
        if(server.getLevel(d.source().dimension())==null || server.getLevel(d.destination().dimension())==null)
            throw new IllegalArgumentException("Region dimension unavailable");
        var old=DEFINITIONS.get(d.id()); if(old!=null && d.equals(old.definition())) return;
        retire(server,d.id());
        var p=new SkyesightPortalRegionPayload(d.id(),++revision,d); DEFINITIONS.put(d.id(),p);
        rebuild(); PortalRegionTraversal.invalidate(d.id());
        PacketDistributor.sendToAllPlayers(p);
    }
    public static void remove(MinecraftServer server,ResourceLocation id) {
        check(server); if(DEFINITIONS.remove(id)==null) return;
        rebuild(); PortalRegionTraversal.invalidate(id);
        retire(server,id); PacketDistributor.sendToAllPlayers(new SkyesightPortalRegionPayload(id,++revision,null));
    }
    private static void retire(MinecraftServer server,ResourceLocation id) {
        for(var player:server.getPlayerList().getPlayers()) for(boolean reverse:new boolean[]{false,true})
            SkyesightRemoteViewLifecycleHandler.removeView(player,SkyesightPortalRegionPayload.viewId(id,reverse));
    }
    public static PortalRegionDefinition forView(ResourceLocation id) {
        for(var p:DEFINITIONS.values()) for(boolean reverse:new boolean[]{false,true})
            if(SkyesightPortalRegionPayload.viewId(p.id(),reverse).equals(id)) return p.definition();
        return null;
    }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent e) {
        if(e.getEntity() instanceof ServerPlayer player) DEFINITIONS.values().stream()
                .sorted(Comparator.comparingLong(SkyesightPortalRegionPayload::revision))
                .forEach(p->PacketDistributor.sendToPlayer(player,p));
    }
    @SubscribeEvent public static void stop(ServerStoppedEvent e) { DEFINITIONS.clear(); DIRECTIONS.clear(); revision=0; }
}
