package org.protege.ai.core;

import org.junit.Test;
import org.protege.ai.api.AgentTool;
import org.protege.ai.api.AgentToolDescription;
import org.protege.ai.api.AgentToolSource;

import javax.swing.SwingUtilities;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.*;
import static org.protege.ai.api.AgentToolResult.Status.*;

public class AgentToolRegistryConcurrencyTest {

    private AgentToolSource source(String name, AgentTool tool) {
        return new AgentToolSource() {
            public String getId() { return name; }
            public AgentToolDescription getDescription() {
                return new AgentToolDescription(name, "Test tool", "{}", "text/plain");
            }
            public AgentTool newInstance() { return tool; }
        };
    }

    @Test
    public void eventThreadCanListAndCloseWhileToolRuns() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger busyDisposed = new AtomicInteger();
        AtomicInteger idleDisposed = new AtomicInteger();
        AgentTool busy = new AgentTool() {
            public String execute(String input) throws InterruptedException {
                started.countDown();
                release.await();
                return "finished";
            }
            public void dispose() { busyDisposed.incrementAndGet(); }
        };
        AgentTool idle = new AgentTool() {
            public String execute(String input) { return input; }
            public void dispose() { idleDisposed.incrementAndGet(); }
        };
        AgentToolRegistry registry = new AgentToolRegistry(List.of(source("busy", busy), source("idle", idle)));
        var worker = Executors.newSingleThreadExecutor();
        try {
            var call = worker.submit(() -> registry.execute("busy", "{}"));
            assertTrue(started.await(5, SECONDS));
            CompletableFuture<Void> closed = new CompletableFuture<>();
            SwingUtilities.invokeLater(() -> {
                try {
                    assertEquals(2, registry.listTools().size());
                    assertTrue(registry.findTool("busy").isPresent());
                    registry.dispose();
                    registry.dispose();
                    assertTrue(registry.listTools().isEmpty());
                    assertEquals(REGISTRY_CLOSED, registry.execute("busy", "{}").status());
                    closed.complete(null);
                } catch (Throwable e) {
                    closed.completeExceptionally(e);
                }
            });
            closed.get(5, SECONDS);
            assertFalse(call.isDone());
            assertEquals(1, idleDisposed.get());
            assertEquals(0, busyDisposed.get());
            release.countDown();
            assertEquals("finished", call.get(5, SECONDS).content());
            assertEquals(1, busyDisposed.get());
        } finally {
            release.countDown();
            worker.shutdownNow();
            assertTrue(worker.awaitTermination(5, SECONDS));
            SwingUtilities.invokeAndWait(registry::dispose);
        }
    }

    @Test
    public void parallelCallsDisposeToolOnlyAfterLastCallIncludingFailure() throws Exception {
        CountDownLatch started = new CountDownLatch(2);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch releaseSecond = new CountDownLatch(1);
        AtomicInteger disposed = new AtomicInteger();
        AgentTool tool = new AgentTool() {
            public String execute(String input) throws InterruptedException {
                started.countDown();
                if (input.equals("first")) {
                    releaseFirst.await();
                    return input;
                }
                releaseSecond.await();
                throw new IllegalStateException("Second call failed");
            }
            public void dispose() { disposed.incrementAndGet(); }
        };
        AgentToolRegistry registry = new AgentToolRegistry(List.of(source("parallel", tool)));
        var workers = Executors.newFixedThreadPool(2);
        try {
            var first = workers.submit(() -> registry.execute("parallel", "first"));
            var second = workers.submit(() -> registry.execute("parallel", "second"));
            assertTrue(started.await(5, SECONDS));
            registry.dispose();
            releaseFirst.countDown();
            assertEquals(SUCCESS, first.get(5, SECONDS).status());
            assertEquals(0, disposed.get());
            releaseSecond.countDown();
            assertEquals(EXECUTION_ERROR, second.get(5, SECONDS).status());
            assertEquals(1, disposed.get());
        } finally {
            releaseFirst.countDown();
            releaseSecond.countDown();
            workers.shutdownNow();
            assertTrue(workers.awaitTermination(5, SECONDS));
            registry.dispose();
        }
    }

    @Test
    public void interruptingRunningCallPreservesFlagAndCompletesDeferredCleanup() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicReference<Thread> runningThread = new AtomicReference<>();
        AtomicBoolean interrupted = new AtomicBoolean();
        AtomicInteger disposed = new AtomicInteger();
        AgentTool tool = new AgentTool() {
            public String execute(String input) throws InterruptedException {
                runningThread.set(Thread.currentThread());
                started.countDown();
                release.await();
                return input;
            }
            public void dispose() { disposed.incrementAndGet(); }
        };
        AgentToolRegistry registry = new AgentToolRegistry(List.of(source("interruptible", tool)));
        var worker = Executors.newSingleThreadExecutor();
        try {
            var call = worker.submit(() -> {
                var result = registry.execute("interruptible", "{}");
                interrupted.set(Thread.currentThread().isInterrupted());
                return result;
            });
            assertTrue(started.await(5, SECONDS));
            registry.dispose();
            runningThread.get().interrupt();
            var result = call.get(5, SECONDS);
            assertEquals(EXECUTION_ERROR, result.status());
            assertTrue(result.content().contains("InterruptedException"));
            assertTrue(interrupted.get());
            assertEquals(1, disposed.get());
        } finally {
            release.countDown();
            worker.shutdownNow();
            assertTrue(worker.awaitTermination(5, SECONDS));
            registry.dispose();
        }
    }
}
