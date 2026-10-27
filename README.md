# JDelayer

A high-precision concurrency utility designed for managing low-latency, thread-bound synchronous boundaries and centralized time-delay lifecycles based on relative durations and system nano deadlines.

## Requirements

- **Java:** 21 or higher

## Features

- **Relative & Absolute Delays:** Block execution using relative time durations or precise absolute deadlines aligned with `System.nanoTime()`.
- **Thread-Bound Safety:** Enforce deterministic boundaries where only explicit, designated threads are authorized to execute a delay sequence via `Delayer`, backed by safety checks.
- **Centralized Orchestration:** Track, manage, and abort multiple concurrent delayed execution paths through a unified `DelayerService`.
- **Premature Aborts & Unblocking:** Forcefully unblock waiting threads via `cancel()`, `cancel(Thread)`, `cancelAll()`, `release()`, `releaseAll()`, or `close()`. Target threads unblock immediately with a `CanceledDelayException`.
- **Resource Lifecycle & Closed State:** Track service status using `isClosed()`. Implements `AutoCloseable` with idempotent `close()` semantics, rejecting post-close operations with `IllegalStateException`.

## Dependency Management

### Maven

```xml
<dependency>
    <groupId>io.github.nullumie</groupId>
    <artifactId>jdelayer</artifactId>
    <version>1.0.0</version>
</dependency>
```

### Gradle

```groovy
implementation 'io.github.nullumie:jdelayer:1.0.0'
```

## Performance Profiles & Implementations

JDelayer provides three distinct implementation pairs of `Delayer` and `DelayerService` tailored for different latency profiles and CPU overhead budgets:

- **AdaptiveDelayer / AdaptiveDelayerService:** Dynamically transitions between aggressive spinning and thread parking based on the remaining deadline. Offers a highly balanced, production-ready profile for mixed workloads.
- **SpinWaitDelayer / SpinWaitDelayerService:** Engineered for ultra-low latency, jitter-sensitive hot paths. Uses strict spin-locking mechanisms to achieve pure nanosecond-level accuracy at the expense of continuous CPU core utilization.
- **StandardParkDelayer / StandardParkDelayerService:** Relies on standard OS thread scheduling primitives (`LockSupport.parkNanos`). It is highly CPU-efficient and ideal for microsecond-to-millisecond constraints where yielding CPU cycles is mandatory.

## Exception Handling

Both domain exceptions derive from `java.lang.InterruptedException`, allowing callers to catch them individually for fine-grained lifecycle handling or catch `InterruptedException` directly to handle any premature delay termination in a single block:

- **`CanceledDelayException`** *(extends `InterruptedException`)*: Thrown inside `delay()` when the waiting sequence is prematurely aborted by calling `cancel()`, `cancelAll()`, `release()`, `releaseAll()`, or `close()`.
- **`InterruptedDelayException`** *(extends `InterruptedException`)*: Thrown inside `delay()` when the underlying worker thread is interrupted directly via standard JVM thread mechanisms (`Thread.interrupt()`) prior to or during execution.

*Note: In addition to delay interruption exceptions, calling operations on a closed `DelayerService` will throw an `IllegalStateException`.*

## Quick Start

JDelayer can be integrated in two ways depending on your application structure:

1. **`Delayer`**: Localized, thread-bound boundary for single-thread precision delays.
2. **`DelayerService`**: Centralized manager for orchestrating multiple concurrent worker threads.

### 1. Standalone Thread Boundary (`Delayer`)

Use `Delayer` directly when thread execution is localized and does not require multi-thread service orchestration:

```java
import io.github.nullumie.jdelayer.Delayer;
import io.github.nullumie.jdelayer.AdaptiveDelayer;
import io.github.nullumie.jdelayer.CanceledDelayException;
import io.github.nullumie.jdelayer.InterruptedDelayException;
import java.util.concurrent.TimeUnit;

public class StandaloneDelayerExample {
    public static void main(String[] args) {
        // Instantiate a thread-bound delayer instance bound to the current thread
        Delayer delayer = new AdaptiveDelayer(Thread.currentThread(), 2_000_000, 15_625_000, 0.5, 0.0025d, 0.10d);

        try {
            System.out.println("Executing high-precision relative delay...");
            delayer.delay(500, TimeUnit.MILLISECONDS);

            System.out.println("Executing deadline delay using System.nanoTime()...");
            long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(500);
            delayer.delay(deadline);

            System.out.println("Delay sequence complete!");
        } catch (CanceledDelayException e) {
            System.out.println("Delay was explicitly canceled via delayer.cancel().");
        } catch (InterruptedDelayException e) {
            System.out.println("Delay was interrupted via standard Thread.interrupt().");
        }
    }
}
```

### 2. Multi-Thread Orchestration (`DelayerService`)

Use `DelayerService` when manager threads need to monitor, inspect, cancel, or release worker threads dynamically:

```java
import io.github.nullumie.jdelayer.DelayerService;
import io.github.nullumie.jdelayer.AdaptiveDelayerService;
import io.github.nullumie.jdelayer.CanceledDelayException;
import io.github.nullumie.jdelayer.InterruptedDelayException;
import java.util.concurrent.TimeUnit;

public class DelayerServiceExample {
    public static void main(String[] args) throws InterruptedException {
        // Initialize the centralized orchestrator
        try (DelayerService delayerService = new AdaptiveDelayerService(2_000_000, 15_625_000, 0.5, 0.0025d, 0.10d)) {
            Thread worker = new Thread(() -> {
                try {
                    System.out.println("[Worker] Initiating a high-precision 10-second delay...");
                    // Delegates to a thread-bound Delayer instance under the hood.
                    // If canceled or released, this call unblocks immediately and throws CanceledDelayException.
                    delayerService.delay(10, TimeUnit.SECONDS);
                    System.out.println("[Worker] Execution finished successfully.");
                } catch (CanceledDelayException e) {
                    System.out.println("[Worker] Aborted! (A service cancel/release operation unblocked the delay).");
                } catch (InterruptedDelayException e) {
                    System.out.println("[Worker] Aborted! (Thread was interrupted via Thread.interrupt()).");
                } finally {
                    // Prevent memory leaks by explicitly releasing the calling thread's context mapping
                    if (!delayerService.isClosed()) {
                        delayerService.release(Thread.currentThread());
                    }
                }
            });

            worker.start();

            // Simulate external lifecycle monitoring
            Thread.sleep(1000);

            // Diagnostics methods on DelayerService mirror the target thread's delay state
            System.out.println("--- Service Diagnostics ---");
            System.out.println("Is Service Closed? " + delayerService.isClosed());
            System.out.println("Has Delayer Context? " + delayerService.hasDelayer(worker));
            System.out.println("Is Worker Delaying? " + delayerService.isDelaying(worker));
            System.out.println("Was Worker Canceled? " + delayerService.isCanceled(worker));
            System.out.println("Was Worker Interrupted? " + delayerService.isInterrupted(worker));

            if (delayerService.isDelaying(worker)) {
                System.out.println("\n[Orchestrator] Worker thread is active. Forcing an abort...");
                // Calling cancel(worker) causes worker's delay() call to immediately unblock and throw CanceledDelayException.
                delayerService.cancel(worker);
            }

            worker.join();
        } // Closing the service cancels active delays, unmaps contexts, and sets isClosed() to true
    }
}
```

## License

This project is licensed under the GNU Lesser General Public License v3.0 only (LGPL-3.0-only). For full terms, permissions, and compliance details, please refer to the main [LICENSE.md](LICENSE.md) file at the root of this repository.