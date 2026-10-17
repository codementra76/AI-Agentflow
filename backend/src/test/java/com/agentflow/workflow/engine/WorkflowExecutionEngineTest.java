package com.agentflow.workflow.engine;

import com.agentflow.entity.Workflow;
import com.agentflow.entity.WorkflowExecution;
import com.agentflow.exception.WorkflowValidationException;
import com.agentflow.repository.NodeExecutionLogRepository;
import com.agentflow.repository.WorkflowExecutionRepository;
import com.agentflow.workflow.engine.executor.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WorkflowExecutionEngineTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private WorkflowValidator validator;
    private WorkflowExecutionEngine engine;

    @Mock
    private NodeExecutionLogRepository nodeLogRepository;

    @Mock
    private WorkflowExecutionRepository executionRepository;

    private Workflow sampleWorkflow;

    @BeforeEach
    void setUp() {
        validator = new WorkflowValidator();

        ManualTriggerExecutor manualTriggerExecutor = new ManualTriggerExecutor();
        TextInputExecutor textInputExecutor = new TextInputExecutor();
        TextTransformExecutor textTransformExecutor = new TextTransformExecutor();
        AiTextProcessorExecutor aiTextProcessorExecutor = new AiTextProcessorExecutor();
        OutputExecutor outputExecutor = new OutputExecutor();

        engine = new WorkflowExecutionEngine(
            objectMapper,
            nodeLogRepository,
            executionRepository,
            manualTriggerExecutor,
            textInputExecutor,
            textTransformExecutor,
            aiTextProcessorExecutor,
            outputExecutor
        );

        sampleWorkflow = new Workflow("Test Flow", "Description", objectMapper.createObjectNode(), true);
        sampleWorkflow.setId(100L);

        lenient().when(nodeLogRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(executionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("Reject graph with no trigger node")
    void shouldRejectGraphWithoutTrigger() {
        ObjectNode graph = objectMapper.createObjectNode();
        ArrayNode nodes = graph.putArray("nodes");
        ObjectNode outNode = nodes.addObject();
        outNode.put("id", "out-1");
        outNode.put("type", "OUTPUT");

        assertThatThrownBy(() -> validator.validateAndBuild(graph))
            .isInstanceOf(WorkflowValidationException.class)
            .hasMessageContaining("must contain at least one trigger node");
    }

    @Test
    @DisplayName("Reject graph with no output node")
    void shouldRejectGraphWithoutOutput() {
        ObjectNode graph = objectMapper.createObjectNode();
        ArrayNode nodes = graph.putArray("nodes");
        ObjectNode trigNode = nodes.addObject();
        trigNode.put("id", "trig-1");
        trigNode.put("type", "MANUAL_TRIGGER");

        assertThatThrownBy(() -> validator.validateAndBuild(graph))
            .isInstanceOf(WorkflowValidationException.class)
            .hasMessageContaining("must contain at least one OUTPUT node");
    }

    @Test
    @DisplayName("Detect and reject cycle in graph (A -> B -> A)")
    void shouldDetectAndRejectSimpleCycle() {
        ObjectNode graph = objectMapper.createObjectNode();
        ArrayNode nodes = graph.putArray("nodes");
        nodes.addObject().put("id", "node-1").put("type", "MANUAL_TRIGGER");
        nodes.addObject().put("id", "node-2").put("type", "TEXT_TRANSFORM");
        nodes.addObject().put("id", "node-3").put("type", "OUTPUT");

        ArrayNode edges = graph.putArray("edges");
        edges.addObject().put("source", "node-1").put("target", "node-2");
        edges.addObject().put("source", "node-2").put("target", "node-1"); // Cycle back to node-1!

        assertThatThrownBy(() -> validator.validateAndBuild(graph))
            .isInstanceOf(WorkflowValidationException.class)
            .hasMessageContaining("cycle");
    }

    @Test
    @DisplayName("Detect and reject self-referencing edge")
    void shouldRejectSelfLoop() {
        ObjectNode graph = objectMapper.createObjectNode();
        ArrayNode nodes = graph.putArray("nodes");
        nodes.addObject().put("id", "node-1").put("type", "MANUAL_TRIGGER");
        nodes.addObject().put("id", "node-2").put("type", "OUTPUT");

        ArrayNode edges = graph.putArray("edges");
        edges.addObject().put("source", "node-1").put("target", "node-1");

        assertThatThrownBy(() -> validator.validateAndBuild(graph))
            .isInstanceOf(WorkflowValidationException.class)
            .hasMessageContaining("Self-referencing edge is not allowed");
    }

    @Test
    @DisplayName("Reject edge pointing to non-existent node")
    void shouldRejectInvalidEdgeReference() {
        ObjectNode graph = objectMapper.createObjectNode();
        ArrayNode nodes = graph.putArray("nodes");
        nodes.addObject().put("id", "node-1").put("type", "MANUAL_TRIGGER");
        nodes.addObject().put("id", "node-2").put("type", "OUTPUT");

        ArrayNode edges = graph.putArray("edges");
        edges.addObject().put("source", "node-1").put("target", "ghost-node");

        assertThatThrownBy(() -> validator.validateAndBuild(graph))
            .isInstanceOf(WorkflowValidationException.class)
            .hasMessageContaining("non-existent target node");
    }

    @Test
    @DisplayName("Successfully execute multi-node text transform pipeline (UPPERCASE)")
    void shouldExecuteUppercaseTransformPipeline() {
        ObjectNode graph = objectMapper.createObjectNode();
        ArrayNode nodes = graph.putArray("nodes");

        nodes.addObject().put("id", "trig-1").put("type", "MANUAL_TRIGGER");

        ObjectNode textNode = nodes.addObject();
        textNode.put("id", "text-1");
        textNode.put("type", "TEXT_INPUT");
        textNode.putObject("config").put("text", "hello agentflow platform");

        ObjectNode transformNode = nodes.addObject();
        transformNode.put("id", "transform-1");
        transformNode.put("type", "TEXT_TRANSFORM");
        transformNode.putObject("config").put("operation", "UPPERCASE");

        ObjectNode outputNode = nodes.addObject();
        outputNode.put("id", "out-1");
        outputNode.put("type", "OUTPUT");

        ArrayNode edges = graph.putArray("edges");
        edges.addObject().put("source", "trig-1").put("target", "text-1");
        edges.addObject().put("source", "text-1").put("target", "transform-1");
        edges.addObject().put("source", "transform-1").put("target", "out-1");

        WorkflowGraph validatedGraph = validator.validateAndBuild(graph);

        WorkflowExecution execution = new WorkflowExecution();
        execution.setId(1L);
        execution.setWorkflow(sampleWorkflow);

        WorkflowExecution result = engine.run(validatedGraph, execution);

        assertThat(result.getStatus()).isEqualTo("COMPLETED");
        assertThat(result.getOutputData()).isNotNull();
        assertThat(result.getOutputData().get("result").asText()).isEqualTo("HELLO AGENTFLOW PLATFORM");
    }

    @Test
    @DisplayName("Successfully execute text transform: LOWERCASE, TRIM, and REPLACE")
    void shouldExecuteAllTextTransformOperations() {
        TextTransformExecutor executor = new TextTransformExecutor();
        ExecutionContext ctx = new ExecutionContext(1L, 1L, null, objectMapper);

        // LOWERCASE
        ObjectNode lowerCfg = objectMapper.createObjectNode();
        lowerCfg.put("operation", "LOWERCASE");
        WorkflowNode lowerNode = new WorkflowNode("n-1", NodeType.TEXT_TRANSFORM, "Lower", lowerCfg);
        ObjectNode input1 = objectMapper.createObjectNode().put("text", "HELLO WORLD");
        ctx.setNodeOutput("src-1", input1);
        assertThat(executor.execute(lowerNode, List.of("src-1"), ctx).get("text").asText()).isEqualTo("hello world");

        // TRIM
        ObjectNode trimCfg = objectMapper.createObjectNode();
        trimCfg.put("operation", "TRIM");
        WorkflowNode trimNode = new WorkflowNode("n-2", NodeType.TEXT_TRANSFORM, "Trim", trimCfg);
        ObjectNode input2 = objectMapper.createObjectNode().put("text", "   spaced text   ");
        ctx.setNodeOutput("src-2", input2);
        assertThat(executor.execute(trimNode, List.of("src-2"), ctx).get("text").asText()).isEqualTo("spaced text");

        // REPLACE
        ObjectNode replaceCfg = objectMapper.createObjectNode();
        replaceCfg.put("operation", "REPLACE");
        replaceCfg.put("find", "cat");
        replaceCfg.put("replace", "dog");
        WorkflowNode replaceNode = new WorkflowNode("n-3", NodeType.TEXT_TRANSFORM, "Replace", replaceCfg);
        ObjectNode input3 = objectMapper.createObjectNode().put("text", "black cat and white cat");
        ctx.setNodeOutput("src-3", input3);
        assertThat(executor.execute(replaceNode, List.of("src-3"), ctx).get("text").asText()).isEqualTo("black dog and white dog");
    }

    @Test
    @DisplayName("Fail when AI_TEXT_PROCESSOR runs without configured API key")
    void shouldFailWhenAiNodeExecutedWithoutApiKey() {
        ObjectNode graph = objectMapper.createObjectNode();
        ArrayNode nodes = graph.putArray("nodes");
        nodes.addObject().put("id", "trig-1").put("type", "MANUAL_TRIGGER");
        nodes.addObject().put("id", "ai-1").put("type", "AI_TEXT_PROCESSOR");
        nodes.addObject().put("id", "out-1").put("type", "OUTPUT");

        ArrayNode edges = graph.putArray("edges");
        edges.addObject().put("source", "trig-1").put("target", "ai-1");
        edges.addObject().put("source", "ai-1").put("target", "out-1");

        WorkflowGraph validatedGraph = validator.validateAndBuild(graph);

        WorkflowExecution execution = new WorkflowExecution();
        execution.setId(2L);
        execution.setWorkflow(sampleWorkflow);

        WorkflowExecution result = engine.run(validatedGraph, execution);

        assertThat(result.getStatus()).isEqualTo("FAILED");
        assertThat(result.getErrorMessage()).contains("AI functionality is unavailable");
    }
}
