package com.skyeshade.skyesight.server;

import com.skyeshade.skyesight.network.SkyesightChunkRequestPayload;
import com.skyeshade.skyesight.Skyesight;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashMap;
import java.util.Map;

/** Opt-in wall-time attribution, not a generation benchmark. Does not load chunks to inspect them. */
@EventBusSubscriber(modid = Skyesight.MODID)
public final class SkyesightRemoteLoadDiagnostics implements AutoCloseable {
    public static final boolean ENABLED = Boolean.getBoolean("skyesight.debug.remoteLoads");
    private static final ThreadLocal<SkyesightRemoteLoadDiagnostics> ACTIVE = new ThreadLocal<>();
    private static final Map<String, Aggregate> LAST_LOG = new HashMap<>();
    private final SkyesightRemoteLoadDiagnostics previous;
    private final ServerPlayer player;
    private final ServerLevel level;
    private final SkyesightChunkRequestPayload request;
    private final long started = System.nanoTime();
    private int loaded, generated, reloaded;
    public int sent;
    public long forceNanos, acquireNanos, serializeNanos, watchNanos;

    private SkyesightRemoteLoadDiagnostics(ServerPlayer player, ServerLevel level, SkyesightChunkRequestPayload request) {
        this.player = player; this.level = level; this.request = request;
        previous = ACTIVE.get(); ACTIVE.set(this);
        for (int z = -request.radius(); z <= request.radius(); z++) {
            for (int x = -request.radius(); x <= request.radius(); x++) {
                if (level.getChunkSource().getChunkNow(request.centerChunkX()+x, request.centerChunkZ()+z) != null) loaded++;
            }
        }
    }
    public static SkyesightRemoteLoadDiagnostics begin(ServerPlayer player, ServerLevel level, SkyesightChunkRequestPayload request) {
        return ENABLED ? new SkyesightRemoteLoadDiagnostics(player, level, request) : null;
    }
    @SubscribeEvent public static void onLoad(ChunkEvent.Load event) {
        var scope = ACTIVE.get();
        if (scope == null || event.getLevel() != scope.level) return;
        var pos = event.getChunk().getPos();
        if (Math.abs(pos.x-scope.request.centerChunkX()) > scope.request.radius()
                || Math.abs(pos.z-scope.request.centerChunkZ()) > scope.request.radius()) return;
        if (event.isNewChunk()) scope.generated++; else scope.reloaded++;
    }
    @Override public void close() {
        if (previous == null) ACTIVE.remove(); else ACTIVE.set(previous);
        long now = System.nanoTime();
        String key = player.getUUID()+"/"+request.viewId();
        Aggregate sum = LAST_LOG.get(key);
        if (sum == null || sum.generation != request.viewGeneration() || sum.x != request.centerChunkX()
                || sum.z != request.centerChunkZ() || sum.radius != request.radius()) {
            sum = new Aggregate(request.viewGeneration(), request.centerChunkX(), request.centerChunkZ(), request.radius(), loaded);
            LAST_LOG.put(key, sum);
        }
        sum.dirty = true;
        sum.generated += generated; sum.reloaded += reloaded; sum.sent += sent; sum.batches++;
        sum.total += now-started; sum.force += forceNanos; sum.acquire += acquireNanos;
        sum.serialize += serializeNanos; sum.watch += watchNanos;
        if (sum.logged != 0 && now-sum.logged < 2_000_000_000L) return;
        log(key, sum, now);
    }
    private static void log(String key, Aggregate sum, long now) {
        sum.logged = now; sum.dirty = false;
        Skyesight.LOGGER.info("REMOTE_CHUNK_BURST playerView={} generation={} center={},{} loadRadius={} desired={} initiallyLoaded={} newlyGeneratedObserved={} reloadedObserved={} batches={} sent={} cumulativeRequestMs={} forceInclusiveMs={} acquireInclusiveMs={} serializeSubmitMs={} watchMs={}",
                key,sum.generation,sum.x,sum.z,sum.radius,(2L*sum.radius+1)*(2L*sum.radius+1),
                sum.initiallyLoaded,sum.generated,sum.reloaded,sum.batches,sum.sent,
                sum.total/1e6,sum.force/1e6,sum.acquire/1e6,sum.serialize/1e6,sum.watch/1e6);
    }
    @SubscribeEvent public static void onTick(ServerTickEvent.Post event) {
        if (!ENABLED) return;
        long now = System.nanoTime();
        LAST_LOG.forEach((key,sum) -> { if (sum.dirty && now-sum.logged >= 2_000_000_000L) log(key,sum,now); });
    }

    private static final class Aggregate {
        final long generation;
        final int x,z,radius,initiallyLoaded;
        boolean dirty;
        long logged,total,force,acquire,serialize,watch;
        int generated,reloaded,sent,batches;
        Aggregate(long generation,int x,int z,int radius,int loaded) {
            this.generation=generation; this.x=x; this.z=z; this.radius=radius; this.initiallyLoaded=loaded;
        }
    }
    @SubscribeEvent public static void onStop(ServerStoppedEvent event) { LAST_LOG.clear(); }
    @SubscribeEvent public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        String prefix=event.getEntity().getUUID()+"/"; LAST_LOG.keySet().removeIf(key -> key.startsWith(prefix));
    }
}
