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

import java.util.concurrent.TimeUnit;

/**
 * Provides centralized management for per-thread delay operations.
 *
 * @see Delayer
 */
public interface DelayerService extends AutoCloseable {

    /**
     * Checks whether this service has been closed.
     *
     * @return {@code true} if closed; {@code false} otherwise
     */
    boolean isClosed();

    /**
     * Checks if the specified thread is currently executing a delay operation.
     *
     * @param thread the target thread
     * @return {@code true} if delaying; {@code false} otherwise
     * @throws NullPointerException if {@code thread} is {@code null}
     * @throws IllegalStateException if this service is closed
     */
    boolean isDelaying(Thread thread);

    /**
     * Checks if the delayer associated with the specified thread is in a canceled state.
     *
     * @param thread the target thread
     * @return {@code true} if canceled; {@code false} otherwise
     * @throws NullPointerException if {@code thread} is {@code null}
     * @throws IllegalStateException if this service is closed
     */
    boolean isCanceled(Thread thread);

    /**
     * Checks if the delayer associated with the specified thread is in an interrupted state.
     *
     * @param thread the target thread
     * @return {@code true} if interrupted; {@code false} otherwise
     * @throws NullPointerException if {@code thread} is {@code null}
     * @throws IllegalStateException if this service is closed
     */
    boolean isInterrupted(Thread thread);

    /**
     * Checks whether a delayer context is registered for the specified thread.
     *
     * @param thread the target thread
     * @return {@code true} if a delayer context exists; {@code false} otherwise
     * @throws NullPointerException if {@code thread} is {@code null}
     * @throws IllegalStateException if this service is closed
     */
    boolean hasDelayer(Thread thread);

    /**
     * Releases the specified thread, removing its delayer context and canceling any ongoing delay.
     *
     * @param thread the target thread
     * @return {@code true} if a delayer context was registered for the thread and released; {@code
     *     false} otherwise
     * @throws NullPointerException if {@code thread} is {@code null}
     * @throws IllegalStateException if this service is closed
     */
    boolean release(Thread thread);

    /**
     * Releases all threads, canceling ongoing delays and clearing all internal contexts.
     *
     * @return {@code true} if at least one delayer context was registered and released; {@code
     *     false} otherwise
     * @throws IllegalStateException if this service is closed
     */
    boolean releaseAll();

    /**
     * Releases the calling thread from this service.
     *
     * @see #release(Thread)
     */
    default void release() {
        release(Thread.currentThread());
    }

    /**
     * Blocks the current thread for the specified duration starting from the current time.
     *
     * @param duration the length of time to delay
     * @param unit the time unit of {@code duration}
     * @throws InterruptedDelayException if interrupted during the delay or if already in an
     *     interrupted state
     * @throws CanceledDelayException if canceled during the delay or if already in a canceled state
     * @throws NullPointerException if {@code unit} is {@code null}
     * @throws IllegalStateException if this service is closed
     */
    default void delay(long duration, TimeUnit unit)
            throws InterruptedDelayException, CanceledDelayException {
        long startTimeNanos = System.nanoTime();
        delay(startTimeNanos, duration, unit);
    }

    /**
     * Blocks the current thread for the specified duration relative to a given start timestamp.
     *
     * @param startNanoTime the start timestamp in nanoseconds relative to {@link System#nanoTime()}
     * @param duration the duration to delay from {@code startNanoTime}
     * @param unit the time unit of {@code duration}
     * @throws InterruptedDelayException if interrupted during the delay or if already in an
     *     interrupted state
     * @throws CanceledDelayException if canceled during the delay or if already in a canceled state
     * @throws NullPointerException if {@code unit} is {@code null}
     * @throws IllegalStateException if this service is closed
     */
    void delay(long startNanoTime, long duration, TimeUnit unit)
            throws InterruptedDelayException, CanceledDelayException;

    /**
     * Blocks the current thread until the specified deadline.
     *
     * @param deadline absolute timestamp in nanoseconds relative to {@link System#nanoTime()}
     * @throws InterruptedDelayException if interrupted during the wait or if already in an
     *     interrupted state
     * @throws CanceledDelayException if canceled during the wait or if already in a canceled state
     * @throws IllegalStateException if this service is closed
     */
    void delay(long deadline) throws InterruptedDelayException, CanceledDelayException;

    /**
     * Cancels any active delay for the specified thread.
     *
     * @param thread the target thread
     * @return {@code true} if an active delay for {@code thread} was canceled; {@code false}
     *     otherwise
     * @throws NullPointerException if {@code thread} is {@code null}
     * @throws IllegalStateException if this service is closed
     */
    boolean cancel(Thread thread);

    /**
     * Cancels all active delays managed by this service.
     *
     * @return {@code true} if at least one active delay was canceled; {@code false} otherwise
     * @throws IllegalStateException if this service is closed
     */
    boolean cancelAll();

    /**
     * Resets the delayer associated with the current thread from a canceled or interrupted state
     * back to idle so it can be reused for subsequent delays, clearing any stale interrupt flag on
     * the thread.
     *
     * @return {@code true} if a delayer for the current thread was reset; {@code false} otherwise
     * @throws IllegalStateException if this service is closed
     */
    boolean reset();

    /**
     * Closes the service, forcefully canceling all active delays and releasing all thread contexts.
     */
    @Override
    void close();
}
