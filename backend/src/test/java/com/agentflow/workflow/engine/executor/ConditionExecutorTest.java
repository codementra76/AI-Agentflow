package com.agentflow.workflow.engine.executor;

import com.agentflow.exception.WorkflowExecutionException;
import com.agentflow.workflow.engine.ExecutionContext;
import com.agentflow.workflow.engine.NodeType;
import com.agentflow.workflow.engine.VariableResolver;
import com.agentflow.workflow.engine.WorkflowNode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConditionExecutorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private ConditionExecutor executor;
    private ExecutionContext context;

    @BeforeEach
    void setUp() {
        executor = new ConditionExecutor(objectMapper);
        context = new ExecutionContext(
                1L,
                100L,
                objectMapper.createObjectNode(),
                objectMapper,
                new VariableResolver(objectMapper)
        );
    }

    private WorkflowNode createConditionNode(String id, JsonNode config) {
        return new WorkflowNode(id, NodeType.CONDITION, "Condition Node", config);
    }

    private ObjectNode createConfig(String left, String op, String right) {
        ObjectNode config = objectMapper.createObjectNode();
        if (left != null) {
            config.put("leftOperand", left);
        }
        if (op != null) {
            config.put("operator", op);
        }
        if (right != null) {
            config.put("rightOperand", right);
        }
        return config;
    }

    @Nested
    @DisplayName("Operator EQUALS")
    class EqualsTests {

        @Test
        @DisplayName("Numerically equal integer and decimal values")
        void shouldEqualNumerically() {
            ObjectNode config = objectMapper.createObjectNode();
            config.put("leftOperand", "10");
            config.put("operator", "EQUALS");
            config.put("rightOperand", "10.0");
            WorkflowNode node = createConditionNode("cond-eq-1", config);

            JsonNode output = executor.execute(node, List.of(), context);

            assertThat(output.get("result").asBoolean()).isTrue();
            assertThat(output.get("branch").asText()).isEqualTo("TRUE");
            assertThat(output.get("operator").asText()).isEqualTo("EQUALS");
            assertThat(output.get("leftType").asText()).isEqualTo("NUMBER");
            assertThat(output.get("rightType").asText()).isEqualTo("NUMBER");
        }

        @Test
        @DisplayName("Numerically non-equal numbers")
        void shouldNotEqualDifferentNumbers() {
            ObjectNode config = createConfig("10", "EQUALS", "20");
            WorkflowNode node = createConditionNode("cond-eq-2", config);

            JsonNode output = executor.execute(node, List.of(), context);

            assertThat(output.get("result").asBoolean()).isFalse();
            assertThat(output.get("branch").asText()).isEqualTo("FALSE");
        }

        @Test
        @DisplayName("Case-insensitive boolean equality")
        void shouldEqualBooleansCaseInsensitively() {
            ObjectNode config = createConfig("true", "==", "TRUE");
            WorkflowNode node = createConditionNode("cond-eq-3", config);

            JsonNode output = executor.execute(node, List.of(), context);

            assertThat(output.get("result").asBoolean()).isTrue();
            assertThat(output.get("branch").asText()).isEqualTo("TRUE");
            assertThat(output.get("leftType").asText()).isEqualTo("BOOLEAN");
        }

        @Test
        @DisplayName("Different booleans")
        void shouldNotEqualDifferentBooleans() {
            ObjectNode config = createConfig("true", "EQUALS", "false");
            WorkflowNode node = createConditionNode("cond-eq-4", config);

            JsonNode output = executor.execute(node, List.of(), context);

            assertThat(output.get("result").asBoolean()).isFalse();
            assertThat(output.get("branch").asText()).isEqualTo("FALSE");
        }

        @Test
        @DisplayName("Case-insensitive string equality")
        void shouldEqualStringsCaseInsensitively() {
            ObjectNode config = createConfig("AgentFlow", "EQUALS", "agentflow");
            WorkflowNode node = createConditionNode("cond-eq-5", config);

            JsonNode output = executor.execute(node, List.of(), context);

            assertThat(output.get("result").asBoolean()).isTrue();
            assertThat(output.get("branch").asText()).isEqualTo("TRUE");
        }

        @Test
        @DisplayName("Different strings")
        void shouldNotEqualDifferentStrings() {
            ObjectNode config = createConfig("AgentFlow", "EQUALS", "Other");
            WorkflowNode node = createConditionNode("cond-eq-6", config);

            JsonNode output = executor.execute(node, List.of(), context);

            assertThat(output.get("result").asBoolean()).isFalse();
            assertThat(output.get("branch").asText()).isEqualTo("FALSE");
        }

        @Test
        @DisplayName("Both null operands equal")
        void shouldEqualWhenBothNull() {
            ObjectNode config = objectMapper.createObjectNode();
            config.putNull("leftOperand");
            config.put("operator", "EQUALS");
            config.putNull("rightOperand");
            WorkflowNode node = createConditionNode("cond-eq-7", config);

            JsonNode output = executor.execute(node, List.of(), context);

            assertThat(output.get("result").asBoolean()).isTrue();
            assertThat(output.get("branch").asText()).isEqualTo("TRUE");
            assertThat(output.get("leftType").asText()).isEqualTo("NULL");
        }

        @Test
        @DisplayName("One null and one non-null operand do not equal")
        void shouldNotEqualWhenOneNull() {
            ObjectNode config = objectMapper.createObjectNode();
            config.putNull("leftOperand");
            config.put("operator", "EQUALS");
            config.put("rightOperand", "hello");
            WorkflowNode node = createConditionNode("cond-eq-8", config);

            JsonNode output = executor.execute(node, List.of(), context);

            assertThat(output.get("result").asBoolean()).isFalse();
            assertThat(output.get("branch").asText()).isEqualTo("FALSE");
        }
    }

    @Nested
    @DisplayName("Operator NOT_EQUALS")
    class NotEqualsTests {

        @Test
        @DisplayName("Different numbers are not equal")
        void shouldNotEqualDifferentNumbers() {
            ObjectNode config = createConfig("10", "NOT_EQUALS", "20");
            WorkflowNode node = createConditionNode("cond-neq-1", config);

            JsonNode output = executor.execute(node, List.of(), context);

            assertThat(output.get("result").asBoolean()).isTrue();
            assertThat(output.get("branch").asText()).isEqualTo("TRUE");
        }

        @Test
        @DisplayName("Equal numbers return false for NOT_EQUALS")
        void shouldReturnFalseForEqualNumbers() {
            ObjectNode config = createConfig("10", "!=", "10.00");
            WorkflowNode node = createConditionNode("cond-neq-2", config);

            JsonNode output = executor.execute(node, List.of(), context);

            assertThat(output.get("result").asBoolean()).isFalse();
            assertThat(output.get("branch").asText()).isEqualTo("FALSE");
        }

        @Test
        @DisplayName("Case-insensitive matching strings return false for NOT_EQUALS")
        void shouldReturnFalseForCaseInsensitiveMatch() {
            ObjectNode config = createConfig("agentflow", "NOT_EQUALS", "AGENTFLOW");
            WorkflowNode node = createConditionNode("cond-neq-3", config);

            JsonNode output = executor.execute(node, List.of(), context);

            assertThat(output.get("result").asBoolean()).isFalse();
            assertThat(output.get("branch").asText()).isEqualTo("FALSE");
        }
    }

    @Nested
    @DisplayName("Operator GREATER_THAN")
    class GreaterThanTests {

        @Test
        @DisplayName("Valid numeric comparison where left > right")
        void shouldEvaluateGreaterThanTrue() {
            ObjectNode config = createConfig("25.5", "GREATER_THAN", "10.2");
            WorkflowNode node = createConditionNode("cond-gt-1", config);

            JsonNode output = executor.execute(node, List.of(), context);

            assertThat(output.get("result").asBoolean()).isTrue();
            assertThat(output.get("branch").asText()).isEqualTo("TRUE");
        }

        @Test
        @DisplayName("Valid numeric comparison where left <= right")
        void shouldEvaluateGreaterThanFalse() {
            ObjectNode config = createConfig("10", ">", "25");
            WorkflowNode node = createConditionNode("cond-gt-2", config);

            JsonNode output = executor.execute(node, List.of(), context);

            assertThat(output.get("result").asBoolean()).isFalse();
            assertThat(output.get("branch").asText()).isEqualTo("FALSE");
        }

        @Test
        @DisplayName("Equal numbers return false for GREATER_THAN")
        void shouldReturnFalseForEqualNumbers() {
            ObjectNode config = createConfig("15", "GREATER_THAN", "15");
            WorkflowNode node = createConditionNode("cond-gt-3", config);

            JsonNode output = executor.execute(node, List.of(), context);

            assertThat(output.get("result").asBoolean()).isFalse();
            assertThat(output.get("branch").asText()).isEqualTo("FALSE");
        }

        @Test
        @DisplayName("Negative numbers comparison")
        void shouldCompareNegativeNumbers() {
            ObjectNode config = createConfig("-5", "GREATER_THAN", "-10");
            WorkflowNode node = createConditionNode("cond-gt-4", config);

            JsonNode output = executor.execute(node, List.of(), context);

            assertThat(output.get("result").asBoolean()).isTrue();
            assertThat(output.get("branch").asText()).isEqualTo("TRUE");
        }

        @Test
        @DisplayName("Fail when non-numeric left operand is provided")
        void shouldFailOnNonNumericLeftOperand() {
            ObjectNode config = createConfig("non-numeric-text", "GREATER_THAN", "10");
            WorkflowNode node = createConditionNode("cond-gt-err-1", config);

            assertThatThrownBy(() -> executor.execute(node, List.of(), context))
                    .isInstanceOf(WorkflowExecutionException.class)
                    .hasMessageContaining("requires numeric operands");
        }

        @Test
        @DisplayName("Fail when non-numeric right operand is provided")
        void shouldFailOnNonNumericRightOperand() {
            ObjectNode config = createConfig("10", "GREATER_THAN", "abc");
            WorkflowNode node = createConditionNode("cond-gt-err-2", config);

            assertThatThrownBy(() -> executor.execute(node, List.of(), context))
                    .isInstanceOf(WorkflowExecutionException.class)
                    .hasMessageContaining("requires numeric operands");
        }
    }

    @Nested
    @DisplayName("Operator GREATER_THAN_OR_EQUAL")
    class GreaterThanOrEqualTests {

        @Test
        @DisplayName("Equal numbers satisfy GREATER_THAN_OR_EQUAL")
        void shouldEvaluateTrueForEqualNumbers() {
            ObjectNode config = createConfig("20", "GREATER_THAN_OR_EQUAL", "20");
            WorkflowNode node = createConditionNode("cond-gte-1", config);

            JsonNode output = executor.execute(node, List.of(), context);

            assertThat(output.get("result").asBoolean()).isTrue();
            assertThat(output.get("branch").asText()).isEqualTo("TRUE");
        }

        @Test
        @DisplayName("Strictly greater number satisfies GREATER_THAN_OR_EQUAL")
        void shouldEvaluateTrueForStrictlyGreater() {
            ObjectNode config = createConfig("21", ">=", "20");
            WorkflowNode node = createConditionNode("cond-gte-2", config);

            JsonNode output = executor.execute(node, List.of(), context);

            assertThat(output.get("result").asBoolean()).isTrue();
            assertThat(output.get("branch").asText()).isEqualTo("TRUE");
        }

        @Test
        @DisplayName("Lesser number returns false for GREATER_THAN_OR_EQUAL")
        void shouldEvaluateFalseForLesserNumber() {
            ObjectNode config = createConfig("19", "GTE", "20");
            WorkflowNode node = createConditionNode("cond-gte-3", config);

            JsonNode output = executor.execute(node, List.of(), context);

            assertThat(output.get("result").asBoolean()).isFalse();
            assertThat(output.get("branch").asText()).isEqualTo("FALSE");
        }
    }

    @Nested
    @DisplayName("Operator LESS_THAN")
    class LessThanTests {

        @Test
        @DisplayName("Valid numeric comparison where left < right")
        void shouldEvaluateLessThanTrue() {
            ObjectNode config = createConfig("10", "LESS_THAN", "25");
            WorkflowNode node = createConditionNode("cond-lt-1", config);

            JsonNode output = executor.execute(node, List.of(), context);

            assertThat(output.get("result").asBoolean()).isTrue();
            assertThat(output.get("branch").asText()).isEqualTo("TRUE");
        }

        @Test
        @DisplayName("Valid numeric comparison where left >= right")
        void shouldEvaluateLessThanFalse() {
            ObjectNode config = createConfig("25", "<", "10");
            WorkflowNode node = createConditionNode("cond-lt-2", config);

            JsonNode output = executor.execute(node, List.of(), context);

            assertThat(output.get("result").asBoolean()).isFalse();
            assertThat(output.get("branch").asText()).isEqualTo("FALSE");
        }

        @Test
        @DisplayName("Equal numbers return false for LESS_THAN")
        void shouldReturnFalseForEqualNumbers() {
            ObjectNode config = createConfig("10", "LT", "10");
            WorkflowNode node = createConditionNode("cond-lt-3", config);

            JsonNode output = executor.execute(node, List.of(), context);

            assertThat(output.get("result").asBoolean()).isFalse();
            assertThat(output.get("branch").asText()).isEqualTo("FALSE");
        }

        @Test
        @DisplayName("Fail when operand is non-numeric")
        void shouldFailOnNonNumeric() {
            ObjectNode config = createConfig("foo", "LESS_THAN", "10");
            WorkflowNode node = createConditionNode("cond-lt-err", config);

            assertThatThrownBy(() -> executor.execute(node, List.of(), context))
                    .isInstanceOf(WorkflowExecutionException.class)
                    .hasMessageContaining("requires numeric operands");
        }
    }

    @Nested
    @DisplayName("Operator LESS_THAN_OR_EQUAL")
    class LessThanOrEqualTests {

        @Test
        @DisplayName("Equal numbers satisfy LESS_THAN_OR_EQUAL")
        void shouldEvaluateTrueForEqualNumbers() {
            ObjectNode config = createConfig("20", "LESS_THAN_OR_EQUAL", "20");
            WorkflowNode node = createConditionNode("cond-lte-1", config);

            JsonNode output = executor.execute(node, List.of(), context);

            assertThat(output.get("result").asBoolean()).isTrue();
            assertThat(output.get("branch").asText()).isEqualTo("TRUE");
        }

        @Test
        @DisplayName("Strictly lesser number satisfies LESS_THAN_OR_EQUAL")
        void shouldEvaluateTrueForStrictlyLesser() {
            ObjectNode config = createConfig("19", "<=", "20");
            WorkflowNode node = createConditionNode("cond-lte-2", config);

            JsonNode output = executor.execute(node, List.of(), context);

            assertThat(output.get("result").asBoolean()).isTrue();
            assertThat(output.get("branch").asText()).isEqualTo("TRUE");
        }

        @Test
        @DisplayName("Greater number returns false for LESS_THAN_OR_EQUAL")
        void shouldEvaluateFalseForGreaterNumber() {
            ObjectNode config = createConfig("21", "LTE", "20");
            WorkflowNode node = createConditionNode("cond-lte-3", config);

            JsonNode output = executor.execute(node, List.of(), context);

            assertThat(output.get("result").asBoolean()).isFalse();
            assertThat(output.get("branch").asText()).isEqualTo("FALSE");
        }
    }

    @Nested
    @DisplayName("Operator CONTAINS and NOT_CONTAINS")
    class ContainsTests {

        @Test
        @DisplayName("Case-insensitive substring match in string")
        void shouldMatchSubstringCaseInsensitively() {
            ObjectNode config = createConfig("Welcome to AgentFlow System", "CONTAINS", "agentflow");
            WorkflowNode node = createConditionNode("cond-ct-1", config);

            JsonNode output = executor.execute(node, List.of(), context);

            assertThat(output.get("result").asBoolean()).isTrue();
            assertThat(output.get("branch").asText()).isEqualTo("TRUE");
        }

        @Test
        @DisplayName("Substring not found returns false")
        void shouldReturnFalseWhenSubstringNotFound() {
            ObjectNode config = createConfig("Welcome to AgentFlow", "CONTAINS", "Kubernetes");
            WorkflowNode node = createConditionNode("cond-ct-2", config);

            JsonNode output = executor.execute(node, List.of(), context);

            assertThat(output.get("result").asBoolean()).isFalse();
            assertThat(output.get("branch").asText()).isEqualTo("FALSE");
        }

        @Test
        @DisplayName("NOT_CONTAINS returns true when substring is absent")
        void shouldReturnTrueWhenNotContainsAbsent() {
            ObjectNode config = createConfig("Welcome to AgentFlow", "NOT_CONTAINS", "Kubernetes");
            WorkflowNode node = createConditionNode("cond-ct-3", config);

            JsonNode output = executor.execute(node, List.of(), context);

            assertThat(output.get("result").asBoolean()).isTrue();
            assertThat(output.get("branch").asText()).isEqualTo("TRUE");
        }

        @Test
        @DisplayName("NOT_CONTAINS returns false when substring is present")
        void shouldReturnFalseWhenNotContainsPresent() {
            ObjectNode config = createConfig("Welcome to AgentFlow", "NOT_CONTAINS", "agentflow");
            WorkflowNode node = createConditionNode("cond-ct-4", config);

            JsonNode output = executor.execute(node, List.of(), context);

            assertThat(output.get("result").asBoolean()).isFalse();
            assertThat(output.get("branch").asText()).isEqualTo("FALSE");
        }

        @Test
        @DisplayName("Array operand matches case-insensitively")
        void shouldMatchArrayElement() {
            ArrayNode skills = objectMapper.createArrayNode();
            skills.add("Java").add("Spring Boot").add("Python");

            ObjectNode config = objectMapper.createObjectNode();
            config.set("leftOperand", skills);
            config.put("operator", "CONTAINS");
            config.put("rightOperand", "java");
            WorkflowNode node = createConditionNode("cond-ct-5", config);

            JsonNode output = executor.execute(node, List.of(), context);

            assertThat(output.get("result").asBoolean()).isTrue();
            assertThat(output.get("branch").asText()).isEqualTo("TRUE");
            assertThat(output.get("leftType").asText()).isEqualTo("ARRAY");
        }
    }

    @Nested
    @DisplayName("Operator IS_EMPTY and IS_NOT_EMPTY")
    class EmptinessTests {

        @Test
        @DisplayName("Empty string is empty")
        void shouldRecognizeEmptyString() {
            ObjectNode config = objectMapper.createObjectNode();
            config.put("leftOperand", "");
            config.put("operator", "IS_EMPTY");
            WorkflowNode node = createConditionNode("cond-emp-1", config);

            JsonNode output = executor.execute(node, List.of(), context);

            assertThat(output.get("result").asBoolean()).isTrue();
            assertThat(output.get("branch").asText()).isEqualTo("TRUE");
        }

        @Test
        @DisplayName("Whitespace-only string is empty")
        void shouldRecognizeWhitespaceString() {
            ObjectNode config = objectMapper.createObjectNode();
            config.put("leftOperand", "   \t\n   ");
            config.put("operator", "IS_EMPTY");
            WorkflowNode node = createConditionNode("cond-emp-2", config);

            JsonNode output = executor.execute(node, List.of(), context);

            assertThat(output.get("result").asBoolean()).isTrue();
            assertThat(output.get("branch").asText()).isEqualTo("TRUE");
        }

        @Test
        @DisplayName("Null operand is empty")
        void shouldRecognizeNullAsEmpty() {
            ObjectNode config = objectMapper.createObjectNode();
            config.putNull("leftOperand");
            config.put("operator", "IS_EMPTY");
            WorkflowNode node = createConditionNode("cond-emp-3", config);

            JsonNode output = executor.execute(node, List.of(), context);

            assertThat(output.get("result").asBoolean()).isTrue();
            assertThat(output.get("branch").asText()).isEqualTo("TRUE");
        }

        @Test
        @DisplayName("Unresolved or missing variable reference is empty")
        void shouldRecognizeMissingVariableAsEmpty() {
            ObjectNode config = objectMapper.createObjectNode();
            config.put("leftOperand", "{{variables.missing_property}}");
            config.put("operator", "IS_EMPTY");
            WorkflowNode node = createConditionNode("cond-emp-4", config);

            JsonNode output = executor.execute(node, List.of(), context);

            assertThat(output.get("result").asBoolean()).isTrue();
            assertThat(output.get("branch").asText()).isEqualTo("TRUE");
        }

        @Test
        @DisplayName("Empty JSON array is empty")
        void shouldRecognizeEmptyArray() {
            ObjectNode config = objectMapper.createObjectNode();
            config.set("leftOperand", objectMapper.createArrayNode());
            config.put("operator", "IS_EMPTY");
            WorkflowNode node = createConditionNode("cond-emp-5", config);

            JsonNode output = executor.execute(node, List.of(), context);

            assertThat(output.get("result").asBoolean()).isTrue();
            assertThat(output.get("branch").asText()).isEqualTo("TRUE");
        }

        @Test
        @DisplayName("Empty JSON object is empty")
        void shouldRecognizeEmptyObject() {
            ObjectNode config = objectMapper.createObjectNode();
            config.set("leftOperand", objectMapper.createObjectNode());
            config.put("operator", "IS_EMPTY");
            WorkflowNode node = createConditionNode("cond-emp-6", config);

            JsonNode output = executor.execute(node, List.of(), context);

            assertThat(output.get("result").asBoolean()).isTrue();
            assertThat(output.get("branch").asText()).isEqualTo("TRUE");
        }

        @Test
        @DisplayName("Non-empty string is not empty")
        void shouldReturnFalseForNonEmptyString() {
            ObjectNode config = objectMapper.createObjectNode();
            config.put("leftOperand", "hello world");
            config.put("operator", "IS_EMPTY");
            WorkflowNode node = createConditionNode("cond-emp-7", config);

            JsonNode output = executor.execute(node, List.of(), context);

            assertThat(output.get("result").asBoolean()).isFalse();
            assertThat(output.get("branch").asText()).isEqualTo("FALSE");
        }

        @Test
        @DisplayName("Number is not empty")
        void shouldReturnFalseForNumber() {
            ObjectNode config = objectMapper.createObjectNode();
            config.put("leftOperand", 0);
            config.put("operator", "IS_EMPTY");
            WorkflowNode node = createConditionNode("cond-emp-8", config);

            JsonNode output = executor.execute(node, List.of(), context);

            assertThat(output.get("result").asBoolean()).isFalse();
            assertThat(output.get("branch").asText()).isEqualTo("FALSE");
        }

        @Test
        @DisplayName("IS_NOT_EMPTY returns true for valid content")
        void shouldReturnTrueForNonEmptyContent() {
            ObjectNode config = objectMapper.createObjectNode();
            config.put("leftOperand", "Active");
            config.put("operator", "IS_NOT_EMPTY");
            WorkflowNode node = createConditionNode("cond-emp-9", config);

            JsonNode output = executor.execute(node, List.of(), context);

            assertThat(output.get("result").asBoolean()).isTrue();
            assertThat(output.get("branch").asText()).isEqualTo("TRUE");
        }

        @Test
        @DisplayName("IS_NOT_EMPTY returns false for empty string")
        void shouldReturnFalseForEmptyStringUnderIsNotEmpty() {
            ObjectNode config = objectMapper.createObjectNode();
            config.put("leftOperand", "");
            config.put("operator", "IS_NOT_EMPTY");
            WorkflowNode node = createConditionNode("cond-emp-10", config);

            JsonNode output = executor.execute(node, List.of(), context);

            assertThat(output.get("result").asBoolean()).isFalse();
            assertThat(output.get("branch").asText()).isEqualTo("FALSE");
        }
    }

    @Nested
    @DisplayName("Variable Resolution in Operands")
    class VariableInterpolationTests {

        @Test
        @DisplayName("Resolve {{input.score}} in leftOperand")
        void shouldResolveInputReference() {
            ObjectNode input = objectMapper.createObjectNode();
            input.put("score", 95);
            context = new ExecutionContext(1L, 100L, input, objectMapper, new VariableResolver(objectMapper));

            ObjectNode config = createConfig("{{input.score}}", "GREATER_THAN", "90");
            WorkflowNode node = createConditionNode("cond-var-1", config);

            JsonNode output = executor.execute(node, List.of(), context);

            assertThat(output.get("result").asBoolean()).isTrue();
            assertThat(output.get("branch").asText()).isEqualTo("TRUE");
            assertThat(output.get("leftType").asText()).isEqualTo("NUMBER");
        }

        @Test
        @DisplayName("Resolve {{nodes.ai-1.status}} from previous node output")
        void shouldResolvePreviousNodeOutput() {
            ObjectNode aiOutput = objectMapper.createObjectNode();
            aiOutput.put("status", "SUCCESS");
            context.setNodeOutput("ai-1", aiOutput);

            ObjectNode config = createConfig("{{nodes.ai-1.status}}", "EQUALS", "success");
            WorkflowNode node = createConditionNode("cond-var-2", config);

            JsonNode output = executor.execute(node, List.of("ai-1"), context);

            assertThat(output.get("result").asBoolean()).isTrue();
            assertThat(output.get("branch").asText()).isEqualTo("TRUE");
        }

        @Test
        @DisplayName("Resolve {{variables.threshold}} in rightOperand")
        void shouldResolveWorkflowVariableInRightOperand() {
            context.setWorkflowVariable("threshold", "50");

            ObjectNode config = createConfig("75", "GREATER_THAN", "{{variables.threshold}}");
            WorkflowNode node = createConditionNode("cond-var-3", config);

            JsonNode output = executor.execute(node, List.of(), context);

            assertThat(output.get("result").asBoolean()).isTrue();
            assertThat(output.get("branch").asText()).isEqualTo("TRUE");
        }

        @Test
        @DisplayName("Compare two resolved variables against each other")
        void shouldCompareTwoResolvedVariables() {
            context.setWorkflowVariable("minSalary", "60000");

            ObjectNode input = objectMapper.createObjectNode();
            input.put("offeredSalary", 75000);
            context = new ExecutionContext(1L, 100L, input, objectMapper, new VariableResolver(objectMapper), context.getWorkflowVariables());

            ObjectNode config = createConfig("{{input.offeredSalary}}", ">=", "{{variables.minSalary}}");
            WorkflowNode node = createConditionNode("cond-var-4", config);

            JsonNode output = executor.execute(node, List.of(), context);

            assertThat(output.get("result").asBoolean()).isTrue();
            assertThat(output.get("branch").asText()).isEqualTo("TRUE");
        }
    }

    @Nested
    @DisplayName("Validation and Error Handling")
    class ValidationTests {

        @Test
        @DisplayName("Fail when node configuration is null")
        void shouldFailOnNullConfig() {
            WorkflowNode node = new WorkflowNode("cond-err-1", NodeType.CONDITION, "Cond", null);

            assertThatThrownBy(() -> executor.execute(node, List.of(), context))
                    .isInstanceOf(WorkflowExecutionException.class)
                    .hasMessageContaining("requires configuration");
        }

        @Test
        @DisplayName("Fail when operator is missing")
        void shouldFailOnMissingOperator() {
            ObjectNode config = objectMapper.createObjectNode();
            config.put("leftOperand", "hello");
            WorkflowNode node = createConditionNode("cond-err-2", config);

            assertThatThrownBy(() -> executor.execute(node, List.of(), context))
                    .isInstanceOf(WorkflowExecutionException.class)
                    .hasMessageContaining("requires an 'operator'");
        }

        @Test
        @DisplayName("Fail on unsupported operator")
        void shouldFailOnUnsupportedOperator() {
            ObjectNode config = createConfig("10", "INVALID_OPERATOR", "20");
            WorkflowNode node = createConditionNode("cond-err-3", config);

            assertThatThrownBy(() -> executor.execute(node, List.of(), context))
                    .isInstanceOf(WorkflowExecutionException.class)
                    .hasMessageContaining("Unsupported operator");
        }

        @Test
        @DisplayName("Fail when leftOperand is completely missing in config")
        void shouldFailOnMissingLeftOperand() {
            ObjectNode config = objectMapper.createObjectNode();
            config.put("operator", "EQUALS");
            config.put("rightOperand", "10");
            WorkflowNode node = createConditionNode("cond-err-4", config);

            assertThatThrownBy(() -> executor.execute(node, List.of(), context))
                    .isInstanceOf(WorkflowExecutionException.class)
                    .hasMessageContaining("missing required 'leftOperand'");
        }

        @Test
        @DisplayName("Fail when rightOperand is missing for binary operator")
        void shouldFailOnMissingRightOperandForBinaryOperator() {
            ObjectNode config = objectMapper.createObjectNode();
            config.put("leftOperand", "10");
            config.put("operator", "EQUALS");
            WorkflowNode node = createConditionNode("cond-err-5", config);

            assertThatThrownBy(() -> executor.execute(node, List.of(), context))
                    .isInstanceOf(WorkflowExecutionException.class)
                    .hasMessageContaining("missing required 'rightOperand'");
        }

        @Test
        @DisplayName("Unary operator IS_EMPTY does not require rightOperand")
        void shouldSucceedForUnaryOperatorWithoutRightOperand() {
            ObjectNode config = objectMapper.createObjectNode();
            config.put("leftOperand", "some-text");
            config.put("operator", "IS_EMPTY");
            WorkflowNode node = createConditionNode("cond-unary-ok", config);

            JsonNode output = executor.execute(node, List.of(), context);

            assertThat(output.get("result").asBoolean()).isFalse();
            assertThat(output.get("branch").asText()).isEqualTo("FALSE");
            assertThat(output.has("rightType")).isFalse();
        }
    }
}
