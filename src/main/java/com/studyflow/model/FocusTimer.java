package com.studyflow.model;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.function.LongSupplier;

/** Countdown state driven by monotonic time. The presentation layer controls when to poll it. */
public final class FocusTimer {
    public enum Mode { FOCUS, BREAK }

    public enum State { READY, RUNNING, PAUSED, FINISHED }

    public record Completion(Mode mode, Duration planned, Duration actual) {}

    private final LongSupplier nanoTime;
    private Mode mode;
    private State state;
    private Duration planned;
    private Duration elapsed;
    private long startedAt;
    private Completion pending;

    public FocusTimer(LongSupplier nanoTime, Mode mode, Duration duration) {
        this.nanoTime = Objects.requireNonNull(nanoTime);
        reset(mode, duration);
    }

    /** Starts or resumes the current countdown. */
    public boolean start() {
        if (state != State.READY && state != State.PAUSED) {
            return false;
        }
        startedAt = nanoTime.getAsLong();
        state = State.RUNNING;
        return true;
    }

    public boolean pause() {
        if (state != State.RUNNING) {
            return false;
        }
        elapsed = currentElapsed();
        if (elapsed.compareTo(planned) >= 0) {
            markFinished();
            return false;
        }
        state = State.PAUSED;
        return true;
    }

    /** Ends an active countdown using the time actually spent, including time before pauses. */
    public boolean finish() {
        if (state != State.RUNNING && state != State.PAUSED) {
            return false;
        }
        elapsed = currentElapsed();
        if (elapsed.isZero()) {
            return false;
        }
        markFinished();
        return true;
    }

    /** Abandons the current countdown and prepares a new one. */
    public void reset(Mode mode, Duration duration) {
        Objects.requireNonNull(mode, "Timer mode is required.");
        if (duration == null || duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException("Timer duration must be positive.");
        }
        this.mode = mode;
        planned = duration;
        elapsed = Duration.ZERO;
        state = State.READY;
        pending = null;
    }

    public Mode mode() {
        return mode;
    }

    public State state() {
        update();
        return state;
    }

    public Duration remaining() {
        return state == State.FINISHED ? Duration.ZERO : planned.minus(currentElapsed());
    }

    public Duration planned() {
        return planned;
    }

    /** Delivers each completion to the caller at most once. */
    public Optional<Completion> consumeCompletion() {
        update();
        Completion result = pending;
        pending = null;
        return Optional.ofNullable(result);
    }

    private void update() {
        if (state == State.RUNNING && currentElapsed().compareTo(planned) >= 0) {
            elapsed = planned;
            markFinished();
        }
    }

    private Duration currentElapsed() {
        if (state != State.RUNNING) {
            return elapsed;
        }
        Duration active = elapsed.plusNanos(Math.max(0, nanoTime.getAsLong() - startedAt));
        return active.compareTo(planned) >= 0 ? planned : active;
    }

    private void markFinished() {
        state = State.FINISHED;
        pending = new Completion(mode, planned, elapsed);
    }
}
