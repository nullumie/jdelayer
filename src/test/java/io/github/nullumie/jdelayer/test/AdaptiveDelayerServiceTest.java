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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import io.github.nullumie.jdelayer.AdaptiveDelayerService;
import io.github.nullumie.jdelayer.CanceledDelayException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class AdaptiveDelayerServiceTest {

    private AdaptiveDelayerService service;

    // Standard valid calibration parameters for testing
    private static final long MIN_SPIN = 1000L;
    private static final long MAX_SPIN = 1_000_000L;
    private static final double THRESHOLD_FACTOR = 0.5;
    private static final double DECREASE_SMOOTHING = 0.1;
    private static final double INCREASE_SMOOTHING = 0.1;

    @BeforeEach
    void setUp() {
        service =
                new AdaptiveDelayerService(
                        MIN_SPIN,
                        MAX_SPIN,
                        THRESHOLD_FACTOR,
                        DECREASE_SMOOTHING,
                        INCREASE_SMOOTHING);
    }

    @Nested
    @DisplayName("Constructor & Validation Tests")
    class ConstructorValidationTests {

        @Test
        @DisplayName("Should successfully construct with valid parameters and expose getters")
        void constructsSuccessfullyWithValidParameters() {
            assertEquals(MIN_SPIN, service.getMinSpinWindow());
            assertEquals(MAX_SPIN, service.getMaxSpinWindow());
            assertEquals(THRESHOLD_FACTOR, service.getSpinThresholdFactor());
            assertEquals(DECREASE_SMOOTHING, service.getResolutionDecreaseSmoothing());
            assertEquals(INCREASE_SMOOTHING, service.getResolutionIncreaseSmoothing());
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException on invalid parameter bounds")
        void throwsExceptionOnInvalidParameters() {
            // Passing negative or invalid min/max or factor ranges should trigger validation
            // exceptions
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            new AdaptiveDelayerService(
                                    -1L,
                                    MAX_SPIN,
                                    THRESHOLD_FACTOR,
                                    DECREASE_SMOOTHING,
                                    INCREASE_SMOOTHING));
        }
    }

    @Nested
    @DisplayName("Adaptive Execution & Spin Window Tests")
    class AdaptiveExecutionTests {

        @Test
        @DisplayName("Should return zero spin window for unmapped or non-adaptive threads")
        void returnsZeroSpinWindowForUnmappedThread() {
            Thread unmappedThread = new Thread(() -> {});
            assertEquals(0L, service.getSpinWindow(unmappedThread));
        }

        @Test
        @DisplayName("Should successfully execute a short delay and provision adaptive delayer")
        void executesAdaptiveDelaySuccessfully() {
            Thread currentThread = Thread.currentThread();

            assertFalse(service.hasDelayer(currentThread));
            assertEquals(0L, service.getSpinWindow(currentThread));

            assertDoesNotThrow(() -> service.delay(5, TimeUnit.MILLISECONDS));

            assertTrue(service.hasDelayer(currentThread));
            // Once provisioned and executed, the spin window should be accessible and within bounds
            long spinWindow = service.getSpinWindow(currentThread);
            assertTrue(spinWindow >= MIN_SPIN && spinWindow <= MAX_SPIN);
        }

        @Test
        @DisplayName("Should handle cancellation of a running adaptive delay")
        void cancelsAdaptiveDelay() throws InterruptedException {
            AtomicReference<Throwable> thrownException = new AtomicReference<>();

            Thread workerThread =
                    new Thread(
                            () -> {
                                try {
                                    service.delay(2, TimeUnit.SECONDS);
                                } catch (Throwable t) {
                                    thrownException.set(t);
                                }
                            },
                            "adaptive-worker");

            workerThread.start();

            long timeoutMillis = 2000;
            long start = System.currentTimeMillis();
            while (!service.isDelaying(workerThread)) {
                if (System.currentTimeMillis() - start > timeoutMillis) {
                    fail("Timed out waiting for worker thread to enter delay state");
                }
                Thread.sleep(10);
            }

            service.cancel(workerThread);
            workerThread.join(2000);

            assertTrue(service.isCanceled(workerThread));
            assertInstanceOf(CanceledDelayException.class, thrownException.get());
        }
    }

    @Nested
    @DisplayName("Service Lifecycle & Utility Tests")
    class LifecycleAndUtilityTests {

        @Test
        @DisplayName("Should report closed state and handle close cleanly")
        void handlesCloseCleanly() {
            assertFalse(service.isClosed());
            assertDoesNotThrow(service::close);
            assertTrue(service.isClosed());
        }

        @Test
        @DisplayName("Should provide a descriptive toString representation")
        void providesToStringRepresentation() {
            String str = service.toString();
            assertNotNull(str);
            assertTrue(str.contains("AdaptiveDelayerService"));
            assertTrue(str.contains("minSpinWindow=" + MIN_SPIN));
            assertTrue(str.contains("maxSpinWindow=" + MAX_SPIN));
        }
    }
}
