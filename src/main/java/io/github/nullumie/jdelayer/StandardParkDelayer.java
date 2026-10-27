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

import java.util.concurrent.locks.LockSupport;

/**
 * A {@link Delayer} implementation that suspends execution using {@link
 * LockSupport#parkNanos(long)}.
 */
public class StandardParkDelayer extends AbstractDelayer {

    /**
     * Constructs a new {@code StandardParkDelayer} bound to the specified thread.
     *
     * @param thread the authorized target thread
     * @throws NullPointerException if {@code thread} is {@code null}
     */
    public StandardParkDelayer(Thread thread) {
        super(thread);
    }

    @Override
    protected void onDelay(long deadline) {
        long nanos = deadline - System.nanoTime();
        LockSupport.parkNanos(nanos);
    }
}
