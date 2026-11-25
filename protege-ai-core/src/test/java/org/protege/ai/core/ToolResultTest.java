package org.protege.ai.core;

import org.junit.Test;
import org.protege.ai.api.AgentToolResult;
import org.protege.ai.api.ToolExecutionResult;
import org.protege.ai.api.ToolStatus;
import org.protege.ai.api.ToolFailure;

import java.util.List;

import static org.junit.Assert.*;

public class ToolResultTest {

    @Test
    public void javaAndJsonSuccessRetainTheSameOriginalValue() {
        var value = List.of("Pizza");
        var javaResult = new ToolExecutionResult.Success<>(value);
        var jsonResult = new AgentToolResult.Success<>(javaResult.value(), "[\"Pizza\"]");

        assertSame(value, javaResult.value());
        assertSame(value, jsonResult.value());
        assertTrue(javaResult.isSuccess());
        assertTrue(jsonResult.isSuccess());
        assertEquals(javaResult.status(), jsonResult.status());
        assertEquals("[\"Pizza\"]", jsonResult.content());
    }

    @Test
    public void oneFailureIsUsableThroughBothResultInterfacesWithoutConversion() {
        var details = List.of(new ToolFailure.Detail("/scope", "Initialize the reasoner first."));
        for (var status : ToolStatus.values()) {
            if (status == ToolStatus.SUCCESS) {
                continue;
            }
            var failure = new ToolFailure(status, "Cannot complete the call.", status.name(), details);
            ToolExecutionResult javaResult = failure;
            AgentToolResult jsonResult = failure;

            assertSame(javaResult, jsonResult);
            assertSame(failure, javaResult);
            assertFalse(javaResult.isSuccess());
            assertFalse(jsonResult.isSuccess());
            assertEquals(status, javaResult.status());
            assertEquals(status, jsonResult.status());
            assertEquals(failure.content(), jsonResult.content());
            assertEquals(details, ((ToolFailure) javaResult).details());
        }
    }

    @Test
    public void neitherSuccessCanContainANullValue() {
        assertThrows(NullPointerException.class, () -> new ToolExecutionResult.Success<>(null));
        assertThrows(NullPointerException.class, () -> new AgentToolResult.Success<>(null, "{}"));
        assertThrows(NullPointerException.class, () -> new AgentToolResult.Success<>("value", null));
    }
}
