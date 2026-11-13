package com.studyflow.education;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Demonstrates priorities without relying on them for execution order. */
class ThreadExamplesTest {
    @Test
    void explicitlyCoordinatesAThreadFromStartThroughCompletion() throws InterruptedException {
        CountDownLatch allowedToWork = new CountDownLatch(1);
        AtomicBoolean completed = new AtomicBoolean();
        Thread worker = Thread.ofPlatform().daemon().name("course-example").unstarted(() -> {
            try {
                completed.set(allowedToWork.await(3, TimeUnit.SECONDS));
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
            }
        });
        worker.setPriority(Thread.MIN_PRIORITY);
        assertFalse(worker.isAlive());
        worker.start();
        allowedToWork.countDown();
        worker.join(TimeUnit.SECONDS.toMillis(4));
        assertTrue(completed.get());
        assertFalse(worker.isAlive());
    }
}
