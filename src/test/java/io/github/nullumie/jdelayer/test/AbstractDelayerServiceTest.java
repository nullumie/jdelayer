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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nullumie.jdelayer.AbstractDelayer;
import io.github.nullumie.jdelayer.AbstractDelayerService;
import io.github.nullumie.jdelayer.CanceledDelayException;
import io.github.nullumie.jdelayer.Delayer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class AbstractDelayerServiceTest {

    private TestDelayerService service;

    private static class StubDelayer extends AbstractDelayer {
        private Runnable onDelayAction;

        public StubDelayer(Thread thread) {
            super(thread);
        }

        void setOnDelayAction(Runnable onDelayAction) {
            this.onDelayAction = onDelayAction;
        }

        @Override
        protected void onDelay(long deadline) {
            if (onDelayAction != null) {
                onDelayAction.run();
            }
            long remainingNanos = deadline - System.nanoTime();
            if (remainingNanos > 0) {
                LockSupport.parkNanos(
                        Math.min(remainingNanos, 10_000_000L)); // Park up to 10ms per iteration
            }
        }
    }

    private static class TestDelayerService extends AbstractDelayerService {
        private Runnable onProvideCallback;

        void setOnProvideCallback(Runnable callback) {
            this.onProvideCallback = callback;
        }

        @Override
        protected Delayer onProvideDelayer(Thread thread) {
            StubDelayer delayer = new StubDelayer(thread);
            if (onProvideCallback != null) {
                delayer.setOnDelayAction(onProvideCallback);
            }
            return delayer;
        }

        public Delayer exposeGetDelayer(Thread thread) {
            return getDelayer(thread);
        }
    }

    @BeforeEach
    void setUp() {
        service = new TestDelayerService();
    }

    @Nested
    @DisplayName("Delayer Registration & Provisioning Tests")
    class ProvisioningTests {

        @Test
        @DisplayName("Should lazily create and map Delayer on first delay request for thread")
        void lazilyProvisionsDelayer() {
            Thread currentThread = Thread.currentThread();

            assertFalse(service.hasDelayer(currentThread));
            assertNull(service.exposeGetDelayer(currentThread));

            assertDoesNotThrow(() -> service.delay(1, TimeUnit.MILLISECONDS));

            assertTrue(service.hasDelayer(currentThread));
            assertNotNull(service.exposeGetDelayer(currentThread));
        }

        @Test
        @DisplayName("Should lazily create and map Delayer when delay(deadline) is invoked")
        void lazilyProvisionsDelayerWithDeadline() {
            Thread currentThread = Thread.currentThread();
            long futureDeadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(10);

            assertFalse(service.hasDelayer(currentThread));

            assertDoesNotThrow(() -> service.delay(futureDeadline));

            assertTrue(service.hasDelayer(currentThread));
            assertNotNull(service.exposeGetDelayer(currentThread));
        }

        @Test
        @DisplayName(
                "Should throw NullPointerException when passing null thread to registry methods")
        void throwsNpeOnNullThread() {
            assertThrows(NullPointerException.class, () -> service.hasDelayer(null));
            assertThrows(NullPointerException.class, () -> service.isDelaying(null));
            assertThrows(NullPointerException.class, () -> service.isCanceled(null));
            assertThrows(NullPointerException.class, () -> service.isInterrupted(null));
            assertThrows(NullPointerException.class, () -> service.cancel(null));
            assertThrows(NullPointerException.class, () -> service.release(null));
            assertThrows(NullPointerException.class, () -> service.exposeGetDelayer(null));
        }

        @Test
        @DisplayName("Should return false/null for status queries on unmapped threads")
        void returnsDefaultStatusForUnmappedThread() {
            Thread otherThread = new Thread(() -> {});

            assertFalse(service.hasDelayer(otherThread));
            assertFalse(service.isDelaying(otherThread));
            assertFalse(service.isCanceled(otherThread));
            assertFalse(service.isInterrupted(otherThread));
            assertNull(service.exposeGetDelayer(otherThread));
        }
    }

    @Nested
    @DisplayName("Delay Execution & Delegation Tests")
    class DelayExecutionTests {

        @Test
        @DisplayName("Should return immediately without registering delayer when duration is <= 0")
        void returnsImmediatelyOnNonPositiveDuration() {
            assertDoesNotThrow(() -> service.delay(0, TimeUnit.MILLISECONDS));
            assertDoesNotThrow(() -> service.delay(-100, TimeUnit.SECONDS));

            assertFalse(service.hasDelayer(Thread.currentThread()));
        }

        @Test
        @DisplayName("Should throw NullPointerException if TimeUnit parameter is null")
        void throwsNpeOnNullTimeUnit() {
            assertThrows(NullPointerException.class, () -> service.delay(10, null));
        }

        @Test
        @DisplayName("Should reflect delaying status during active delay execution")
        void reflectsDelayingStatus() throws InterruptedException {
            CountDownLatch enteredDelay = new CountDownLatch(1);
            AtomicBoolean wasDelayingReported = new AtomicBoolean(false);

            service.setOnProvideCallback(enteredDelay::countDown);

            Thread workerThread =
                    new Thread(
                            () -> {
                                try {
                                    service.delay(1, TimeUnit.SECONDS);
                                } catch (Throwable ignored) {
                                }
                            },
                            "worker-thread");

            workerThread.start();
            assertTrue(enteredDelay.await(2, TimeUnit.SECONDS));

            wasDelayingReported.set(service.isDelaying(workerThread));

            service.cancel(workerThread);
            workerThread.join();

            assertTrue(wasDelayingReported.get());
            assertFalse(service.isDelaying(workerThread));
        }
    }

    @Nested
    @DisplayName("Cancellation & Release Lifecycle Tests")
    class CancellationAndReleaseTests {

        @Test
        @DisplayName("Should safely ignore cancel(thread) if thread has no delayer")
        void ignoreCancelOnUnmappedThread() {
            Thread unmappedThread = new Thread(() -> {});
            assertDoesNotThrow(() -> service.cancel(unmappedThread));
        }

        @Test
        @DisplayName("Should cancel target thread's delayer when cancel(thread) is invoked")
        void cancelsSpecificThreadDelayer() throws InterruptedException {
            CountDownLatch delayStarted = new CountDownLatch(1);
            AtomicReference<Throwable> thrown = new AtomicReference<>();

            service.setOnProvideCallback(delayStarted::countDown);

            Thread worker =
                    new Thread(
                            () -> {
                                try {
                                    service.delay(2, TimeUnit.SECONDS);
                                } catch (Throwable t) {
                                    thrown.set(t);
                                }
                            });

            worker.start();
            assertTrue(delayStarted.await(2, TimeUnit.SECONDS));

            service.cancel(worker);
            worker.join();

            assertTrue(service.isCanceled(worker));
            assertInstanceOf(CanceledDelayException.class, thrown.get());
        }

        @Test
        @DisplayName("Should cancel all active delayers when cancelAll() is invoked")
        void cancelsAllRegisteredDelayers() throws InterruptedException {
            CountDownLatch startSignal = new CountDownLatch(2);

            service.setOnProvideCallback(startSignal::countDown);

            Thread w1 =
                    new Thread(
                            () -> {
                                try {
                                    service.delay(2, TimeUnit.SECONDS);
                                } catch (Throwable ignored) {
                                }
                            });

            Thread w2 =
                    new Thread(
                            () -> {
                                try {
                                    service.delay(2, TimeUnit.SECONDS);
                                } catch (Throwable ignored) {
                                }
                            });

            w1.start();
            w2.start();
            assertTrue(startSignal.await(2, TimeUnit.SECONDS));

            service.cancelAll();

            w1.join();
            w2.join();

            assertTrue(service.isCanceled(w1));
            assertTrue(service.isCanceled(w2));
        }

        @Test
        @DisplayName("Should remove delayer and invoke cancel() on release(thread)")
        void releasesThreadDelayer() {
            assertDoesNotThrow(() -> service.delay(1, TimeUnit.MILLISECONDS));
            Thread current = Thread.currentThread();

            Delayer delayer = service.exposeGetDelayer(current);
            assertTrue(service.hasDelayer(current));

            service.release(current);

            assertFalse(service.hasDelayer(current));
            assertTrue(delayer.isCanceled());
        }

        @Test
        @DisplayName("Should remove all delayers and cancel them on releaseAll()")
        void releasesAllDelayers() throws InterruptedException {
            CountDownLatch latch = new CountDownLatch(2);
            AtomicReference<Delayer> d1Ref = new AtomicReference<>();
            AtomicReference<Delayer> d2Ref = new AtomicReference<>();

            Thread w1 =
                    new Thread(
                            () -> {
                                try {
                                    service.delay(1, TimeUnit.MILLISECONDS);
                                    d1Ref.set(service.exposeGetDelayer(Thread.currentThread()));
                                    latch.countDown();
                                } catch (Throwable ignored) {
                                }
                            });

            Thread w2 =
                    new Thread(
                            () -> {
                                try {
                                    service.delay(1, TimeUnit.MILLISECONDS);
                                    d2Ref.set(service.exposeGetDelayer(Thread.currentThread()));
                                    latch.countDown();
                                } catch (Throwable ignored) {
                                }
                            });

            w1.start();
            w2.start();
            assertTrue(latch.await(2, TimeUnit.SECONDS));
            w1.join();
            w2.join();

            service.releaseAll();

            assertFalse(service.hasDelayer(w1));
            assertFalse(service.hasDelayer(w2));
            assertTrue(d1Ref.get().isCanceled());
            assertTrue(d2Ref.get().isCanceled());
        }
    }

    @Nested
    @DisplayName("Service Close & Shutdown Tests")
    class CloseLifecycleTests {

        @Test
        @DisplayName("Should report isClosed state accurately before and after close()")
        void reportsClosedStateCorrectly() {
            assertFalse(service.isClosed());
            service.close();
            assertTrue(service.isClosed());
        }

        @Test
        @DisplayName("Should cancel and release all registered delayers on close()")
        void closesAndReleasesEverything() {
            assertDoesNotThrow(() -> service.delay(1, TimeUnit.MILLISECONDS));
            Thread current = Thread.currentThread();
            Delayer delayer = service.exposeGetDelayer(current);

            service.close();

            assertTrue(service.isClosed());
            assertTrue(delayer.isCanceled());
        }

        @Test
        @DisplayName("Should be idempotent when calling close() multiple times")
        void idempotentClose() {
            assertDoesNotThrow(service::close);
            assertDoesNotThrow(service::close);
            assertTrue(service.isClosed());
        }

        @Test
        @DisplayName(
                "Should throw IllegalStateException on subsequent operations after service is closed")
        void throwsIllegalStateExceptionWhenClosed() {
            service.close();

            Thread current = Thread.currentThread();

            assertThrows(
                    IllegalStateException.class, () -> service.delay(10, TimeUnit.MILLISECONDS));
            assertThrows(
                    IllegalStateException.class, () -> service.delay(System.nanoTime() + 1000));
            assertThrows(IllegalStateException.class, () -> service.hasDelayer(current));
            assertThrows(IllegalStateException.class, () -> service.isDelaying(current));
            assertThrows(IllegalStateException.class, () -> service.isCanceled(current));
            assertThrows(IllegalStateException.class, () -> service.isInterrupted(current));
            assertThrows(IllegalStateException.class, () -> service.cancel(current));
            assertThrows(IllegalStateException.class, () -> service.cancelAll());
            assertThrows(IllegalStateException.class, () -> service.release(current));
            assertThrows(IllegalStateException.class, () -> service.releaseAll());
            assertThrows(IllegalStateException.class, () -> service.exposeGetDelayer(current));
        }
    }
}
