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

/** Defines a synchronous boundary for blocking thread execution. */
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
     * Checks if the delay operation was interrupted.
     *
     * @return {@code true} if interrupted; {@code false} otherwise
     */
    boolean isInterrupted();

    /**
     * Blocks the bound thread for the specified duration.
     *
     * @param duration the duration to delay
     * @param unit the time unit of {@code duration}
     * @throws InterruptedDelayException if interrupted prior to or during the delay
     * @throws CanceledDelayException if canceled prior to or during the delay
     * @throws WrongThreadException if called from an unauthorized thread
     * @throws NullPointerException if {@code unit} is {@code null}
     */
    void delay(long duration, TimeUnit unit)
            throws InterruptedDelayException, CanceledDelayException;

    /**
     * Blocks the bound thread until the specified deadline.
     *
     * @param deadline absolute timestamp in nanoseconds relative to {@link System#nanoTime()}
     * @throws InterruptedDelayException if interrupted prior to or during the wait
     * @throws CanceledDelayException if canceled prior to or during the wait
     * @throws WrongThreadException if called from an unauthorized thread
     */
    void delay(long deadline) throws InterruptedDelayException, CanceledDelayException;

    /** Requests cancellation of the current execution. */
    void cancel();
}
