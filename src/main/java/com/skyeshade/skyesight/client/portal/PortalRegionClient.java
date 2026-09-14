package com.skyeshade.skyesight.client.portal;

import com.skyeshade.skyesight.Skyesight;
import com.skyeshade.skyesight.api.*;
import com.skyeshade.skyesight.network.SkyesightPortalRegionPayload;
import com.skyeshade.skyesight.portal.PortalRegionWindow;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import java.util.*;

/** One local aperture and one scene/streaming lease per active direction, never one per tile. */
@EventBusSubscriber(modid=Skyesight.MODID,value=Dist.CLIENT)
public final class PortalRegionClient {
    private static final Map<ResourceLocation,SkyesightPortalRegionPayload> DEFINITIONS=new LinkedHashMap<>();
    private static final Map<ResourceLocation,PortalRegionDefinition> REVERSED=new HashMap<>();
    private static final Map<ResourceLocation,PortalRegionWindow> WINDOWS=new HashMap<>();
    private static final Map<ResourceLocation,net.minecraft.world.level.ChunkPos> STREAM_CENTERS=new HashMap<>();
    public static net.minecraft.world.level.ChunkPos streamCenter(ResourceLocation id,net.minecraft.world.level.ChunkPos fallback) {
        return STREAM_CENTERS.getOrDefault(id,fallback);
    }
    public static float renderWidth(PortalEndpoint endpoint) { var w=renderWindow(endpoint);return w==null?endpoint.width():w.visibleSize(); }
    public static float renderHeight(PortalEndpoint endpoint) { var w=renderWindow(endpoint);return w==null?endpoint.height():w.visibleSize(); }
    private static PortalRegionWindow renderWindow(PortalEndpoint endpoint) {
        for(var e:WINDOWS.entrySet()) if(endpoint.id().equals(e.getKey().toString()) || endpoint.id().equals(e.getKey()+"/target"))return e.getValue();
        return null;
    }
    private static long lastRevision;
    private static long lastTraversal;
    public static PortalRegionDefinition definition(ResourceLocation view) {
        for(var p:DEFINITIONS.values()) {
            if(SkyesightPortalRegionPayload.viewId(p.id(),false).equals(view)) return p.definition();
            if(SkyesightPortalRegionPayload.viewId(p.id(),true).equals(view)) return REVERSED.get(p.id());
        }
        return null;
    }
    public static java.util.List<RegisteredPortalView> traversableViews() {
        return WINDOWS.keySet().stream().filter(id->{var d=definition(id);return d!=null && d.behavior()==PortalBehavior.TRAVERSABLE;})
                .map(SkyesightPortalRegistry::get).filter(Objects::nonNull).toList();
    }
    public static void traverse(com.skyeshade.skyesight.network.SkyesightRegionTraversalPayload p) {
        if(p.sequence()<=lastTraversal) return;
        for(var d:DEFINITIONS.values()) for(boolean reverse:new boolean[]{false,true}) {
            if(!SkyesightPortalRegionPayload.viewId(d.id(),reverse).equals(p.view()) || d.revision()!=p.revision()
                    || d.definition().behavior()!=PortalBehavior.TRAVERSABLE || reverse&&!d.definition().bidirectional()) continue;
            lastTraversal=p.sequence();
            com.skyeshade.skyesight.client.transition.TraversalPortalClient.beginTraversal(p.view().toString(),
                    SkyesightPortalRegionPayload.viewId(d.id(),!reverse).toString(),p.feet(),p.sequence());
        }
    }
    private PortalRegionClient() {}
    public static void receive(SkyesightPortalRegionPayload p) {
        // Ordered connection plus sorted login replay needs only one revision fence, no retained tombstones.
        if(p.revision()<=lastRevision) return;
        lastRevision=p.revision();
        for(boolean reverse:new boolean[]{false,true}) {
            var id=SkyesightPortalRegionPayload.viewId(p.id(),reverse);
            com.skyeshade.skyesight.client.transition.TraversalPortalStandby.remove(id.toString());
            com.skyeshade.skyesight.client.transition.SecondaryTransition.invalidate(id);remove(id);
        }
        if(p.definition()==null) DEFINITIONS.remove(p.id()); else DEFINITIONS.put(p.id(),p);
        REVERSED.remove(p.id());
        if(p.definition()!=null && p.definition().bidirectional()) REVERSED.put(p.id(),p.definition().reversed());
    }
    private static void remove(ResourceLocation id) {
        if(com.skyeshade.skyesight.client.transition.TraversalPortalClient.retains(id)
                || com.skyeshade.skyesight.client.transition.TraversalPortalStandby.contains(id)) return;
        WINDOWS.remove(id); SkyesightPortalRegistry.remove(id);
        STREAM_CENTERS.remove(id);
        com.skyeshade.skyesight.client.render.PlayerPerspectiveViews.remove(id.toString());
    }
    public static PortalSidedness sides(ResourceLocation id) {
        for(var p:DEFINITIONS.values()) if(p.definition()!=null)
            for(boolean r:new boolean[]{false,true}) if(SkyesightPortalRegionPayload.viewId(p.id(),r).equals(id)) return p.definition().sidedness();
        return null;
    }
    /** Called once before the main world's mask pass, never while rendering a secondary world. */
    public static void update() {
        var mc=Minecraft.getInstance();
        if(mc.level==null) { clear(); return; }
        var camera=mc.gameRenderer.getMainCamera().getPosition();
        for(var p:DEFINITIONS.values()) {
            if(p.definition()==null) continue;
            for(boolean reverse:new boolean[]{false,true}) {
                var d=reverse?REVERSED.get(p.id()):p.definition(); var id=SkyesightPortalRegionPayload.viewId(p.id(),reverse);
                if(d==null || !d.source().dimension().equals(mc.level.dimension())) { remove(id); continue; }
                var old=WINDOWS.get(id);
                var window=PortalRegionWindow.at(d,camera,mc.player==null?net.minecraft.world.phys.Vec3.ZERO:mc.player.getDeltaMovement(),old);
                if(window==null) { remove(id); continue; }
                var center=window.center(d);
                var source=d.source().endpoint(id.toString(),center,64);
                var target=d.destination().endpoint(id+"/target",d.mapPosition(center),64);
                var mask=PortalStencilMask.rectangles(window.rectangles());
                if(old==null) {
                    var settings=new PortalRenderSettings(true,true,0,d.renderRadiusChunks(),d.entityRadiusChunks(),
                            d.renderRadiusChunks(),d.renderRadiusChunks(),true,true,true,true,true,true).withStencilMask(mask);
                    SkyesightPortalRegistry.register(id,source,target,settings,true,null,d.id().toString(),"portal_region",true,false);
                    if(d.behavior()==PortalBehavior.TRAVERSABLE) com.skyeshade.skyesight.client.render.PlayerPerspectiveViews.register(id.toString());
                } else SkyesightPortalRegistry.moveRegionWindow(id,source,target,mask);
                WINDOWS.put(id,window);
                if(d.behavior()==PortalBehavior.TRAVERSABLE)
                    // Only refresh the observed direction. The old side expires normally
                    // after departure, rather than watching a stale location indefinitely.
                    com.skyeshade.skyesight.client.transition.TraversalPortalStandby.touch(id.toString(),id.toString());
                STREAM_CENTERS.put(id,new net.minecraft.world.level.ChunkPos(net.minecraft.core.BlockPos.containing(window.mappedWarmCenter(d,camera))));
                // Warm observers even when their aperture is outside the camera frustum. An
                // empty renewal preserves the existing watch instead of letting its lease expire.
                com.skyeshade.skyesight.client.world.SkyesightClientChunkRequester.requestChunksFor(
                        id, d.destination().dimension(), STREAM_CENTERS.get(id), d.renderRadiusChunks());
                com.skyeshade.skyesight.client.world.SkyesightClientChunkRequester.keepAlive(id);
            }
        }
    }
    public static Map<ResourceLocation,PortalRegionWindow> windows() { return Map.copyOf(WINDOWS); }
    @SubscribeEvent public static void commands(net.neoforged.neoforge.client.event.RegisterClientCommandsEvent e) {
        e.getDispatcher().register(net.minecraft.commands.Commands.literal("skyesightregionstatus").executes(c -> {
            var mc=Minecraft.getInstance();
            if(mc.player==null) return 0;
            mc.player.displayClientMessage(net.minecraft.network.chat.Component.literal("Region windows: "+WINDOWS.size()),false);
            WINDOWS.forEach((id,w)-> {
                var view=SkyesightPortalRegistry.get(id);
                mc.player.displayClientMessage(net.minecraft.network.chat.Component.literal(id+": cells="+w.activeCells()
                        +" centerUV="+w.centerU()+","+w.centerV()+" generation="+view.generation()
                        +" warmCells="+w.warmCells().size()+" visibleSize="+w.visibleSize()+" warmCenter="+STREAM_CENTERS.get(id)
                        +" terrainRadius="+view.renderSettings().terrainChunkRadius()+" chunks"),false);
            });
            return WINDOWS.size();
        }));
    }
    private static void clear() {
        for(var id:List.copyOf(WINDOWS.keySet())) {
            com.skyeshade.skyesight.client.transition.TraversalPortalStandby.remove(id.toString());
            com.skyeshade.skyesight.client.transition.SecondaryTransition.invalidate(id);remove(id);
        }
        STREAM_CENTERS.clear();
        DEFINITIONS.clear();
        REVERSED.clear();
        lastRevision=0;
        lastTraversal=0;
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut e) { clear(); }
}
