package com.agentflow.workflow.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves workflow variable references and structured data paths in string templates.
 *
 * Supported reference patterns:
 * - {{input.<field>}} or {{input}}
 * - {{nodes.<nodeId>.<field>}} or {{nodes.<nodeId>}}
 * - {{variables.<name>}} or {{variables}}
 *
 * Nested JSON properties and array indexes (e.g. {{input.skills[0]}} or {{nodes.ai-1.output.result}})
 * are supported.
 *
 * Unresolved references are left unchanged in the template.
 * Ordinary Java braces and code blocks (e.g. { ... }) are preserved.
 */
@Component
public class VariableResolver {

    private static final Pattern VARIABLE_PATTERN =
            Pattern.compile("\\{\\{\\s*([a-zA-Z0-9_.\\[\\]\\-]+)\\s*\\}\\}");

    private static final Pattern INDEX_PATTERN =
            Pattern.compile("\\[(\\d+)\\]");

    private final ObjectMapper objectMapper;

    public VariableResolver() {
        this(new ObjectMapper());
    }

    @Autowired
    public VariableResolver(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
    }

    /**
     * Resolves variable expressions in the provided template.
     *
     * @param template          the template string containing {{...}} placeholders
     * @param inputData         workflow execution input data (may be null)
     * @param nodeOutputs       map of nodeId to node output JsonNode (may be null)
     * @param workflowVariables global workflow variables JsonNode (may be null)
     * @return the resolved string with valid placeholders substituted, leaving unresolved placeholders intact
     */
    public String resolve(
            String template,
            JsonNode inputData,
            Map<String, JsonNode> nodeOutputs,
            JsonNode workflowVariables) {

        if (template == null || !template.contains("{{")) {
            return template;
        }

        StringBuilder sb = new StringBuilder();
        Matcher matcher = VARIABLE_PATTERN.matcher(template);

        while (matcher.find()) {
            String fullMatch = matcher.group(0);
            String expression = matcher.group(1).trim();

            ResolutionResult result = resolveExpression(expression, inputData, nodeOutputs, workflowVariables);

            if (result.resolved) {
                matcher.appendReplacement(sb, Matcher.quoteReplacement(result.value));
            } else {
                matcher.appendReplacement(sb, Matcher.quoteReplacement(fullMatch));
            }
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * Convenience overload when workflow variables are not supplied.
     */
    public String resolve(
            String template,
            JsonNode inputData,
            Map<String, JsonNode> nodeOutputs) {
        return resolve(template, inputData, nodeOutputs, (JsonNode) null);
    }

    /**
     * Overload supporting workflowVariables provided as a Map<String, ?>.
     */
    public String resolveWithVariableMap(
            String template,
            JsonNode inputData,
            Map<String, JsonNode> nodeOutputs,
            Map<String, ?> workflowVariables) {
        JsonNode variablesNode = workflowVariables != null ? objectMapper.valueToTree(workflowVariables) : null;
        return resolve(template, inputData, nodeOutputs, variablesNode);
    }

    /**
     * Resolves a single variable expression or template to its underlying JsonNode value.
     *
     * If the input is a single {{...}} reference (e.g. {{input.score}} or {{nodes.n1.output}}),
     * it returns the direct JsonNode (NumberNode, BooleanNode, ArrayNode, ObjectNode, etc.),
     * or null if the referenced path is missing/unresolved.
     *
     * If the input is plain text or a template containing mixed text/interpolations, it returns
     * a TextNode with the resolved string result.
     */
    public JsonNode resolveToNode(
            String expressionOrTemplate,
            JsonNode inputData,
            Map<String, JsonNode> nodeOutputs,
            JsonNode workflowVariables) {

        if (expressionOrTemplate == null) {
            return null;
        }

        String trimmed = expressionOrTemplate.trim();
        Matcher matcher = VARIABLE_PATTERN.matcher(trimmed);
        if (matcher.matches()) {
            String expression = matcher.group(1).trim();
            return resolveExpressionToNode(expression, inputData, nodeOutputs, workflowVariables);
        }

        String resolved = resolve(expressionOrTemplate, inputData, nodeOutputs, workflowVariables);
        return resolved != null ? objectMapper.getNodeFactory().textNode(resolved) : null;
    }

    private ResolutionResult resolveExpression(
            String expression,
            JsonNode inputData,
            Map<String, JsonNode> nodeOutputs,
            JsonNode workflowVariables) {
        JsonNode node = resolveExpressionToNode(expression, inputData, nodeOutputs, workflowVariables);
        if (node == null || node.isMissingNode()) {
            return ResolutionResult.unresolved();
        }
        return formatNode(node);
    }

    private JsonNode resolveExpressionToNode(
            String expression,
            JsonNode inputData,
            Map<String, JsonNode> nodeOutputs,
            JsonNode workflowVariables) {

        if (expression.isEmpty()) {
            return null;
        }

        if (expression.equals("input")) {
            return inputData;
        }
        if (expression.startsWith("input.")) {
            String path = expression.substring("input.".length()).trim();
            return resolvePathToNode(inputData, path);
        }

        if (expression.equals("variables")) {
            return workflowVariables;
        }
        if (expression.startsWith("variables.")) {
            String path = expression.substring("variables.".length()).trim();
            return resolvePathToNode(workflowVariables, path);
        }

        if (expression.startsWith("nodes.")) {
            String remainder = expression.substring("nodes.".length()).trim();
            return resolveNodeReferenceToNode(remainder, nodeOutputs);
        }

        return null;
    }

    private ResolutionResult resolveNodeReference(String remainder, Map<String, JsonNode> nodeOutputs) {
        JsonNode node = resolveNodeReferenceToNode(remainder, nodeOutputs);
        if (node == null || node.isMissingNode()) {
            return ResolutionResult.unresolved();
        }
        return formatNode(node);
    }

    private JsonNode resolveNodeReferenceToNode(String remainder, Map<String, JsonNode> nodeOutputs) {
        if (nodeOutputs == null || remainder.isEmpty()) {
            return null;
        }

        String resolvedNodeId = null;
        String fieldPath = null;

        if (nodeOutputs.containsKey(remainder)) {
            resolvedNodeId = remainder;
            fieldPath = null;
        } else {
            // First attempt: direct split on first dot (handles standard nodeIds like "input-1", "ai-1", "node_1")
            int firstDot = remainder.indexOf('.');
            if (firstDot != -1) {
                String candidateId = remainder.substring(0, firstDot);
                if (nodeOutputs.containsKey(candidateId)) {
                    resolvedNodeId = candidateId;
                    fieldPath = remainder.substring(firstDot + 1);
                }
            }

            // Fallback: check if any key in nodeOutputs matches remainder prefix (handles nodeIds with dots)
            if (resolvedNodeId == null) {
                for (String key : nodeOutputs.keySet()) {
                    if (remainder.startsWith(key + ".")) {
                        resolvedNodeId = key;
                        fieldPath = remainder.substring(key.length() + 1);
                        break;
                    }
                }
            }
        }

        if (resolvedNodeId == null) {
            return null;
        }

        JsonNode nodeOutput = nodeOutputs.get(resolvedNodeId);
        if (fieldPath == null || fieldPath.isEmpty()) {
            return nodeOutput;
        }

        return resolvePathToNode(nodeOutput, fieldPath);
    }

    private ResolutionResult resolvePath(JsonNode root, String path) {
        JsonNode node = resolvePathToNode(root, path);
        if (node == null || node.isMissingNode()) {
            return ResolutionResult.unresolved();
        }
        return formatNode(node);
    }

    private JsonNode resolvePathToNode(JsonNode root, String path) {
        if (root == null || root.isMissingNode()) {
            return null;
        }
        if (path == null || path.isEmpty()) {
            return root;
        }

        String[] segments = path.split("\\.");
        JsonNode current = root;

        for (int i = 0; i < segments.length; i++) {
            String segment = segments[i].trim();
            if (segment.isEmpty()) {
                return null;
            }

            if (segment.contains("[")) {
                current = resolveIndexedSegment(current, segment);
                if (current == null) {
                    return null;
                }
            } else {
                if (current.isArray() && segment.matches("\\d+")) {
                    int index = Integer.parseInt(segment);
                    if (index < 0 || index >= current.size()) {
                        return null;
                    }
                    current = current.get(index);
                } else if (current.isObject()) {
                    if (!current.has(segment)) {
                        return null;
                    }
                    current = current.get(segment);
                } else {
                    return null;
                }
            }

            if (current == null) {
                return null;
            }
        }

        return current;
    }

    private JsonNode resolveIndexedSegment(JsonNode current, String segment) {
        int bracketIndex = segment.indexOf('[');
        String propertyName = segment.substring(0, bracketIndex).trim();

        JsonNode node = current;
        if (!propertyName.isEmpty()) {
            if (!node.isObject() || !node.has(propertyName)) {
                return null;
            }
            node = node.get(propertyName);
            if (node == null) {
                return null;
            }
        }

        Matcher matcher = INDEX_PATTERN.matcher(segment.substring(bracketIndex));
        while (matcher.find()) {
            if (!node.isArray()) {
                return null;
            }
            int index = Integer.parseInt(matcher.group(1));
            if (index < 0 || index >= node.size()) {
                return null;
            }
            node = node.get(index);
            if (node == null) {
                return null;
            }
        }

        return node;
    }

    private ResolutionResult formatNode(JsonNode node) {
        if (node == null || node.isMissingNode()) {
            return ResolutionResult.unresolved();
        }
        if (node.isNull()) {
            return ResolutionResult.resolved("");
        }
        if (node.isTextual()) {
            return ResolutionResult.resolved(node.asText());
        }
        if (node.isNumber() || node.isBoolean()) {
            return ResolutionResult.resolved(node.asText());
        }
        return ResolutionResult.resolved(node.toString());
    }

    private static final class ResolutionResult {
        final boolean resolved;
        final String value;

        private ResolutionResult(boolean resolved, String value) {
            this.resolved = resolved;
            this.value = value;
        }

        static ResolutionResult unresolved() {
            return new ResolutionResult(false, null);
        }

        static ResolutionResult resolved(String value) {
            return new ResolutionResult(true, value != null ? value : "");
        }
    }
}
