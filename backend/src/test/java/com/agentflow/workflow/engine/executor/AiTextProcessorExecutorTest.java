package com.agentflow.workflow.engine.executor;

import com.agentflow.dto.ai.AiChatResponse;
import com.agentflow.exception.AiServiceUnavailableException;
import com.agentflow.exception.WorkflowExecutionException;
import com.agentflow.service.OllamaService;
import com.agentflow.workflow.engine.ExecutionContext;
import com.agentflow.workflow.engine.NodeType;
import com.agentflow.workflow.engine.WorkflowNode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiTextProcessorExecutorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private OllamaService ollamaService;

    private AiTextProcessorExecutor executor;
    private ExecutionContext context;

    @BeforeEach
    void setUp() {
        executor = new AiTextProcessorExecutor(ollamaService);
        context = new ExecutionContext(1L, 100L, objectMapper.createObjectNode(), objectMapper);
    }

    @Test
    @DisplayName("Combine configured prompt and upstream input text into unified Ollama prompt")
    void shouldCombineConfiguredPromptAndUpstreamText() {
        ObjectNode config = objectMapper.createObjectNode();
        config.put("prompt", "Explain the following text in simple terms.");
        WorkflowNode node = new WorkflowNode("ai-node-1", NodeType.AI_TEXT_PROCESSOR, "AI Processor", config);

        ObjectNode upstreamOutput = objectMapper.createObjectNode();
        upstreamOutput.put("text", "Java polymorphism");
        context.setNodeOutput("src-1", upstreamOutput);

        when(ollamaService.generateChat(any(), eq(null)))
            .thenReturn(new AiChatResponse("Polymorphism explained simply.", "qwen2.5:3b"));

        JsonNode result = executor.execute(node, List.of("src-1"), context);

        assertThat(result.get("status").asText()).isEqualTo("SUCCESS");
        assertThat(result.get("nodeId").asText()).isEqualTo("ai-node-1");
        assertThat(result.get("text").asText()).isEqualTo("Polymorphism explained simply.");
        assertThat(result.get("result").asText()).isEqualTo("Polymorphism explained simply.");
        assertThat(result.get("model").asText()).isEqualTo("qwen2.5:3b");

        verify(ollamaService).generateChat(
            argThat(p -> p.startsWith("Explain the following text in simple terms.") &&
                         p.contains("Input:\nJava polymorphism")),
            eq(null)
        );
    }

    @Test
    @DisplayName("Execute with prompt only when no upstream text is present")
    void shouldUsePromptOnlyWhenNoUpstreamText() {
        ObjectNode config = objectMapper.createObjectNode();
        config.put("prompt", "Write a haiku about programming.");
        WorkflowNode node = new WorkflowNode("ai-node-2", NodeType.AI_TEXT_PROCESSOR, "AI Processor", config);

        when(ollamaService.generateChat("Write a haiku about programming.", null))
            .thenReturn(new AiChatResponse("Code flows like water.", "qwen2.5:3b"));

        JsonNode result = executor.execute(node, List.of(), context);

        assertThat(result.get("text").asText()).isEqualTo("Code flows like water.");
        verify(ollamaService).generateChat("Write a haiku about programming.", null);
    }

    @Test
    @DisplayName("Execute with upstream text only when config prompt is absent")
    void shouldUseUpstreamTextOnlyWhenConfigPromptAbsent() {
        WorkflowNode node = new WorkflowNode("ai-node-3", NodeType.AI_TEXT_PROCESSOR, "AI Processor", null);

        ObjectNode upstreamOutput = objectMapper.createObjectNode();
        upstreamOutput.put("text", "Direct prompt from upstream node");
        context.setNodeOutput("src-2", upstreamOutput);

        when(ollamaService.generateChat("Direct prompt from upstream node", null))
            .thenReturn(new AiChatResponse("Processed direct prompt", "qwen2.5:3b"));

        JsonNode result = executor.execute(node, List.of("src-2"), context);

        assertThat(result.get("text").asText()).isEqualTo("Processed direct prompt");
        verify(ollamaService).generateChat("Direct prompt from upstream node", null);
    }

    @Test
    @DisplayName("Pass optional model override from config to OllamaService")
    void shouldPassModelOverrideWhenPresentInConfig() {
        ObjectNode config = objectMapper.createObjectNode();
        config.put("prompt", "Analyze text");
        config.put("model", "llama3:latest");
        WorkflowNode node = new WorkflowNode("ai-node-4", NodeType.AI_TEXT_PROCESSOR, "AI Processor", config);

        when(ollamaService.generateChat("Analyze text", "llama3:latest"))
            .thenReturn(new AiChatResponse("Analyzed by custom model", "llama3:latest"));

        JsonNode result = executor.execute(node, List.of(), context);

        assertThat(result.get("model").asText()).isEqualTo("llama3:latest");
        verify(ollamaService).generateChat("Analyze text", "llama3:latest");
    }

    @Test
    @DisplayName("Throw WorkflowExecutionException when both prompt and upstream input are missing")
    void shouldThrowExceptionWhenBothPromptAndInputMissing() {
        WorkflowNode node = new WorkflowNode("ai-node-5", NodeType.AI_TEXT_PROCESSOR, "AI Processor", null);

        assertThatThrownBy(() -> executor.execute(node, List.of(), context))
            .isInstanceOf(WorkflowExecutionException.class)
            .hasMessageContaining("requires a prompt in config or input text from an upstream node");
    }

    @Test
    @DisplayName("Propagate WorkflowExecutionException when Ollama service fails")
    void shouldPropagateWorkflowExecutionExceptionWhenOllamaFails() {
        ObjectNode config = objectMapper.createObjectNode().put("prompt", "Failing prompt");
        WorkflowNode node = new WorkflowNode("ai-node-6", NodeType.AI_TEXT_PROCESSOR, "AI Processor", config);

        when(ollamaService.generateChat(any(), any()))
            .thenThrow(new AiServiceUnavailableException("Connection refused to Ollama"));

        assertThatThrownBy(() -> executor.execute(node, List.of(), context))
            .isInstanceOf(WorkflowExecutionException.class)
            .hasMessageContaining("AI processing failed at node 'ai-node-6'")
            .hasMessageContaining("Connection refused to Ollama");
    }
}
