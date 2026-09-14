package com.skyeshade.skyesight.server.portal;

import com.skyeshade.skyesight.api.PortalAperture;
import com.skyeshade.skyesight.api.PortalEndpoint;
import com.skyeshade.skyesight.api.SkyesightPortalRaycast.Link;
import com.skyeshade.skyesight.mixin.common.ProjectileTraversalInvoker;
import com.skyeshade.skyesight.network.SkyesightTraversalPayload;
import com.skyeshade.skyesight.portal.PortalCollisionMath;
import com.skyeshade.skyesight.portal.PortalCrossingState;
import com.skyeshade.skyesight.portal.PortalTraversalMath;
import com.skyeshade.skyesight.Skyesight;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

import java.util.*;

/** Server tick sweeps for non-player roots. Player accepted-packet traversal remains independent. */
@EventBusSubscriber(modid = Skyesight.MODID)
public final class PortalEntityTraversal {
    private record Sample(ServerLevel level, Vec3 position) {}
    private record Key(ResourceLocation id, long revision) {}
    private static final Map<Entity, Sample> SAMPLES = new WeakHashMap<>();
    private static final Map<Entity, Map<Key, PortalCrossingState>> SIDES = new WeakHashMap<>();
    private static final Map<Entity, HitResult> DEFERRED_IMPACTS = new WeakHashMap<>();
    private static final Map<Entity, Integer> ARRIVED_TICK = new WeakHashMap<>();
    private static final Map<Entity, PortalEndpoint> EXIT_GUARDS = new WeakHashMap<>();
    private PortalEntityTraversal() {}
    private static List<Link> links(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension) {
        var result=new ArrayList<>(TraversalPortalManager.links(dimension)); result.addAll(PortalRegionManager.links(dimension));return result;
    }
    private static boolean eligible(Entity entity) {
        return entity.level() instanceof ServerLevel && !(entity instanceof ServerPlayer)
                && !(entity instanceof Marker) && !entity.isRemoved() && !entity.isSpectator()
                && !entity.isPassenger() && !entity.isVehicle() && entity.canUsePortal(false);
    }
    @SubscribeEvent public static void beforeTick(EntityTickEvent.Pre event) {
        Entity entity = event.getEntity();
        if (!(entity.level() instanceof ServerLevel level)) return;
        if (Objects.equals(ARRIVED_TICK.get(entity), level.getServer().getTickCount())) {
            event.setCanceled(true); return; // Destination may tick later than source in the same server tick.
        }
        ARRIVED_TICK.remove(entity);
        var exit = EXIT_GUARDS.get(entity);
        if (exit != null && (!exit.dimension().equals(entity.level().dimension())
                || (PortalRegionManager.forView(ResourceLocation.parse(exit.id())) != null
                    ? PortalTraversalMath.clearedExit(exit, entity.getBoundingBox())
                    : PortalTraversalMath.clearedExit(exit, entity.position(), entity.getBbWidth())))) EXIT_GUARDS.remove(entity);
        if (eligible(entity) && !links(entity.level().dimension()).isEmpty())
            SAMPLES.put(entity, new Sample((ServerLevel) entity.level(), entity.position()));
    }
    @SubscribeEvent public static void leave(EntityLeaveLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel)) return;
        SAMPLES.remove(event.getEntity()); SIDES.remove(event.getEntity()); DEFERRED_IMPACTS.remove(event.getEntity()); ARRIVED_TICK.remove(event.getEntity()); EXIT_GUARDS.remove(event.getEntity());
    }
    @SubscribeEvent public static void afterTick(EntityTickEvent.Post event) {
        Entity entity = event.getEntity();
        if (!(entity.level() instanceof ServerLevel)) return;
        Sample sample = SAMPLES.remove(entity);
        if (sample == null || sample.level != entity.level() || !eligible(entity)) return;
        var links = links(entity.level().dimension());
        if (links.isEmpty()) { SIDES.remove(entity); EXIT_GUARDS.remove(entity); return; }
        if (EXIT_GUARDS.containsKey(entity) && links.stream().noneMatch(link -> link.source().equals(EXIT_GUARDS.get(entity))))
            EXIT_GUARDS.remove(entity);
        var states = SIDES.computeIfAbsent(entity, ignored -> new HashMap<>());
        states.keySet().removeIf(key -> links.stream().noneMatch(link -> key.equals(new Key(link.id(), link.revision()))));
        Link closest = null; Vec3 closestHit = null;
        for (Link link : links) {
            if (link.source().equals(EXIT_GUARDS.get(entity)) || !link.sidedness().allows(PortalTraversalMath.local(link.source(),sample.position).z)) continue;
            var a = PortalTraversalMath.local(link.source(), sample.position);
            var b = PortalTraversalMath.local(link.source(), entity.position());
            if (Math.min(Math.abs(a.z), Math.abs(b.z)) > entity.getBbWidth() + .1 && a.z * b.z > 0) {
                states.remove(new Key(link.id(), link.revision())); continue;
            }
            var state = states.computeIfAbsent(new Key(link.id(), link.revision()), ignored -> {
                var created = new PortalCrossingState();
                created.update(link.source(), sample.position, point -> fits(entity, link, point));
                return created;
            });
            Vec3 hit = state.update(link.source(), entity.position(), point -> fits(entity, link, point));
            if (hit != null && (closestHit == null || sample.position.distanceToSqr(hit) < sample.position.distanceToSqr(closestHit))) {
                closest = link; closestHit = hit;
            }
        }
        if (closest != null && teleport(entity, closest, closestHit)) { DEFERRED_IMPACTS.remove(entity); return; }
        // A mod may veto travel. In that case the original source impact still happens.
        var deferred = DEFERRED_IMPACTS.remove(entity);
        if (deferred != null && !entity.isRemoved()) {
            finishImpact((Projectile) entity, sample.position, deferred);
        }
    }
    @SubscribeEvent public static void impact(ProjectileImpactEvent event) {
        var entity = event.getProjectile();
        if (!(entity.level() instanceof ServerLevel)) return;
        var sample = SAMPLES.get(entity);
        if (sample == null || !eligible(entity)) return;
        // Collision behind an aperture belongs to the destination. Let the native tick finish
        // its drag/gravity update, then perform the ordered dimension transition at tick end.
        Vec3 end = sample.position.add(entity.getDeltaMovement());
        for (Link link : links(entity.level().dimension())) {
            if (link.source().equals(EXIT_GUARDS.get(entity)) || !link.sidedness().allows(PortalTraversalMath.local(link.source(),sample.position).z)) continue;
            Vec3 a = PortalTraversalMath.local(link.source(), sample.position), b = PortalTraversalMath.local(link.source(), end);
            if (a.z * b.z >= 0 || a.z == b.z) continue;
            Vec3 hit = sample.position.lerp(end, a.z / (a.z - b.z));
            if (sample.position.distanceToSqr(hit) >= sample.position.distanceToSqr(event.getRayTraceResult().getLocation())
                    || !fits(entity, link, hit)) continue;
            var target = sample.level.getServer().getLevel(link.target().dimension());
            if (target == null || !entity.canChangeDimensions(sample.level, target)) continue;
            DEFERRED_IMPACTS.putIfAbsent(entity, event.getRayTraceResult());
            event.setCanceled(true); return;
        }
    }
    private static boolean fits(Entity entity, Link link, Vec3 at) {
        var region=PortalRegionManager.forView(link.id());
        if(region!=null) {
            if(link.id().getPath().endsWith("/b")) region=region.reversed();
            return region.containsWorld(at);
        }
        return PortalAperture.fits(link, entity.getBoundingBox().move(at.subtract(entity.position())));
    }
    private static boolean teleport(Entity entity, Link link, Vec3 intersection) {
        var source = (ServerLevel) entity.level();
        var destination = source.getServer().getLevel(link.target().dimension());
        if (destination == null || !entity.canChangeDimensions(source, destination)) return false;
        var pose = PortalTraversalMath.transform(link.source(), link.target(), entity.position(),
                entity.getDeltaMovement(), entity.getYRot(), entity.getXRot());
        var exit = PortalTraversalMath.position(link.source(), link.target(), intersection);
                var exitLink=TraversalPortalManager.links(link.target().dimension()).stream()
                .filter(l -> l.id().equals(SkyesightTraversalPayload.pairedPortalId(link.id()))).findFirst().orElse(null);
        var exitBounds=PortalCollisionMath.transform(entity.getBoundingBox().move(intersection.subtract(entity.position())),link.source(),link.target());
        if(PortalRegionManager.forView(link.id())==null && (exitLink==null || !PortalAperture.fits(exitLink,exitBounds))) return false;
        destination.getChunkAt(BlockPos.containing(pose.position()));
        Entity owner = entity instanceof Projectile projectile ? projectile.getOwner() : null;
        float head = entity.getYHeadRot();
        float body = entity instanceof LivingEntity living ? living.yBodyRot : entity.getYRot();
        Entity moved = entity.changeDimension(new DimensionTransition(destination, pose.position(), pose.velocity(),
                pose.yaw(), pose.pitch(), DimensionTransition.PLACE_PORTAL_TICKET));
        if (moved == null) return false; // NeoForge travel veto: normal entity remains in its source level.
        ARRIVED_TICK.put(moved, source.getServer().getTickCount());
        EXIT_GUARDS.put(moved, link.target());
        // Base Entity.changeDimension in 1.21.1 retains old pitch; supply the transformed pitch explicitly.
        moved.setXRot(pose.pitch()); moved.xRotO = pose.pitch(); moved.yRotO = pose.yaw();
        var rotation = PortalTraversalMath.rotation(link.source().rotation(), link.target().rotation());
        moved.setYHeadRot(PortalTraversalMath.yaw(PortalTraversalMath.rotate(Vec3.directionFromRotation(0, head), rotation)));
        if (moved instanceof LivingEntity living) {
            living.yHeadRotO = living.getYHeadRot();
            living.yBodyRot = living.yBodyRotO = PortalTraversalMath.yaw(PortalTraversalMath.rotate(Vec3.directionFromRotation(0, body), rotation));
        }
        if (moved instanceof Projectile projectile && owner != null) projectile.setOwner(owner);
        moved.setDeltaMovement(pose.velocity()); moved.hasImpulse = true;
        if (moved instanceof Projectile projectile) {
            // Check the remainder of this SAME sweep in its destination, so a fast projectile
            // cannot tunnel through destination blocks/entities while source impacts are deferred.
            var invoker = (ProjectileTraversalInvoker) projectile;
            HitResult hit = destination.clip(new ClipContext(
                    exit, pose.position(), ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE, projectile));
            Vec3 end = hit.getType() == HitResult.Type.MISS ? pose.position() : hit.getLocation();
            var entityHit = ProjectileUtil.getEntityHitResult(destination, projectile,
                    exit, end, new AABB(exit, end).inflate(1), invoker::skyesight$canHit);
            if (entityHit != null) hit = entityHit;
            if (hit.getType() != HitResult.Type.MISS
                    && !EventHooks.onProjectileImpact(projectile, hit)) {
                finishImpact(projectile, exit, hit);
            }
        }
        SIDES.remove(entity); SAMPLES.remove(entity);
        var arrivals = SIDES.computeIfAbsent(moved, ignored -> new HashMap<>());
        for (Link reverse : links(destination.dimension())) {
            var side = new PortalCrossingState(); side.arrive(reverse.source(), moved.position());
            arrivals.put(new Key(reverse.id(), reverse.revision()), side);
        }
        if (Boolean.getBoolean("skyesight.debugTraversal")) Skyesight.LOGGER.info(
                "[EntityTraversal] uuid={} type={} portal={} hit={} destination={} velocity={} recreated={}",
                moved.getUUID(), moved.getType(), link.id(), intersection, moved.position(), moved.getDeltaMovement(), moved != entity);
        return true;
    }
    private static void finishImpact(Projectile projectile, Vec3 start, HitResult hit) {
        projectile.setPos(start);
        ((ProjectileTraversalInvoker) projectile).skyesight$impact(hit);
        // Native projectile ticks advance AFTER impact handling. In particular AbstractArrow
        // replaces velocity with (hit - start) and backs its position up by .05 before embedding.
        // We are already at tick end, so omitting this step embeds the arrow at the portal instead.
        if (!projectile.isRemoved()) projectile.setPos(projectile.position().add(projectile.getDeltaMovement()));
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) {
        SAMPLES.clear(); SIDES.clear(); DEFERRED_IMPACTS.clear(); ARRIVED_TICK.clear(); EXIT_GUARDS.clear();
    }
}
