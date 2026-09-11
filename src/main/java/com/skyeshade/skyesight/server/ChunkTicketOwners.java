package com.skyeshade.skyesight.server;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Idempotent requester sets: callers act only on first acquire / final release. */
public final class ChunkTicketOwners<K, O> {
    private final Map<K, Set<O>> owners = new HashMap<>();

    public boolean acquire(K chunk, O owner) {
        Set<O> requests = owners.computeIfAbsent(chunk, ignored -> new HashSet<>());
        return requests.add(owner) && requests.size() == 1;
    }

    public boolean release(K chunk, O owner) {
        Set<O> requests = owners.get(chunk);
        if (requests == null || !requests.remove(owner) || !requests.isEmpty()) return false;
        owners.remove(chunk);
        return true;
    }

    public int count(K chunk) {
        Set<O> requests = owners.get(chunk);
        return requests == null ? 0 : requests.size();
    }

    public Set<K> chunks() { return Set.copyOf(owners.keySet()); }
    public void clear() { owners.clear(); }
}
