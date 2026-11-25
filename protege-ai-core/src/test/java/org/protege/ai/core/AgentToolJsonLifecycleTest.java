package org.protege.ai.core;

import org.junit.Test;
import org.protege.ai.api.AgentTool;
import org.protege.ai.api.AgentToolDefinition;
import org.protege.ai.api.AgentToolResult;
import org.protege.ai.api.AgentToolSource;
import org.protege.ai.api.ToolFailure;

import javax.swing.SwingUtilities;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.*;
import static org.protege.ai.api.ToolStatus.*;

public class AgentToolJsonLifecycleTest {
    public static final class SlowResult {
        private final CountDownLatch started;
        private final CountDownLatch release;
        private final AtomicInteger disposed;
        private final boolean fail;

        SlowResult(CountDownLatch started, CountDownLatch release, AtomicInteger disposed, boolean fail) {
            this.started = started;
            this.release = release;
            this.disposed = disposed;
            this.fail = fail;
        }

        public String getValue() throws InterruptedException {
            started.countDown();
            assertTrue(release.await(5, SECONDS));
            assertEquals("Tool disposed during result conversion", 0, disposed.get());
            if (fail) {
                throw new IllegalStateException("Private serialization failure");
            }
            return "finished";
        }
    }

    @Test
    public void closingOnSwingWaitsForNeitherSuccessfulNorFailedResultConversion() throws Exception {
        for (boolean fail : List.of(false, true)) {
            checkClosingDuringConversion(fail);
        }
    }

    private void checkClosingDuringConversion(boolean fail) throws Exception {
        var started = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var disposed = new AtomicInteger();
        var calls = new AtomicInteger();
        var value = new SlowResult(started, release, disposed, fail);
        var definition = new AgentToolDefinition<>("slow", "Slow result", String.class, SlowResult.class);
        var source = new AgentToolSource<String, SlowResult>() {
            public String getId() { return "test.slowResult"; }
            public AgentToolDefinition<String, SlowResult> getDefinition() { return definition; }
            public AgentTool<String, SlowResult> newInstance() {
                return new AgentTool<>() {
                    public AgentToolDefinition<String, SlowResult> getDefinition() { return definition; }
                    public SlowResult execute(String arguments) { calls.incrementAndGet(); return value; }
                    public void dispose() { disposed.incrementAndGet(); }
                };
            }
        };
        var jsonTools = new AgentToolJsonService(List.of(source));
        var worker = Executors.newSingleThreadExecutor();
        try {
            var call = worker.submit(() -> jsonTools.execute("slow", "\"input\""));
            assertTrue(started.await(5, SECONDS));
            var closed = new CompletableFuture<Void>();
            SwingUtilities.invokeLater(() -> {
                try {
                    assertEquals(1, jsonTools.registry().listTools().size());
                    assertTrue(jsonTools.findSchemas("slow").isPresent());
                    jsonTools.dispose();
                    assertEquals(REGISTRY_CLOSED, jsonTools.execute("slow", "\"next\"").status());
                    assertEquals(REGISTRY_CLOSED, jsonTools.registry().execute("slow", "next").status());
                    assertTrue(jsonTools.findSchemas("slow").isEmpty());
                    closed.complete(null);
                } catch (Throwable e) {
                    closed.completeExceptionally(e);
                }
            });
            closed.get(5, SECONDS);
            assertFalse(call.isDone());
            assertEquals(0, disposed.get());
            release.countDown();
            var result = call.get(5, SECONDS);
            if (fail) {
                assertTrue(result instanceof ToolFailure);
                assertEquals(EXECUTION_ERROR, result.status());
                assertFalse(result.content().contains("Private serialization failure"));
            } else {
                var success = (AgentToolResult.Success<?>) result;
                assertSame(value, success.value());
                assertEquals("{\"value\":\"finished\"}", success.json());
            }
            assertEquals(1, calls.get());
            assertEquals(1, disposed.get());
        } finally {
            release.countDown();
            worker.shutdownNow();
            assertTrue(worker.awaitTermination(5, SECONDS));
            jsonTools.dispose();
        }
    }
}
