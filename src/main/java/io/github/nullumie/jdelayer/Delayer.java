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

/** Defines a thread-bound synchronous boundary for blocking thread execution. */
public interface Delayer {

    /**
     * Checks if the bound thread is currently executing a delay.
     *
     * @return {@code true} if delaying; {@code false} otherwise
     */
    boolean isDelaying();

    /**
     * Checks if the delayer is currently in a canceled state.
     *
     * @return {@code true} if canceled; {@code false} otherwise
     */
    boolean isCanceled();

    /**
     * Checks if the delayer is currently in an interrupted state.
     *
     * @return {@code true} if interrupted; {@code false} otherwise
     */
    boolean isInterrupted();

    /**
     * Blocks the bound thread for the specified duration starting from the current time.
     *
     * @param duration the duration to delay
     * @param unit the time unit of {@code duration}
     * @throws InterruptedDelayException if interrupted during the delay or if already in an
     *     interrupted state
     * @throws CanceledDelayException if canceled during the delay or if already in a canceled state
     * @throws WrongThreadException if called from an unauthorized thread
     * @throws NullPointerException if {@code unit} is {@code null}
     */
    default void delay(long duration, TimeUnit unit)
            throws InterruptedDelayException, CanceledDelayException {
        long startTimeNanos = System.nanoTime();
        delay(startTimeNanos, duration, unit);
    }

    /**
     * Blocks the bound thread for the specified duration relative to a given start timestamp.
     *
     * @param startNanoTime the start timestamp in nanoseconds relative to {@link System#nanoTime()}
     * @param duration the duration to delay from {@code startNanoTime}
     * @param unit the time unit of {@code duration}
     * @throws InterruptedDelayException if interrupted during the delay or if already in an
     *     interrupted state
     * @throws CanceledDelayException if canceled during the delay or if already in a canceled state
     * @throws WrongThreadException if called from an unauthorized thread
     * @throws NullPointerException if {@code unit} is {@code null}
     */
    void delay(long startNanoTime, long duration, TimeUnit unit)
            throws InterruptedDelayException, CanceledDelayException;

    /**
     * Blocks the bound thread until the specified deadline.
     *
     * @param deadline absolute timestamp in nanoseconds relative to {@link System#nanoTime()}
     * @throws InterruptedDelayException if interrupted during the wait or if already in an
     *     interrupted state
     * @throws CanceledDelayException if canceled during the wait or if already in a canceled state
     * @throws WrongThreadException if called from an unauthorized thread
     */
    void delay(long deadline) throws InterruptedDelayException, CanceledDelayException;

    /**
     * Cancels an active delay execution and interrupts the bound thread.
     *
     * @return {@code true} if an active delay was canceled; {@code false} otherwise
     */
    boolean cancel();

    /**
     * Resets the delayer from a canceled or interrupted state back to idle so it can be reused for
     * subsequent delays, clearing any stale interrupt flag on the bound thread.
     *
     * @return {@code true} if the delayer was reset; {@code false} otherwise
     * @throws WrongThreadException if called from an unauthorized thread
     */
    boolean reset();
}
