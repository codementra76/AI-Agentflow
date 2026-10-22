package com.agentflow.service;

import com.agentflow.dto.WorkflowExecutionRequest;
import com.agentflow.dto.WorkflowExecutionResponse;
import com.agentflow.dto.ai.AiChatResponse;
import com.agentflow.entity.Workflow;
import com.agentflow.repository.WorkflowRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class OllamaLiveIntegrationTest {

    @Autowired
    private OllamaService ollamaService;

    @Autowired
    private WorkflowRepository workflowRepository;

    @Autowired
    private WorkflowExecutionService workflowExecutionService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("Verify real call to local Ollama with qwen2.5:3b when Ollama is running")
    void shouldCallRealOllamaIfRunning() {
        Assumptions.assumeTrue(isOllamaReachable(), "Local Ollama is not reachable on localhost:11434, skipping live test");

        long start = System.currentTimeMillis();
        AiChatResponse response = ollamaService.generateChat("Reply with exactly: 'AgentFlow Ollama Online'");
        long elapsed = System.currentTimeMillis() - start;

        System.out.println("==================================================");
        System.out.println("LIVE OLLAMA TEST RESPONSE:");
        System.out.println("Model: " + response.model());
        System.out.println("Duration: " + elapsed + " ms");
        System.out.println("Generated Content: " + response.response());
        System.out.println("==================================================");

        assertThat(response).isNotNull();
        assertThat(response.response()).isNotBlank();
        assertThat(response.model()).isEqualTo("qwen2.5:3b");
    }

    @Test
    @DisplayName("Verify real call for prompt: Explain Java polymorphism in simple terms.")
    void shouldExplainJavaPolymorphism() {
        Assumptions.assumeTrue(isOllamaReachable(), "Local Ollama is not reachable on localhost:11434, skipping live test");

        long start = System.currentTimeMillis();
        AiChatResponse response = ollamaService.generateChat("Explain Java polymorphism in simple terms.");
        long elapsed = System.currentTimeMillis() - start;

        System.out.println("==================================================");
        System.out.println("PROMPT: Explain Java polymorphism in simple terms.");
        System.out.println("MODEL: " + response.model());
        System.out.println("DURATION: " + elapsed + " ms");
        System.out.println("RESPONSE:\n" + response.response());
        System.out.println("==================================================");

        assertThat(response).isNotNull();
        assertThat(response.response()).containsIgnoringCase("polymorphism");
        assertThat(response.model()).isEqualTo("qwen2.5:3b");
    }

    @Test
    @DisplayName("Verify real end-to-end workflow execution containing AI_TEXT_PROCESSOR with local Ollama")
    void shouldExecuteLiveWorkflowWithAiNode() {
        Assumptions.assumeTrue(isOllamaReachable(), "Local Ollama is not reachable on localhost:11434, skipping live test");

        ObjectNode graph = objectMapper.createObjectNode();
        ArrayNode nodes = graph.putArray("nodes");
        nodes.addObject().put("id", "trigger-1").put("type", "MANUAL_TRIGGER");

        ObjectNode textNode = nodes.addObject();
        textNode.put("id", "input-1");
        textNode.put("type", "TEXT_INPUT");
        textNode.putObject("config").put("text", "AgentFlow is an automated AI workflow platform.");

        ObjectNode aiNode = nodes.addObject();
        aiNode.put("id", "ai-1");
        aiNode.put("type", "AI_TEXT_PROCESSOR");
        aiNode.putObject("config").put("prompt", "Summarize in 5 words or fewer.");

        nodes.addObject().put("id", "output-1").put("type", "OUTPUT");

        ArrayNode edges = graph.putArray("edges");
        edges.addObject().put("source", "trigger-1").put("target", "input-1");
        edges.addObject().put("source", "input-1").put("target", "ai-1");
        edges.addObject().put("source", "ai-1").put("target", "output-1");

        Workflow workflow = new Workflow("Live AI Flow", "Test Flow", graph, true);
        Workflow savedWorkflow = workflowRepository.save(workflow);

        long start = System.currentTimeMillis();
        WorkflowExecutionResponse result = workflowExecutionService.executeWorkflow(
            savedWorkflow.getId(),
            new WorkflowExecutionRequest(null)
        );
        long elapsed = System.currentTimeMillis() - start;

        System.out.println("==================================================");
        System.out.println("LIVE AI WORKFLOW EXECUTION RESULT:");
        System.out.println("Execution ID: " + result.id());
        System.out.println("Workflow ID: " + result.workflowId());
        System.out.println("Status: " + result.status());
        System.out.println("Duration: " + elapsed + " ms");
        System.out.println("Output Result: " + result.outputData().get("result").asText());
        System.out.println("Node Logs count: " + result.nodeLogs().size());
        System.out.println("==================================================");

        assertThat(result.status()).isEqualTo("COMPLETED");
        assertThat(result.outputData().get("result").asText()).isNotBlank();
        assertThat(result.nodeLogs()).hasSize(4);
    }

    private boolean isOllamaReachable() {
        try {
            HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(ollamaService.getBaseUrl() + "/api/tags"))
                .timeout(Duration.ofSeconds(2))
                .GET()
                .build();
            HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
            return response.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }
}
