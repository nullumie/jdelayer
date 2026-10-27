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
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class AbstractDelayerTest {

    private TestDelayer delayer;
    private Thread targetThread;

    /** Concrete test implementation of AbstractDelayer. */
    private static class TestDelayer extends AbstractDelayer {
        private Runnable onDelayAction;

        TestDelayer(Thread thread) {
            super(thread);
        }

        void setOnDelayAction(Runnable onDelayAction) {
            this.onDelayAction = onDelayAction;
        }

        @Override
        protected void onDelay(long deadline) {
            if (onDelayAction != null) {
                onDelayAction.run();
            } else {
                Thread.yield();
            }
        }
    }

    @BeforeEach
    void setUp() {
        targetThread = Thread.currentThread();
        delayer = new TestDelayer(targetThread);
    }

    @Nested
    @DisplayName("Constructor & Initial State Tests")
    class InitializationTests {

        @Test
        @DisplayName("Should throw NullPointerException when initialized with null thread")
        void constructor_nullThread_throwsException() {
            assertThrows(NullPointerException.class, () -> new TestDelayer(null));
        }

        @Test
        @DisplayName("Should initialize in IDLE state")
        void initialStates_areFalse() {
            assertFalse(delayer.isDelaying());
            assertFalse(delayer.isCanceled());
            assertFalse(delayer.isInterrupted());
        }
    }

    @Nested
    @DisplayName("Thread Affinity & Validation Tests")
    class ThreadValidationTests {

        @Test
        @DisplayName("Should throw WrongThreadException when delay called from wrong thread")
        void delay_fromWrongThread_throwsWrongThreadException() throws InterruptedException {
            AtomicReference<Throwable> thrown = new AtomicReference<>();
            Thread wrongThread =
                    new Thread(
                            () -> {
                                try {
                                    delayer.delay(100, TimeUnit.MILLISECONDS);
                                } catch (Throwable t) {
                                    thrown.set(t);
                                }
                            });

            wrongThread.start();
            wrongThread.join();

            assertInstanceOf(WrongThreadException.class, thrown.get());
        }

        @Test
        @DisplayName("Should throw NullPointerException when TimeUnit is null")
        void delay_nullTimeUnit_throwsException() {
            assertThrows(NullPointerException.class, () -> delayer.delay(100, null));
        }
    }

    @Nested
    @DisplayName("Delay Execution & Boundary Tests")
    class DelayExecutionTests {

        @Test
        @DisplayName("Should complete immediately for non-positive duration")
        void delay_nonPositiveDuration_returnsImmediately() {
            assertDoesNotThrow(() -> delayer.delay(0, TimeUnit.MILLISECONDS));
            assertDoesNotThrow(() -> delayer.delay(-10, TimeUnit.MILLISECONDS));

            assertFalse(delayer.isDelaying());
        }

        @Test
        @DisplayName("Should complete immediately if target deadline is in the past or now")
        void delay_deadlineInPast_returnsImmediately() {
            long now = System.nanoTime();
            assertDoesNotThrow(() -> delayer.delay(now - 1_000_000));
            assertDoesNotThrow(() -> delayer.delay(now));

            assertFalse(delayer.isDelaying());
        }

        @Test
        @DisplayName("Should execute delay using absolute deadline overload")
        void delay_withDeadline_executesUntilExpired() {
            AtomicBoolean executed = new AtomicBoolean(false);
            delayer.setOnDelayAction(() -> executed.set(true));

            long futureDeadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(10);
            assertDoesNotThrow(() -> delayer.delay(futureDeadline));

            assertTrue(executed.get());
            assertFalse(delayer.isDelaying());
        }

        @Test
        @DisplayName("Should handle long duration saturation without throwing numeric overflow")
        void delay_overflowDuration_capsAtLongMax() throws InterruptedException {
            AtomicBoolean executed = new AtomicBoolean(false);
            Thread worker =
                    new Thread(
                            () -> {
                                TestDelayer overflowDelayer =
                                        new TestDelayer(Thread.currentThread());
                                overflowDelayer.setOnDelayAction(
                                        () -> {
                                            executed.set(true);
                                            overflowDelayer.cancel(); // Exit Long.MAX_VALUE loop
                                        });

                                assertThrows(
                                        CanceledDelayException.class,
                                        () -> overflowDelayer.delay(Long.MAX_VALUE, TimeUnit.DAYS));
                            });

            worker.start();
            worker.join(2000);

            assertTrue(executed.get());
        }

        @Test
        @DisplayName("Should enter DELAYING state during execution and return to IDLE afterwards")
        void delay_tracksDelayingStateCorrectly() {
            AtomicBoolean wasDelaying = new AtomicBoolean(false);
            delayer.setOnDelayAction(() -> wasDelaying.set(delayer.isDelaying()));

            assertDoesNotThrow(() -> delayer.delay(10, TimeUnit.MILLISECONDS));

            assertTrue(wasDelaying.get());
            assertFalse(delayer.isDelaying());
        }
    }

    @Nested
    @DisplayName("Interruption & Cancellation Tests")
    class CancellationAndInterruptTests {

        @Test
        @DisplayName(
                "Should throw InterruptedDelayException when bound thread is interrupted prior to delay")
        void delay_threadInterruptedBeforehand_throwsInterruptedDelayException() {
            Thread.currentThread().interrupt();
            try {
                assertThrows(
                        InterruptedDelayException.class,
                        () -> delayer.delay(100, TimeUnit.MILLISECONDS));

                assertTrue(delayer.isInterrupted());
            } finally {
                Thread.interrupted(); // Safely clear interrupt status even if assertions fail
            }
        }

        @Test
        @DisplayName("Should throw CanceledDelayException when pre-canceled")
        void delay_precanceled_throwsCanceledDelayException() {
            delayer.cancel();

            assertThrows(
                    CanceledDelayException.class, () -> delayer.delay(100, TimeUnit.MILLISECONDS));

            assertTrue(delayer.isCanceled());
        }

        @Test
        @DisplayName(
                "Should interrupt active delaying thread when cancel is invoked from another thread")
        void cancel_activeDelayingThread_triggersInterruption() throws Exception {
            CountDownLatch enteredDelayLatch = new CountDownLatch(1);
            AtomicReference<Throwable> caughtException = new AtomicReference<>();

            TestDelayer[] activeDelayerHolder = new TestDelayer[1];

            Thread workerThread =
                    new Thread(
                            () -> {
                                TestDelayer activeDelayer = new TestDelayer(Thread.currentThread());
                                activeDelayerHolder[0] = activeDelayer;
                                activeDelayer.setOnDelayAction(
                                        () -> {
                                            enteredDelayLatch.countDown();
                                            Thread.yield();
                                        });

                                try {
                                    activeDelayer.delay(10, TimeUnit.SECONDS);
                                } catch (Throwable t) {
                                    caughtException.set(t);
                                }
                            });

            workerThread.start();
            assertTrue(enteredDelayLatch.await(2, TimeUnit.SECONDS));

            activeDelayerHolder[0].cancel();

            workerThread.join(2000);

            assertInstanceOf(CanceledDelayException.class, caughtException.get());
            assertTrue(activeDelayerHolder[0].isCanceled());
        }
    }
}
