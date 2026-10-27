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
     * Checks if the delay operation of the specified thread was interrupted.
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
     * @throws NullPointerException if {@code thread} is {@code null}
     * @throws IllegalStateException if this service is closed
     */
    void release(Thread thread);

    /**
     * Releases all threads, canceling ongoing delays and clearing all internal contexts.
     *
     * @throws IllegalStateException if this service is closed
     */
    void releaseAll();

    /**
     * Releases the calling thread from this service.
     *
     * @see #release(Thread)
     */
    default void release() {
        release(Thread.currentThread());
    }

    /**
     * Blocks the current thread for the specified duration.
     *
     * @param duration the length of time to delay
     * @param unit the time unit of {@code duration}
     * @throws InterruptedDelayException if interrupted prior to or during the delay
     * @throws CanceledDelayException if canceled prior to or during the delay
     * @throws NullPointerException if {@code unit} is {@code null}
     * @throws IllegalStateException if this service is closed
     */
    void delay(long duration, TimeUnit unit)
            throws InterruptedDelayException, CanceledDelayException;

    /**
     * Blocks the current thread until the specified deadline.
     *
     * @param deadline absolute timestamp in nanoseconds relative to {@link System#nanoTime()}
     * @throws InterruptedDelayException if interrupted prior to or during the wait
     * @throws CanceledDelayException if canceled prior to or during the wait
     * @throws IllegalStateException if this service is closed
     */
    void delay(long deadline) throws InterruptedDelayException, CanceledDelayException;

    /**
     * Cancels any active delay for the specified thread.
     *
     * @param thread the target thread
     * @throws NullPointerException if {@code thread} is {@code null}
     * @throws IllegalStateException if this service is closed
     */
    void cancel(Thread thread);

    /**
     * Cancels all active delays managed by this service.
     *
     * @throws IllegalStateException if this service is closed
     */
    void cancelAll();

    /**
     * Closes the service, forcefully canceling all active delays and releasing all thread contexts.
     */
    @Override
    void close();
}
