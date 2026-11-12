package com.agentflow.workflow.engine.executor;

import com.agentflow.workflow.engine.ExecutionContext;
import com.agentflow.workflow.engine.NodeType;
import com.agentflow.workflow.engine.WorkflowNode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TextInputExecutorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private TextInputExecutor executor;
    private ExecutionContext context;

    @BeforeEach
    void setUp() {
        executor = new TextInputExecutor();
        context = new ExecutionContext(1L, 100L, objectMapper.createObjectNode(), objectMapper);
    }

    @Test
    @DisplayName("Produce plain configured text without variable references")
    void shouldProducePlainTextWithoutVariables() {
        ObjectNode config = objectMapper.createObjectNode();
        config.put("text", "Plain static text message");
        WorkflowNode node = new WorkflowNode("text-1", NodeType.TEXT_INPUT, "Text Input", config);

        JsonNode result = executor.execute(node, List.of(), context);

        assertThat(result.get("text").asText()).isEqualTo("Plain static text message");
        assertThat(result.get("value").asText()).isEqualTo("Plain static text message");
        assertThat(result.get("nodeId").asText()).isEqualTo("text-1");
    }

    @Test
    @DisplayName("Resolve {{input.field}} reference in text-input config")
    void shouldResolveInputReferenceInTextInput() {
        ObjectNode input = objectMapper.createObjectNode();
        input.put("greeting", "Welcome aboard");
        context = new ExecutionContext(1L, 100L, input, objectMapper);

        ObjectNode config = objectMapper.createObjectNode();
        config.put("text", "Greeting: {{input.greeting}}!");
        WorkflowNode node = new WorkflowNode("text-2", NodeType.TEXT_INPUT, "Text Input", config);

        JsonNode result = executor.execute(node, List.of(), context);

        assertThat(result.get("text").asText()).isEqualTo("Greeting: Welcome aboard!");
        assertThat(result.get("value").asText()).isEqualTo("Greeting: Welcome aboard!");
    }

    @Test
    @DisplayName("Resolve {{nodes.<nodeId>.<field>}} reference from previous node output")
    void shouldResolvePreviousNodeOutputReference() {
        ObjectNode prevOutput = objectMapper.createObjectNode();
        prevOutput.put("result", "Processed Data #42");
        context.setNodeOutput("src-node", prevOutput);

        ObjectNode config = objectMapper.createObjectNode();
        config.put("text", "Received: {{nodes.src-node.result}}");
        WorkflowNode node = new WorkflowNode("text-3", NodeType.TEXT_INPUT, "Text Input", config);

        JsonNode result = executor.execute(node, List.of("src-node"), context);

        assertThat(result.get("text").asText()).isEqualTo("Received: Processed Data #42");
    }

    @Test
    @DisplayName("Resolve {{variables.<name>}} reference in text-input config")
    void shouldResolveWorkflowVariableReference() {
        context.setWorkflowVariable("environment", "staging");

        ObjectNode config = objectMapper.createObjectNode();
        config.put("text", "Deploy target: {{variables.environment}}");
        WorkflowNode node = new WorkflowNode("text-4", NodeType.TEXT_INPUT, "Text Input", config);

        JsonNode result = executor.execute(node, List.of(), context);

        assertThat(result.get("text").asText()).isEqualTo("Deploy target: staging");
    }

    @Test
    @DisplayName("Leave unresolved or missing variable references unchanged")
    void shouldLeaveUnresolvedReferencesIntact() {
        ObjectNode config = objectMapper.createObjectNode();
        config.put("text", "Values: {{nodes.unexecuted.data}} and {{variables.not_found}}");
        WorkflowNode node = new WorkflowNode("text-5", NodeType.TEXT_INPUT, "Text Input", config);

        JsonNode result = executor.execute(node, List.of(), context);

        assertThat(result.get("text").asText())
            .isEqualTo("Values: {{nodes.unexecuted.data}} and {{variables.not_found}}");
    }

    @Test
    @DisplayName("Fall back to incoming upstream text when config is absent and interpolate if needed")
    void shouldFallbackToIncomingTextAndInterpolate() {
        context.setWorkflowVariable("appName", "AgentFlow");

        ObjectNode upstreamOutput = objectMapper.createObjectNode();
        upstreamOutput.put("text", "Welcome to {{variables.appName}}");
        context.setNodeOutput("trigger-1", upstreamOutput);

        WorkflowNode node = new WorkflowNode("text-6", NodeType.TEXT_INPUT, "Text Input", null);

        JsonNode result = executor.execute(node, List.of("trigger-1"), context);

        assertThat(result.get("text").asText()).isEqualTo("Welcome to AgentFlow");
    }

    @Test
    @DisplayName("Produce empty string when neither config nor incoming text is present")
    void shouldReturnEmptyStringWhenNoConfigOrIncoming() {
        WorkflowNode node = new WorkflowNode("text-7", NodeType.TEXT_INPUT, "Text Input", null);

        JsonNode result = executor.execute(node, List.of(), context);

        assertThat(result.get("text").asText()).isEmpty();
        assertThat(result.get("value").asText()).isEmpty();
    }
}
