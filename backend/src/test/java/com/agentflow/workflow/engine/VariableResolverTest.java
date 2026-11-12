package com.agentflow.workflow.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class VariableResolverTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private VariableResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new VariableResolver(mapper);
    }

    @Nested
    @DisplayName("1. Input Data References")
    class InputReferences {

        @Test
        @DisplayName("Resolve primitive input text")
        void shouldResolvePrimitiveInputText() {
            String template = "Prompt: {{input}}";
            String result = resolver.resolve(template, mapper.valueToTree("Explain polymorphism"), null);
            assertThat(result).isEqualTo("Prompt: Explain polymorphism");
        }

        @Test
        @DisplayName("Resolve flat input field")
        void shouldResolveFlatInputField() {
            ObjectNode input = mapper.createObjectNode();
            input.put("text", "Java Streams API");

            String template = "Analyze: {{input.text}}";
            String result = resolver.resolve(template, input, null);
            assertThat(result).isEqualTo("Analyze: Java Streams API");
        }

        @Test
        @DisplayName("Resolve nested JSON object fields in input")
        void shouldResolveNestedInputField() {
            ObjectNode input = mapper.createObjectNode();
            ObjectNode candidate = input.putObject("candidate");
            candidate.put("name", "Alice Smith");
            candidate.put("experienceYears", 5);

            String template = "Review candidate {{input.candidate.name}} with {{input.candidate.experienceYears}} years experience.";
            String result = resolver.resolve(template, input, null);
            assertThat(result).isEqualTo("Review candidate Alice Smith with 5 years experience.");
        }

        @Test
        @DisplayName("Resolve array index in input using brackets and dots")
        void shouldResolveArrayIndexInInput() {
            ObjectNode input = mapper.createObjectNode();
            ArrayNode skills = input.putArray("skills");
            skills.add("Java").add("Spring Boot").add("MySQL");

            String template1 = "Primary skill: {{input.skills[0]}}, Secondary: {{input.skills[1]}}";
            assertThat(resolver.resolve(template1, input, null))
                    .isEqualTo("Primary skill: Java, Secondary: Spring Boot");

            String template2 = "Tertiary skill: {{input.skills.2}}";
            assertThat(resolver.resolve(template2, input, null))
                    .isEqualTo("Tertiary skill: MySQL");
        }
    }

    @Nested
    @DisplayName("2. Node Output References")
    class NodeOutputReferences {

        @Test
        @DisplayName("Resolve entire node output object as JSON string")
        void shouldResolveEntireNodeOutput() {
            Map<String, com.fasterxml.jackson.databind.JsonNode> outputs = new HashMap<>();
            ObjectNode nodeOut = mapper.createObjectNode();
            nodeOut.put("status", "SUCCESS");
            nodeOut.put("value", 42);
            outputs.put("node-1", nodeOut);

            String template = "Output: {{nodes.node-1}}";
            String result = resolver.resolve(template, null, outputs);
            assertThat(result).isEqualTo("Output: {\"status\":\"SUCCESS\",\"value\":42}");
        }

        @Test
        @DisplayName("Resolve single field from previous node output")
        void shouldResolveSingleFieldFromNodeOutput() {
            Map<String, com.fasterxml.jackson.databind.JsonNode> outputs = new HashMap<>();
            ObjectNode aiOut = mapper.createObjectNode();
            aiOut.put("result", "Polymorphism enables OOP flexibility");
            aiOut.put("model", "qwen2.5:3b");
            outputs.put("ai-1", aiOut);

            String template = "AI explanation: {{nodes.ai-1.result}} [model: {{nodes.ai-1.model}}]";
            String result = resolver.resolve(template, null, outputs);
            assertThat(result).isEqualTo("AI explanation: Polymorphism enables OOP flexibility [model: qwen2.5:3b]");
        }

        @Test
        @DisplayName("Resolve deeply nested fields in node output")
        void shouldResolveDeeplyNestedNodeOutput() {
            Map<String, com.fasterxml.jackson.databind.JsonNode> outputs = new HashMap<>();
            ObjectNode nodeOut = mapper.createObjectNode();
            ObjectNode analysis = nodeOut.putObject("analysis");
            analysis.putObject("scores").put("matchingScore", 95);
            outputs.put("matcher-node", nodeOut);

            String template = "Score: {{nodes.matcher-node.analysis.scores.matchingScore}}%";
            String result = resolver.resolve(template, null, outputs);
            assertThat(result).isEqualTo("Score: 95%");
        }
    }

    @Nested
    @DisplayName("3. Workflow Variables References")
    class WorkflowVariablesReferences {

        @Test
        @DisplayName("Resolve workflow variables from JsonNode")
        void shouldResolveWorkflowVariablesFromJsonNode() {
            ObjectNode vars = mapper.createObjectNode();
            vars.put("jobTitle", "Senior Backend Engineer");
            vars.put("minYears", 4);
            vars.put("isRemote", true);

            String template = "Role: {{variables.jobTitle}}, Min Years: {{variables.minYears}}, Remote: {{variables.isRemote}}";
            String result = resolver.resolve(template, null, null, vars);
            assertThat(result).isEqualTo("Role: Senior Backend Engineer, Min Years: 4, Remote: true");
        }

        @Test
        @DisplayName("Resolve workflow variables from Map<String, ?>")
        void shouldResolveWorkflowVariablesFromMap() {
            Map<String, Object> vars = new HashMap<>();
            vars.put("department", "Engineering");
            vars.put("grade", 7);

            String template = "Dept: {{variables.department}}, Grade: {{variables.grade}}";
            String result = resolver.resolveWithVariableMap(template, null, null, vars);
            assertThat(result).isEqualTo("Dept: Engineering, Grade: 7");
        }

        @Test
        @DisplayName("Resolve nested variable structures")
        void shouldResolveNestedVariableStructures() {
            ObjectNode vars = mapper.createObjectNode();
            ArrayNode reqs = vars.putObject("criteria").putArray("requiredSkills");
            reqs.add("Java").add("Spring");

            String template = "Must have: {{variables.criteria.requiredSkills[0]}}";
            String result = resolver.resolve(template, null, null, vars);
            assertThat(result).isEqualTo("Must have: Java");
        }
    }

    @Nested
    @DisplayName("4. Missing Values & Unresolved References")
    class MissingValues {

        @Test
        @DisplayName("Leave missing input fields unchanged in template")
        void shouldLeaveMissingInputFieldUnchanged() {
            ObjectNode input = mapper.createObjectNode().put("name", "Bob");

            String template = "User: {{input.name}}, Age: {{input.age}}";
            String result = resolver.resolve(template, input, null);
            assertThat(result).isEqualTo("User: Bob, Age: {{input.age}}");
        }

        @Test
        @DisplayName("Leave reference to non-existent node unchanged")
        void shouldLeaveNonExistentNodeUnchanged() {
            Map<String, com.fasterxml.jackson.databind.JsonNode> outputs = new HashMap<>();
            outputs.put("node-1", mapper.createObjectNode().put("text", "val"));

            String template = "Found: {{nodes.node-1.text}}, Missing: {{nodes.ghost-node.result}}";
            String result = resolver.resolve(template, null, outputs);
            assertThat(result).isEqualTo("Found: val, Missing: {{nodes.ghost-node.result}}");
        }

        @Test
        @DisplayName("Leave non-existent workflow variable unchanged")
        void shouldLeaveNonExistentVariableUnchanged() {
            ObjectNode vars = mapper.createObjectNode().put("existing", "here");

            String template = "{{variables.existing}} and {{variables.missing}}";
            String result = resolver.resolve(template, null, null, vars);
            assertThat(result).isEqualTo("here and {{variables.missing}}");
        }

        @Test
        @DisplayName("Leave out-of-bounds array index unchanged")
        void shouldLeaveOutOfBoundsArrayUnchanged() {
            ObjectNode input = mapper.createObjectNode();
            input.putArray("items").add("first");

            String template = "{{input.items[0]}} and {{input.items[5]}}";
            String result = resolver.resolve(template, input, null);
            assertThat(result).isEqualTo("first and {{input.items[5]}}");
        }

        @Test
        @DisplayName("Leave unknown prefixes unchanged")
        void shouldLeaveUnknownPrefixesUnchanged() {
            String template = "Testing {{custom.variable}} and {{unknown.ref}}";
            String result = resolver.resolve(template, null, null);
            assertThat(result).isEqualTo("Testing {{custom.variable}} and {{unknown.ref}}");
        }

        @Test
        @DisplayName("Gracefully handle null inputData, null nodeOutputs, and null workflowVariables")
        void shouldHandleAllNullContexts() {
            String template = "{{input.field}} -> {{nodes.ai-1.text}} -> {{variables.role}}";
            String result = resolver.resolve(template, null, null);
            assertThat(result).isEqualTo("{{input.field}} -> {{nodes.ai-1.text}} -> {{variables.role}}");
        }
    }

    @Nested
    @DisplayName("5. Null Values")
    class NullValues {

        @Test
        @DisplayName("Resolve JSON null field in input to empty string")
        void shouldResolveNullInputFieldToEmptyString() {
            ObjectNode input = mapper.createObjectNode();
            input.put("title", "Project Manager");
            input.putNull("comments");

            String template = "Title: '{{input.title}}', Comments: '{{input.comments}}'";
            String result = resolver.resolve(template, input, null);
            assertThat(result).isEqualTo("Title: 'Project Manager', Comments: ''");
        }

        @Test
        @DisplayName("Resolve JSON null field in node output to empty string")
        void shouldResolveNullNodeOutputFieldToEmptyString() {
            Map<String, com.fasterxml.jackson.databind.JsonNode> outputs = new HashMap<>();
            ObjectNode nodeOut = mapper.createObjectNode();
            nodeOut.putNull("error");
            nodeOut.put("status", "SUCCESS");
            outputs.put("node-1", nodeOut);

            String template = "Status: {{nodes.node-1.status}}, Error: '{{nodes.node-1.error}}'";
            String result = resolver.resolve(template, null, outputs);
            assertThat(result).isEqualTo("Status: SUCCESS, Error: ''");
        }

        @Test
        @DisplayName("Resolve JSON null variable to empty string")
        void shouldResolveNullVariableToEmptyString() {
            ObjectNode vars = mapper.createObjectNode();
            vars.putNull("optionalSummary");

            String template = "Summary: '{{variables.optionalSummary}}'";
            String result = resolver.resolve(template, null, null, vars);
            assertThat(result).isEqualTo("Summary: ''");
        }

        @Test
        @DisplayName("Resolve nested null field to empty string")
        void shouldResolveNestedNullFieldToEmptyString() {
            ObjectNode input = mapper.createObjectNode();
            ObjectNode user = input.putObject("user");
            user.putNull("avatarUrl");

            String template = "Avatar: '{{input.user.avatarUrl}}'";
            String result = resolver.resolve(template, input, null);
            assertThat(result).isEqualTo("Avatar: ''");
        }
    }

    @Nested
    @DisplayName("6. Multiple References")
    class MultipleReferences {

        @Test
        @DisplayName("Resolve multiple distinct references across input, nodes, and variables")
        void shouldResolveMultipleDistinctReferences() {
            ObjectNode input = mapper.createObjectNode().put("candidate", "Jane Doe");

            Map<String, com.fasterxml.jackson.databind.JsonNode> outputs = new HashMap<>();
            outputs.put("ai-1", mapper.createObjectNode().put("result", "Strong candidate for full-stack"));

            ObjectNode vars = mapper.createObjectNode().put("openRole", "Staff Software Engineer");

            String template = "Review {{input.candidate}} for {{variables.openRole}}. Summary: {{nodes.ai-1.result}}.";
            String result = resolver.resolve(template, input, outputs, vars);
            assertThat(result).isEqualTo("Review Jane Doe for Staff Software Engineer. Summary: Strong candidate for full-stack.");
        }

        @Test
        @DisplayName("Resolve repeated references to the same variable")
        void shouldResolveRepeatedReferences() {
            ObjectNode input = mapper.createObjectNode().put("term", "Polymorphism");

            String template = "{{input.term}} means many forms. To apply {{input.term}}, define an interface.";
            String result = resolver.resolve(template, input, null);
            assertThat(result).isEqualTo("Polymorphism means many forms. To apply Polymorphism, define an interface.");
        }

        @Test
        @DisplayName("Resolve consecutive adjacent placeholders")
        void shouldResolveAdjacentPlaceholders() {
            ObjectNode input = mapper.createObjectNode();
            input.put("first", "John");
            input.put("last", "Doe");

            String template = "{{input.first}}{{input.last}}";
            String result = resolver.resolve(template, input, null);
            assertThat(result).isEqualTo("JohnDoe");
        }
    }

    @Nested
    @DisplayName("7. Java Code Preservation & Braces")
    class JavaCodePreservation {

        @Test
        @DisplayName("Preserve Java class declaration and methods with single braces")
        void shouldPreserveJavaClassWithBraces() {
            String javaCode = """
                    package com.example;

                    public class Greeter {
                        private final String message;

                        public Greeter(String message) {
                            this.message = message;
                        }

                        public void greet() {
                            if (message != null) {
                                System.out.println(message);
                            }
                        }
                    }
                    """;

            String result = resolver.resolve(javaCode, null, null);
            assertThat(result).isEqualTo(javaCode);
        }

        @Test
        @DisplayName("Preserve Java code while interpolating embedded variable reference")
        void shouldPreserveJavaCodeWhileInterpolatingVariable() {
            ObjectNode input = mapper.createObjectNode().put("greeting", "Hello from AgentFlow!");

            String template = """
                    public class DynamicPrinter {
                        public static void main(String[] args) {
                            String message = "{{input.greeting}}";
                            System.out.println(message);
                        }
                    }
                    """;

            String expected = """
                    public class DynamicPrinter {
                        public static void main(String[] args) {
                            String message = "Hello from AgentFlow!";
                            System.out.println(message);
                        }
                    }
                    """;

            String result = resolver.resolve(template, input, null);
            assertThat(result).isEqualTo(expected);
        }

        @Test
        @DisplayName("Preserve Java double-brace initialization syntax")
        void shouldPreserveJavaDoubleBraceInitialization() {
            String code = """
                    Map<String, String> config = new HashMap<>() {{
                        put("key1", "val1");
                        put("key2", "val2");
                    }};
                    """;

            String result = resolver.resolve(code, null, null);
            assertThat(result).isEqualTo(code);
        }

        @Test
        @DisplayName("Safely handle regex replacement characters like $ and \\ in resolved values")
        void shouldHandleDollarAndBackslashInValues() {
            ObjectNode input = mapper.createObjectNode();
            input.put("price", "$100.00");
            input.put("path", "C:\\Users\\agentflow\\data");

            String template = "Price is {{input.price}} and saved to {{input.path}}";
            String result = resolver.resolve(template, input, null);
            assertThat(result).isEqualTo("Price is $100.00 and saved to C:\\Users\\agentflow\\data");
        }
    }

    @Nested
    @DisplayName("8. Whitespace, Edge Cases & Null Templates")
    class EdgeCases {

        @Test
        @DisplayName("Handle null and empty templates")
        void shouldHandleNullAndEmptyTemplates() {
            assertThat(resolver.resolve(null, null, null)).isNull();
            assertThat(resolver.resolve("", null, null)).isEmpty();
        }

        @Test
        @DisplayName("Return original template unchanged if no {{ is present")
        void shouldReturnUnchangedWhenNoDoubleBraces() {
            String plain = "A simple prompt with no placeholders at all.";
            assertThat(resolver.resolve(plain, null, null)).isSameAs(plain);
        }

        @Test
        @DisplayName("Trim whitespace inside double braces")
        void shouldTrimWhitespaceInsideBraces() {
            ObjectNode input = mapper.createObjectNode().put("name", "Sam");

            String template = "Hello {{  input.name  }}, welcome!";
            String result = resolver.resolve(template, input, null);
            assertThat(result).isEqualTo("Hello Sam, welcome!");
        }

        @Test
        @DisplayName("Leave empty double braces {{}} unchanged")
        void shouldLeaveEmptyDoubleBracesUnchanged() {
            String template = "Empty: {{}} and spaced: {{   }}";
            String result = resolver.resolve(template, null, null);
            assertThat(result).isEqualTo(template);
        }
    }
}
