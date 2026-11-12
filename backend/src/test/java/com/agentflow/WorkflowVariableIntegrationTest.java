package com.agentflow;

import com.agentflow.dto.ai.AiChatResponse;
import com.agentflow.repository.NodeExecutionLogRepository;
import com.agentflow.repository.WorkflowExecutionRepository;
import com.agentflow.repository.WorkflowRepository;
import com.agentflow.service.OllamaService;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class WorkflowVariableIntegrationTest {

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

    private ObjectNode createWorkflowPayloadWithVariables(String name, ObjectNode variables) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("name", name);
        payload.put("description", "Workflow testing variables persistence and execution");
        payload.put("active", true);

        ObjectNode definition = payload.putObject("workflowDefinition");
        if (variables != null) {
            definition.set("variables", variables);
        }

        ArrayNode nodes = definition.putArray("nodes");
        nodes.addObject().put("id", "trigger-1").put("type", "MANUAL_TRIGGER").put("name", "Manual Trigger");

        ObjectNode textNode = nodes.addObject();
        textNode.put("id", "input-1");
        textNode.put("type", "TEXT_INPUT");
        textNode.put("name", "Candidate Application");
        textNode.putObject("config").put("text", "Applicant: {{variables.candidateName}} applying for {{variables.targetRole}} at {{variables.company}}");

        ObjectNode aiNode = nodes.addObject();
        aiNode.put("id", "ai-1");
        aiNode.put("type", "AI_TEXT_PROCESSOR");
        aiNode.put("name", "Screen Candidate");
        aiNode.putObject("config")
            .put("prompt", "Screen applicant for {{variables.targetRole}}: {{nodes.input-1.text}}")
            .put("model", "qwen2.5:3b");

        nodes.addObject().put("id", "output-1").put("type", "OUTPUT").put("name", "Screening Result");

        ArrayNode edges = definition.putArray("edges");
        edges.addObject().put("source", "trigger-1").put("target", "input-1");
        edges.addObject().put("source", "input-1").put("target", "ai-1");
        edges.addObject().put("source", "ai-1").put("target", "output-1");

        return payload;
    }

    @Test
    @DisplayName("Persist, restore, and update workflow variables via REST APIs without schema changes")
    void shouldPersistRestoreAndUpdateWorkflowVariables() throws Exception {
        ObjectNode variables = objectMapper.createObjectNode();
        variables.put("candidateName", "Alice Smith");
        variables.put("targetRole", "Senior Java Architect");
        variables.put("company", "Acme Corporation");

        ObjectNode createPayload = createWorkflowPayloadWithVariables("Candidate Screening Flow", variables);

        // 1. POST /api/v1/workflows: Verify variables persisted on creation
        String createResponse = mockMvc.perform(post("/api/v1/workflows")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createPayload.toString()))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.workflowDefinition.variables.candidateName").value("Alice Smith"))
            .andExpect(jsonPath("$.data.workflowDefinition.variables.targetRole").value("Senior Java Architect"))
            .andExpect(jsonPath("$.data.workflowDefinition.variables.company").value("Acme Corporation"))
            .andReturn().getResponse().getContentAsString();

        Long workflowId = objectMapper.readTree(createResponse).get("data").get("id").asLong();

        // 2. GET /api/v1/workflows/{id}: Verify variables restored when reopening workflow
        mockMvc.perform(get("/api/v1/workflows/" + workflowId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.workflowDefinition.variables.candidateName").value("Alice Smith"))
            .andExpect(jsonPath("$.data.workflowDefinition.variables.targetRole").value("Senior Java Architect"))
            .andExpect(jsonPath("$.data.workflowDefinition.variables.company").value("Acme Corporation"));

        // 3. PUT /api/v1/workflows/{id}: Verify variables updated
        ObjectNode updatedVariables = objectMapper.createObjectNode();
        updatedVariables.put("candidateName", "Bob Jones");
        updatedVariables.put("targetRole", "Staff Cloud Engineer");
        updatedVariables.put("company", "Globex");

        ObjectNode updatePayload = createWorkflowPayloadWithVariables("Candidate Screening Flow (Updated)", updatedVariables);

        mockMvc.perform(put("/api/v1/workflows/" + workflowId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(updatePayload.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.workflowDefinition.variables.candidateName").value("Bob Jones"))
            .andExpect(jsonPath("$.data.workflowDefinition.variables.targetRole").value("Staff Cloud Engineer"))
            .andExpect(jsonPath("$.data.workflowDefinition.variables.company").value("Globex"));

        // Verify database persistence after update
        mockMvc.perform(get("/api/v1/workflows/" + workflowId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.workflowDefinition.variables.candidateName").value("Bob Jones"))
            .andExpect(jsonPath("$.data.workflowDefinition.variables.targetRole").value("Staff Cloud Engineer"))
            .andExpect(jsonPath("$.data.workflowDefinition.variables.company").value("Globex"));
    }

    @Test
    @DisplayName("End-to-End: Execute workflow with persisted variables in TEXT_INPUT and AI_TEXT_PROCESSOR")
    void shouldExecuteWorkflowWithPersistedVariables() throws Exception {
        ObjectNode variables = objectMapper.createObjectNode();
        variables.put("candidateName", "Alice Smith");
        variables.put("targetRole", "Senior Java Architect");
        variables.put("company", "Acme Corporation");

        ObjectNode createPayload = createWorkflowPayloadWithVariables("Variable Execution Flow", variables);

        String createResponse = mockMvc.perform(post("/api/v1/workflows")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createPayload.toString()))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();

        Long workflowId = objectMapper.readTree(createResponse).get("data").get("id").asLong();

        String expectedEvaluation = "Candidate Alice Smith is highly qualified for the Senior Java Architect position.";

        // Mock Ollama to expect interpolated prompt
        when(ollamaService.generateChat(
            argThat(prompt -> prompt != null &&
                prompt.contains("Screen applicant for Senior Java Architect:") &&
                prompt.contains("Applicant: Alice Smith applying for Senior Java Architect at Acme Corporation")),
            eq("qwen2.5:3b")
        )).thenReturn(new AiChatResponse(expectedEvaluation, "qwen2.5:3b"));

        // Execute workflow via POST /api/v1/workflows/{id}/execute
        mockMvc.perform(post("/api/v1/workflows/" + workflowId + "/execute")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.status").value("COMPLETED"))
            .andExpect(jsonPath("$.data.outputData.result").value(expectedEvaluation))
            .andExpect(jsonPath("$.data.nodeLogs", hasSize(4)))
            .andExpect(jsonPath("$.data.nodeLogs[1].nodeType").value("TEXT_INPUT"))
            .andExpect(jsonPath("$.data.nodeLogs[1].outputData.text")
                .value("Applicant: Alice Smith applying for Senior Java Architect at Acme Corporation"))
            .andExpect(jsonPath("$.data.nodeLogs[2].nodeType").value("AI_TEXT_PROCESSOR"))
            .andExpect(jsonPath("$.data.nodeLogs[2].outputData.result").value(expectedEvaluation));

        verify(ollamaService).generateChat(
            argThat(p -> p.contains("Senior Java Architect") && p.contains("Alice Smith") && p.contains("Acme Corporation")),
            eq("qwen2.5:3b")
        );
    }

    @Test
    @DisplayName("Verify existing workflows without variables remain 100% compatible")
    void shouldPreserveExistingWorkflowsWithoutVariables() throws Exception {
        // Create workflow without 'variables' field
        ObjectNode createPayload = createWorkflowPayloadWithVariables("No Variables Flow", null);

        String createResponse = mockMvc.perform(post("/api/v1/workflows")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createPayload.toString()))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();

        Long workflowId = objectMapper.readTree(createResponse).get("data").get("id").asLong();

        when(ollamaService.generateChat(any(), any()))
            .thenReturn(new AiChatResponse("Default execution response", "qwen2.5:3b"));

        // Execute and verify clean execution with unmapped placeholders left intact
        mockMvc.perform(post("/api/v1/workflows/" + workflowId + "/execute")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.status").value("COMPLETED"));
    }
}
