package com.skyeshade.skyesight.server;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PortalServerViewCacheInvalidatorTest {
    @Test void stoppedInlineExecutorDoesNotRedispatch() {
        var stopped=new AtomicBoolean();var calls=new AtomicInteger();var invalidations=new AtomicInteger();
        PortalServerViewCacheInvalidator.dispatch(task->{calls.incrementAndGet();stopped.set(true);task.run();},
                stopped::get,invalidations::incrementAndGet);
        assertEquals(1,calls.get());assertEquals(0,invalidations.get());
    }
    @Test void queuedWorkIsDiscardedWhenServerStopsBeforeItRuns() {
        var stopped=new AtomicBoolean();var tasks=new java.util.ArrayList<Runnable>();var calls=new AtomicInteger();
        PortalServerViewCacheInvalidator.dispatch(tasks::add,stopped::get,calls::incrementAndGet);
        stopped.set(true);tasks.getFirst().run();assertEquals(0,calls.get());
        PortalServerViewCacheInvalidator.dispatch(tasks::add,stopped::get,calls::incrementAndGet);
        assertEquals(1,tasks.size());
    }
    @Test void activeInlineAndQueuedExecutorsInvalidateOnce() {
        var calls=new AtomicInteger();
        PortalServerViewCacheInvalidator.dispatch(Runnable::run,()->false,calls::incrementAndGet);
        assertEquals(1,calls.get());
        var tasks=new java.util.ArrayList<Runnable>();
        PortalServerViewCacheInvalidator.dispatch(tasks::add,()->false,calls::incrementAndGet);
        assertEquals(1,calls.get());tasks.getFirst().run();assertEquals(2,calls.get());
    }
}
