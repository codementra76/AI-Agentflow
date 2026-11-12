package com.agentflow;

import com.agentflow.dto.ai.AiChatResponse;
import com.agentflow.entity.Workflow;
import com.agentflow.entity.WorkflowExecution;
import com.agentflow.repository.NodeExecutionLogRepository;
import com.agentflow.repository.WorkflowExecutionRepository;
import com.agentflow.repository.WorkflowRepository;
import com.agentflow.service.OllamaService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ConditionalWorkflowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private WorkflowRepository workflowRepository;

    @Autowired
    private WorkflowExecutionRepository executionRepository;

    @Autowired
    private NodeExecutionLogRepository nodeLogRepository;

    @MockitoBean
    private OllamaService ollamaService;

    @BeforeEach
    void cleanUp() {
        nodeLogRepository.deleteAll();
        executionRepository.deleteAll();
        workflowRepository.deleteAll();
    }

    /**
     * Builds a standard conditional workflow with branch convergence:
     *
     *   MANUAL_TRIGGER (trigger-1)
     *          │
     *      CONDITION (cond-1): {{input.score}} GREATER_THAN_OR_EQUAL 70
     *         ┌┴──────────────┐
     *  (TRUE) │               │ (FALSE)
     *         ▼               ▼
     *    TEXT_INPUT      TEXT_INPUT
     *   (true-branch)   (false-branch)
     *         │               │
     *         └───────┬───────┘
     *                 ▼
     *              OUTPUT (out-1)
     */
    private ObjectNode buildConditionalWorkflowPayload(String name) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("name", name);
        payload.put("description", "End-to-End Conditional Workflow with True/False branches and Rejoining");
        payload.put("active", true);

        ObjectNode definition = payload.putObject("workflowDefinition");

        // Workflow variables
        ObjectNode variables = definition.putObject("variables");
        variables.put("passingThreshold", "70");

        // Nodes
        ArrayNode nodes = definition.putArray("nodes");

        nodes.addObject()
                .put("id", "trigger-1")
                .put("type", "MANUAL_TRIGGER")
                .put("name", "Manual Trigger");

        ObjectNode condNode = nodes.addObject();
        condNode.put("id", "cond-1");
        condNode.put("type", "CONDITION");
        condNode.put("name", "Score Check");
        ObjectNode condConfig = condNode.putObject("config");
        condConfig.put("leftOperand", "{{input.score}}");
        condConfig.put("operator", "GREATER_THAN_OR_EQUAL");
        condConfig.put("rightOperand", "70");

        ObjectNode trueNode = nodes.addObject();
        trueNode.put("id", "true-branch");
        trueNode.put("type", "TEXT_INPUT");
        trueNode.put("name", "Passed Candidate");
        trueNode.putObject("config").put("text", "PASSED: Candidate scored {{input.score}} (Threshold: {{variables.passingThreshold}})");

        ObjectNode falseNode = nodes.addObject();
        falseNode.put("id", "false-branch");
        falseNode.put("type", "TEXT_INPUT");
        falseNode.put("name", "Failed Candidate");
        falseNode.putObject("config").put("text", "REJECTED: Candidate scored {{input.score}} below {{variables.passingThreshold}}");

        nodes.addObject()
                .put("id", "out-1")
                .put("type", "OUTPUT")
                .put("name", "Final Decision Output");

        // Edges: 1 incoming, 2 conditional branches (true/false), 2 rejoining
        ArrayNode edges = definition.putArray("edges");

        // Ordinary edge into condition
        edges.addObject()
                .put("id", "e-trig-cond")
                .put("source", "trigger-1")
                .put("target", "cond-1");

        // TRUE branch edge
        edges.addObject()
                .put("id", "e-cond-true")
                .put("source", "cond-1")
                .put("target", "true-branch")
                .put("sourceHandle", "true")
                .put("label", "TRUE");

        // FALSE branch edge
        edges.addObject()
                .put("id", "e-cond-false")
                .put("source", "cond-1")
                .put("target", "false-branch")
                .put("sourceHandle", "false")
                .put("label", "FALSE");

        // Rejoining edges into OUTPUT
        edges.addObject()
                .put("id", "e-true-out")
                .put("source", "true-branch")
                .put("target", "out-1");

        edges.addObject()
                .put("id", "e-false-out")
                .put("source", "false-branch")
                .put("target", "out-1");

        return payload;
    }

    @Test
    @DisplayName("1. Persistence: Verify conditional workflow creation, restoration, and update with sourceHandle and labels")
    void shouldPersistAndRestoreConditionalWorkflow() throws Exception {
        ObjectNode payload = buildConditionalWorkflowPayload("Persistence Test Flow");

        // 1. Create workflow via POST /api/v1/workflows
        String createJson = mockMvc.perform(post("/api/v1/workflows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload.toString()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.workflowDefinition.nodes", hasSize(5)))
                .andExpect(jsonPath("$.data.workflowDefinition.edges", hasSize(5)))
                .andReturn().getResponse().getContentAsString();

        Long workflowId = objectMapper.readTree(createJson).get("data").get("id").asLong();

        // 2. Load workflow via GET /api/v1/workflows/{id} and verify preservation
        String getJson = mockMvc.perform(get("/api/v1/workflows/" + workflowId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Persistence Test Flow"))
                // Variables preserved
                .andExpect(jsonPath("$.data.workflowDefinition.variables.passingThreshold").value("70"))
                // Condition node configuration preserved
                .andExpect(jsonPath("$.data.workflowDefinition.nodes[1].id").value("cond-1"))
                .andExpect(jsonPath("$.data.workflowDefinition.nodes[1].type").value("CONDITION"))
                .andExpect(jsonPath("$.data.workflowDefinition.nodes[1].config.leftOperand").value("{{input.score}}"))
                .andExpect(jsonPath("$.data.workflowDefinition.nodes[1].config.operator").value("GREATER_THAN_OR_EQUAL"))
                .andExpect(jsonPath("$.data.workflowDefinition.nodes[1].config.rightOperand").value("70"))
                .andReturn().getResponse().getContentAsString();

        JsonNode edges = objectMapper.readTree(getJson).get("data").get("workflowDefinition").get("edges");
        assertThat(edges.isArray()).isTrue();

        // Verify TRUE branch edge has sourceHandle "true" and label "TRUE"
        JsonNode trueEdge = null;
        JsonNode falseEdge = null;
        JsonNode ordinaryEdge = null;
        for (JsonNode e : edges) {
            if ("cond-1".equals(e.get("source").asText()) && "true".equalsIgnoreCase(e.path("sourceHandle").asText())) {
                trueEdge = e;
            } else if ("cond-1".equals(e.get("source").asText()) && "false".equalsIgnoreCase(e.path("sourceHandle").asText())) {
                falseEdge = e;
            } else if ("trigger-1".equals(e.get("source").asText())) {
                ordinaryEdge = e;
            }
        }

        assertThat(trueEdge).isNotNull();
        assertThat(trueEdge.get("target").asText()).isEqualTo("true-branch");
        assertThat(trueEdge.get("sourceHandle").asText()).isEqualTo("true");
        assertThat(trueEdge.get("label").asText()).isEqualTo("TRUE");

        assertThat(falseEdge).isNotNull();
        assertThat(falseEdge.get("target").asText()).isEqualTo("false-branch");
        assertThat(falseEdge.get("sourceHandle").asText()).isEqualTo("false");
        assertThat(falseEdge.get("label").asText()).isEqualTo("FALSE");

        assertThat(ordinaryEdge).isNotNull();
        assertThat(ordinaryEdge.path("sourceHandle").isNull() || ordinaryEdge.path("sourceHandle").isMissingNode()).isTrue();
    }

    @Test
    @DisplayName("2. TRUE Branch: input score=85 executes TRUE branch, skips FALSE branch, reaches OUTPUT")
    void shouldExecuteTrueBranchAndSkipFalseBranch() throws Exception {
        ObjectNode payload = buildConditionalWorkflowPayload("True Branch Execution Flow");
        String createJson = mockMvc.perform(post("/api/v1/workflows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload.toString()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long workflowId = objectMapper.readTree(createJson).get("data").get("id").asLong();

        // Execute workflow with {"score": 85}
        ObjectNode execPayload = objectMapper.createObjectNode();
        execPayload.putObject("inputData").put("score", 85);

        String resultJson = mockMvc.perform(post("/api/v1/workflows/" + workflowId + "/execute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(execPayload.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                // Output from true-branch should reach final OUTPUT node
                .andExpect(jsonPath("$.data.outputData.result").value("PASSED: Candidate scored 85 (Threshold: 70)"))
                .andExpect(jsonPath("$.data.nodeLogs", hasSize(5)))
                .andReturn().getResponse().getContentAsString();

        JsonNode logs = objectMapper.readTree(resultJson).get("data").get("nodeLogs");

        // Find each node's log
        JsonNode condLog = findLogByNodeId(logs, "cond-1");
        JsonNode trueLog = findLogByNodeId(logs, "true-branch");
        JsonNode falseLog = findLogByNodeId(logs, "false-branch");
        JsonNode outLog = findLogByNodeId(logs, "out-1");

        // CONDITION evaluated to TRUE
        assertThat(condLog).isNotNull();
        assertThat(condLog.get("status").asText()).isEqualTo("COMPLETED");
        assertThat(condLog.get("outputData").get("result").asBoolean()).isTrue();
        assertThat(condLog.get("outputData").get("branch").asText()).isEqualTo("TRUE");

        // TRUE branch executed successfully
        assertThat(trueLog).isNotNull();
        assertThat(trueLog.get("status").asText()).isEqualTo("COMPLETED");
        assertThat(trueLog.get("outputData").get("text").asText()).isEqualTo("PASSED: Candidate scored 85 (Threshold: 70)");

        // FALSE branch was SKIPPED
        assertThat(falseLog).isNotNull();
        assertThat(falseLog.get("status").asText()).isEqualTo("SKIPPED");
        assertThat(falseLog.get("outputData").get("skipped").asBoolean()).isTrue();
        assertThat(falseLog.get("outputData").get("reason").asText()).contains("Branch not taken");

        // OUTPUT node executed with TRUE branch output
        assertThat(outLog).isNotNull();
        assertThat(outLog.get("status").asText()).isEqualTo("COMPLETED");
        assertThat(outLog.get("outputData").get("result").asText()).isEqualTo("PASSED: Candidate scored 85 (Threshold: 70)");

        // Verify database persistence of the execution and logs
        assertThat(executionRepository.findByWorkflowId(workflowId)).hasSize(1);
        assertThat(nodeLogRepository.findAll()).hasSize(5);
    }

    @Test
    @DisplayName("3. FALSE Branch: input score=45 executes FALSE branch, skips TRUE branch, reaches OUTPUT")
    void shouldExecuteFalseBranchAndSkipTrueBranch() throws Exception {
        ObjectNode payload = buildConditionalWorkflowPayload("False Branch Execution Flow");
        String createJson = mockMvc.perform(post("/api/v1/workflows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload.toString()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long workflowId = objectMapper.readTree(createJson).get("data").get("id").asLong();

        // Execute workflow with {"score": 45}
        ObjectNode execPayload = objectMapper.createObjectNode();
        execPayload.putObject("inputData").put("score", 45);

        String resultJson = mockMvc.perform(post("/api/v1/workflows/" + workflowId + "/execute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(execPayload.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                // Output from false-branch should reach final OUTPUT node
                .andExpect(jsonPath("$.data.outputData.result").value("REJECTED: Candidate scored 45 below 70"))
                .andExpect(jsonPath("$.data.nodeLogs", hasSize(5)))
                .andReturn().getResponse().getContentAsString();

        JsonNode logs = objectMapper.readTree(resultJson).get("data").get("nodeLogs");

        JsonNode condLog = findLogByNodeId(logs, "cond-1");
        JsonNode trueLog = findLogByNodeId(logs, "true-branch");
        JsonNode falseLog = findLogByNodeId(logs, "false-branch");
        JsonNode outLog = findLogByNodeId(logs, "out-1");

        // CONDITION evaluated to FALSE
        assertThat(condLog).isNotNull();
        assertThat(condLog.get("status").asText()).isEqualTo("COMPLETED");
        assertThat(condLog.get("outputData").get("result").asBoolean()).isFalse();
        assertThat(condLog.get("outputData").get("branch").asText()).isEqualTo("FALSE");

        // TRUE branch was SKIPPED
        assertThat(trueLog).isNotNull();
        assertThat(trueLog.get("status").asText()).isEqualTo("SKIPPED");
        assertThat(trueLog.get("outputData").get("skipped").asBoolean()).isTrue();

        // FALSE branch executed successfully
        assertThat(falseLog).isNotNull();
        assertThat(falseLog.get("status").asText()).isEqualTo("COMPLETED");
        assertThat(falseLog.get("outputData").get("text").asText()).isEqualTo("REJECTED: Candidate scored 45 below 70");

        // OUTPUT node executed with FALSE branch output
        assertThat(outLog).isNotNull();
        assertThat(outLog.get("status").asText()).isEqualTo("COMPLETED");
        assertThat(outLog.get("outputData").get("result").asText()).isEqualTo("REJECTED: Candidate scored 45 below 70");
    }

    @Test
    @DisplayName("4. Rejoining & Execution History: Convergence node runs once, history logs completed & skipped accurately")
    void shouldRejoinCleanlyAndRecordExecutionHistory() throws Exception {
        ObjectNode payload = buildConditionalWorkflowPayload("History & Rejoin Flow");
        String createJson = mockMvc.perform(post("/api/v1/workflows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload.toString()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long workflowId = objectMapper.readTree(createJson).get("data").get("id").asLong();

        // Run 1: Score 85 (True branch)
        ObjectNode exec1 = objectMapper.createObjectNode();
        exec1.putObject("inputData").put("score", 85);
        mockMvc.perform(post("/api/v1/workflows/" + workflowId + "/execute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(exec1.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));

        // Run 2: Score 45 (False branch)
        ObjectNode exec2 = objectMapper.createObjectNode();
        exec2.putObject("inputData").put("score", 45);
        mockMvc.perform(post("/api/v1/workflows/" + workflowId + "/execute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(exec2.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));

        // Query execution history via GET /api/v1/workflows/{id}/executions
        String historyJson = mockMvc.perform(get("/api/v1/workflows/" + workflowId + "/executions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andReturn().getResponse().getContentAsString();

        JsonNode historyArray = objectMapper.readTree(historyJson).get("data");
        Long run1Id = historyArray.get(0).get("id").asLong();
        Long run2Id = historyArray.get(1).get("id").asLong();

        // Inspect Run 1 details via GET /api/v1/executions/{id}
        mockMvc.perform(get("/api/v1/executions/" + run1Id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.nodeLogs", hasSize(5)));

        // Inspect Run 2 details via GET /api/v1/executions/{id}
        mockMvc.perform(get("/api/v1/executions/" + run2Id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.nodeLogs", hasSize(5)));
    }

    @Test
    @DisplayName("5. Backward Compatibility: Linear workflows without CONDITION execute without error")
    void shouldMaintainCompatibilityWithExistingLinearWorkflows() throws Exception {
        ObjectNode graph = objectMapper.createObjectNode();
        ArrayNode nodes = graph.putArray("nodes");
        nodes.addObject().put("id", "trig-1").put("type", "MANUAL_TRIGGER");
        nodes.addObject().put("id", "text-1").put("type", "TEXT_INPUT").putObject("config").put("text", "hello world");
        nodes.addObject().put("id", "out-1").put("type", "OUTPUT");

        ArrayNode edges = graph.putArray("edges");
        edges.addObject().put("source", "trig-1").put("target", "text-1");
        edges.addObject().put("source", "text-1").put("target", "out-1");

        Workflow workflow = new Workflow("Linear Compatibility Flow", "Linear flow", graph, true);
        Workflow savedWorkflow = workflowRepository.save(workflow);

        mockMvc.perform(post("/api/v1/workflows/" + savedWorkflow.getId() + "/execute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.outputData.result").value("hello world"))
                .andExpect(jsonPath("$.data.nodeLogs", hasSize(3)));
    }

    private JsonNode findLogByNodeId(JsonNode logs, String nodeId) {
        if (logs == null || !logs.isArray()) return null;
        for (JsonNode log : logs) {
            if (nodeId.equals(log.path("nodeId").asText())) {
                return log;
            }
        }
        return null;
    }
}
