package com.agentflow.workflow.engine.executor;

import com.agentflow.exception.WorkflowExecutionException;
import com.agentflow.workflow.engine.ConditionOperator;
import com.agentflow.workflow.engine.ExecutionContext;
import com.agentflow.workflow.engine.WorkflowNode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

/**
 * Executes a CONDITION node by evaluating a deterministic comparison between left and right operands.
 *
 * Safe condition evaluation:
 * - Interpolates {{input...}}, {{nodes...}}, and {{variables...}} in string operands.
 * - Compares numbers numerically (e.g. 10 == 10.0).
 * - Compares booleans as booleans (case-insensitive true/false).
 * - Compares strings case-insensitively.
 * - Enforces numeric operands for ordering operators (GT, GTE, LT, LTE) without silent fallback.
 * - Safely tests emptiness for nulls, whitespace strings, empty arrays, and empty objects.
 * - Never executes arbitrary expressions, eval, or code.
 */
@Component
public class ConditionExecutor implements NodeExecutor {

    private static final Logger log = LoggerFactory.getLogger(ConditionExecutor.class);
    private final ObjectMapper objectMapper;

    public ConditionExecutor() {
        this(new ObjectMapper());
    }

    public ConditionExecutor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
    }

    @Override
    public JsonNode execute(WorkflowNode node, List<String> sourceNodeIds, ExecutionContext context) {
        ObjectMapper mapper = (context != null && context.getObjectMapper() != null)
                ? context.getObjectMapper()
                : this.objectMapper;

        JsonNode config = node.config();
        if (config == null || !config.isObject() || config.isEmpty()) {
            throw new WorkflowExecutionException("CONDITION node '" + node.id() + "' requires configuration with 'operator' and operands");
        }

        // 1. Extract and validate operator
        String opStr = extractOperatorString(config);
        if (opStr == null || opStr.trim().isEmpty()) {
            throw new WorkflowExecutionException("CONDITION node '" + node.id() + "' requires an 'operator' in configuration");
        }

        ConditionOperator operator = ConditionOperator.fromString(opStr);
        if (operator == null) {
            throw new WorkflowExecutionException("Unsupported operator '" + opStr + "' in CONDITION node '" + node.id()
                    + "'. Supported operators: " + Arrays.toString(ConditionOperator.values()));
        }

        // 2. Validate leftOperand presence in configuration
        if (!hasLeftOperandConfig(config)) {
            throw new WorkflowExecutionException("CONDITION node '" + node.id() + "' missing required 'leftOperand' in configuration");
        }

        // 3. Validate rightOperand presence for binary operators
        if (!operator.isUnary() && !hasRightOperandConfig(config)) {
            throw new WorkflowExecutionException("CONDITION node '" + node.id() + "' missing required 'rightOperand' for operator " + operator.name());
        }

        // 4. Extract and resolve operands
        JsonNode leftRaw = extractLeftOperandNode(config);
        JsonNode rightRaw = !operator.isUnary() ? extractRightOperandNode(config) : null;

        JsonNode leftEvaluated = evaluateOperand(leftRaw, context);
        JsonNode rightEvaluated = !operator.isUnary() ? evaluateOperand(rightRaw, context) : null;

        // 5. Evaluate condition
        boolean conditionMet = evaluateCondition(node.id(), operator, leftEvaluated, rightEvaluated);

        // 6. Build structured output (avoiding logging sensitive operand values)
        ObjectNode result = mapper.createObjectNode();
        result.put("result", conditionMet);
        result.put("branch", conditionMet ? "TRUE" : "FALSE");
        result.put("operator", operator.name());
        result.put("leftType", describeOperandType(leftEvaluated));
        if (!operator.isUnary()) {
            result.put("rightType", describeOperandType(rightEvaluated));
        }
        result.put("nodeId", node.id());

        log.debug("Evaluated CONDITION node [{}] (operator: {}, result: {}, branch: {})",
                node.id(), operator.name(), conditionMet, conditionMet ? "TRUE" : "FALSE");

        return result;
    }

    private boolean evaluateCondition(
            String nodeId,
            ConditionOperator operator,
            JsonNode left,
            JsonNode right) {

        return switch (operator) {
            case EQUALS -> evaluateEquals(left, right);
            case NOT_EQUALS -> !evaluateEquals(left, right);
            case GREATER_THAN, GREATER_THAN_OR_EQUAL, LESS_THAN, LESS_THAN_OR_EQUAL ->
                    evaluateNumericComparison(nodeId, operator, left, right);
            case CONTAINS -> evaluateContains(left, right);
            case NOT_CONTAINS -> !evaluateContains(left, right);
            case IS_EMPTY -> isOperandEmpty(left);
            case IS_NOT_EMPTY -> !isOperandEmpty(left);
        };
    }

    private boolean evaluateEquals(JsonNode left, JsonNode right) {
        if (isNullOrMissing(left) && isNullOrMissing(right)) {
            return true;
        }
        if (isNullOrMissing(left) || isNullOrMissing(right)) {
            return false;
        }

        // Numeric comparison when both are valid numbers
        BigDecimal leftNum = tryParseNumber(left);
        BigDecimal rightNum = tryParseNumber(right);
        if (leftNum != null && rightNum != null) {
            return leftNum.compareTo(rightNum) == 0;
        }

        // Boolean comparison when both are valid booleans
        Boolean leftBool = tryParseBoolean(left);
        Boolean rightBool = tryParseBoolean(right);
        if (leftBool != null && rightBool != null) {
            return leftBool.equals(rightBool);
        }

        // Case-insensitive string comparison fallback
        String leftStr = asString(left);
        String rightStr = asString(right);
        if (leftStr == null && rightStr == null) {
            return true;
        }
        if (leftStr == null || rightStr == null) {
            return false;
        }
        return leftStr.equalsIgnoreCase(rightStr);
    }

    private boolean evaluateNumericComparison(
            String nodeId,
            ConditionOperator operator,
            JsonNode left,
            JsonNode right) {

        BigDecimal leftNum = tryParseNumber(left);
        BigDecimal rightNum = tryParseNumber(right);

        if (leftNum == null || rightNum == null) {
            throw new WorkflowExecutionException("Operator " + operator.name() + " in CONDITION node '" + nodeId
                    + "' requires numeric operands, but received: left="
                    + describeOperandType(left) + ", right=" + describeOperandType(right));
        }

        int comparison = leftNum.compareTo(rightNum);
        return switch (operator) {
            case GREATER_THAN -> comparison > 0;
            case GREATER_THAN_OR_EQUAL -> comparison >= 0;
            case LESS_THAN -> comparison < 0;
            case LESS_THAN_OR_EQUAL -> comparison <= 0;
            default -> throw new IllegalStateException("Unexpected operator: " + operator);
        };
    }

    private boolean evaluateContains(JsonNode left, JsonNode right) {
        if (isNullOrMissing(left) || isNullOrMissing(right)) {
            return false;
        }

        // If left operand is a JSON array, check element membership or substring
        if (left.isArray()) {
            String rightStr = asString(right);
            if (rightStr == null) {
                return false;
            }
            for (JsonNode element : left) {
                if (evaluateEquals(element, right)) {
                    return true;
                }
                String elemStr = asString(element);
                if (elemStr != null && elemStr.toLowerCase().contains(rightStr.toLowerCase())) {
                    return true;
                }
            }
            return false;
        }

        String leftStr = asString(left);
        String rightStr = asString(right);
        if (leftStr == null || rightStr == null) {
            return false;
        }

        return leftStr.toLowerCase().contains(rightStr.toLowerCase());
    }

    private boolean isOperandEmpty(JsonNode node) {
        if (isNullOrMissing(node)) {
            return true;
        }
        if (node.isTextual()) {
            String text = node.asText().trim();
            if (text.isEmpty()) {
                return true;
            }
            return text.equals("{}") || text.equals("[]");
        }
        if (node.isArray()) {
            return node.isEmpty();
        }
        if (node.isObject()) {
            return node.isEmpty();
        }
        return false;
    }

    private JsonNode evaluateOperand(JsonNode operandNode, ExecutionContext context) {
        if (isNullOrMissing(operandNode)) {
            return null;
        }
        if (operandNode.isTextual()) {
            String text = operandNode.asText();
            if (context != null) {
                return context.resolveToNode(text);
            }
            return operandNode;
        }
        return operandNode;
    }

    private boolean isNullOrMissing(JsonNode node) {
        return node == null || node.isNull() || node.isMissingNode();
    }

    private BigDecimal tryParseNumber(JsonNode node) {
        if (isNullOrMissing(node)) {
            return null;
        }
        if (node.isNumber()) {
            return node.decimalValue();
        }
        if (node.isTextual()) {
            String s = node.asText().trim();
            if (s.isEmpty()) {
                return null;
            }
            try {
                return new BigDecimal(s);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private Boolean tryParseBoolean(JsonNode node) {
        if (isNullOrMissing(node)) {
            return null;
        }
        if (node.isBoolean()) {
            return node.asBoolean();
        }
        if (node.isTextual()) {
            String s = node.asText().trim();
            if (s.equalsIgnoreCase("true")) {
                return Boolean.TRUE;
            }
            if (s.equalsIgnoreCase("false")) {
                return Boolean.FALSE;
            }
        }
        return null;
    }

    private String asString(JsonNode node) {
        if (isNullOrMissing(node)) {
            return null;
        }
        if (node.isTextual()) {
            return node.asText();
        }
        if (node.isContainerNode()) {
            return node.toString();
        }
        return node.asText();
    }

    private String describeOperandType(JsonNode node) {
        if (isNullOrMissing(node)) {
            return "NULL";
        }
        if (node.isNumber() || tryParseNumber(node) != null) {
            return "NUMBER";
        }
        if (node.isBoolean() || tryParseBoolean(node) != null) {
            return "BOOLEAN";
        }
        if (node.isArray()) {
            return "ARRAY";
        }
        if (node.isObject()) {
            return "OBJECT";
        }
        return "STRING";
    }

    private String extractOperatorString(JsonNode config) {
        if (config.hasNonNull("operator")) {
            return config.get("operator").asText();
        }
        if (config.hasNonNull("op")) {
            return config.get("op").asText();
        }
        return null;
    }

    private boolean hasLeftOperandConfig(JsonNode config) {
        return config.has("leftOperand")
                || config.has("left")
                || config.has("field")
                || config.has("variable");
    }

    private JsonNode extractLeftOperandNode(JsonNode config) {
        if (config.has("leftOperand")) {
            return config.get("leftOperand");
        }
        if (config.has("left")) {
            return config.get("left");
        }
        if (config.has("field")) {
            return config.get("field");
        }
        if (config.has("variable")) {
            return config.get("variable");
        }
        return null;
    }

    private boolean hasRightOperandConfig(JsonNode config) {
        return config.has("rightOperand")
                || config.has("right")
                || config.has("value")
                || config.has("target");
    }

    private JsonNode extractRightOperandNode(JsonNode config) {
        if (config.has("rightOperand")) {
            return config.get("rightOperand");
        }
        if (config.has("right")) {
            return config.get("right");
        }
        if (config.has("value")) {
            return config.get("value");
        }
        if (config.has("target")) {
            return config.get("target");
        }
        return null;
    }
}
