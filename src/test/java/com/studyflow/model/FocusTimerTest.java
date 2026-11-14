package com.studyflow.model;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class FocusTimerTest {
    private final AtomicLong nanos = new AtomicLong();
    private final FocusTimer timer = new FocusTimer(nanos::get, FocusTimer.Mode.FOCUS, Duration.ofSeconds(10));

    @Test
    void startsPausesAndResumesWithoutCountingPausedTime() {
        assertEquals(FocusTimer.State.READY, timer.state());
        assertTrue(timer.start());
        advance(Duration.ofSeconds(4));
        assertTrue(timer.pause());
        assertEquals(FocusTimer.State.PAUSED, timer.state());
        advance(Duration.ofMinutes(2));
        assertEquals(Duration.ofSeconds(6), timer.remaining());
        assertTrue(timer.consumeCompletion().isEmpty());

        assertTrue(timer.start());
        advance(Duration.ofSeconds(6));
        FocusTimer.Completion finished = timer.consumeCompletion().orElseThrow();
        assertEquals(FocusTimer.Mode.FOCUS, finished.mode());
        assertEquals(Duration.ofSeconds(10), finished.actual());
        assertEquals(FocusTimer.State.FINISHED, timer.state());
        assertEquals(Duration.ZERO, timer.remaining());
    }

    @Test
    void anEarlyFinishReportsActualActiveTime() {
        assertTrue(timer.start());
        advance(Duration.ofMillis(2750));
        assertTrue(timer.pause());
        advance(Duration.ofMinutes(1));
        assertTrue(timer.finish());
        FocusTimer.Completion result = timer.consumeCompletion().orElseThrow();
        assertEquals(Duration.ofSeconds(10), result.planned());
        assertEquals(Duration.ofMillis(2750), result.actual());
    }

    @Test
    void expiryClampsLatePollAndCompletesOnlyOnce() {
        assertTrue(timer.start());
        advance(Duration.ofMinutes(1));
        assertFalse(timer.pause());
        assertEquals(FocusTimer.State.FINISHED, timer.state());
        assertEquals(Duration.ofSeconds(10), timer.consumeCompletion().orElseThrow().actual());
        assertFalse(timer.start());
        assertFalse(timer.finish());
        assertTrue(timer.consumeCompletion().isEmpty());
    }

    @Test
    void resetDiscardsAccumulatedTimeAndCanSwitchToBreak() {
        assertTrue(timer.start());
        advance(Duration.ofSeconds(3));
        timer.reset(FocusTimer.Mode.BREAK, Duration.ofMinutes(5));
        assertEquals(FocusTimer.State.READY, timer.state());
        assertEquals(FocusTimer.Mode.BREAK, timer.mode());
        assertEquals(Duration.ofMinutes(5), timer.remaining());
        assertTrue(timer.consumeCompletion().isEmpty());
        assertTrue(timer.start());
        advance(Duration.ofMinutes(5));
        assertEquals(FocusTimer.Mode.BREAK, timer.consumeCompletion().orElseThrow().mode());
    }

    @Test
    void rejectsNonPositiveDurationsAndCannotCompleteATimerThatHasNotRun() {
        assertThrows(IllegalArgumentException.class, () -> timer.reset(FocusTimer.Mode.FOCUS, Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () ->
                timer.reset(FocusTimer.Mode.BREAK, Duration.ofSeconds(-1)));
        assertFalse(timer.finish());
        assertTrue(timer.start());
        assertFalse(timer.finish());
        assertTrue(timer.consumeCompletion().isEmpty());
    }

    private void advance(Duration elapsed) {
        nanos.addAndGet(elapsed.toNanos());
    }
}
