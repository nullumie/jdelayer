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
 * High-precision {@link Delayer} implementation using an adaptive hybrid park/spin-wait algorithm.
 */
public final class AdaptiveDelayer extends AbstractDelayer {

    private final long minSpinWindow;
    private final long maxSpinWindow;
    private final double spinThresholdFactor;
    private final double spinWindowDecreaseSmoothing;
    private final double spinWindowIncreaseSmoothing;
    private volatile long spinWindow;

    /**
     * Constructs an {@code AdaptiveDelayer} bound to the specified thread.
     *
     * @param thread the authorized target thread
     * @param minSpinWindow minimum spin-wait window in nanoseconds
     * @param maxSpinWindow maximum spin-wait window in nanoseconds
     * @param spinThresholdFactor multiplier factor used to determine transition boundary
     * @param spinWindowDecreaseSmoothing downward scaling coefficient for spin-window calibration
     * @param spinWindowIncreaseSmoothing upward scaling coefficient for spin-window calibration
     * @throws NullPointerException if {@code thread} is {@code null}
     * @throws IllegalArgumentException if parameters fall outside valid bounds
     */
    public AdaptiveDelayer(
            Thread thread,
            long minSpinWindow,
            long maxSpinWindow,
            double spinThresholdFactor,
            double spinWindowDecreaseSmoothing,
            double spinWindowIncreaseSmoothing) {
        super(thread);
        this.minSpinWindow = validateMinSpinWindow(minSpinWindow);
        this.maxSpinWindow = validateMaxSpinWindow(this.minSpinWindow, maxSpinWindow);
        this.spinThresholdFactor = validateFactorRange(spinThresholdFactor);
        this.spinWindowDecreaseSmoothing = validateFactorRange(spinWindowDecreaseSmoothing);
        this.spinWindowIncreaseSmoothing = validateFactorRange(spinWindowIncreaseSmoothing);
        this.spinWindow = this.minSpinWindow;
    }

    /**
     * Returns the minimum spin-wait window in nanoseconds.
     *
     * @return the minimum spin window
     */
    public long getMinSpinWindow() {
        return minSpinWindow;
    }

    /**
     * Returns the maximum spin-wait window in nanoseconds.
     *
     * @return the maximum spin window
     */
    public long getMaxSpinWindow() {
        return maxSpinWindow;
    }

    /**
     * Returns the spin threshold factor coefficient.
     *
     * @return the threshold factor
     */
    public double getSpinThresholdFactor() {
        return spinThresholdFactor;
    }

    /**
     * Returns the downward scaling smoothing coefficient.
     *
     * @return the decrease smoothing coefficient
     */
    public double getSpinWindowDecreaseSmoothing() {
        return spinWindowDecreaseSmoothing;
    }

    /**
     * Returns the upward scaling smoothing coefficient.
     *
     * @return the increase smoothing coefficient
     */
    public double getSpinWindowIncreaseSmoothing() {
        return spinWindowIncreaseSmoothing;
    }

    /**
     * Returns the current calibrated spin-wait window in nanoseconds.
     *
     * @return the active spin window
     */
    public long getSpinWindow() {
        return spinWindow;
    }

    @Override
    protected void onDelay(long deadline) {
        long now = System.nanoTime();
        long minSpinWindow = this.minSpinWindow;
        long maxSpinWindow = this.maxSpinWindow;
        long currentSpinWindow = this.spinWindow;
        long timeoutRemainingTime = deadline - now;
        long dynamicSpinWindow =
                Math.max(minSpinWindow, (long) (currentSpinWindow * spinThresholdFactor));

        if (timeoutRemainingTime <= dynamicSpinWindow) {
            Thread.onSpinWait();
            return;
        }

        long windowReserve = Math.clamp(currentSpinWindow, dynamicSpinWindow, timeoutRemainingTime);
        long parkTargetTime = deadline - windowReserve;
        long requestedParkNanos = parkTargetTime - now;

        if (requestedParkNanos > 0) {
            LockSupport.parkNanos(requestedParkNanos);
            long actualWakeup = System.nanoTime();
            long actualParkDuration = actualWakeup - now;
            long osJitter = actualParkDuration - requestedParkNanos;

            if (osJitter >= 0) {
                long boundedJitter = Math.min(osJitter, maxSpinWindow);
                double smoothing =
                        (boundedJitter < currentSpinWindow)
                                ? spinWindowDecreaseSmoothing
                                : spinWindowIncreaseSmoothing;
                long nextSpinWindow =
                        (long) (currentSpinWindow * (1 - smoothing) + boundedJitter * smoothing);
                this.spinWindow = Math.clamp(nextSpinWindow, minSpinWindow, maxSpinWindow);
            }
        } else {
            Thread.onSpinWait();
        }
    }

    @Override
    public String toString() {
        return "AdaptiveDelayer["
                + "minSpinWindow="
                + minSpinWindow
                + ", maxSpinWindow="
                + maxSpinWindow
                + ", spinThresholdFactor="
                + spinThresholdFactor
                + ", spinWindowDecreaseSmoothing="
                + spinWindowDecreaseSmoothing
                + ", spinWindowIncreaseSmoothing="
                + spinWindowIncreaseSmoothing
                + ", spinWindow="
                + spinWindow
                + ']';
    }

    static long validateMinSpinWindow(long minSpinWindow) {
        if (minSpinWindow >= 0) return minSpinWindow;
        throw new IllegalArgumentException(
                "Invalid min spin window: Value cannot be negative. Provided: " + minSpinWindow);
    }

    static long validateMaxSpinWindow(long minSpinWindow, long maxSpinWindow) {
        if (maxSpinWindow >= minSpinWindow) return maxSpinWindow;
        throw new IllegalArgumentException(
                "Invalid spin window range: Max spin window ("
                        + maxSpinWindow
                        + ") cannot be smaller than min spin window ("
                        + minSpinWindow
                        + ").");
    }

    static double validateFactorRange(double factor) {
        if (factor >= 0.0d && factor <= 1.0d) return factor;
        throw new IllegalArgumentException(
                "Invalid factor: Value must be within the range [0.0, 1.0] (inclusive). Provided: "
                        + factor);
    }
}
