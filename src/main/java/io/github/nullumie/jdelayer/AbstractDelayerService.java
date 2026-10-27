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

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jspecify.annotations.Nullable;

/**
 * Skeletal implementation providing a thread-safe registry foundation for {@link DelayerService}
 * implementations.
 *
 * @see DelayerService
 * @see Delayer
 */
public abstract class AbstractDelayerService implements DelayerService, AutoCloseable {

    private static final String NULL_UNIT_MSG = "unit must not be null";
    private static final String NULL_THREAD_MSG = "thread must not be null";
    private static final String SHUTDOWN_MSG = "DelayerService has been closed";

    private final Map<Thread, Delayer> delayers = new ConcurrentHashMap<>();
    private final AtomicBoolean closed = new AtomicBoolean(false);

    /** Constructs a new {@code AbstractDelayerService}. */
    protected AbstractDelayerService() {}

    @Override
    public final boolean isClosed() {
        return closed.get();
    }

    @Override
    public final boolean isDelaying(Thread thread) {
        Delayer delayer = getDelayer(thread);
        return delayer != null && delayer.isDelaying();
    }

    @Override
    public final boolean isCanceled(Thread thread) {
        Delayer delayer = getDelayer(thread);
        return delayer != null && delayer.isCanceled();
    }

    @Override
    public final boolean isInterrupted(Thread thread) {
        Delayer delayer = getDelayer(thread);
        return delayer != null && delayer.isInterrupted();
    }

    @Override
    public final boolean hasDelayer(Thread thread) {
        ensureNotClosed();
        Objects.requireNonNull(thread, NULL_THREAD_MSG);
        return delayers.containsKey(thread);
    }

    @Override
    public final void delay(long duration, TimeUnit unit)
            throws InterruptedDelayException, CanceledDelayException {
        if (duration <= 0) return;
        long deadline =
                System.nanoTime() + Objects.requireNonNull(unit, NULL_UNIT_MSG).toNanos(duration);
        delay(deadline);
    }

    @Override
    public final void delay(long deadline)
            throws InterruptedDelayException, CanceledDelayException {
        ensureNotClosed();
        Thread thread = Thread.currentThread();
        Delayer delayer = delayers.computeIfAbsent(thread, this::onProvideDelayer);
        delayer.delay(deadline);
    }

    @Override
    public final void cancel(Thread thread) {
        Delayer delayer = getDelayer(thread);
        if (delayer == null) return;
        delayer.cancel();
    }

    @Override
    public final void cancelAll() {
        ensureNotClosed();
        for (Delayer delayer : delayers.values()) delayer.cancel();
    }

    @Override
    public final void release(Thread thread) {
        ensureNotClosed();
        Objects.requireNonNull(thread, NULL_THREAD_MSG);
        Delayer delayer = delayers.remove(thread);
        if (delayer != null) delayer.cancel();
    }

    @Override
    public final void releaseAll() {
        ensureNotClosed();
        releaseAllInternal();
    }

    @Override
    public final void close() {
        if (!closed.compareAndSet(false, true)) return;
        releaseAllInternal();
    }

    /**
     * Retrieves the active delayer for the target thread.
     *
     * @param thread the target thread
     * @return the mapped {@link Delayer}, or {@code null} if none exists
     * @throws NullPointerException if {@code thread} is {@code null}
     * @throws IllegalStateException if this service is closed
     */
    protected final @Nullable Delayer getDelayer(Thread thread) {
        ensureNotClosed();
        Objects.requireNonNull(thread, NULL_THREAD_MSG);
        return delayers.get(thread);
    }

    /**
     * Provisions a new delayer instance for an unmapped thread.
     *
     * @param thread the requesting thread
     * @return a new delayer instance
     */
    protected abstract Delayer onProvideDelayer(Thread thread);

    private void releaseAllInternal() {
        for (Delayer delayer : delayers.values()) delayer.cancel();
        delayers.clear();
    }

    private void ensureNotClosed() {
        if (!closed.get()) return;
        throw new IllegalStateException(SHUTDOWN_MSG);
    }
}
