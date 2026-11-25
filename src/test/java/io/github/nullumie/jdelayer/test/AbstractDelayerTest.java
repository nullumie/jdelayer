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
import io.github.nullumie.jdelayer.CanceledDelayException;
import io.github.nullumie.jdelayer.InterruptedDelayException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import java.util.function.LongConsumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class AbstractDelayerTest {

    @FunctionalInterface
    private interface DelayerCall {
        void invoke(TestDelayer d) throws InterruptedDelayException, CanceledDelayException;
    }

    private TestDelayer delayer;

    private static class TestDelayer extends AbstractDelayer {
        private LongConsumer onDelayAction;

        TestDelayer(Thread thread) {
            super(thread);
        }

        void setOnDelayAction(Runnable action) {
            this.onDelayAction = action != null ? deadline -> action.run() : null;
        }

        void setOnDelayDeadlineAction(LongConsumer action) {
            this.onDelayAction = action;
        }

        @Override
        protected void onDelay(long deadline) {
            if (onDelayAction != null) {
                onDelayAction.accept(deadline);
            } else {
                LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1));
            }
        }
    }

    @BeforeEach
    void setUp() {
        delayer = new TestDelayer(Thread.currentThread());
    }

    private static Throwable runOffThread(Runnable task) throws InterruptedException {
        AtomicReference<Throwable> thrown = new AtomicReference<>();
        Thread other =
                new Thread(
                        () -> {
                            try {
                                task.run();
                            } catch (Throwable t) {
                                thrown.set(t);
                            }
                        });
        other.start();
        other.join();
        return thrown.get();
    }

    @Nested
    @DisplayName("Initialization & Thread Validation")
    class ValidationTests {

        static Stream<Named<DelayerCall>> threadBoundCalls() {
            return Stream.of(
                    Named.of("delay(duration, unit)", d -> d.delay(100, TimeUnit.MILLISECONDS)),
                    Named.of(
                            "delay(start, duration, unit)",
                            d -> d.delay(System.nanoTime(), 100, TimeUnit.MILLISECONDS)),
                    Named.of(
                            "delay(deadline)",
                            d -> d.delay(System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(100))),
                    Named.of("reset()", TestDelayer::reset));
        }

        @Test
        @DisplayName("Should throw NullPointerException for null thread or null TimeUnit")
        void nullArguments_throwNullPointerException() {
            assertThrows(NullPointerException.class, () -> new TestDelayer(null));
            assertThrows(NullPointerException.class, () -> delayer.delay(100, null));
            assertThrows(
                    NullPointerException.class, () -> delayer.delay(System.nanoTime(), 100, null));
        }

        @Test
        @DisplayName("Should initialize in IDLE state")
        void initialStates_areFalse() {
            assertFalse(delayer.isDelaying());
            assertFalse(delayer.isCanceled());
            assertFalse(delayer.isInterrupted());
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("threadBoundCalls")
        @DisplayName("Should throw WrongThreadException when called from unauthorized thread")
        void boundMethods_fromWrongThread_throwWrongThreadException(DelayerCall call)
                throws InterruptedException {
            Throwable thrown =
                    runOffThread(
                            () ->
                                    assertThrows(
                                            WrongThreadException.class,
                                            () -> call.invoke(delayer)));
            assertNull(thrown);
        }
    }

    @Nested
    @DisplayName("Delay Execution & Boundaries")
    class DelayExecutionTests {

        static Stream<Named<DelayerCall>> immediateCompletionCalls() {
            return Stream.of(
                    Named.of("zero duration", d -> d.delay(0, TimeUnit.MILLISECONDS)),
                    Named.of("negative duration", d -> d.delay(-10, TimeUnit.MILLISECONDS)),
                    Named.of(
                            "zero duration with start",
                            d -> d.delay(System.nanoTime(), 0, TimeUnit.MILLISECONDS)),
                    Named.of(
                            "negative duration with start",
                            d -> d.delay(System.nanoTime(), -10, TimeUnit.MILLISECONDS)),
                    Named.of("past deadline", d -> d.delay(System.nanoTime() - 1_000_000)),
                    Named.of("current deadline", d -> d.delay(System.nanoTime())),
                    Named.of(
                            "already elapsed window",
                            d ->
                                    d.delay(
                                            System.nanoTime() - TimeUnit.MILLISECONDS.toNanos(50),
                                            10,
                                            TimeUnit.MILLISECONDS)));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("immediateCompletionCalls")
        @DisplayName(
                "Should return immediately without invoking onDelay for expired/non-positive inputs")
        void nonPositiveOrExpired_returnsImmediatelyWithoutStep(DelayerCall call) {
            AtomicBoolean executed = new AtomicBoolean(false);
            delayer.setOnDelayAction(() -> executed.set(true));

            assertDoesNotThrow(() -> call.invoke(delayer));
            assertFalse(executed.get());
            assertFalse(delayer.isDelaying());
        }

        @Test
        @DisplayName("Should execute delay overloads and track DELAYING state accurately")
        void validDelays_executeAndTrackState() {
            AtomicBoolean wasDelaying = new AtomicBoolean(false);
            delayer.setOnDelayAction(() -> wasDelaying.set(delayer.isDelaying()));

            assertDoesNotThrow(() -> delayer.delay(System.nanoTime(), 5, TimeUnit.MILLISECONDS));
            assertTrue(wasDelaying.get());
            assertFalse(delayer.isDelaying());

            wasDelaying.set(false);
            assertDoesNotThrow(
                    () -> delayer.delay(System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(5)));
            assertTrue(wasDelaying.get());
            assertFalse(delayer.isDelaying());
        }

        @Test
        @DisplayName("Should cap step duration at Long.MAX_VALUE / 2 without numeric overflow")
        void hugeDuration_capsStepSafely() {
            AtomicLong capturedDeadline = new AtomicLong();
            long before = System.nanoTime();

            delayer.setOnDelayDeadlineAction(
                    deadline -> {
                        capturedDeadline.set(deadline);
                        delayer.cancel();
                    });

            assertThrows(
                    CanceledDelayException.class,
                    () -> delayer.delay(before, Long.MAX_VALUE, TimeUnit.DAYS));

            long step = capturedDeadline.get() - before;
            assertTrue(step > 0 && step <= (Long.MAX_VALUE / 2) + TimeUnit.SECONDS.toNanos(1));
        }

        @Test
        @DisplayName("Should throw IllegalStateException on re-entrant delay call")
        void reentrantDelay_throwsIllegalStateException() {
            delayer.setOnDelayAction(
                    () ->
                            assertThrows(
                                    IllegalStateException.class,
                                    () -> delayer.delay(5, TimeUnit.MILLISECONDS)));

            assertDoesNotThrow(() -> delayer.delay(5, TimeUnit.MILLISECONDS));
            assertFalse(delayer.isDelaying());
        }
    }

    @Nested
    @DisplayName("Interruption, Cancellation & Reset")
    class LifecycleTests {

        @Test
        @DisplayName("Should return false when cancel or reset is called in IDLE state")
        void cancelAndReset_whenIdle_returnFalse() {
            assertFalse(delayer.cancel());
            assertFalse(delayer.isCanceled());
            assertFalse(delayer.reset());
        }

        @Test
        @DisplayName("Should throw InterruptedDelayException if thread is interrupted beforehand")
        void preInterruptedThread_throwsImmediatelyAndRejectsSubsequentDelays() {
            Thread.currentThread().interrupt();
            try {
                assertThrows(
                        InterruptedDelayException.class,
                        () -> delayer.delay(50, TimeUnit.MILLISECONDS));
                assertTrue(delayer.isInterrupted());
                assertFalse(delayer.isDelaying());

                // Subsequent calls while still INTERRUPTED must also fail
                assertThrows(
                        InterruptedDelayException.class,
                        () -> delayer.delay(5, TimeUnit.MILLISECONDS));
            } finally {
                Thread.interrupted();
            }
        }

        @Test
        @DisplayName(
                "Should transition to INTERRUPTED when interrupted mid-delay by another thread")
        void externalInterrupt_duringDelay_transitionsToInterrupted() throws Exception {
            CountDownLatch entered = new CountDownLatch(1);
            AtomicReference<Throwable> thrown = new AtomicReference<>();
            AtomicReference<TestDelayer> workerDelayer = new AtomicReference<>();

            Thread worker =
                    new Thread(
                            () -> {
                                TestDelayer d = new TestDelayer(Thread.currentThread());
                                workerDelayer.set(d);
                                d.setOnDelayAction(
                                        () -> {
                                            entered.countDown();
                                            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(5));
                                        });
                                try {
                                    d.delay(10, TimeUnit.SECONDS);
                                } catch (Throwable t) {
                                    thrown.set(t);
                                }
                            });

            worker.start();
            assertTrue(entered.await(2, TimeUnit.SECONDS));

            worker.interrupt();
            worker.join(2000);

            assertFalse(worker.isAlive());
            assertInstanceOf(InterruptedDelayException.class, thrown.get());
            assertTrue(workerDelayer.get().isInterrupted());
            assertFalse(workerDelayer.get().isDelaying());
        }

        @Test
        @DisplayName("Should cancel active delay from another thread and reject subsequent delays")
        void externalCancel_duringDelay_cancelsAndInterrupts() throws Exception {
            CountDownLatch entered = new CountDownLatch(1);
            AtomicReference<Throwable> thrown = new AtomicReference<>();
            AtomicReference<TestDelayer> workerDelayer = new AtomicReference<>();

            Thread worker =
                    new Thread(
                            () -> {
                                TestDelayer d = new TestDelayer(Thread.currentThread());
                                workerDelayer.set(d);
                                d.setOnDelayAction(
                                        () -> {
                                            entered.countDown();
                                            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(5));
                                        });
                                try {
                                    d.delay(10, TimeUnit.SECONDS);
                                } catch (Throwable t) {
                                    thrown.set(t);
                                }
                                // Subsequent call while CANCELED must also throw
                                // CanceledDelayException
                                assertThrows(
                                        CanceledDelayException.class,
                                        () -> d.delay(5, TimeUnit.MILLISECONDS));
                            });

            worker.start();
            assertTrue(entered.await(2, TimeUnit.SECONDS));

            assertTrue(workerDelayer.get().cancel());
            worker.join(2000);

            assertFalse(worker.isAlive());
            assertInstanceOf(CanceledDelayException.class, thrown.get());
            assertTrue(workerDelayer.get().isCanceled());
            assertFalse(workerDelayer.get().isDelaying());
        }

        @Test
        @DisplayName(
                "Should reset CANCELED and INTERRUPTED states back to IDLE and clear interrupt flag")
        void reset_fromTerminalStates_restoresIdleAndAllowsReuse() {
            // 1. Reset from CANCELED
            delayer.setOnDelayAction(delayer::cancel);
            assertThrows(
                    CanceledDelayException.class, () -> delayer.delay(50, TimeUnit.MILLISECONDS));
            assertTrue(delayer.isCanceled());

            assertTrue(delayer.reset());
            assertFalse(delayer.isCanceled());
            assertFalse(Thread.currentThread().isInterrupted());

            delayer.setOnDelayAction(null);
            assertDoesNotThrow(() -> delayer.delay(2, TimeUnit.MILLISECONDS));

            // 2. Reset from INTERRUPTED
            Thread.currentThread().interrupt();
            assertThrows(
                    InterruptedDelayException.class,
                    () -> delayer.delay(50, TimeUnit.MILLISECONDS));
            assertTrue(delayer.isInterrupted());

            assertTrue(delayer.reset());
            assertFalse(delayer.isInterrupted());
            assertFalse(Thread.currentThread().isInterrupted());
            assertDoesNotThrow(() -> delayer.delay(2, TimeUnit.MILLISECONDS));
        }
    }
}
