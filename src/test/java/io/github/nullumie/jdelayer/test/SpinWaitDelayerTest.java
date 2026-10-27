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

import io.github.nullumie.jdelayer.SpinWaitDelayer;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SpinWaitDelayerTest {

    private SpinWaitDelayer delayer;

    @BeforeEach
    void setUp() {
        Thread targetThread = Thread.currentThread();
        delayer = new SpinWaitDelayer(targetThread);
    }

    @Nested
    @DisplayName("Constructor Tests")
    class InitializationTests {

        @Test
        @DisplayName("Should throw NullPointerException when initialized with null thread")
        void constructor_nullThread_throwsException() {
            assertThrows(NullPointerException.class, () -> new SpinWaitDelayer(null));
        }
    }

    @Nested
    @DisplayName("Delay Execution Tests")
    class DelayExecutionTests {

        @Test
        @DisplayName("Should execute spin-wait delay successfully")
        void delay_executesSuccessfully() {
            assertDoesNotThrow(() -> delayer.delay(2, TimeUnit.MILLISECONDS));
            assertFalse(delayer.isDelaying());
        }
    }
}
