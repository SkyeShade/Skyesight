package com.skyeshade.skyesight.server.portal;

import com.skyeshade.skyesight.Skyesight;
import com.skyeshade.skyesight.api.PortalEndpoint;
import com.skyeshade.skyesight.network.SkyesightRegionTraversalPayload;
import com.skyeshade.skyesight.portal.PortalCrossingState;
import com.skyeshade.skyesight.portal.PortalTraversalMath;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;

/** Accepted movement sweeps; no portal cells/entities/colliders are instantiated. */
@EventBusSubscriber(modid=Skyesight.MODID)
public final class PortalRegionTraversal {
    private record Sample(ResourceKey<Level> dimension,Vec3 position,Vec3 eye) {}
    private record Key(ResourceLocation view,long revision) {}
    private record WarmChunk(ResourceLocation view, ResourceKey<Level> dimension, int x, int z) {}
    private static final Map<UUID,Sample> PREVIOUS=new HashMap<>();
    private static final Map<UUID,Map<Key,PortalCrossingState>> SIDES=new HashMap<>();
    private static final Map<UUID,PortalEndpoint> GUARDS=new HashMap<>();
    private static final Map<UUID,WarmChunk> WARMED=new HashMap<>();
    private static long sequence;
    private PortalRegionTraversal() {}
    public static void reset(ServerPlayer p){PREVIOUS.remove(p.getUUID());SIDES.remove(p.getUUID());GUARDS.remove(p.getUUID());WARMED.remove(p.getUUID());}
    public static void invalidate(ResourceLocation id) {
        var a=com.skyeshade.skyesight.network.SkyesightPortalRegionPayload.viewId(id,false);
        var b=com.skyeshade.skyesight.network.SkyesightPortalRegionPayload.viewId(id,true);
        SIDES.values().forEach(states->states.keySet().removeIf(k->k.view.equals(a)||k.view.equals(b)));
        GUARDS.values().removeIf(exit->exit.id().equals(a.toString())||exit.id().equals(b.toString()));
        WARMED.values().removeIf(w->w.view.equals(a)||w.view.equals(b));
    }
    public static void movementAccepted(ServerPlayer player) {
        var id=player.getUUID();var current=player.position();var eye=player.getEyePosition();
        var previous=PREVIOUS.put(id,new Sample(player.level().dimension(),current,eye));
        if(previous==null || !previous.dimension.equals(player.level().dimension()) || !player.isAlive()
                || player.isPassenger() || player.isVehicle() || !player.canUsePortal(false)) return;
        var guard=GUARDS.get(id);
        if(guard!=null && (!guard.dimension().equals(player.level().dimension())
                || PortalTraversalMath.clearedExit(guard,player.getBoundingBox()))) GUARDS.remove(id);
        Vec3 velocity=current.subtract(previous.position);
        var states=SIDES.computeIfAbsent(id,k->new HashMap<>());
        PortalRegionManager.Direction closest=null;Vec3 closestHit=null;
        for(var direction:PortalRegionManager.directions()) {
            var d=direction.definition();if(!d.source().dimension().equals(player.level().dimension())) continue;
            var source=direction.source();var a=d.source().local(previous.eye);var b=d.source().local(eye);
            if(source.equals(GUARDS.get(id))) continue;
            var key=new Key(direction.view(),direction.revision());
            if(Math.min(Math.abs(a.z),Math.abs(b.z))>d.activationDistance() && a.z*b.z>0){states.remove(key);continue;}
            var destination=player.server.getLevel(d.destination().dimension());if(destination==null) continue;
            double speed=d.source().normal().dot(velocity);
            if(speed*b.z<0 && Math.abs(b.z)<Math.max(8,Math.min(d.activationDistance(),Math.abs(speed)*20))) {
                var predicted=eye.add(velocity.scale(Math.min(20,Math.abs(b.z/speed))));
                if(d.containsWorld(predicted)) {
                    var pos=BlockPos.containing(d.mapPosition(predicted));
                    var chunkOrigin=new WarmChunk(direction.view(), destination.dimension(), pos.getX()>>4, pos.getZ()>>4);
                    if(!chunkOrigin.equals(WARMED.get(id))) { destination.getChunkAt(pos);WARMED.put(id,chunkOrigin); }
                }
            }
            var state=states.computeIfAbsent(key,k->{var s=new PortalCrossingState();s.updateEye(source,previous.eye,d::containsWorld);return s;});
            int side=state.stableSide();
            var hit=state.updateEye(source,eye,d::containsWorld);
            if(hit!=null && d.sidedness().allows(side==0?a.z:side)
                    && (closestHit==null || previous.eye.distanceToSqr(hit)<previous.eye.distanceToSqr(closestHit))) {
                closest=direction;closestHit=hit;
            }
        }
        if(closest==null) return;
        var source=closest.source();var target=closest.target();
        var pose=PortalTraversalMath.transformAtEyeCrossing(source,target,closestHit,eye,eye.subtract(current),
                velocity,player.getYRot(),player.getXRot());
        var destination=player.server.getLevel(target.dimension());
        destination.getChunkAt(BlockPos.containing(pose.position()));
        if(!destination.getWorldBorder().isWithinBounds(BlockPos.containing(pose.position()))) return;
        PacketDistributor.sendToPlayer(player,new SkyesightRegionTraversalPayload(closest.view(),closest.revision(),++sequence,pose.position()));
        PortalTransfers.player(player,source,target,pose);
        PREVIOUS.put(id,new Sample(target.dimension(),player.position(),player.getEyePosition())); GUARDS.put(id,target);
        if(Boolean.getBoolean("skyesight.debugTraversal")) Skyesight.LOGGER.info("[RegionTraversal] view={} hit={} position={} velocity={} yaw={} pitch={}",
                closest.view(),closestHit,pose.position(),pose.velocity(),pose.yaw(),pose.pitch());
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post e){for(var p:e.getServer().getPlayerList().getPlayers())movementAccepted(p);}
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e){if(e.getEntity() instanceof ServerPlayer p)reset(p);}
    @SubscribeEvent public static void stopped(ServerStoppedEvent e){PREVIOUS.clear();SIDES.clear();GUARDS.clear();WARMED.clear();sequence=0;}
}
