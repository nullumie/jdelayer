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

import static org.junit.jupiter.api.Assertions.*;

import io.github.nullumie.jdelayer.AbstractDelayer;
import io.github.nullumie.jdelayer.AbstractDelayerService;
import io.github.nullumie.jdelayer.CanceledDelayException;
import io.github.nullumie.jdelayer.Delayer;
import io.github.nullumie.jdelayer.InterruptedDelayException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class AbstractDelayerServiceTest {

    private TestDelayerService service;

    private static class StubDelayer extends AbstractDelayer {
        private volatile Runnable onStep;

        StubDelayer(Thread thread) {
            super(thread);
        }

        void setOnStep(Runnable onStep) {
            this.onStep = onStep;
        }

        @Override
        protected void onDelay(long deadline) {
            Runnable action = onStep;
            if (action != null) {
                action.run();
            } else {
                LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1));
            }
        }
    }

    private static class TestDelayerService extends AbstractDelayerService {
        private final AtomicInteger providedCount = new AtomicInteger();
        private volatile Consumer<StubDelayer> configurer;

        void setConfigurer(Consumer<StubDelayer> configurer) {
            this.configurer = configurer;
        }

        int getProvidedCount() {
            return providedCount.get();
        }

        @Override
        protected Delayer onProvideDelayer(Thread thread) {
            providedCount.incrementAndGet();
            StubDelayer delayer = new StubDelayer(thread);
            Consumer<StubDelayer> cfg = configurer;
            if (cfg != null) {
                cfg.accept(delayer);
            }
            return delayer;
        }
    }

    @BeforeEach
    void setUp() {
        service = new TestDelayerService();
    }

    @AfterEach
    void tearDown() {
        if (!service.isClosed()) {
            service.close();
        }
        Thread.interrupted();
    }

    @Nested
    @DisplayName("Null Validation & Unregistered Thread Queries")
    class ValidationAndDefaultsTests {

        static Stream<Named<Consumer<TestDelayerService>>> nullThreadCalls() {
            return Stream.of(
                    Named.of("isDelaying(null)", s -> s.isDelaying(null)),
                    Named.of("isCanceled(null)", s -> s.isCanceled(null)),
                    Named.of("isInterrupted(null)", s -> s.isInterrupted(null)),
                    Named.of("hasDelayer(null)", s -> s.hasDelayer(null)),
                    Named.of("cancel(null)", s -> s.cancel(null)),
                    Named.of("release(null)", s -> s.release(null)));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("nullThreadCalls")
        @DisplayName("Should throw NullPointerException when thread argument is null")
        void nullThread_throwsNullPointerException(Consumer<TestDelayerService> call) {
            assertThrows(NullPointerException.class, () -> call.accept(service));
        }

        @Test
        @DisplayName("Should throw NullPointerException when TimeUnit is null without provisioning")
        void nullTimeUnit_throwsNullPointerException() {
            assertThrows(
                    NullPointerException.class, () -> service.delay(System.nanoTime(), 10, null));
            assertFalse(service.hasDelayer(Thread.currentThread()));
        }

        @Test
        @DisplayName("Should throw NullPointerException when onProvideDelayer returns null")
        void nullProvidedDelayer_throwsNullPointerException() {
            AbstractDelayerService brokenService =
                    new AbstractDelayerService() {
                        @Override
                        protected Delayer onProvideDelayer(Thread thread) {
                            return null;
                        }
                    };

            NullPointerException ex =
                    assertThrows(
                            NullPointerException.class,
                            () -> brokenService.delay(System.nanoTime() + 1_000_000));
            assertEquals("onProvideDelayer must not return null", ex.getMessage());
            assertFalse(brokenService.hasDelayer(Thread.currentThread()));
        }

        @Test
        @DisplayName("Should return false for all queries and mutators on unregistered threads")
        void unregisteredThread_returnsFalseForAllPublicMethods() {
            Thread current = Thread.currentThread();
            assertFalse(service.isClosed());
            assertFalse(service.hasDelayer(current));
            assertFalse(service.isDelaying(current));
            assertFalse(service.isCanceled(current));
            assertFalse(service.isInterrupted(current));
            assertFalse(service.cancel(current));
            assertFalse(service.cancelAll());
            assertFalse(service.reset());
            assertFalse(service.release(current));
            assertFalse(service.releaseAll());
        }
    }

    @Nested
    @DisplayName("Provisioning, Delegation & Thread Isolation")
    class ProvisioningAndDelegationTests {

        @Test
        @DisplayName("Should provision once per thread across both delay overloads")
        void delay_provisionsOncePerThreadAndReusesDelayer() {
            Thread current = Thread.currentThread();

            assertDoesNotThrow(() -> service.delay(System.nanoTime(), 2, TimeUnit.MILLISECONDS));
            assertTrue(service.hasDelayer(current));
            assertEquals(1, service.getProvidedCount());

            assertDoesNotThrow(
                    () -> service.delay(System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(2)));
            assertEquals(1, service.getProvidedCount());
        }

        @Test
        @DisplayName("Should isolate delayer instances across different threads")
        void delay_fromMultipleThreads_isolatesDelayers() throws InterruptedException {
            Thread mainThread = Thread.currentThread();
            assertDoesNotThrow(() -> service.delay(System.nanoTime(), 1, TimeUnit.MILLISECONDS));

            AtomicReference<Thread> workerRef = new AtomicReference<>();
            Thread worker =
                    new Thread(
                            () -> {
                                workerRef.set(Thread.currentThread());
                                assertDoesNotThrow(
                                        () ->
                                                service.delay(
                                                        System.nanoTime(),
                                                        1,
                                                        TimeUnit.MILLISECONDS));
                            });
            worker.start();
            worker.join(2000);

            assertEquals(2, service.getProvidedCount());
            assertTrue(service.hasDelayer(mainThread));
            assertTrue(service.hasDelayer(workerRef.get()));
        }

        @Test
        @DisplayName("Should accurately delegate isDelaying, isCanceled, cancel, and reset")
        void stateQueriesAndControl_delegateToThreadDelayer() {
            Thread current = Thread.currentThread();

            service.setConfigurer(
                    delayer ->
                            delayer.setOnStep(
                                    () -> {
                                        assertTrue(service.isDelaying(current));
                                        assertTrue(service.cancel(current));
                                    }));

            assertThrows(
                    CanceledDelayException.class,
                    () -> service.delay(System.nanoTime(), 50, TimeUnit.MILLISECONDS));

            assertFalse(service.isDelaying(current));
            assertTrue(service.isCanceled(current));
            assertTrue(service.reset());
            assertFalse(service.isCanceled(current));
            assertFalse(service.reset());
        }

        @Test
        @DisplayName("Should accurately delegate isInterrupted when bound thread is interrupted")
        void isInterrupted_delegatesToThreadDelayer() {
            Thread current = Thread.currentThread();
            Thread.currentThread().interrupt();

            assertThrows(
                    InterruptedDelayException.class,
                    () -> service.delay(System.nanoTime(), 50, TimeUnit.MILLISECONDS));

            assertTrue(service.isInterrupted(current));
            assertTrue(service.reset());
            assertFalse(service.isInterrupted(current));
        }
    }

    @Nested
    @DisplayName("Bulk Operations & Release Semantics")
    class BulkOperationsTests {

        @Test
        @DisplayName("Should cancel active delays via cancelAll while keeping delayers registered")
        void cancelAll_cancelsActiveDelaysAndKeepsRegistrations() throws Exception {
            CountDownLatch entered = new CountDownLatch(1);
            AtomicReference<Throwable> thrown = new AtomicReference<>();

            service.setConfigurer(
                    delayer ->
                            delayer.setOnStep(
                                    () -> {
                                        entered.countDown();
                                        LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(5));
                                    }));

            Thread worker =
                    new Thread(
                            () -> {
                                try {
                                    service.delay(System.nanoTime(), 10, TimeUnit.SECONDS);
                                } catch (Throwable t) {
                                    thrown.set(t);
                                }
                            });

            worker.start();
            assertTrue(entered.await(2, TimeUnit.SECONDS));

            assertTrue(service.cancelAll());
            worker.join(2000);

            assertInstanceOf(CanceledDelayException.class, thrown.get());
            assertTrue(service.hasDelayer(worker));
            assertTrue(service.isCanceled(worker));
            assertFalse(service.cancelAll());
        }

        @Test
        @DisplayName(
                "Should return boolean on release(thread), cancel active delay, and remove entry")
        void release_cancelsAndRemovesSingleThread() throws Exception {
            CountDownLatch entered = new CountDownLatch(1);
            AtomicReference<Throwable> thrown = new AtomicReference<>();

            service.setConfigurer(
                    delayer ->
                            delayer.setOnStep(
                                    () -> {
                                        entered.countDown();
                                        LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(5));
                                    }));

            Thread worker =
                    new Thread(
                            () -> {
                                try {
                                    service.delay(System.nanoTime(), 10, TimeUnit.SECONDS);
                                } catch (Throwable t) {
                                    thrown.set(t);
                                }
                            });

            worker.start();
            assertTrue(entered.await(2, TimeUnit.SECONDS));

            assertTrue(service.release(worker));
            assertFalse(service.release(worker));
            worker.join(2000);

            assertInstanceOf(CanceledDelayException.class, thrown.get());
            assertFalse(service.hasDelayer(worker));
        }

        @Test
        @DisplayName("Should return boolean on releaseAll(), cancel all delays, and clear registry")
        void releaseAll_cancelsAllAndClearsRegistry() throws Exception {
            assertFalse(service.releaseAll());

            CountDownLatch entered = new CountDownLatch(1);
            AtomicReference<Throwable> thrown = new AtomicReference<>();

            service.setConfigurer(
                    delayer ->
                            delayer.setOnStep(
                                    () -> {
                                        entered.countDown();
                                        LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(5));
                                    }));

            Thread worker =
                    new Thread(
                            () -> {
                                try {
                                    service.delay(System.nanoTime(), 10, TimeUnit.SECONDS);
                                } catch (Throwable t) {
                                    thrown.set(t);
                                }
                            });

            worker.start();
            assertTrue(entered.await(2, TimeUnit.SECONDS));

            assertTrue(service.releaseAll());
            assertFalse(service.releaseAll());
            worker.join(2000);

            assertInstanceOf(CanceledDelayException.class, thrown.get());
            assertFalse(service.hasDelayer(worker));
        }
    }

    @Nested
    @DisplayName("Shutdown & AutoCloseable Lifecycle")
    class CloseLifecycleTests {

        static Stream<Named<Consumer<TestDelayerService>>> postCloseOperations() {
            Thread t = Thread.currentThread();
            return Stream.of(
                    Named.of("isDelaying(thread)", s -> s.isDelaying(t)),
                    Named.of("isCanceled(thread)", s -> s.isCanceled(t)),
                    Named.of("isInterrupted(thread)", s -> s.isInterrupted(t)),
                    Named.of("hasDelayer(thread)", s -> s.hasDelayer(t)),
                    Named.of(
                            "delay(start, duration, unit)",
                            s -> {
                                try {
                                    s.delay(System.nanoTime(), 10, TimeUnit.MILLISECONDS);
                                } catch (InterruptedDelayException | CanceledDelayException e) {
                                    fail(e);
                                }
                            }),
                    Named.of(
                            "delay(deadline)",
                            s -> {
                                try {
                                    s.delay(System.nanoTime() + 1_000_000);
                                } catch (InterruptedDelayException | CanceledDelayException e) {
                                    fail(e);
                                }
                            }),
                    Named.of("cancel(thread)", s -> s.cancel(t)),
                    Named.of("cancelAll()", TestDelayerService::cancelAll),
                    Named.of("reset()", TestDelayerService::reset),
                    Named.of("release(thread)", s -> s.release(t)),
                    Named.of("releaseAll()", TestDelayerService::releaseAll));
        }

        @Test
        @DisplayName("Should close idempotently and cancel active worker delays")
        void close_isIdempotentAndCancelsActiveDelays() throws Exception {
            CountDownLatch entered = new CountDownLatch(1);
            AtomicReference<Throwable> thrown = new AtomicReference<>();

            service.setConfigurer(
                    delayer ->
                            delayer.setOnStep(
                                    () -> {
                                        entered.countDown();
                                        LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(5));
                                    }));

            Thread worker =
                    new Thread(
                            () -> {
                                try {
                                    service.delay(System.nanoTime(), 10, TimeUnit.SECONDS);
                                } catch (Throwable t) {
                                    thrown.set(t);
                                }
                            });

            worker.start();
            assertTrue(entered.await(2, TimeUnit.SECONDS));

            service.close();
            assertTrue(service.isClosed());
            assertDoesNotThrow(() -> service.close());

            worker.join(2000);
            assertInstanceOf(CanceledDelayException.class, thrown.get());
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("postCloseOperations")
        @DisplayName("Should throw IllegalStateException for every public operation after close")
        void operationsAfterClose_throwIllegalStateException(Consumer<TestDelayerService> call) {
            service.close();
            assertThrows(IllegalStateException.class, () -> call.accept(service));
        }
    }
}
