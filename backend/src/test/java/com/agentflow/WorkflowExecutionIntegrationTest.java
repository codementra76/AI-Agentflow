package com.agentflow;

import com.agentflow.entity.Workflow;
import com.agentflow.repository.NodeExecutionLogRepository;
import com.agentflow.repository.WorkflowExecutionRepository;
import com.agentflow.repository.WorkflowRepository;
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
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class WorkflowExecutionIntegrationTest {

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

    @BeforeEach
    void cleanUp() {
        nodeLogRepository.deleteAll();
        executionRepository.deleteAll();
        workflowRepository.deleteAll();
    }

    @Test
    @DisplayName("End-to-End: Execute 4-node pipeline, persist execution history and logs in database")
    void shouldExecuteFullPipelineAndPersistHistory() throws Exception {
        // Construct valid 4-node pipeline
        ObjectNode graph = objectMapper.createObjectNode();
        ArrayNode nodes = graph.putArray("nodes");

        nodes.addObject().put("id", "trigger-1").put("type", "MANUAL_TRIGGER");

        ObjectNode textNode = nodes.addObject();
        textNode.put("id", "text-1");
        textNode.put("type", "TEXT_INPUT");
        textNode.putObject("config").put("text", "agentflow enterprise automation");

        ObjectNode transformNode = nodes.addObject();
        transformNode.put("id", "transform-1");
        transformNode.put("type", "TEXT_TRANSFORM");
        transformNode.putObject("config").put("operation", "UPPERCASE");

        nodes.addObject().put("id", "output-1").put("type", "OUTPUT");

        ArrayNode edges = graph.putArray("edges");
        edges.addObject().put("source", "trigger-1").put("target", "text-1");
        edges.addObject().put("source", "text-1").put("target", "transform-1");
        edges.addObject().put("source", "transform-1").put("target", "output-1");

        Workflow workflow = new Workflow("Full Pipeline", "Automated test flow", graph, true);
        Workflow savedWorkflow = workflowRepository.save(workflow);

        // Execute workflow via POST /api/v1/workflows/{id}/execute
        mockMvc.perform(post("/api/v1/workflows/" + savedWorkflow.getId() + "/execute")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.status").value("COMPLETED"))
            .andExpect(jsonPath("$.data.outputData.result").value("AGENTFLOW ENTERPRISE AUTOMATION"))
            .andExpect(jsonPath("$.data.nodeLogs", hasSize(4)));

        // Verify execution persistence in database
        assertThat(executionRepository.findByWorkflowId(savedWorkflow.getId())).hasSize(1);
        assertThat(nodeLogRepository.findAll()).hasSize(4);

        // Query execution history via GET /api/v1/workflows/{id}/executions
        mockMvc.perform(get("/api/v1/workflows/" + savedWorkflow.getId() + "/executions"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data", hasSize(1)))
            .andExpect(jsonPath("$.data[0].status").value("COMPLETED"))
            .andExpect(jsonPath("$.data[0].outputData.result").value("AGENTFLOW ENTERPRISE AUTOMATION"));
    }

    @Test
    @DisplayName("Reject execution when workflow definition has a cycle")
    void shouldRejectExecutionForCyclicWorkflow() throws Exception {
        ObjectNode graph = objectMapper.createObjectNode();
        ArrayNode nodes = graph.putArray("nodes");
        nodes.addObject().put("id", "trig").put("type", "MANUAL_TRIGGER");
        nodes.addObject().put("id", "loop-a").put("type", "TEXT_TRANSFORM");
        nodes.addObject().put("id", "loop-b").put("type", "TEXT_TRANSFORM");
        nodes.addObject().put("id", "out").put("type", "OUTPUT");

        ArrayNode edges = graph.putArray("edges");
        edges.addObject().put("source", "trig").put("target", "loop-a");
        edges.addObject().put("source", "loop-a").put("target", "loop-b");
        edges.addObject().put("source", "loop-b").put("target", "loop-a"); // Cycle!

        Workflow workflow = new Workflow("Cyclic Workflow", "Desc", graph, true);
        Workflow savedWorkflow = workflowRepository.save(workflow);

        mockMvc.perform(post("/api/v1/workflows/" + savedWorkflow.getId() + "/execute")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.message", containsString("cycle")));
    }

    @Test
    @DisplayName("Reject execution on inactive workflow")
    void shouldRejectExecutionOnInactiveWorkflow() throws Exception {
        ObjectNode graph = objectMapper.createObjectNode();
        ArrayNode nodes = graph.putArray("nodes");
        nodes.addObject().put("id", "trig").put("type", "MANUAL_TRIGGER");
        nodes.addObject().put("id", "out").put("type", "OUTPUT");
        ArrayNode edges = graph.putArray("edges");
        edges.addObject().put("source", "trig").put("target", "out");

        Workflow workflow = new Workflow("Inactive Flow", "Desc", graph, false);
        Workflow savedWorkflow = workflowRepository.save(workflow);

        mockMvc.perform(post("/api/v1/workflows/" + savedWorkflow.getId() + "/execute")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.message", containsString("Cannot execute inactive workflow")));
    }
}
