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

    @Test
    @DisplayName("Resolve {{input.field}} reference in AI prompt")
    void shouldResolveInputReferenceInAiPrompt() {
        ObjectNode input = objectMapper.createObjectNode();
        input.put("topic", "Microservices Architecture");
        context = new ExecutionContext(1L, 100L, input, objectMapper);

        ObjectNode config = objectMapper.createObjectNode();
        config.put("prompt", "Explain {{input.topic}} in 3 key points.");
        WorkflowNode node = new WorkflowNode("ai-node-input", NodeType.AI_TEXT_PROCESSOR, "AI Processor", config);

        when(ollamaService.generateChat("Explain Microservices Architecture in 3 key points.", null))
            .thenReturn(new AiChatResponse("1. Loose coupling 2. Independent deployability 3. Scalability", "qwen2.5:3b"));

        JsonNode result = executor.execute(node, List.of(), context);

        assertThat(result.get("text").asText()).isEqualTo("1. Loose coupling 2. Independent deployability 3. Scalability");
        verify(ollamaService).generateChat("Explain Microservices Architecture in 3 key points.", null);
    }

    @Test
    @DisplayName("Resolve {{nodes.<nodeId>.<field>}} reference from previously executed node output")
    void shouldResolvePreviousNodeOutputReferenceInAiPrompt() {
        ObjectNode previousOutput = objectMapper.createObjectNode();
        previousOutput.put("text", "Draft summary from step 1");
        context.setNodeOutput("text-1", previousOutput);

        ObjectNode config = objectMapper.createObjectNode();
        config.put("prompt", "Refine this text: {{nodes.text-1.text}}");
        WorkflowNode node = new WorkflowNode("ai-node-prev", NodeType.AI_TEXT_PROCESSOR, "AI Processor", config);

        when(ollamaService.generateChat("Refine this text: Draft summary from step 1", null))
            .thenReturn(new AiChatResponse("Refined summary output", "qwen2.5:3b"));

        JsonNode result = executor.execute(node, List.of("text-1"), context);

        assertThat(result.get("text").asText()).isEqualTo("Refined summary output");
        verify(ollamaService).generateChat("Refine this text: Draft summary from step 1", null);
    }

    @Test
    @DisplayName("Resolve {{variables.<name>}} reference in AI prompt and combine with upstream input")
    void shouldResolveWorkflowVariableInAiPromptAndCombineWithUpstreamInput() {
        context.setWorkflowVariable("role", "Senior Java Architect");

        ObjectNode upstreamOutput = objectMapper.createObjectNode();
        upstreamOutput.put("text", "Explain virtual threads");
        context.setNodeOutput("src-1", upstreamOutput);

        ObjectNode config = objectMapper.createObjectNode();
        config.put("prompt", "You are a {{variables.role}}.");
        WorkflowNode node = new WorkflowNode("ai-node-var", NodeType.AI_TEXT_PROCESSOR, "AI Processor", config);

        when(ollamaService.generateChat(any(), eq(null)))
            .thenReturn(new AiChatResponse("Virtual threads are lightweight threads.", "qwen2.5:3b"));

        JsonNode result = executor.execute(node, List.of("src-1"), context);

        assertThat(result.get("text").asText()).isEqualTo("Virtual threads are lightweight threads.");
        verify(ollamaService).generateChat(
            eq("You are a Senior Java Architect.\n\nInput:\nExplain virtual threads"),
            eq(null)
        );
    }

    @Test
    @DisplayName("Leave unresolved or missing variable references unchanged in AI prompt")
    void shouldLeaveMissingReferencesIntactInAiPrompt() {
        ObjectNode config = objectMapper.createObjectNode();
        config.put("prompt", "Analyze {{nodes.nonexistent.output}} and {{variables.unknown}}");
        WorkflowNode node = new WorkflowNode("ai-node-missing", NodeType.AI_TEXT_PROCESSOR, "AI Processor", config);

        when(ollamaService.generateChat("Analyze {{nodes.nonexistent.output}} and {{variables.unknown}}", null))
            .thenReturn(new AiChatResponse("Handled raw references", "qwen2.5:3b"));

        JsonNode result = executor.execute(node, List.of(), context);

        assertThat(result.get("text").asText()).isEqualTo("Handled raw references");
        verify(ollamaService).generateChat("Analyze {{nodes.nonexistent.output}} and {{variables.unknown}}", null);
    }

    @Test
    @DisplayName("Do not resolve reference to a node that has not executed yet")
    void shouldNotResolveReferenceToUnexecutedNode() {
        // Node 'future-node' has not executed, so it's not present in context nodeOutputs
        ObjectNode config = objectMapper.createObjectNode();
        config.put("prompt", "Process: {{nodes.future-node.result}}");
        WorkflowNode node = new WorkflowNode("ai-node-unexecuted", NodeType.AI_TEXT_PROCESSOR, "AI Processor", config);

        when(ollamaService.generateChat("Process: {{nodes.future-node.result}}", null))
            .thenReturn(new AiChatResponse("Processed with unexecuted reference verbatim", "qwen2.5:3b"));

        JsonNode result = executor.execute(node, List.of(), context);

        assertThat(result.get("text").asText()).isEqualTo("Processed with unexecuted reference verbatim");
        verify(ollamaService).generateChat("Process: {{nodes.future-node.result}}", null);
    }
}
