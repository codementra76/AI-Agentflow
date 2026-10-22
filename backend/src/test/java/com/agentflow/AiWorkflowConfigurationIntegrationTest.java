package com.agentflow;

import com.agentflow.dto.ai.AiChatResponse;
import com.agentflow.entity.WorkflowExecution;
import com.agentflow.exception.AiServiceUnavailableException;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class AiWorkflowConfigurationIntegrationTest {

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

    private ObjectNode createAiWorkflowPayload(String name, String prompt, String model) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("name", name);
        payload.put("description", "Workflow testing AI configuration and execution");
        payload.put("active", true);

        ObjectNode definition = payload.putObject("workflowDefinition");
        ArrayNode nodes = definition.putArray("nodes");

        nodes.addObject().put("id", "trigger-1").put("type", "MANUAL_TRIGGER").put("name", "Manual Trigger");

        ObjectNode textNode = nodes.addObject();
        textNode.put("id", "input-1");
        textNode.put("type", "TEXT_INPUT");
        textNode.put("name", "Java Concept Input");
        textNode.putObject("config").put("text", "Java polymorphism");

        ObjectNode aiNode = nodes.addObject();
        aiNode.put("id", "ai-1");
        aiNode.put("type", "AI_TEXT_PROCESSOR");
        aiNode.put("name", "Explain Java Concepts");
        ObjectNode aiConfig = aiNode.putObject("config");
        if (prompt != null) {
            aiConfig.put("prompt", prompt);
        }
        if (model != null) {
            aiConfig.put("model", model);
        }

        nodes.addObject().put("id", "output-1").put("type", "OUTPUT").put("name", "Explanation Output");

        ArrayNode edges = definition.putArray("edges");
        edges.addObject().put("source", "trigger-1").put("target", "input-1");
        edges.addObject().put("source", "input-1").put("target", "ai-1");
        edges.addObject().put("source", "ai-1").put("target", "output-1");

        return payload;
    }

    @Test
    @DisplayName("Verify AI node configuration persistence and restoration via REST API")
    void shouldPersistAndRestoreAiWorkflowConfiguration() throws Exception {
        ObjectNode createPayload = createAiWorkflowPayload(
            "Java Concept Explainer",
            "Explain the supplied Java concept in simple terms. Include a short example when appropriate.",
            "qwen2.5:3b"
        );

        // 1. Create workflow via POST /api/v1/workflows
        String createResponse = mockMvc.perform(post("/api/v1/workflows")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createPayload.toString()))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.id").isNumber())
            .andExpect(jsonPath("$.data.name").value("Java Concept Explainer"))
            .andReturn().getResponse().getContentAsString();

        Long workflowId = objectMapper.readTree(createResponse).get("data").get("id").asLong();

        // 2. Fetch workflow via GET /api/v1/workflows/{id}
        mockMvc.perform(get("/api/v1/workflows/" + workflowId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.id").value(workflowId))
            .andExpect(jsonPath("$.data.workflowDefinition.nodes", hasSize(4)))
            .andExpect(jsonPath("$.data.workflowDefinition.nodes[2].id").value("ai-1"))
            .andExpect(jsonPath("$.data.workflowDefinition.nodes[2].type").value("AI_TEXT_PROCESSOR"))
            .andExpect(jsonPath("$.data.workflowDefinition.nodes[2].name").value("Explain Java Concepts"))
            .andExpect(jsonPath("$.data.workflowDefinition.nodes[2].config.prompt").value(
                "Explain the supplied Java concept in simple terms. Include a short example when appropriate."
            ))
            .andExpect(jsonPath("$.data.workflowDefinition.nodes[2].config.model").value("qwen2.5:3b"))
            .andExpect(jsonPath("$.data.workflowDefinition.nodes[1].config.text").value("Java polymorphism"));

        // 3. Update workflow via PUT /api/v1/workflows/{id}
        ObjectNode updatePayload = createAiWorkflowPayload(
            "Java Concept Explainer (Updated)",
            "Summarize the Java concept in 10 words.",
            "qwen2.5:3b"
        );

        mockMvc.perform(put("/api/v1/workflows/" + workflowId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(updatePayload.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.name").value("Java Concept Explainer (Updated)"))
            .andExpect(jsonPath("$.data.workflowDefinition.nodes[2].config.prompt").value(
                "Summarize the Java concept in 10 words."
            ));
    }

    @Test
    @DisplayName("Verify execution output visibility in results and history for AI workflow")
    void shouldExecuteAiWorkflowAndExposeOutputInResultsAndHistory() throws Exception {
        ObjectNode createPayload = createAiWorkflowPayload(
            "Java Concept Explainer",
            "Explain polymorphism simply.",
            "qwen2.5:3b"
        );

        String createResponse = mockMvc.perform(post("/api/v1/workflows")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createPayload.toString()))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();

        Long workflowId = objectMapper.readTree(createResponse).get("data").get("id").asLong();

        when(ollamaService.generateChat(any(), any()))
            .thenReturn(new AiChatResponse("Polymorphism allows objects of different classes to respond to the same method.", "qwen2.5:3b"));

        // Execute workflow via POST /api/v1/workflows/{id}/execute
        mockMvc.perform(post("/api/v1/workflows/" + workflowId + "/execute")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.status").value("COMPLETED"))
            .andExpect(jsonPath("$.data.outputData.result").value(
                "Polymorphism allows objects of different classes to respond to the same method."
            ))
            .andExpect(jsonPath("$.data.nodeLogs", hasSize(4)))
            .andExpect(jsonPath("$.data.nodeLogs[2].nodeType").value("AI_TEXT_PROCESSOR"))
            .andExpect(jsonPath("$.data.nodeLogs[2].status").value("COMPLETED"))
            .andExpect(jsonPath("$.data.nodeLogs[2].outputData.result").value(
                "Polymorphism allows objects of different classes to respond to the same method."
            ));

        // Verify history via GET /api/v1/workflows/{id}/executions
        mockMvc.perform(get("/api/v1/workflows/" + workflowId + "/executions"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data", hasSize(1)))
            .andExpect(jsonPath("$.data[0].status").value("COMPLETED"))
            .andExpect(jsonPath("$.data[0].outputData.result").value(
                "Polymorphism allows objects of different classes to respond to the same method."
            ));
    }

    @Test
    @DisplayName("Verify clean failure feedback without stack traces when Ollama is unavailable")
    void shouldProvideCleanFailureFeedbackWhenOllamaFails() throws Exception {
        ObjectNode createPayload = createAiWorkflowPayload(
            "Java Concept Explainer",
            "Explain polymorphism simply.",
            "qwen2.5:3b"
        );

        String createResponse = mockMvc.perform(post("/api/v1/workflows")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createPayload.toString()))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();

        Long workflowId = objectMapper.readTree(createResponse).get("data").get("id").asLong();

        when(ollamaService.generateChat(any(), any()))
            .thenThrow(new AiServiceUnavailableException("Cannot connect to local Ollama service at http://localhost:11434"));

        // Execute workflow via POST /api/v1/workflows/{id}/execute
        mockMvc.perform(post("/api/v1/workflows/" + workflowId + "/execute")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.status").value("FAILED"))
            .andExpect(jsonPath("$.data.errorMessage", containsString("Cannot connect to local Ollama service at http://localhost:11434")))
            .andExpect(jsonPath("$.data.errorMessage", not(containsString("at com.agentflow."))))
            .andExpect(jsonPath("$.data.nodeLogs[2].nodeType").value("AI_TEXT_PROCESSOR"))
            .andExpect(jsonPath("$.data.nodeLogs[2].status").value("FAILED"));

        // Verify database state has recorded failed execution and logs
        List<WorkflowExecution> executions = executionRepository.findByWorkflowId(workflowId);
        assertThat(executions).hasSize(1);
        assertThat(executions.get(0).getStatus()).isEqualTo("FAILED");
    }

    @Test
    @DisplayName("Verify retry on failed execution creates a new execution record and preserves original failure")
    void shouldRetryFailedExecutionSuccessfully() throws Exception {
        ObjectNode createPayload = createAiWorkflowPayload(
            "Java Concept Explainer",
            "Explain polymorphism simply.",
            "qwen2.5:3b"
        );

        String createResponse = mockMvc.perform(post("/api/v1/workflows")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createPayload.toString()))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();

        Long workflowId = objectMapper.readTree(createResponse).get("data").get("id").asLong();

        // 1. Initial run fails due to Ollama outage
        when(ollamaService.generateChat(any(), any()))
            .thenThrow(new AiServiceUnavailableException("Connection refused"));

        String failedExecResponse = mockMvc.perform(post("/api/v1/workflows/" + workflowId + "/execute")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("FAILED"))
            .andReturn().getResponse().getContentAsString();

        Long firstExecutionId = objectMapper.readTree(failedExecResponse).get("data").get("id").asLong();

        // 2. Ollama is now back online
        org.mockito.Mockito.reset(ollamaService);
        when(ollamaService.generateChat(any(), any()))
            .thenReturn(new AiChatResponse("Polymorphism explained successfully on retry.", "qwen2.5:3b"));

        // 3. Retry the failed execution via POST /api/v1/executions/{id}/retry
        String retryResponse = mockMvc.perform(post("/api/v1/executions/" + firstExecutionId + "/retry"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("Workflow execution retried successfully"))
            .andExpect(jsonPath("$.data.status").value("COMPLETED"))
            .andExpect(jsonPath("$.data.outputData.result").value("Polymorphism explained successfully on retry."))
            .andExpect(jsonPath("$.data.id").value(not(firstExecutionId)))
            .andReturn().getResponse().getContentAsString();

        Long retriedExecutionId = objectMapper.readTree(retryResponse).get("data").get("id").asLong();
        assertThat(retriedExecutionId).isGreaterThan(firstExecutionId);

        // 4. Verify database records: original execution is still FAILED, retry is COMPLETED
        WorkflowExecution original = executionRepository.findById(firstExecutionId).orElseThrow();
        assertThat(original.getStatus()).isEqualTo("FAILED");

        WorkflowExecution retried = executionRepository.findById(retriedExecutionId).orElseThrow();
        assertThat(retried.getStatus()).isEqualTo("COMPLETED");

        List<WorkflowExecution> allForWorkflow = executionRepository.findByWorkflowId(workflowId);
        assertThat(allForWorkflow).hasSize(2);
    }

    @Test
    @DisplayName("Reject retry on a non-failed execution")
    void shouldRejectRetryOnNonFailedExecution() throws Exception {
        ObjectNode createPayload = createAiWorkflowPayload(
            "Successful Flow",
            "Explain polymorphism.",
            "qwen2.5:3b"
        );

        String createResponse = mockMvc.perform(post("/api/v1/workflows")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createPayload.toString()))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();

        Long workflowId = objectMapper.readTree(createResponse).get("data").get("id").asLong();

        when(ollamaService.generateChat(any(), any()))
            .thenReturn(new AiChatResponse("Success.", "qwen2.5:3b"));

        String execResponse = mockMvc.perform(post("/api/v1/workflows/" + workflowId + "/execute")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        Long execId = objectMapper.readTree(execResponse).get("data").get("id").asLong();

        // Attempting retry on COMPLETED execution
        mockMvc.perform(post("/api/v1/executions/" + execId + "/retry"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.message", containsString("only FAILED executions can be retried")));
    }

    @Test
    @DisplayName("Verify execution history filtering by workflow and status")
    void shouldFilterExecutionsByWorkflowAndStatus() throws Exception {
        ObjectNode createPayload = createAiWorkflowPayload(
            "Filterable Flow",
            "Prompt",
            "qwen2.5:3b"
        );

        String createResponse = mockMvc.perform(post("/api/v1/workflows")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createPayload.toString()))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();

        Long workflowId = objectMapper.readTree(createResponse).get("data").get("id").asLong();

        // 1. One success
        when(ollamaService.generateChat(any(), any())).thenReturn(new AiChatResponse("Done", "qwen2.5:3b"));
        mockMvc.perform(post("/api/v1/workflows/" + workflowId + "/execute").contentType(MediaType.APPLICATION_JSON).content("{}"));

        // 2. One failure
        when(ollamaService.generateChat(any(), any())).thenThrow(new AiServiceUnavailableException("Outage"));
        mockMvc.perform(post("/api/v1/workflows/" + workflowId + "/execute").contentType(MediaType.APPLICATION_JSON).content("{}"));

        // Filter by status=COMPLETED
        mockMvc.perform(get("/api/v1/executions").param("status", "COMPLETED"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data", hasSize(1)))
            .andExpect(jsonPath("$.data[0].status").value("COMPLETED"));

        // Filter by status=FAILED
        mockMvc.perform(get("/api/v1/executions").param("status", "FAILED"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data", hasSize(1)))
            .andExpect(jsonPath("$.data[0].status").value("FAILED"));

        // Filter by workflowId and status=FAILED
        mockMvc.perform(get("/api/v1/executions")
                .param("workflowId", workflowId.toString())
                .param("status", "FAILED"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data", hasSize(1)))
            .andExpect(jsonPath("$.data[0].workflowId").value(workflowId))
            .andExpect(jsonPath("$.data[0].status").value("FAILED"));
    }

    @Test
    @DisplayName("Verify AI workflow execution requires no AI_API_KEY, passes upstream text to Ollama, and delivers answer to OUTPUT node")
    void shouldExecuteAiWorkflowWithoutApiKeyAndDeliverOutputToTerminalNode() throws Exception {
        ObjectNode createPayload = createAiWorkflowPayload(
            "Java Concept Explainer",
            "Explain Java concepts in simple terms with examples.",
            "qwen2.5:3b"
        );

        String createResponse = mockMvc.perform(post("/api/v1/workflows")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createPayload.toString()))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();

        Long workflowId = objectMapper.readTree(createResponse).get("data").get("id").asLong();

        String expectedExplanation = "Polymorphism is the ability of an object to take on many forms in Java.";
        when(ollamaService.generateChat(
            org.mockito.ArgumentMatchers.argThat(p -> p != null &&
                p.contains("Explain Java concepts in simple terms with examples.") &&
                p.contains("Java polymorphism")),
            org.mockito.ArgumentMatchers.eq("qwen2.5:3b")
        )).thenReturn(new AiChatResponse(expectedExplanation, "qwen2.5:3b"));

        // Execute workflow via POST /api/v1/workflows/{id}/execute
        mockMvc.perform(post("/api/v1/workflows/" + workflowId + "/execute")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.status").value("COMPLETED"))
            .andExpect(jsonPath("$.data.errorMessage").doesNotExist())
            .andExpect(jsonPath("$.data.outputData.result").value(expectedExplanation))
            .andExpect(jsonPath("$.data.nodeLogs", hasSize(4)))
            .andExpect(jsonPath("$.data.nodeLogs[2].nodeType").value("AI_TEXT_PROCESSOR"))
            .andExpect(jsonPath("$.data.nodeLogs[2].status").value("COMPLETED"))
            .andExpect(jsonPath("$.data.nodeLogs[2].outputData.result").value(expectedExplanation))
            .andExpect(jsonPath("$.data.nodeLogs[3].nodeType").value("OUTPUT"))
            .andExpect(jsonPath("$.data.nodeLogs[3].status").value("COMPLETED"))
            .andExpect(jsonPath("$.data.nodeLogs[3].outputData.result").value(expectedExplanation));
    }
}
