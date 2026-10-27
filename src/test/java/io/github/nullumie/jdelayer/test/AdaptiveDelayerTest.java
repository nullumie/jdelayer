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
package io.github.nullumie.jdelayer.test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nullumie.jdelayer.AdaptiveDelayer;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AdaptiveDelayerTest {

    private static final long MIN_SPIN = 100L;
    private static final long MAX_SPIN = 1000L;
    private static final double THRESHOLD_FACTOR = 0.5;
    private static final double DECREASE_SMOOTHING = 0.1;
    private static final double INCREASE_SMOOTHING = 0.2;

    private AdaptiveDelayer delayer;
    private Thread targetThread;

    @BeforeEach
    void setUp() {
        targetThread = Thread.currentThread();
        delayer =
                new AdaptiveDelayer(
                        targetThread,
                        MIN_SPIN,
                        MAX_SPIN,
                        THRESHOLD_FACTOR,
                        DECREASE_SMOOTHING,
                        INCREASE_SMOOTHING);
    }

    @Nested
    @DisplayName("Constructor & Validation Tests")
    class InitializationTests {

        @Test
        @DisplayName("Should initialize fields and spinWindow correctly with valid parameters")
        void constructor_validParameters_setsFieldsCorrectly() {
            assertEquals(MIN_SPIN, delayer.getMinSpinWindow());
            assertEquals(MAX_SPIN, delayer.getMaxSpinWindow());
            assertEquals(THRESHOLD_FACTOR, delayer.getSpinThresholdFactor());
            assertEquals(DECREASE_SMOOTHING, delayer.getSpinWindowDecreaseSmoothing());
            assertEquals(INCREASE_SMOOTHING, delayer.getSpinWindowIncreaseSmoothing());
            assertEquals(MIN_SPIN, delayer.getSpinWindow()); // Initializes to minSpinWindow
        }

        @Test
        @DisplayName("Should throw NullPointerException when initialized with null thread")
        void constructor_nullThread_throwsException() {
            assertThrows(
                    NullPointerException.class,
                    () ->
                            new AdaptiveDelayer(
                                    null,
                                    MIN_SPIN,
                                    MAX_SPIN,
                                    THRESHOLD_FACTOR,
                                    DECREASE_SMOOTHING,
                                    INCREASE_SMOOTHING));
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException when minSpinWindow is negative")
        void constructor_negativeMinSpinWindow_throwsException() {
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            new AdaptiveDelayer(
                                    targetThread,
                                    -1L,
                                    MAX_SPIN,
                                    THRESHOLD_FACTOR,
                                    DECREASE_SMOOTHING,
                                    INCREASE_SMOOTHING));
        }

        @Test
        @DisplayName(
                "Should throw IllegalArgumentException when maxSpinWindow is smaller than minSpinWindow")
        void constructor_maxSmallerThanMinSpinWindow_throwsException() {
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            new AdaptiveDelayer(
                                    targetThread,
                                    500L,
                                    100L,
                                    THRESHOLD_FACTOR,
                                    DECREASE_SMOOTHING,
                                    INCREASE_SMOOTHING));
        }

        @ParameterizedTest
        @ValueSource(doubles = {-0.1, 1.1, 2.0, -100.0})
        @DisplayName(
                "Should throw IllegalArgumentException when factors are outside range [0.0, 1.0]")
        void constructor_invalidFactorRange_throwsException(double invalidFactor) {
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            new AdaptiveDelayer(
                                    targetThread,
                                    MIN_SPIN,
                                    MAX_SPIN,
                                    invalidFactor,
                                    DECREASE_SMOOTHING,
                                    INCREASE_SMOOTHING));
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            new AdaptiveDelayer(
                                    targetThread,
                                    MIN_SPIN,
                                    MAX_SPIN,
                                    THRESHOLD_FACTOR,
                                    invalidFactor,
                                    INCREASE_SMOOTHING));
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            new AdaptiveDelayer(
                                    targetThread,
                                    MIN_SPIN,
                                    MAX_SPIN,
                                    THRESHOLD_FACTOR,
                                    DECREASE_SMOOTHING,
                                    invalidFactor));
        }
    }

    @Nested
    @DisplayName("Delay Execution & Algorithm Tests")
    class DelayExecutionTests {

        @Test
        @DisplayName("Should execute delay successfully using adaptive hybrid algorithm")
        void delay_withAdaptiveDelayer_executesSuccessfully() {
            assertDoesNotThrow(() -> delayer.delay(5, TimeUnit.MILLISECONDS));
            assertFalse(delayer.isDelaying());
        }

        @Test
        @DisplayName("Should execute successfully when target deadline is immediate or in the past")
        void delay_immediateDeadline_executesSuccessfully() {
            long now = System.nanoTime();
            assertDoesNotThrow(() -> delayer.delay(now));
            assertFalse(delayer.isDelaying());
        }
    }

    @Nested
    @DisplayName("String Representation Tests")
    class ToStringTests {

        @Test
        @DisplayName(
                "Should return well-formatted string containing configuration and active spin window")
        void toString_containsExpectedFields() {
            String description = delayer.toString();

            assertNotNull(description);
            assertTrue(description.contains("AdaptiveDelayer"));
            assertTrue(description.contains("minSpinWindow=" + MIN_SPIN));
            assertTrue(description.contains("maxSpinWindow=" + MAX_SPIN));
            assertTrue(description.contains("spinThresholdFactor=" + THRESHOLD_FACTOR));
            assertTrue(description.contains("spinWindowDecreaseSmoothing=" + DECREASE_SMOOTHING));
            assertTrue(description.contains("spinWindowIncreaseSmoothing=" + INCREASE_SMOOTHING));
            assertTrue(description.contains("spinWindow=" + MIN_SPIN));
        }
    }
}
