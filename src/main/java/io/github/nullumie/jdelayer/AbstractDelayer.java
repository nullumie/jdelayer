/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (c) 2026 Nullumie
 *
 * This library is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, version 3 of the License only.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this library. If not, see <https://www.gnu.org/licenses/>
 * and <https://gnu.org>.
 */
package io.github.nullumie.jdelayer;

import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Skeletal implementation providing thread-containment foundation for {@link Delayer}
 * implementations.
 *
 * @see Delayer
 */
public abstract class AbstractDelayer implements Delayer {

    private enum State {
        IDLE,
        DELAYING,
        INTERRUPTED,
        CANCELED
    }

    private static final String INTERRUPT_MSG = "thread interrupted during delay";
    private static final String CANCELLED_MSG = "delay execution was explicitly cancelled";
    private static final String NULL_UNIT_MSG = "unit must not be null";
    private static final String NULL_THREAD_MSG = "thread must not be null";
    private static final String REENTRANT_DELAY_MSG = "re-entrant delay calls are not allowed";

    private final Thread thread;
    private final AtomicReference<State> state = new AtomicReference<>(State.IDLE);

    /**
     * Constructs a new {@code AbstractDelayer} bound to the specified thread.
     *
     * @param thread the authorized target thread
     * @throws NullPointerException if {@code thread} is {@code null}
     */
    public AbstractDelayer(Thread thread) {
        this.thread = Objects.requireNonNull(thread, NULL_THREAD_MSG);
    }

    @Override
    public final boolean isDelaying() {
        return state.get() == State.DELAYING;
    }

    @Override
    public final boolean isCanceled() {
        return state.get() == State.CANCELED;
    }

    @Override
    public final boolean isInterrupted() {
        State current = state.get();
        return current == State.INTERRUPTED
                || (current == State.DELAYING && thread.isInterrupted());
    }

    @Override
    public final void delay(long duration, TimeUnit unit)
            throws InterruptedDelayException, CanceledDelayException {
        ensureOnThread();
        Objects.requireNonNull(unit, NULL_UNIT_MSG);
        enterDelayState();
        try {
            if (duration <= 0) return;
            delayFrom(System.nanoTime(), duration, unit);
        } finally {
            exitDelayState();
        }
    }

    @Override
    public final void delay(long startNanoTime, long duration, TimeUnit unit)
            throws InterruptedDelayException, CanceledDelayException {
        ensureOnThread();
        Objects.requireNonNull(unit, NULL_UNIT_MSG);
        enterDelayState();
        try {
            if (duration <= 0) return;
            delayFrom(startNanoTime, duration, unit);
        } finally {
            exitDelayState();
        }
    }

    @Override
    public final void delay(long deadline)
            throws InterruptedDelayException, CanceledDelayException {
        ensureOnThread();
        enterDelayState();
        try {
            if (deadline - System.nanoTime() <= 0) return;
            while (deadline - System.nanoTime() > 0) {
                onDelay(deadline);
                checkState();
            }
        } finally {
            exitDelayState();
        }
    }

    @Override
    public final boolean cancel() {
        if (!state.compareAndSet(State.DELAYING, State.CANCELED)) return false;
        thread.interrupt();
        return true;
    }

    @Override
    public final boolean reset() {
        ensureOnThread();
        while (true) {
            State current = state.get();
            if (current != State.CANCELED && current != State.INTERRUPTED) return false;
            if (state.compareAndSet(current, State.IDLE)) {
                boolean ignored = Thread.interrupted();
                return true;
            }
        }
    }

    /**
     * Performs a single delay step toward the target deadline.
     *
     * @param deadline absolute timestamp in nanoseconds relative to {@link System#nanoTime()}
     */
    protected abstract void onDelay(long deadline);

    private void delayFrom(long startNanoTime, long duration, TimeUnit unit)
            throws InterruptedDelayException, CanceledDelayException {
        long remainingNanos = unit.toNanos(duration);
        while (true) {
            long elapsed = System.nanoTime() - startNanoTime;
            long left = remainingNanos - elapsed;
            if (left <= 0) break;
            long stepNanos = Math.min(left, Long.MAX_VALUE / 2);
            onDelay(System.nanoTime() + stepNanos);
            checkState();
        }
    }

    private void enterDelayState() throws CanceledDelayException, InterruptedDelayException {
        do {
            State current = state.get();
            if (current == State.DELAYING) throw new IllegalStateException(REENTRANT_DELAY_MSG);
            if (current == State.CANCELED) throw new CanceledDelayException(CANCELLED_MSG);
            if (current == State.INTERRUPTED) throw new InterruptedDelayException(INTERRUPT_MSG);
        } while (!state.compareAndSet(State.IDLE, State.DELAYING));
        checkState();
    }

    private void exitDelayState() {
        state.compareAndSet(State.DELAYING, State.IDLE);
    }

    private void checkState() throws InterruptedDelayException, CanceledDelayException {
        State current = state.get();
        if (current == State.CANCELED) throw new CanceledDelayException(CANCELLED_MSG);
        if (current == State.INTERRUPTED) throw new InterruptedDelayException(INTERRUPT_MSG);
        if (thread.isInterrupted()) {
            if (state.compareAndSet(State.DELAYING, State.INTERRUPTED)
                    || state.get() == State.INTERRUPTED)
                throw new InterruptedDelayException(INTERRUPT_MSG);
            if (state.get() == State.CANCELED) throw new CanceledDelayException(CANCELLED_MSG);
        }
    }

    private void ensureOnThread() {
        Thread currentThread = Thread.currentThread();
        if (this.thread == currentThread) return;
        throwWrongThreadException(this.thread, currentThread);
    }

    private static void throwWrongThreadException(Thread boundThread, Thread current) {
        throw new WrongThreadException(
                "Concurrency violation: Delayer accessed from an unauthorized thread! "
                        + "Bound thread: ["
                        + boundThread.getName()
                        + " (ID: "
                        + boundThread.threadId()
                        + ")], "
                        + "Offending thread: ["
                        + current.getName()
                        + " (ID: "
                        + current.threadId()
                        + ")].");
    }
}
