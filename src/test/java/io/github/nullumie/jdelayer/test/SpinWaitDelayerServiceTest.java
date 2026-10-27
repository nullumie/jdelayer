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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import io.github.nullumie.jdelayer.CanceledDelayException;
import io.github.nullumie.jdelayer.SpinWaitDelayerService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SpinWaitDelayerServiceTest {

    private SpinWaitDelayerService service;

    @BeforeEach
    void setUp() {
        service = new SpinWaitDelayerService();
    }

    @Nested
    @DisplayName("SpinWaitDelayerService Provisioning & Execution Tests")
    class SpinWaitExecutionTests {

        @Test
        @DisplayName("Should successfully provision and execute a short delay using spin-wait")
        void executesSpinWaitDelaySuccessfully() {
            Thread currentThread = Thread.currentThread();

            assertFalse(service.hasDelayer(currentThread));

            assertDoesNotThrow(() -> service.delay(5, TimeUnit.MILLISECONDS));

            assertTrue(service.hasDelayer(currentThread));
        }

        @Test
        @DisplayName("Should handle cancellation of a running spin-wait delay")
        void cancelsSpinWaitDelay() throws InterruptedException {
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
                            "spin-worker");

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
    @DisplayName("Service Lifecycle Tests")
    class LifecycleTests {

        @Test
        @DisplayName("Should report closed state and handle close cleanly")
        void handlesCloseCleanly() {
            assertFalse(service.isClosed());
            assertDoesNotThrow(service::close);
            assertTrue(service.isClosed());
        }
    }
}
