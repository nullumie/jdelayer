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

/**
 * A {@link DelayerService} implementation that provisions adaptive, self-tuning delay mechanisms.
 */
public final class AdaptiveDelayerService extends AbstractDelayerService {

    private final long minSpinWindow;
    private final long maxSpinWindow;
    private final double spinThresholdFactor;
    private final double resolutionDecreaseSmoothing;
    private final double resolutionIncreaseSmoothing;

    /**
     * Constructs an {@code AdaptiveDelayerService} with the specified calibration profile.
     *
     * @param minSpinWindow minimum spin-wait window in nanoseconds
     * @param maxSpinWindow maximum spin-wait window in nanoseconds
     * @param spinThresholdFactor multiplier factor used to determine transition boundary
     * @param resolutionDecreaseSmoothing downward scaling coefficient for spin-window calibration
     * @param resolutionIncreaseSmoothing upward scaling coefficient for spin-window calibration
     * @throws IllegalArgumentException if parameters fall outside valid bounds
     */
    public AdaptiveDelayerService(
            long minSpinWindow,
            long maxSpinWindow,
            double spinThresholdFactor,
            double resolutionDecreaseSmoothing,
            double resolutionIncreaseSmoothing) {
        this.minSpinWindow = AdaptiveDelayer.validateMinSpinWindow(minSpinWindow);
        this.maxSpinWindow = AdaptiveDelayer.validateMaxSpinWindow(minSpinWindow, maxSpinWindow);
        this.spinThresholdFactor = AdaptiveDelayer.validateFactorRange(spinThresholdFactor);
        this.resolutionDecreaseSmoothing =
                AdaptiveDelayer.validateFactorRange(resolutionDecreaseSmoothing);
        this.resolutionIncreaseSmoothing =
                AdaptiveDelayer.validateFactorRange(resolutionIncreaseSmoothing);
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
    public double getResolutionDecreaseSmoothing() {
        return resolutionDecreaseSmoothing;
    }

    /**
     * Returns the upward scaling smoothing coefficient.
     *
     * @return the increase smoothing coefficient
     */
    public double getResolutionIncreaseSmoothing() {
        return resolutionIncreaseSmoothing;
    }

    /**
     * Returns the current calibrated spin-wait window in nanoseconds for the specified thread.
     *
     * @param thread the target thread
     * @return the active spin window, or {@code 0} if no adaptive delayer exists for the thread
     */
    public long getSpinWindow(Thread thread) {
        Delayer delayer = getDelayer(thread);
        if (delayer instanceof AdaptiveDelayer adaptiveDelayer) {
            return adaptiveDelayer.getSpinWindow();
        }
        return 0;
    }

    @Override
    protected Delayer onProvideDelayer(Thread thread) {
        return new AdaptiveDelayer(
                thread,
                minSpinWindow,
                maxSpinWindow,
                spinThresholdFactor,
                resolutionDecreaseSmoothing,
                resolutionIncreaseSmoothing);
    }

    @Override
    public String toString() {
        return "AdaptiveDelayerService["
                + "minSpinWindow="
                + minSpinWindow
                + ", maxSpinWindow="
                + maxSpinWindow
                + ", spinThresholdFactor="
                + spinThresholdFactor
                + ", resolutionDecreaseSmoothing="
                + resolutionDecreaseSmoothing
                + ", resolutionIncreaseSmoothing="
                + resolutionIncreaseSmoothing
                + ']';
    }
}
