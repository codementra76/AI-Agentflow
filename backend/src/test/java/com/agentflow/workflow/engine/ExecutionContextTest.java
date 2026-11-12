package com.agentflow.workflow.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ExecutionContextTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private VariableResolver variableResolver;

    @BeforeEach
    void setUp() {
        variableResolver = new VariableResolver(mapper);
    }

    @Nested
    @DisplayName("1. Input References Integration")
    class InputReferences {

        @Test
        @DisplayName("Resolve flat input references via execution context")
        void shouldResolveFlatInputReference() {
            ObjectNode input = mapper.createObjectNode().put("topic", "Spring Boot Architecture");
            ExecutionContext context = new ExecutionContext(1L, 10L, input, mapper, variableResolver);

            String resolved = context.resolve("Explain the following topic: {{input.topic}}");
            assertThat(resolved).isEqualTo("Explain the following topic: Spring Boot Architecture");
        }

        @Test
        @DisplayName("Resolve nested JSON input fields via execution context")
        void shouldResolveNestedInputField() {
            ObjectNode input = mapper.createObjectNode();
            ObjectNode candidate = input.putObject("candidate");
            candidate.put("name", "Jordan Lee");
            ArrayNode skills = candidate.putArray("skills");
            skills.add("Java").add("Spring Boot");

            ExecutionContext context = new ExecutionContext(1L, 10L, input, mapper, variableResolver);

            String template = "Candidate {{input.candidate.name}} has primary skill {{input.candidate.skills[0]}}.";
            assertThat(context.resolve(template))
                    .isEqualTo("Candidate Jordan Lee has primary skill Java.");
        }

        @Test
        @DisplayName("Resolve raw primitive input text")
        void shouldResolveRawPrimitiveInput() {
            ExecutionContext context = new ExecutionContext(
                    1L, 10L, mapper.valueToTree("Polymorphism in Java"), mapper, variableResolver
            );

            assertThat(context.resolve("Subject: {{input}}"))
                    .isEqualTo("Subject: Polymorphism in Java");
        }
    }

    @Nested
    @DisplayName("2. Previously Executed Node Outputs")
    class NodeOutputReferences {

        @Test
        @DisplayName("Resolve output from previously executed node")
        void shouldResolveExecutedNodeOutput() {
            ExecutionContext context = new ExecutionContext(1L, 10L, null, mapper, variableResolver);

            ObjectNode textOutput = mapper.createObjectNode();
            textOutput.put("text", "Clean Code Principles");
            textOutput.put("nodeId", "text-input-1");
            context.setNodeOutput("text-input-1", textOutput);

            String resolved = context.resolve("Input text was: {{nodes.text-input-1.text}}");
            assertThat(resolved).isEqualTo("Input text was: Clean Code Principles");
        }

        @Test
        @DisplayName("Resolve nested output fields from completed node")
        void shouldResolveNestedNodeOutputFields() {
            ExecutionContext context = new ExecutionContext(1L, 10L, null, mapper, variableResolver);

            ObjectNode aiOutput = mapper.createObjectNode();
            ObjectNode meta = aiOutput.putObject("meta");
            meta.put("model", "qwen2.5:3b");
            meta.put("tokensUsed", 128);
            aiOutput.put("result", "Explanation generated successfully");
            context.setNodeOutput("ai-1", aiOutput);

            String template = "AI Output: {{nodes.ai-1.result}} (Model: {{nodes.ai-1.meta.model}})";
            assertThat(context.resolve(template))
                    .isEqualTo("AI Output: Explanation generated successfully (Model: qwen2.5:3b)");
        }
    }

    @Nested
    @DisplayName("3. Temporal Execution Ordering (Future/Unexecuted Node Isolation)")
    class TemporalExecutionOrdering {

        @Test
        @DisplayName("Future or unexecuted node outputs must not be accessible and remain unreplaced")
        void shouldNotExposeFutureNodeOutputsBeforeExecution() {
            ExecutionContext context = new ExecutionContext(1L, 10L, null, mapper, variableResolver);

            // Step 1 executes
            ObjectNode step1Out = mapper.createObjectNode().put("result", "Step 1 Completed");
            context.setNodeOutput("step-1", step1Out);

            // Template references both Step 1 (executed) and Step 2 (not yet executed)
            String template = "Current: {{nodes.step-1.result}}, Next: {{nodes.step-2.result}}";

            // Step 1 must resolve, but future Step 2 must remain untouched
            String partiallyResolved = context.resolve(template);
            assertThat(partiallyResolved).isEqualTo("Current: Step 1 Completed, Next: {{nodes.step-2.result}}");

            // Now Step 2 executes and registers its output
            ObjectNode step2Out = mapper.createObjectNode().put("result", "Step 2 Completed");
            context.setNodeOutput("step-2", step2Out);

            // Both step 1 and step 2 now resolve, while unexecuted step 3 remains unreplaced
            String templateWithStep3 = "S1: {{nodes.step-1.result}}, S2: {{nodes.step-2.result}}, S3: {{nodes.step-3.result}}";
            String fullyResolved = context.resolve(templateWithStep3);
            assertThat(fullyResolved).isEqualTo("S1: Step 1 Completed, S2: Step 2 Completed, S3: {{nodes.step-3.result}}");
        }
    }

    @Nested
    @DisplayName("4. Workflow Variables Integration")
    class WorkflowVariables {

        @Test
        @DisplayName("Resolve workflow variables initialized via constructor")
        void shouldResolveWorkflowVariablesFromConstructor() {
            ObjectNode vars = mapper.createObjectNode();
            vars.put("jobTitle", "Senior Software Engineer");
            vars.put("maxRetries", 3);

            ExecutionContext context = new ExecutionContext(1L, 10L, null, mapper, variableResolver, vars);

            String template = "Position: {{variables.jobTitle}} (Retries allowed: {{variables.maxRetries}})";
            assertThat(context.resolve(template))
                    .isEqualTo("Position: Senior Software Engineer (Retries allowed: 3)");
        }

        @Test
        @DisplayName("Resolve workflow variables updated dynamically at runtime")
        void shouldResolveDynamicallyUpdatedWorkflowVariable() {
            ExecutionContext context = new ExecutionContext(1L, 10L, null, mapper, variableResolver);

            // Add variables via setter
            context.setWorkflowVariable("threshold", "85%");
            context.setWorkflowVariable("enabled", mapper.valueToTree(true));

            String template = "Threshold: {{variables.threshold}}, Enabled: {{variables.enabled}}";
            assertThat(context.resolve(template)).isEqualTo("Threshold: 85%, Enabled: true");
        }

        @Test
        @DisplayName("Resolve entire workflow variables replacement")
        void shouldResolveReplacedWorkflowVariables() {
            ExecutionContext context = new ExecutionContext(1L, 10L, null, mapper, variableResolver);

            ObjectNode newVars = mapper.createObjectNode();
            newVars.putObject("config").put("timeoutSeconds", 60);
            context.setWorkflowVariables(newVars);

            assertThat(context.resolve("Timeout: {{variables.config.timeoutSeconds}}s"))
                    .isEqualTo("Timeout: 60s");
        }
    }

    @Nested
    @DisplayName("5. Missing References & Null Safety")
    class MissingAndNullReferences {

        @Test
        @DisplayName("Leave missing references untouched across input, nodes, and variables")
        void shouldLeaveMissingReferencesUntouched() {
            ObjectNode input = mapper.createObjectNode().put("existing", "yes");
            ExecutionContext context = new ExecutionContext(1L, 10L, input, mapper, variableResolver);

            String template = "{{input.existing}} | {{input.missing}} | {{nodes.ghost.result}} | {{variables.absent}}";
            String result = context.resolve(template);
            assertThat(result).isEqualTo("yes | {{input.missing}} | {{nodes.ghost.result}} | {{variables.absent}}");
        }

        @Test
        @DisplayName("Resolve explicit JSON null values to empty string")
        void shouldResolveExplicitNullValuesToEmptyString() {
            ObjectNode input = mapper.createObjectNode();
            input.putNull("optionalNote");

            ObjectNode vars = mapper.createObjectNode();
            vars.putNull("defaultTag");

            ExecutionContext context = new ExecutionContext(1L, 10L, input, mapper, variableResolver, vars);

            ObjectNode nodeOut = mapper.createObjectNode();
            nodeOut.putNull("errorMessage");
            context.setNodeOutput("ai-1", nodeOut);

            String template = "Note: '{{input.optionalNote}}', Err: '{{nodes.ai-1.errorMessage}}', Tag: '{{variables.defaultTag}}'";
            assertThat(context.resolve(template)).isEqualTo("Note: '', Err: '', Tag: ''");
        }

        @Test
        @DisplayName("Handle null and empty template strings safely")
        void shouldHandleNullAndEmptyTemplates() {
            ExecutionContext context = new ExecutionContext(1L, 10L, null, mapper, variableResolver);

            assertThat(context.resolve(null)).isNull();
            assertThat(context.resolve("")).isEmpty();
            assertThat(context.resolve("Plain text")).isEqualTo("Plain text");
        }
    }

    @Nested
    @DisplayName("6. Combined Multi-Source Reference Resolution")
    class CombinedReferences {

        @Test
        @DisplayName("Simulate real multi-step workflow pipeline variable resolution")
        void shouldResolveMultiStepPipelineVariables() {
            // 1. Initial workflow input
            ObjectNode input = mapper.createObjectNode();
            input.put("applicantName", "Taylor Smith");
            input.putArray("skills").add("Java 21").add("Spring Boot");

            // 2. Workflow global variables
            ObjectNode vars = mapper.createObjectNode();
            vars.put("targetRole", "Senior Java Cloud Engineer");

            ExecutionContext context = new ExecutionContext(1L, 100L, input, mapper, variableResolver, vars);

            // Step 1 executes (manual trigger / text input)
            ObjectNode step1 = mapper.createObjectNode().put("text", "Taylor Smith profile summary");
            context.setNodeOutput("input-1", step1);

            // Step 2 executes (AI processor)
            ObjectNode aiStep = mapper.createObjectNode().put("result", "Candidate demonstrates deep Java knowledge.");
            context.setNodeOutput("ai-1", aiStep);

            // Downstream node (e.g. notification / output) resolves template referencing all three sources:
            String promptTemplate = """
                    Evaluation Report:
                    - Candidate: {{input.applicantName}}
                    - Target Position: {{variables.targetRole}}
                    - Primary Competency: {{input.skills[0]}}
                    - Assessment: {{nodes.ai-1.result}}
                    """;

            String expectedReport = """
                    Evaluation Report:
                    - Candidate: Taylor Smith
                    - Target Position: Senior Java Cloud Engineer
                    - Primary Competency: Java 21
                    - Assessment: Candidate demonstrates deep Java knowledge.
                    """;

            assertThat(context.resolve(promptTemplate)).isEqualTo(expectedReport);
        }
    }

    @Nested
    @DisplayName("7. Preservation of Existing ExecutionContext APIs")
    class ExistingApiPreservation {

        @Test
        @DisplayName("Preserve existing getIncomingData single-source behavior")
        void shouldPreserveSingleSourceIncomingData() {
            ExecutionContext context = new ExecutionContext(42L, 999L, null, mapper, variableResolver);

            ObjectNode out = mapper.createObjectNode().put("value", "Hello");
            context.setNodeOutput("src-1", out);

            assertThat(context.getWorkflowId()).isEqualTo(42L);
            assertThat(context.getExecutionId()).isEqualTo(999L);
            assertThat(context.getIncomingData("target-node", List.of("src-1"))).isEqualTo(out);
        }

        @Test
        @DisplayName("Preserve existing getIncomingData multi-source aggregation behavior")
        void shouldPreserveMultiSourceIncomingData() {
            ExecutionContext context = new ExecutionContext(1L, 1L, null, mapper, variableResolver);

            context.setNodeOutput("src-A", mapper.createObjectNode().put("msg", "A"));
            context.setNodeOutput("src-B", mapper.createObjectNode().put("msg", "B"));

            com.fasterxml.jackson.databind.JsonNode incoming = context.getIncomingData("target", List.of("src-A", "src-B"));
            assertThat(incoming.isObject()).isTrue();
            assertThat(incoming.get("src-A").get("msg").asText()).isEqualTo("A");
            assertThat(incoming.get("src-B").get("msg").asText()).isEqualTo("B");
        }

        @Test
        @DisplayName("Preserve getFinalOutput and setFinalOutput behavior")
        void shouldPreserveFinalOutputBehavior() {
            ExecutionContext context = new ExecutionContext(1L, 1L, null, mapper, variableResolver);

            ObjectNode finalRes = mapper.createObjectNode().put("status", "SUCCESS");
            context.setFinalOutput(finalRes);

            assertThat(context.getFinalOutput()).isEqualTo(finalRes);
        }
    }
}
