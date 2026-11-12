package com.agentflow.workflow.engine;

import com.agentflow.entity.NodeExecutionLog;
import com.agentflow.entity.Workflow;
import com.agentflow.entity.WorkflowExecution;
import com.agentflow.exception.WorkflowValidationException;
import com.agentflow.repository.NodeExecutionLogRepository;
import com.agentflow.repository.WorkflowExecutionRepository;
import com.agentflow.dto.ai.AiChatResponse;
import com.agentflow.exception.AiServiceUnavailableException;
import com.agentflow.service.OllamaService;
import com.agentflow.workflow.engine.executor.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

    @Mock
    private OllamaService ollamaService;

    private Workflow sampleWorkflow;

    @BeforeEach
    void setUp() {
        validator = new WorkflowValidator();

        ManualTriggerExecutor manualTriggerExecutor = new ManualTriggerExecutor();
        TextInputExecutor textInputExecutor = new TextInputExecutor();
        TextTransformExecutor textTransformExecutor = new TextTransformExecutor();
        AiTextProcessorExecutor aiTextProcessorExecutor = new AiTextProcessorExecutor(ollamaService);
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
    @DisplayName("Successfully execute AI_TEXT_PROCESSOR combining prompt and upstream input text")
    void shouldSuccessfullyExecuteAiNodeWithUpstreamText() {
        when(ollamaService.generateChat(any(), any()))
            .thenReturn(new AiChatResponse("Polymorphism allows objects to take multiple forms.", "qwen2.5:3b"));

        ObjectNode graph = objectMapper.createObjectNode();
        ArrayNode nodes = graph.putArray("nodes");
        nodes.addObject().put("id", "trig-1").put("type", "MANUAL_TRIGGER");

        ObjectNode textNode = nodes.addObject();
        textNode.put("id", "text-1").put("type", "TEXT_INPUT");
        textNode.putObject("config").put("text", "Java polymorphism");

        ObjectNode aiNode = nodes.addObject();
        aiNode.put("id", "ai-1").put("type", "AI_TEXT_PROCESSOR");
        aiNode.putObject("config").put("prompt", "Explain the following text in simple terms.");

        nodes.addObject().put("id", "out-1").put("type", "OUTPUT");

        ArrayNode edges = graph.putArray("edges");
        edges.addObject().put("source", "trig-1").put("target", "text-1");
        edges.addObject().put("source", "text-1").put("target", "ai-1");
        edges.addObject().put("source", "ai-1").put("target", "out-1");

        WorkflowGraph validatedGraph = validator.validateAndBuild(graph);

        WorkflowExecution execution = new WorkflowExecution();
        execution.setId(10L);
        execution.setWorkflow(sampleWorkflow);

        WorkflowExecution result = engine.run(validatedGraph, execution);

        assertThat(result.getStatus()).isEqualTo("COMPLETED");
        assertThat(result.getOutputData().get("result").asText())
            .isEqualTo("Polymorphism allows objects to take multiple forms.");

        verify(ollamaService).generateChat(
            argThat(p -> p.contains("Explain the following text in simple terms.") && p.contains("Java polymorphism")),
            eq(null)
        );
    }

    @Test
    @DisplayName("Successfully execute AI_TEXT_PROCESSOR with prompt only when no upstream text exists")
    void shouldExecuteAiNodeWithPromptOnlyWhenNoUpstreamText() {
        when(ollamaService.generateChat(any(), any()))
            .thenReturn(new AiChatResponse("Spring Boot accelerates microservices development.", "qwen2.5:3b"));

        ObjectNode graph = objectMapper.createObjectNode();
        ArrayNode nodes = graph.putArray("nodes");
        nodes.addObject().put("id", "trig-1").put("type", "MANUAL_TRIGGER");

        ObjectNode aiNode = nodes.addObject();
        aiNode.put("id", "ai-1").put("type", "AI_TEXT_PROCESSOR");
        aiNode.putObject("config").put("prompt", "Explain Spring Boot concisely.");

        nodes.addObject().put("id", "out-1").put("type", "OUTPUT");

        ArrayNode edges = graph.putArray("edges");
        edges.addObject().put("source", "trig-1").put("target", "ai-1");
        edges.addObject().put("source", "ai-1").put("target", "out-1");

        WorkflowGraph validatedGraph = validator.validateAndBuild(graph);

        WorkflowExecution execution = new WorkflowExecution();
        execution.setId(11L);
        execution.setWorkflow(sampleWorkflow);

        WorkflowExecution result = engine.run(validatedGraph, execution);

        assertThat(result.getStatus()).isEqualTo("COMPLETED");
        assertThat(result.getOutputData().get("result").asText())
            .isEqualTo("Spring Boot accelerates microservices development.");

        verify(ollamaService).generateChat(eq("Explain Spring Boot concisely."), eq(null));
    }

    @Test
    @DisplayName("Fail when AI_TEXT_PROCESSOR has neither configured prompt nor upstream text")
    void shouldFailWhenBothPromptAndUpstreamTextAreMissing() {
        ObjectNode graph = objectMapper.createObjectNode();
        ArrayNode nodes = graph.putArray("nodes");
        nodes.addObject().put("id", "trig-1").put("type", "MANUAL_TRIGGER");
        nodes.addObject().put("id", "ai-1").put("type", "AI_TEXT_PROCESSOR"); // no prompt in config
        nodes.addObject().put("id", "out-1").put("type", "OUTPUT");

        ArrayNode edges = graph.putArray("edges");
        edges.addObject().put("source", "trig-1").put("target", "ai-1");
        edges.addObject().put("source", "ai-1").put("target", "out-1");

        WorkflowGraph validatedGraph = validator.validateAndBuild(graph);

        WorkflowExecution execution = new WorkflowExecution();
        execution.setId(12L);
        execution.setWorkflow(sampleWorkflow);

        WorkflowExecution result = engine.run(validatedGraph, execution);

        assertThat(result.getStatus()).isEqualTo("FAILED");
        assertThat(result.getErrorMessage()).contains("requires a prompt in config or input text from an upstream node");
    }

    @Test
    @DisplayName("Fail workflow execution when OllamaService throws an exception")
    void shouldFailWorkflowExecutionWhenOllamaThrowsException() {
        when(ollamaService.generateChat(any(), any()))
            .thenThrow(new AiServiceUnavailableException("Cannot connect to local Ollama service at http://localhost:11434"));

        ObjectNode graph = objectMapper.createObjectNode();
        ArrayNode nodes = graph.putArray("nodes");
        nodes.addObject().put("id", "trig-1").put("type", "MANUAL_TRIGGER");

        ObjectNode aiNode = nodes.addObject();
        aiNode.put("id", "ai-1").put("type", "AI_TEXT_PROCESSOR");
        aiNode.putObject("config").put("prompt", "Explain AI");

        nodes.addObject().put("id", "out-1").put("type", "OUTPUT");

        ArrayNode edges = graph.putArray("edges");
        edges.addObject().put("source", "trig-1").put("target", "ai-1");
        edges.addObject().put("source", "ai-1").put("target", "out-1");

        WorkflowGraph validatedGraph = validator.validateAndBuild(graph);

        WorkflowExecution execution = new WorkflowExecution();
        execution.setId(13L);
        execution.setWorkflow(sampleWorkflow);

        WorkflowExecution result = engine.run(validatedGraph, execution);

        assertThat(result.getStatus()).isEqualTo("FAILED");
        assertThat(result.getErrorMessage()).contains("Cannot connect to local Ollama service at http://localhost:11434");
    }

    @Test
    @DisplayName("Execute workflow with variable interpolation across TEXT_INPUT and AI_TEXT_PROCESSOR")
    void shouldExecuteWorkflowWithVariableInterpolationAcrossNodes() {
        // Workflow definition contains variables
        ObjectNode graph = objectMapper.createObjectNode();
        ObjectNode variables = graph.putObject("variables");
        variables.put("role", "Technical Lead");

        ArrayNode nodes = graph.putArray("nodes");
        nodes.addObject().put("id", "trig-1").put("type", "MANUAL_TRIGGER");

        ObjectNode textNode = nodes.addObject();
        textNode.put("id", "text-1");
        textNode.put("type", "TEXT_INPUT");
        textNode.putObject("config").put("text", "Task for {{variables.role}}: {{input.query}}");

        ObjectNode aiNode = nodes.addObject();
        aiNode.put("id", "ai-1");
        aiNode.put("type", "AI_TEXT_PROCESSOR");
        aiNode.putObject("config").put("prompt", "Please prioritize this task: {{nodes.text-1.text}}");

        nodes.addObject().put("id", "out-1").put("type", "OUTPUT");

        ArrayNode edges = graph.putArray("edges");
        edges.addObject().put("source", "trig-1").put("target", "text-1");
        edges.addObject().put("source", "text-1").put("target", "ai-1");
        edges.addObject().put("source", "ai-1").put("target", "out-1");

        Workflow workflow = new Workflow("Interpolation Flow", "Desc", graph, true);
        workflow.setId(200L);

        WorkflowGraph validatedGraph = validator.validateAndBuild(graph);

        ObjectNode inputData = objectMapper.createObjectNode();
        inputData.put("query", "Refactor monolithic controllers");

        WorkflowExecution execution = new WorkflowExecution();
        execution.setId(50L);
        execution.setWorkflow(workflow);
        execution.setInputData(inputData);

        when(ollamaService.generateChat(
            eq("Please prioritize this task: Task for Technical Lead: Refactor monolithic controllers"),
            eq(null)
        )).thenReturn(new AiChatResponse("High priority: break down into feature services.", "qwen2.5:3b"));

        WorkflowExecution result = engine.run(validatedGraph, execution);

        assertThat(result.getStatus()).isEqualTo("COMPLETED");
        assertThat(result.getOutputData().get("result").asText())
            .isEqualTo("High priority: break down into feature services.");
        verify(ollamaService).generateChat(
            eq("Please prioritize this task: Task for Technical Lead: Refactor monolithic controllers"),
            eq(null)
        );
    }

    @Test
    @DisplayName("Execute workflow containing a CONDITION node and record its structured evaluation")
    void shouldExecuteWorkflowWithConditionNode() {
        ObjectNode graph = objectMapper.createObjectNode();
        ArrayNode nodes = graph.putArray("nodes");
        nodes.addObject().put("id", "trig-1").put("type", "MANUAL_TRIGGER");

        ObjectNode condNode = nodes.addObject();
        condNode.put("id", "cond-1");
        condNode.put("type", "CONDITION");
        ObjectNode condConfig = condNode.putObject("config");
        condConfig.put("leftOperand", "{{input.score}}");
        condConfig.put("operator", "GREATER_THAN");
        condConfig.put("rightOperand", "75");

        nodes.addObject().put("id", "out-1").put("type", "OUTPUT");

        ArrayNode edges = graph.putArray("edges");
        edges.addObject().put("source", "trig-1").put("target", "cond-1");
        edges.addObject().put("source", "cond-1").put("target", "out-1").put("sourceHandle", "true");

        Workflow workflow = new Workflow("Condition Flow", "Desc", graph, true);
        workflow.setId(300L);

        WorkflowGraph validatedGraph = validator.validateAndBuild(graph);

        ObjectNode inputData = objectMapper.createObjectNode();
        inputData.put("score", 90);

        WorkflowExecution execution = new WorkflowExecution();
        execution.setId(60L);
        execution.setWorkflow(workflow);
        execution.setInputData(inputData);

        WorkflowExecution result = engine.run(validatedGraph, execution);

        assertThat(result.getStatus()).isEqualTo("COMPLETED");
        JsonNode condOutput = result.getOutputData().get("result");
        assertThat(condOutput.get("result").asBoolean()).isTrue();
        assertThat(condOutput.get("branch").asText()).isEqualTo("TRUE");
        assertThat(condOutput.get("operator").asText()).isEqualTo("GREATER_THAN");
    }

    @Test
    @DisplayName("TRUE branch executes and FALSE branch is skipped")
    void shouldExecuteTrueBranchAndSkipFalseBranch() {
        ObjectNode graph = objectMapper.createObjectNode();
        ArrayNode nodes = graph.putArray("nodes");
        nodes.addObject().put("id", "trig-1").put("type", "MANUAL_TRIGGER");

        ObjectNode condNode = nodes.addObject();
        condNode.put("id", "cond-1").put("type", "CONDITION");
        condNode.putObject("config")
                .put("leftOperand", "{{input.status}}")
                .put("operator", "EQUALS")
                .put("rightOperand", "ACTIVE");

        ObjectNode textTrue = nodes.addObject();
        textTrue.put("id", "text-true").put("type", "TEXT_INPUT");
        textTrue.putObject("config").put("text", "Processed via TRUE branch");

        ObjectNode textFalse = nodes.addObject();
        textFalse.put("id", "text-false").put("type", "TEXT_INPUT");
        textFalse.putObject("config").put("text", "Processed via FALSE branch");

        nodes.addObject().put("id", "out-true").put("type", "OUTPUT");
        nodes.addObject().put("id", "out-false").put("type", "OUTPUT");

        ArrayNode edges = graph.putArray("edges");
        edges.addObject().put("source", "trig-1").put("target", "cond-1");
        edges.addObject().put("source", "cond-1").put("target", "text-true").put("sourceHandle", "true");
        edges.addObject().put("source", "cond-1").put("target", "text-false").put("sourceHandle", "false");
        edges.addObject().put("source", "text-true").put("target", "out-true");
        edges.addObject().put("source", "text-false").put("target", "out-false");

        Workflow workflow = new Workflow("Branching Flow", "Desc", graph, true);
        workflow.setId(301L);
        WorkflowGraph validatedGraph = validator.validateAndBuild(graph);

        ObjectNode inputData = objectMapper.createObjectNode();
        inputData.put("status", "ACTIVE");

        WorkflowExecution execution = new WorkflowExecution();
        execution.setId(61L);
        execution.setWorkflow(workflow);
        execution.setInputData(inputData);

        WorkflowExecution result = engine.run(validatedGraph, execution);

        assertThat(result.getStatus()).isEqualTo("COMPLETED");

        ArgumentCaptor<NodeExecutionLog> logCaptor = ArgumentCaptor.forClass(NodeExecutionLog.class);
        verify(nodeLogRepository, atLeastOnce()).save(logCaptor.capture());
        List<NodeExecutionLog> savedLogs = logCaptor.getAllValues();

        NodeExecutionLog condLog = findLatestLog(savedLogs, "cond-1");
        assertThat(condLog.getStatus()).isEqualTo("COMPLETED");
        assertThat(condLog.getOutputData().get("branch").asText()).isEqualTo("TRUE");

        NodeExecutionLog trueTextLog = findLatestLog(savedLogs, "text-true");
        assertThat(trueTextLog.getStatus()).isEqualTo("COMPLETED");

        NodeExecutionLog trueOutLog = findLatestLog(savedLogs, "out-true");
        assertThat(trueOutLog.getStatus()).isEqualTo("COMPLETED");

        NodeExecutionLog falseTextLog = findLatestLog(savedLogs, "text-false");
        assertThat(falseTextLog.getStatus()).isEqualTo("SKIPPED");
        assertThat(falseTextLog.getOutputData().get("skipped").asBoolean()).isTrue();
        assertThat(falseTextLog.getOutputData().get("reason").asText()).isEqualTo("Branch not taken");

        NodeExecutionLog falseOutLog = findLatestLog(savedLogs, "out-false");
        assertThat(falseOutLog.getStatus()).isEqualTo("SKIPPED");
    }

    @Test
    @DisplayName("FALSE branch executes and TRUE branch is skipped")
    void shouldExecuteFalseBranchAndSkipTrueBranch() {
        ObjectNode graph = objectMapper.createObjectNode();
        ArrayNode nodes = graph.putArray("nodes");
        nodes.addObject().put("id", "trig-1").put("type", "MANUAL_TRIGGER");

        ObjectNode condNode = nodes.addObject();
        condNode.put("id", "cond-1").put("type", "CONDITION");
        condNode.putObject("config")
                .put("leftOperand", "{{input.status}}")
                .put("operator", "EQUALS")
                .put("rightOperand", "ACTIVE");

        ObjectNode textTrue = nodes.addObject();
        textTrue.put("id", "text-true").put("type", "TEXT_INPUT");
        textTrue.putObject("config").put("text", "Processed via TRUE branch");

        ObjectNode textFalse = nodes.addObject();
        textFalse.put("id", "text-false").put("type", "TEXT_INPUT");
        textFalse.putObject("config").put("text", "Processed via FALSE branch");

        nodes.addObject().put("id", "out-true").put("type", "OUTPUT");
        nodes.addObject().put("id", "out-false").put("type", "OUTPUT");

        ArrayNode edges = graph.putArray("edges");
        edges.addObject().put("source", "trig-1").put("target", "cond-1");
        edges.addObject().put("source", "cond-1").put("target", "text-true").put("sourceHandle", "true");
        edges.addObject().put("source", "cond-1").put("target", "text-false").put("sourceHandle", "false");
        edges.addObject().put("source", "text-true").put("target", "out-true");
        edges.addObject().put("source", "text-false").put("target", "out-false");

        Workflow workflow = new Workflow("Branching Flow False", "Desc", graph, true);
        workflow.setId(302L);
        WorkflowGraph validatedGraph = validator.validateAndBuild(graph);

        ObjectNode inputData = objectMapper.createObjectNode();
        inputData.put("status", "INACTIVE");

        WorkflowExecution execution = new WorkflowExecution();
        execution.setId(62L);
        execution.setWorkflow(workflow);
        execution.setInputData(inputData);

        WorkflowExecution result = engine.run(validatedGraph, execution);

        assertThat(result.getStatus()).isEqualTo("COMPLETED");

        ArgumentCaptor<NodeExecutionLog> logCaptor = ArgumentCaptor.forClass(NodeExecutionLog.class);
        verify(nodeLogRepository, atLeastOnce()).save(logCaptor.capture());
        List<NodeExecutionLog> savedLogs = logCaptor.getAllValues();

        NodeExecutionLog condLog = findLatestLog(savedLogs, "cond-1");
        assertThat(condLog.getStatus()).isEqualTo("COMPLETED");
        assertThat(condLog.getOutputData().get("branch").asText()).isEqualTo("FALSE");

        NodeExecutionLog trueTextLog = findLatestLog(savedLogs, "text-true");
        assertThat(trueTextLog.getStatus()).isEqualTo("SKIPPED");

        NodeExecutionLog falseTextLog = findLatestLog(savedLogs, "text-false");
        assertThat(falseTextLog.getStatus()).isEqualTo("COMPLETED");
    }

    @Test
    @DisplayName("A multi-node inactive branch is skipped transitively")
    void shouldTransitivelySkipMultiNodeBranch() {
        ObjectNode graph = objectMapper.createObjectNode();
        ArrayNode nodes = graph.putArray("nodes");
        nodes.addObject().put("id", "trig-1").put("type", "MANUAL_TRIGGER");

        ObjectNode condNode = nodes.addObject();
        condNode.put("id", "cond-1").put("type", "CONDITION");
        condNode.putObject("config")
                .put("leftOperand", "{{input.value}}")
                .put("operator", "GREATER_THAN")
                .put("rightOperand", "100");

        // True branch: 3 consecutive steps
        ObjectNode true1 = nodes.addObject();
        true1.put("id", "true-step1").put("type", "TEXT_INPUT");
        true1.putObject("config").put("text", "Step 1");

        ObjectNode true2 = nodes.addObject();
        true2.put("id", "true-step2").put("type", "TEXT_TRANSFORM");
        true2.putObject("config").put("operation", "UPPERCASE");

        ObjectNode true3 = nodes.addObject();
        true3.put("id", "true-step3").put("type", "TEXT_TRANSFORM");
        true3.putObject("config").put("operation", "TRIM");

        nodes.addObject().put("id", "out-true").put("type", "OUTPUT");

        // False branch: single step
        ObjectNode false1 = nodes.addObject();
        false1.put("id", "false-step1").put("type", "TEXT_INPUT");
        false1.putObject("config").put("text", "Fallback");

        nodes.addObject().put("id", "out-false").put("type", "OUTPUT");

        ArrayNode edges = graph.putArray("edges");
        edges.addObject().put("source", "trig-1").put("target", "cond-1");
        edges.addObject().put("source", "cond-1").put("target", "true-step1").put("sourceHandle", "true");
        edges.addObject().put("source", "true-step1").put("target", "true-step2");
        edges.addObject().put("source", "true-step2").put("target", "true-step3");
        edges.addObject().put("source", "true-step3").put("target", "out-true");
        edges.addObject().put("source", "cond-1").put("target", "false-step1").put("sourceHandle", "false");
        edges.addObject().put("source", "false-step1").put("target", "out-false");

        Workflow workflow = new Workflow("Transitive Skip Flow", "Desc", graph, true);
        workflow.setId(303L);
        WorkflowGraph validatedGraph = validator.validateAndBuild(graph);

        ObjectNode inputData = objectMapper.createObjectNode();
        inputData.put("value", 50); // <= 100 -> evaluates to FALSE

        WorkflowExecution execution = new WorkflowExecution();
        execution.setId(63L);
        execution.setWorkflow(workflow);
        execution.setInputData(inputData);

        WorkflowExecution result = engine.run(validatedGraph, execution);

        assertThat(result.getStatus()).isEqualTo("COMPLETED");

        ArgumentCaptor<NodeExecutionLog> logCaptor = ArgumentCaptor.forClass(NodeExecutionLog.class);
        verify(nodeLogRepository, atLeastOnce()).save(logCaptor.capture());
        List<NodeExecutionLog> savedLogs = logCaptor.getAllValues();

        assertThat(findLatestLog(savedLogs, "true-step1").getStatus()).isEqualTo("SKIPPED");
        assertThat(findLatestLog(savedLogs, "true-step2").getStatus()).isEqualTo("SKIPPED");
        assertThat(findLatestLog(savedLogs, "true-step3").getStatus()).isEqualTo("SKIPPED");
        assertThat(findLatestLog(savedLogs, "out-true").getStatus()).isEqualTo("SKIPPED");

        assertThat(findLatestLog(savedLogs, "false-step1").getStatus()).isEqualTo("COMPLETED");
        assertThat(findLatestLog(savedLogs, "out-false").getStatus()).isEqualTo("COMPLETED");
    }

    @Test
    @DisplayName("A branch rejoins and the merge node executes exactly once")
    void shouldRejoinBranchesAndExecuteMergeNodeOnce() {
        ObjectNode graph = objectMapper.createObjectNode();
        ArrayNode nodes = graph.putArray("nodes");
        nodes.addObject().put("id", "trig-1").put("type", "MANUAL_TRIGGER");

        ObjectNode condNode = nodes.addObject();
        condNode.put("id", "cond-1").put("type", "CONDITION");
        condNode.putObject("config")
                .put("leftOperand", "{{input.role}}")
                .put("operator", "EQUALS")
                .put("rightOperand", "ADMIN");

        ObjectNode adminText = nodes.addObject();
        adminText.put("id", "admin-text").put("type", "TEXT_INPUT");
        adminText.putObject("config").put("text", "admin access granted");

        ObjectNode userText = nodes.addObject();
        userText.put("id", "user-text").put("type", "TEXT_INPUT");
        userText.putObject("config").put("text", "standard user access");

        ObjectNode mergeNode = nodes.addObject();
        mergeNode.put("id", "merge-transform").put("type", "TEXT_TRANSFORM");
        mergeNode.putObject("config").put("operation", "UPPERCASE");

        nodes.addObject().put("id", "out-1").put("type", "OUTPUT");

        ArrayNode edges = graph.putArray("edges");
        edges.addObject().put("source", "trig-1").put("target", "cond-1");
        edges.addObject().put("source", "cond-1").put("target", "admin-text").put("sourceHandle", "true");
        edges.addObject().put("source", "cond-1").put("target", "user-text").put("sourceHandle", "false");
        edges.addObject().put("source", "admin-text").put("target", "merge-transform");
        edges.addObject().put("source", "user-text").put("target", "merge-transform");
        edges.addObject().put("source", "merge-transform").put("target", "out-1");

        Workflow workflow = new Workflow("Rejoin Flow", "Desc", graph, true);
        workflow.setId(304L);
        WorkflowGraph validatedGraph = validator.validateAndBuild(graph);

        ObjectNode inputData = objectMapper.createObjectNode();
        inputData.put("role", "ADMIN");

        WorkflowExecution execution = new WorkflowExecution();
        execution.setId(64L);
        execution.setWorkflow(workflow);
        execution.setInputData(inputData);

        WorkflowExecution result = engine.run(validatedGraph, execution);

        assertThat(result.getStatus()).isEqualTo("COMPLETED");

        ArgumentCaptor<NodeExecutionLog> logCaptor = ArgumentCaptor.forClass(NodeExecutionLog.class);
        verify(nodeLogRepository, atLeastOnce()).save(logCaptor.capture());
        List<NodeExecutionLog> savedLogs = logCaptor.getAllValues();

        assertThat(findLatestLog(savedLogs, "admin-text").getStatus()).isEqualTo("COMPLETED");
        assertThat(findLatestLog(savedLogs, "user-text").getStatus()).isEqualTo("SKIPPED");

        NodeExecutionLog mergeLog = findLatestLog(savedLogs, "merge-transform");
        assertThat(mergeLog.getStatus()).isEqualTo("COMPLETED");
        assertThat(mergeLog.getOutputData().get("text").asText()).isEqualTo("ADMIN ACCESS GRANTED");

        NodeExecutionLog outLog = findLatestLog(savedLogs, "out-1");
        assertThat(outLog.getStatus()).isEqualTo("COMPLETED");
    }

    @Test
    @DisplayName("Skipped outputs are not passed as ordinary data")
    void shouldNotPassSkippedOutputsAsOrdinaryData() {
        ObjectNode graph = objectMapper.createObjectNode();
        ArrayNode nodes = graph.putArray("nodes");
        nodes.addObject().put("id", "trig-1").put("type", "MANUAL_TRIGGER");

        ObjectNode condNode = nodes.addObject();
        condNode.put("id", "cond-1").put("type", "CONDITION");
        condNode.putObject("config")
                .put("leftOperand", "{{input.role}}")
                .put("operator", "EQUALS")
                .put("rightOperand", "ADMIN");

        ObjectNode adminText = nodes.addObject();
        adminText.put("id", "admin-text").put("type", "TEXT_INPUT");
        adminText.putObject("config").put("text", "admin payload");

        ObjectNode userText = nodes.addObject();
        userText.put("id", "user-text").put("type", "TEXT_INPUT");
        userText.putObject("config").put("text", "user payload");

        ObjectNode downstreamText = nodes.addObject();
        downstreamText.put("id", "downstream-text").put("type", "TEXT_INPUT");
        downstreamText.putObject("config")
                .put("text", "Admin: {{nodes.admin-text.text}}, User: {{nodes.user-text.text}}");

        nodes.addObject().put("id", "out-1").put("type", "OUTPUT");

        ArrayNode edges = graph.putArray("edges");
        edges.addObject().put("source", "trig-1").put("target", "cond-1");
        edges.addObject().put("source", "cond-1").put("target", "admin-text").put("sourceHandle", "true");
        edges.addObject().put("source", "cond-1").put("target", "user-text").put("sourceHandle", "false");
        edges.addObject().put("source", "admin-text").put("target", "downstream-text");
        edges.addObject().put("source", "user-text").put("target", "downstream-text");
        edges.addObject().put("source", "downstream-text").put("target", "out-1");

        Workflow workflow = new Workflow("Skipped Output Flow", "Desc", graph, true);
        workflow.setId(305L);
        WorkflowGraph validatedGraph = validator.validateAndBuild(graph);

        ObjectNode inputData = objectMapper.createObjectNode();
        inputData.put("role", "ADMIN");

        WorkflowExecution execution = new WorkflowExecution();
        execution.setId(65L);
        execution.setWorkflow(workflow);
        execution.setInputData(inputData);

        WorkflowExecution result = engine.run(validatedGraph, execution);

        assertThat(result.getStatus()).isEqualTo("COMPLETED");

        ArgumentCaptor<NodeExecutionLog> logCaptor = ArgumentCaptor.forClass(NodeExecutionLog.class);
        verify(nodeLogRepository, atLeastOnce()).save(logCaptor.capture());
        List<NodeExecutionLog> savedLogs = logCaptor.getAllValues();

        NodeExecutionLog downstreamLog = findLatestLog(savedLogs, "downstream-text");
        assertThat(downstreamLog.getStatus()).isEqualTo("COMPLETED");
        // {{nodes.user-text.text}} was skipped so it remained unresolved; it did not inject stale or skipped payload
        assertThat(downstreamLog.getOutputData().get("text").asText())
                .isEqualTo("Admin: admin payload, User: {{nodes.user-text.text}}");
    }

    @Test
    @DisplayName("CONDITION node with an unconnected selected branch behaves predictably")
    void shouldHandleUnconnectedSelectedBranchPredictably() {
        ObjectNode graph = objectMapper.createObjectNode();
        ArrayNode nodes = graph.putArray("nodes");
        nodes.addObject().put("id", "trig-1").put("type", "MANUAL_TRIGGER");

        ObjectNode condNode = nodes.addObject();
        condNode.put("id", "cond-1").put("type", "CONDITION");
        condNode.putObject("config")
                .put("leftOperand", "{{input.score}}")
                .put("operator", "GREATER_THAN")
                .put("rightOperand", "100");

        ObjectNode falseText = nodes.addObject();
        falseText.put("id", "false-text").put("type", "TEXT_INPUT");
        falseText.putObject("config").put("text", "Handled false");

        nodes.addObject().put("id", "out-false").put("type", "OUTPUT");

        ArrayNode edges = graph.putArray("edges");
        edges.addObject().put("source", "trig-1").put("target", "cond-1");
        // Only the FALSE branch is connected; TRUE branch has no outgoing edge!
        edges.addObject().put("source", "cond-1").put("target", "false-text").put("sourceHandle", "false");
        edges.addObject().put("source", "false-text").put("target", "out-false");

        Workflow workflow = new Workflow("Unconnected True Flow", "Desc", graph, true);
        workflow.setId(306L);
        WorkflowGraph validatedGraph = validator.validateAndBuild(graph);

        ObjectNode inputData = objectMapper.createObjectNode();
        inputData.put("score", 150); // > 100 -> evaluates to TRUE (unconnected)

        WorkflowExecution execution = new WorkflowExecution();
        execution.setId(66L);
        execution.setWorkflow(workflow);
        execution.setInputData(inputData);

        WorkflowExecution result = engine.run(validatedGraph, execution);

        assertThat(result.getStatus()).isEqualTo("COMPLETED");

        ArgumentCaptor<NodeExecutionLog> logCaptor = ArgumentCaptor.forClass(NodeExecutionLog.class);
        verify(nodeLogRepository, atLeastOnce()).save(logCaptor.capture());
        List<NodeExecutionLog> savedLogs = logCaptor.getAllValues();

        assertThat(findLatestLog(savedLogs, "cond-1").getStatus()).isEqualTo("COMPLETED");
        assertThat(findLatestLog(savedLogs, "cond-1").getOutputData().get("branch").asText()).isEqualTo("TRUE");

        // The unconnected TRUE branch activated nothing, so false-text and out-false are skipped
        assertThat(findLatestLog(savedLogs, "false-text").getStatus()).isEqualTo("SKIPPED");
        assertThat(findLatestLog(savedLogs, "out-false").getStatus()).isEqualTo("SKIPPED");
    }

    @Test
    @DisplayName("Invalid condition edge handles are rejected by validator")
    void shouldRejectInvalidConditionEdgeHandles() {
        ObjectNode graph = objectMapper.createObjectNode();
        ArrayNode nodes = graph.putArray("nodes");
        nodes.addObject().put("id", "trig-1").put("type", "MANUAL_TRIGGER");
        nodes.addObject().put("id", "cond-1").put("type", "CONDITION");
        nodes.addObject().put("id", "out-1").put("type", "OUTPUT");

        ArrayNode edges = graph.putArray("edges");
        edges.addObject().put("source", "trig-1").put("target", "cond-1");
        // Missing sourceHandle
        edges.addObject().put("source", "cond-1").put("target", "out-1");

        assertThatThrownBy(() -> validator.validateAndBuild(graph))
                .isInstanceOf(WorkflowValidationException.class)
                .hasMessageContaining("must specify a 'sourceHandle'");

        // Invalid sourceHandle
        ArrayNode edges2 = objectMapper.createArrayNode();
        edges2.addObject().put("source", "trig-1").put("target", "cond-1");
        edges2.addObject().put("source", "cond-1").put("target", "out-1").put("sourceHandle", "maybe");
        graph.set("edges", edges2);

        assertThatThrownBy(() -> validator.validateAndBuild(graph))
                .isInstanceOf(WorkflowValidationException.class)
                .hasMessageContaining("invalid sourceHandle 'maybe'");
    }

    @Test
    @DisplayName("Existing workflows without CONDITION nodes remain unchanged")
    void shouldPreserveExistingWorkflowWithoutConditionNodes() {
        ObjectNode graph = objectMapper.createObjectNode();
        ArrayNode nodes = graph.putArray("nodes");
        nodes.addObject().put("id", "trig-1").put("type", "MANUAL_TRIGGER");

        ObjectNode textNode = nodes.addObject();
        textNode.put("id", "text-1").put("type", "TEXT_INPUT");
        textNode.putObject("config").put("text", "hello linear workflow");

        ObjectNode transformNode = nodes.addObject();
        transformNode.put("id", "trans-1").put("type", "TEXT_TRANSFORM");
        transformNode.putObject("config").put("operation", "UPPERCASE");

        nodes.addObject().put("id", "out-1").put("type", "OUTPUT");

        ArrayNode edges = graph.putArray("edges");
        edges.addObject().put("source", "trig-1").put("target", "text-1");
        edges.addObject().put("source", "text-1").put("target", "trans-1");
        edges.addObject().put("source", "trans-1").put("target", "out-1");

        Workflow workflow = new Workflow("Linear Flow", "Desc", graph, true);
        workflow.setId(307L);
        WorkflowGraph validatedGraph = validator.validateAndBuild(graph);

        WorkflowExecution execution = new WorkflowExecution();
        execution.setId(67L);
        execution.setWorkflow(workflow);
        execution.setInputData(objectMapper.createObjectNode());

        WorkflowExecution result = engine.run(validatedGraph, execution);

        assertThat(result.getStatus()).isEqualTo("COMPLETED");
        assertThat(result.getOutputData().get("result").asText()).isEqualTo("HELLO LINEAR WORKFLOW");

        ArgumentCaptor<NodeExecutionLog> logCaptor = ArgumentCaptor.forClass(NodeExecutionLog.class);
        verify(nodeLogRepository, atLeastOnce()).save(logCaptor.capture());
        List<NodeExecutionLog> savedLogs = logCaptor.getAllValues();

        assertThat(savedLogs.stream().noneMatch(l -> "SKIPPED".equals(l.getStatus()))).isTrue();
    }

    private NodeExecutionLog findLatestLog(List<NodeExecutionLog> logs, String nodeId) {
        List<NodeExecutionLog> matching = logs.stream()
                .filter(l -> l.getNodeId().equals(nodeId))
                .toList();
        if (matching.isEmpty()) {
            throw new AssertionError("No logs found for node: " + nodeId);
        }
        return matching.get(matching.size() - 1);
    }
}
