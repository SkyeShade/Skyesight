package com.skyeshade.skyesight;

import java.util.HashMap;
import java.util.Map;

public final class SkyesightLogLimiter {
    private static final Map<String, Entry> ENTRIES = new HashMap<>();

    private SkyesightLogLimiter() {}

    public static boolean shouldLog(String key, long intervalMillis) {
        long now = System.currentTimeMillis();
        Entry entry = ENTRIES.computeIfAbsent(key, ignored -> new Entry());
        if (now - entry.lastLogMillis < intervalMillis) {
            entry.suppressed++;
            return false;
        }
        entry.lastLogMillis = now;
        return true;
    }

    public static int suppressedSinceLastLog(String key) {
        Entry entry = ENTRIES.get(key);
        if (entry == null) {
            return 0;
        }
        int suppressed = entry.suppressed;
        entry.suppressed = 0;
        return suppressed;
    }

    private static final class Entry {
        private long lastLogMillis;
        private int suppressed;
    }
}
