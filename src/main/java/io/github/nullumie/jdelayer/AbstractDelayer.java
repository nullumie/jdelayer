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
        return state.get() == State.INTERRUPTED;
    }

    @Override
    public final void delay(long duration, TimeUnit unit)
            throws InterruptedDelayException, CanceledDelayException {
        ensureOnThread();
        Objects.requireNonNull(unit, NULL_UNIT_MSG);

        enterDelayState();
        try {
            if (duration <= 0) return;

            long remainingNanos = unit.toNanos(duration);
            long now = System.nanoTime();

            long deadline =
                    (remainingNanos > Long.MAX_VALUE - now) ? Long.MAX_VALUE : now + remainingNanos;

            while (deadline - System.nanoTime() > 0) {
                checkState();
                onDelay(deadline);
            }

            if (deadline == Long.MAX_VALUE) {
                long elapsed = System.nanoTime() - now;
                remainingNanos -= elapsed;

                if (remainingNanos > 0) {
                    deadline = System.nanoTime() + remainingNanos;

                    while (deadline - System.nanoTime() > 0) {
                        checkState();
                        onDelay(deadline);
                    }
                }
            }

            checkState();
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
                checkState();
                onDelay(deadline);
            }
            checkState();
        } finally {
            exitDelayState();
        }
    }

    @Override
    public final void cancel() {
        State previousState = state.getAndSet(State.CANCELED);
        if (previousState == State.DELAYING) {
            thread.interrupt();
        }
    }

    /**
     * Performs a single delay step toward the target deadline.
     *
     * @param deadline absolute timestamp in nanoseconds relative to {@link System#nanoTime()}
     */
    protected abstract void onDelay(long deadline);

    private void enterDelayState() throws CanceledDelayException, InterruptedDelayException {
        State previousState = state.getAndSet(State.DELAYING);

        if (previousState == State.CANCELED) {
            state.set(State.CANCELED);
            throwCancelledDelayException();
        }

        checkState();
    }

    private void exitDelayState() {
        state.compareAndSet(State.DELAYING, State.IDLE);
    }

    private void checkState() throws InterruptedDelayException, CanceledDelayException {
        if (state.get() == State.CANCELED) {
            throwCancelledDelayException();
        }

        if (thread.isInterrupted()) {
            state.set(State.INTERRUPTED);
            throwInterruptedDelayException();
        }
    }

    private void ensureOnThread() {
        Thread currentThread = Thread.currentThread();
        if (this.thread == currentThread) return;
        throwWrongThreadException(this.thread, currentThread);
    }

    private static void throwInterruptedDelayException() throws InterruptedDelayException {
        throw new InterruptedDelayException(INTERRUPT_MSG);
    }

    private static void throwCancelledDelayException() throws CanceledDelayException {
        throw new CanceledDelayException(CANCELLED_MSG);
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
