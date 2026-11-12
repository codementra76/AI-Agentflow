import React, { useState, useRef, useCallback, useMemo, useEffect } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import {
  ReactFlow,
  ReactFlowProvider,
  Controls,
  Background,
  MiniMap,
  applyNodeChanges,
  applyEdgeChanges,
  addEdge,
  useReactFlow,
} from '@xyflow/react';
import CustomWorkflowNode from '../components/CustomWorkflowNode';
import ScheduleEditor from '../components/ScheduleEditor';
import {
  createWorkflow,
  updateWorkflow,
  getWorkflowById,
  executeWorkflow,
  getWorkflowExecutions,
  getAllExecutions,
  retryExecution,
  setWorkflowActive,
  getWorkflowSchedule,
  getErrorMessage,
} from '../services/api';

// Backend supported node types and palette information
const PALETTE_NODES = [
  {
    type: 'MANUAL_TRIGGER',
    name: 'Manual Trigger',
    icon: 'bi-play-circle-fill',
    color: '#f59e0b',
    description: 'Initial pipeline trigger point',
    defaultConfig: {},
  },
  {
    type: 'TEXT_INPUT',
    name: 'Text Input',
    icon: 'bi-input-cursor-text',
    color: '#3b82f6',
    description: 'Provides input text to pipeline',
    defaultConfig: { text: 'Hello AgentFlow' },
  },
  {
    type: 'TEXT_TRANSFORM',
    name: 'Text Transform',
    icon: 'bi-arrow-repeat',
    color: '#8b5cf6',
    description: 'Transforms string (UPPERCASE, etc.)',
    defaultConfig: { operation: 'UPPERCASE', search: '', replace: '' },
  },
  {
    type: 'CONDITION',
    name: 'Condition',
    icon: 'bi-signpost-split-fill',
    color: '#d97706',
    description: 'Branch execution based on condition (True/False)',
    defaultConfig: {
      leftOperand: '{{input.score}}',
      operator: 'GREATER_THAN',
      rightOperand: '75',
    },
  },
  {
    type: 'AI_TEXT_PROCESSOR',
    name: 'AI Text Processor',
    icon: 'bi-robot',
    color: '#06b6d4',
    description: 'Autonomous LLM agent prompt step',
    defaultConfig: {
      prompt: 'Explain the supplied text in simple terms. Include a short Java example when appropriate.',
      model: 'qwen2.5:3b',
    },
  },
  {
    type: 'OUTPUT',
    name: 'Output',
    icon: 'bi-box-arrow-right',
    color: '#10b981',
    description: 'Final pipeline output terminal',
    defaultConfig: {},
  },
];

// 10 Supported Condition Operators matching ConditionExecutor in backend
const CONDITION_OPERATORS = [
  {
    value: 'EQUALS',
    label: 'EQUALS (==)',
    isUnary: false,
    description: 'Compares left and right operands. Matches numbers numerically (e.g. 10 == 10.0), booleans (true == True), or strings case-insensitively.',
  },
  {
    value: 'NOT_EQUALS',
    label: 'NOT EQUALS (!=)',
    isUnary: false,
    description: 'Evaluates to TRUE if left operand is not equal to right operand.',
  },
  {
    value: 'GREATER_THAN',
    label: 'GREATER THAN (>)',
    isUnary: false,
    description: 'Evaluates to TRUE if left number is strictly greater than right number. Both operands must resolve to numbers.',
  },
  {
    value: 'GREATER_THAN_OR_EQUAL',
    label: 'GREATER THAN OR EQUAL (>=)',
    isUnary: false,
    description: 'Evaluates to TRUE if left number is greater than or equal to right number. Both operands must resolve to numbers.',
  },
  {
    value: 'LESS_THAN',
    label: 'LESS THAN (<)',
    isUnary: false,
    description: 'Evaluates to TRUE if left number is strictly less than right number. Both operands must resolve to numbers.',
  },
  {
    value: 'LESS_THAN_OR_EQUAL',
    label: 'LESS THAN OR EQUAL (<=)',
    isUnary: false,
    description: 'Evaluates to TRUE if left number is less than or equal to right number. Both operands must resolve to numbers.',
  },
  {
    value: 'CONTAINS',
    label: 'CONTAINS',
    isUnary: false,
    description: 'Evaluates to TRUE if left text contains right substring (case-insensitive) or left array contains right element.',
  },
  {
    value: 'NOT_CONTAINS',
    label: 'NOT CONTAINS',
    isUnary: false,
    description: 'Evaluates to TRUE if left text or array does not contain right operand.',
  },
  {
    value: 'IS_EMPTY',
    label: 'IS EMPTY (Unary)',
    isUnary: true,
    description: 'Evaluates to TRUE if left operand is null, undefined, blank text, empty array [], or empty object {}. Right operand is not required.',
  },
  {
    value: 'IS_NOT_EMPTY',
    label: 'IS NOT EMPTY (Unary)',
    isUnary: true,
    description: 'Evaluates to TRUE if left operand contains a non-empty value. Right operand is not required.',
  },
];

// Variable name validation rule: starts with letter or underscore, followed by letters, digits, or underscores
const VAR_NAME_REGEX = /^[a-zA-Z_][a-zA-Z0-9_]*$/;

// Example workflow variables for quick insertion
const EXAMPLE_WORKFLOW_VARIABLES = [
  { key: 'candidateName', value: 'Alice Smith', description: 'Applicant name' },
  { key: 'targetRole', value: 'Senior Java Architect', description: 'Target job title' },
  { key: 'company', value: 'Acme Corporation', description: 'Company name' },
];

function getVariableValidationError(key, allVars, currentIndex) {
  const trimmed = (key || '').trim();
  if (!trimmed) {
    return 'Variable name cannot be empty.';
  }
  if (!VAR_NAME_REGEX.test(trimmed)) {
    return 'Must start with a letter/underscore; letters, numbers, and underscores only.';
  }
  const isDuplicate = allVars.some(
    (v, i) => i !== currentIndex && (v.key || '').trim().toLowerCase() === trimmed.toLowerCase()
  );
  if (isDuplicate) {
    return `Duplicate variable name '${trimmed}'. Names must be unique.`;
  }
  return null;
}

// Inner Canvas Component that has access to useReactFlow hook
function WorkflowCanvasInner() {
  const [searchParams] = useSearchParams();
  const workflowIdParam = searchParams.get('workflowId');

  const [workflowName, setWorkflowName] = useState('Untitled Workflow');
  const [workflowDescription, setWorkflowDescription] = useState('');
  const [isActive, setIsActive] = useState(true);
  const [nodes, setNodes] = useState([]);
  const [edges, setEdges] = useState([]);
  const [selectedNodeId, setSelectedNodeId] = useState(null);

  // Workflow ID being edited (if null, builder creates a new workflow)
  const [loadedWorkflowId, setLoadedWorkflowId] = useState(null);
  const [isLoadingWorkflow, setIsLoadingWorkflow] = useState(false);
  const [loadError, setLoadError] = useState(null);

  // Saving state and status notifications
  const [isSaving, setIsSaving] = useState(false);
  const [saveStatus, setSaveStatus] = useState(null); // { type: 'success' | 'danger', message: '', savedId?: number }
  const [notification, setNotification] = useState(null);

  // Real execution state
  const [isExecuting, setIsExecuting] = useState(false);
  const [executionResult, setExecutionResult] = useState(null);
  const [executionError, setExecutionError] = useState(null);
  const [showResultsPanel, setShowResultsPanel] = useState(false);
  const [resultsTab, setResultsTab] = useState('output'); // 'output' | 'trace' | 'history'
  const [executionHistory, setExecutionHistory] = useState([]);
  const [loadingHistory, setLoadingHistory] = useState(false);
  const [historyFilter, setHistoryFilter] = useState('ALL'); // 'ALL' | 'COMPLETED' | 'FAILED'
  const [isRetrying, setIsRetrying] = useState(false);
  const [showExecutePrompt, setShowExecutePrompt] = useState(null); // { type: 'unSavedWorkflow' | 'inactive' | 'unsaved', title: '', message: '' }
  const [lastSavedSnapshot, setLastSavedSnapshot] = useState(null);
  const [executionInputJson, setExecutionInputJson] = useState('{"score": 85}');

  // Workflow Variables state
  const [variables, setVariables] = useState([]); // Array of { id: string, key: string, value: string }
  const [activeSideTab, setActiveSideTab] = useState('node'); // 'node' | 'variables' | 'schedule'
  const [activeSchedule, setActiveSchedule] = useState(null); // WorkflowSchedule object if configured
  const [copiedVarKey, setCopiedVarKey] = useState(null);

  const reactFlowWrapper = useRef(null);
  const { screenToFlowPosition, fitView, getNodes, getEdges } = useReactFlow();

  // Unique ID counter generator
  const idCounterRef = useRef(1);
  const varIdCounterRef = useRef(1);

  // Helper to compute a normalized string snapshot of the workflow graph including variables
  const computeSnapshot = useCallback((name, desc, active, nds, eds, vars) => {
    const serializedNodes = (nds || []).map((n) => ({
      id: String(n.id),
      type: n.data?.type,
      name: (n.data?.name || n.data?.label || n.id).trim(),
      config: n.data?.config || {},
      position: {
        x: Math.round(n.position?.x || 0),
        y: Math.round(n.position?.y || 0),
      },
    }));
    const serializedEdges = (eds || []).map((e) => ({
      source: String(e.source),
      target: String(e.target),
      sourceHandle: e.sourceHandle ? String(e.sourceHandle) : undefined,
      label: e.label ? String(e.label) : undefined,
    }));
    const serializedVars = (vars || []).map((v) => ({
      key: (v.key || '').trim(),
      value: v.value ?? '',
    }));
    return JSON.stringify({
      name: (name || '').trim(),
      description: (desc || '').trim(),
      active: !!active,
      nodes: serializedNodes,
      edges: serializedEdges,
      variables: serializedVars,
    });
  }, []);

  // Detect whether current canvas has unsaved changes compared to last saved snapshot
  const hasUnsavedChanges = useMemo(() => {
    if (!loadedWorkflowId || !lastSavedSnapshot) return false;
    const currentNodes = getNodes && getNodes().length > 0 ? getNodes() : nodes;
    const currentEdges = getEdges ? getEdges() : edges;
    const current = computeSnapshot(workflowName, workflowDescription, isActive, currentNodes, currentEdges, variables);
    return current !== lastSavedSnapshot;
  }, [loadedWorkflowId, lastSavedSnapshot, workflowName, workflowDescription, isActive, nodes, edges, variables, getNodes, getEdges, computeSnapshot]);

  // Handlers for workflow variables
  const handleAddVariable = (key = '', value = '') => {
    const newId = `var-${varIdCounterRef.current++}`;
    setVariables((prev) => [...prev, { id: newId, key, value }]);
    setActiveSideTab('variables');
  };

  const handleUpdateVariable = (id, field, val) => {
    setVariables((prev) =>
      prev.map((v) => (v.id === id ? { ...v, [field]: val } : v))
    );
  };

  const handleRemoveVariable = (id) => {
    setVariables((prev) => prev.filter((v) => v.id !== id));
  };

  const handleAddExampleVariable = (example) => {
    const exists = variables.some(
      (v) => (v.key || '').trim().toLowerCase() === example.key.toLowerCase()
    );
    if (!exists) {
      handleAddVariable(example.key, example.value);
    } else {
      setNotification({
        type: 'warning',
        message: `Variable '${example.key}' already exists in this workflow.`,
      });
    }
  };

  const handleCopyVariableReference = (key) => {
    if (!key) return;
    const ref = `{{variables.${key.trim()}}}`;
    if (navigator?.clipboard?.writeText) {
      navigator.clipboard.writeText(ref);
      setCopiedVarKey(key);
      setTimeout(() => setCopiedVarKey(null), 2000);
    }
  };

  // Load execution history for current workflow from backend with optional status filter
  const loadExecutionHistory = useCallback(async (wfId, filter = historyFilter) => {
    if (!wfId) return;
    setLoadingHistory(true);
    try {
      const res = await getAllExecutions(wfId, filter !== 'ALL' ? filter : null);
      if (res && res.success && Array.isArray(res.data)) {
        setExecutionHistory(res.data);
      }
    } catch {
      try {
        const fallbackRes = await getWorkflowExecutions(wfId);
        if (fallbackRes && fallbackRes.success && Array.isArray(fallbackRes.data)) {
          const list = fallbackRes.data;
          setExecutionHistory(filter !== 'ALL' ? list.filter((e) => e.status === filter) : list);
        }
      } catch {
        // Ignore background load failures
      }
    } finally {
      setLoadingHistory(false);
    }
  }, [historyFilter]);

  // Decorate canvas nodes with step execution statuses from any execution run
  const applyExecutionToCanvas = useCallback((executionData) => {
    if (!executionData) return;
    const logsMap = new Map();
    if (Array.isArray(executionData.nodeLogs)) {
      for (const log of executionData.nodeLogs) {
        logsMap.set(log.nodeId, log.status);
      }
    }

    setNodes((nds) =>
      nds.map((node) => {
        const stepStatus = logsMap.get(node.id);
        if (stepStatus) {
          return {
            ...node,
            data: {
              ...node.data,
              executionStatus: stepStatus,
            },
          };
        }
        return {
          ...node,
          data: {
            ...node.data,
            executionStatus: executionData.status === 'COMPLETED' ? 'COMPLETED' : undefined,
          },
        };
      })
    );
  }, []);

  // Retry a failed execution as a new execution run
  const handleRetryExecution = async (execId) => {
    if (!execId) return;
    setIsRetrying(true);
    try {
      const response = await retryExecution(execId);
      if (response && response.success && response.data) {
        const newExecution = response.data;
        setExecutionResult(newExecution);
        setExecutionError(null);
        applyExecutionToCanvas(newExecution);
        setResultsTab('output');
        await loadExecutionHistory(loadedWorkflowId, historyFilter);
        setNotification({
          type: newExecution.status === 'COMPLETED' ? 'success' : 'warning',
          message: `Execution #${execId} retried as new Run #${newExecution.id} (${newExecution.status}). Original failure record preserved.`,
        });
      } else {
        setNotification({
          type: 'danger',
          message: response?.message || `Failed to retry execution #${execId}.`,
        });
      }
    } catch (err) {
      setNotification({
        type: 'danger',
        message: `Retry failed: ${getErrorMessage(err)}`,
      });
    } finally {
      setIsRetrying(false);
    }
  };

  // Define custom node types
  const nodeTypes = useMemo(() => ({
    workflowNode: CustomWorkflowNode,
  }), []);

  // Find currently selected node
  const selectedNode = useMemo(
    () => nodes.find((n) => n.id === selectedNodeId) || null,
    [nodes, selectedNodeId]
  );

  // Node changes handler (moving, selecting)
  const onNodesChange = useCallback((changes) => {
    setNodes((nds) => applyNodeChanges(changes, nds));
  }, []);

  // Edge changes handler (moving, deleting edges)
  const onEdgesChange = useCallback((changes) => {
    setEdges((eds) => applyEdgeChanges(changes, eds));
  }, []);

  // Connection handler (connecting source to target)
  const onConnect = useCallback((connection) => {
    // Prevent connecting to self
    if (connection.source === connection.target) {
      setNotification({
        type: 'warning',
        message: 'Cannot connect a node to itself.',
      });
      return;
    }

    setEdges((eds) => {
      // Prevent duplicate connections between same source, sourceHandle, and target
      const exists = eds.some(
        (e) =>
          e.source === connection.source &&
          e.target === connection.target &&
          (e.sourceHandle || null) === (connection.sourceHandle || null)
      );
      if (exists) {
        setNotification({
          type: 'info',
          message: 'Connection already exists between these nodes on this handle.',
        });
        return eds;
      }

      const isTrueHandle = connection.sourceHandle === 'true';
      const isFalseHandle = connection.sourceHandle === 'false';

      const edgeStyle = isTrueHandle
        ? { stroke: '#198754', strokeWidth: 2.5 }
        : isFalseHandle
        ? { stroke: '#dc3545', strokeWidth: 2.5 }
        : { stroke: '#4f46e5', strokeWidth: 2 };

      const edgeLabel = isTrueHandle ? 'TRUE' : isFalseHandle ? 'FALSE' : undefined;
      const labelStyle = isTrueHandle
        ? { fill: '#198754', fontWeight: 700, fontSize: 11 }
        : isFalseHandle
        ? { fill: '#dc3545', fontWeight: 700, fontSize: 11 }
        : undefined;
      const labelBgStyle = isTrueHandle
        ? { fill: '#d1e7dd', fillOpacity: 0.95 }
        : isFalseHandle
        ? { fill: '#f8d7da', fillOpacity: 0.95 }
        : undefined;

      const edgeId = connection.sourceHandle
        ? `e-${connection.source}-${connection.sourceHandle}-${connection.target}`
        : `e-${connection.source}-${connection.target}`;

      return addEdge(
        {
          ...connection,
          id: edgeId,
          animated: true,
          label: edgeLabel,
          labelStyle,
          labelBgStyle,
          labelBgPadding: isTrueHandle || isFalseHandle ? [6, 3] : undefined,
          labelBgBorderRadius: isTrueHandle || isFalseHandle ? 4 : undefined,
          style: edgeStyle,
        },
        eds
      );
    });
  }, []);

  useEffect(() => {
    if (!workflowIdParam) {
      setLoadedWorkflowId((prev) => (prev !== null ? null : prev));
      return;
    }

    const id = parseInt(workflowIdParam, 10);
    if (isNaN(id) || id <= 0) {
      setLoadError(`Invalid workflow ID parameter: "${workflowIdParam}"`);
      return;
    }

    let isMounted = true;
    const fetchWorkflow = async () => {
      setIsLoadingWorkflow(true);
      setLoadError(null);
      try {
        const response = await getWorkflowById(id);
        if (!isMounted) return;

        if (response && response.success && response.data) {
          const wf = response.data;
          setLoadedWorkflowId(wf.id);
          setWorkflowName(wf.name || 'Untitled Workflow');
          setWorkflowDescription(wf.description || '');
          setIsActive(wf.active ?? true);

          const definition = wf.workflowDefinition || {};
          const rawNodes = Array.isArray(definition.nodes) ? definition.nodes : [];
          const rawEdges = Array.isArray(definition.edges) ? definition.edges : [];
          const rawVariables =
            definition.variables &&
            typeof definition.variables === 'object' &&
            !Array.isArray(definition.variables)
              ? definition.variables
              : {};

          const mappedVariables = Object.entries(rawVariables).map(([key, val], idx) => ({
            id: `var-${idx + 1}`,
            key,
            value: typeof val === 'object' ? JSON.stringify(val) : String(val ?? ''),
          }));
          varIdCounterRef.current = mappedVariables.length + 1;
          setVariables(mappedVariables);

          // Map backend nodes into React Flow nodes
          const mappedNodes = rawNodes.map((n) => {
            const paletteItem = PALETTE_NODES.find((p) => p.type === n.type);
            return {
              id: n.id,
              type: 'workflowNode',
              position: n.position || { x: 100, y: 100 },
              data: {
                id: n.id,
                type: n.type,
                name: n.name || paletteItem?.name || n.type,
                label: n.name || paletteItem?.name || n.type,
                config: n.config || { ...(paletteItem?.defaultConfig || {}) },
              },
            };
          });

          // Map backend edges into React Flow edges
          const mappedEdges = rawEdges.map((e, index) => {
            const isTrue = e.sourceHandle === 'true';
            const isFalse = e.sourceHandle === 'false';
            return {
              id: e.id || `e-${e.source}-${e.target}-${index}`,
              source: e.source,
              target: e.target,
              sourceHandle: e.sourceHandle || undefined,
              label: e.label || (isTrue ? 'TRUE' : isFalse ? 'FALSE' : undefined),
              labelStyle: isTrue
                ? { fill: '#198754', fontWeight: 700, fontSize: 11 }
                : isFalse
                ? { fill: '#dc3545', fontWeight: 700, fontSize: 11 }
                : undefined,
              labelBgStyle: isTrue
                ? { fill: '#d1e7dd', fillOpacity: 0.95 }
                : isFalse
                ? { fill: '#f8d7da', fillOpacity: 0.95 }
                : undefined,
              labelBgPadding: isTrue || isFalse ? [6, 3] : undefined,
              labelBgBorderRadius: isTrue || isFalse ? 4 : undefined,
              animated: true,
              style: isTrue
                ? { stroke: '#198754', strokeWidth: 2.5 }
                : isFalse
                ? { stroke: '#dc3545', strokeWidth: 2.5 }
                : { stroke: '#4f46e5', strokeWidth: 2 },
            };
          });

          setNodes(mappedNodes);
          setEdges(mappedEdges);

          // Take snapshot of loaded workflow to track unsaved changes
          const loadedSnapshot = computeSnapshot(wf.name, wf.description, wf.active ?? true, mappedNodes, mappedEdges, mappedVariables);
          setLastSavedSnapshot(loadedSnapshot);

          // Load execution history from backend
          loadExecutionHistory(wf.id);

          // Load schedule configuration if configured
          try {
            const schedRes = await getWorkflowSchedule(wf.id);
            if (schedRes && schedRes.success && schedRes.data) {
              setActiveSchedule(schedRes.data);
            } else {
              setActiveSchedule(null);
            }
          } catch {
            setActiveSchedule(null);
          }

          // Update idCounterRef to avoid ID collisions with new nodes
          let maxCounter = 1;
          for (const n of mappedNodes) {
            const match = n.id.match(/-(\d+)$/);
            if (match) {
              const num = parseInt(match[1], 10);
              if (!isNaN(num) && num >= maxCounter) {
                maxCounter = num + 1;
              }
            }
          }
          idCounterRef.current = maxCounter;

          // Fit view after a tick to fit the restored graph
          setTimeout(() => {
            if (fitView) fitView({ padding: 0.2 });
          }, 150);
        } else {
          setLoadError(response?.message || `Workflow #${id} could not be loaded.`);
        }
      } catch (err) {
        if (!isMounted) return;
        setLoadError(`Failed to load workflow #${id}: ${getErrorMessage(err)}`);
      } finally {
        if (isMounted) {
          setIsLoadingWorkflow(false);
        }
      }
    };

    fetchWorkflow();

    return () => {
      isMounted = false;
    };
  }, [workflowIdParam, fitView, computeSnapshot, loadExecutionHistory]);

  // Helper to sequentially connect current canvas nodes from left to right
  const autoConnectPipeline = useCallback(() => {
    const currentNodes = getNodes && getNodes().length > 0 ? getNodes() : nodes;
    if (currentNodes.length < 2) {
      setNotification({
        type: 'warning',
        message: 'Add at least 2 nodes to the canvas to connect them.',
      });
      return;
    }

    // Sort nodes horizontally by position.x
    const sortedNodes = [...currentNodes].sort((a, b) => a.position.x - b.position.x);
    const newEdges = [];

    for (let i = 0; i < sortedNodes.length - 1; i++) {
      const source = sortedNodes[i];
      const target = sortedNodes[i + 1];

      // Disallow OUTPUT as source, MANUAL_TRIGGER as target, and CONDITION as source (requires explicit TRUE/FALSE handles)
      if (source.data?.type === 'OUTPUT' || target.data?.type === 'MANUAL_TRIGGER' || source.data?.type === 'CONDITION') {
        continue;
      }
      if (source.id === target.id) continue;

      newEdges.push({
        id: `e-${source.id}-${target.id}`,
        source: source.id,
        target: target.id,
        animated: true,
        style: { stroke: '#4f46e5', strokeWidth: 2 },
      });
    }

    setEdges(newEdges);
    setNotification({
      type: 'success',
      message: `Sequentially connected ${newEdges.length} step${newEdges.length === 1 ? '' : 's'}.`,
    });
  }, [nodes, getNodes]);

  // Load pre-configured "Java Concept Explainer" example workflow (Phase 4 template)
  const loadJavaConceptExplainerTemplate = useCallback(() => {
    const templateNodes = [
      {
        id: 'trigger-1',
        type: 'workflowNode',
        position: { x: 60, y: 160 },
        data: {
          id: 'trigger-1',
          type: 'MANUAL_TRIGGER',
          name: 'Manual Trigger',
          label: 'Manual Trigger',
          config: {},
        },
      },
      {
        id: 'input-1',
        type: 'workflowNode',
        position: { x: 300, y: 160 },
        data: {
          id: 'input-1',
          type: 'TEXT_INPUT',
          name: 'Java Concept Input',
          label: 'Java Concept Input',
          config: {
            text: 'Java polymorphism',
          },
        },
      },
      {
        id: 'ai-1',
        type: 'workflowNode',
        position: { x: 580, y: 160 },
        data: {
          id: 'ai-1',
          type: 'AI_TEXT_PROCESSOR',
          name: 'Explain Java Concepts',
          label: 'Explain Java Concepts',
          config: {
            prompt: 'Explain the supplied Java concept in simple terms. Include a short example when appropriate.',
            model: 'qwen2.5:3b',
          },
        },
      },
      {
        id: 'output-1',
        type: 'workflowNode',
        position: { x: 880, y: 160 },
        data: {
          id: 'output-1',
          type: 'OUTPUT',
          name: 'Explanation Output',
          label: 'Explanation Output',
          config: {},
        },
      },
    ];

    const templateEdges = [
      {
        id: 'e-trigger-1-input-1',
        source: 'trigger-1',
        target: 'input-1',
        animated: true,
        style: { stroke: '#4f46e5', strokeWidth: 2 },
      },
      {
        id: 'e-input-1-ai-1',
        source: 'input-1',
        target: 'ai-1',
        animated: true,
        style: { stroke: '#4f46e5', strokeWidth: 2 },
      },
      {
        id: 'e-ai-1-output-1',
        source: 'ai-1',
        target: 'output-1',
        animated: true,
        style: { stroke: '#4f46e5', strokeWidth: 2 },
      },
    ];

    setLoadedWorkflowId(null);
    setWorkflowName('Java Concept Explainer');
    setWorkflowDescription('Explain Java concepts in simple terms with examples using local Ollama (qwen2.5:3b).');
    setIsActive(true);
    setNodes(templateNodes);
    setEdges(templateEdges);
    setVariables([]);
    setActiveSchedule(null);
    setSelectedNodeId('ai-1');
    setActiveSideTab('node');
    setExecutionResult(null);
    setExecutionError(null);
    setShowResultsPanel(false);
    idCounterRef.current = 5;

    setNotification({
      type: 'success',
      message: 'Loaded "Java Concept Explainer" template! Click "Save Workflow" to save it to MySQL.',
    });

    setTimeout(() => {
      if (fitView) fitView({ padding: 0.2 });
    }, 100);
  }, [fitView]);

  // Compute incoming upstream nodes for the selected node
  const selectedNodeIncomingNodes = useMemo(() => {
    if (!selectedNodeId) return [];
    const incomingEdgeSources = new Set(
      edges.filter((e) => e.target === selectedNodeId).map((e) => e.source)
    );
    return nodes.filter((n) => incomingEdgeSources.has(n.id));
  }, [selectedNodeId, edges, nodes]);

  // Node selection handler
  const onNodeClick = useCallback((_, node) => {
    setSelectedNodeId(node.id);
    setActiveSideTab('node');
  }, []);

  // Deselect node on clicking canvas background
  const onPaneClick = useCallback(() => {
    setSelectedNodeId(null);
  }, []);

  /**
   * Add a new node to the canvas
   */
  const addNodeToCanvas = useCallback(
    (paletteItem, position = null) => {
      const typeKey = paletteItem.type.toLowerCase().replace('_', '-');
      const uniqueId = `${typeKey}-${idCounterRef.current++}`;

      // Default positioning staggered in view
      const defaultPos = position || {
        x: 100 + (nodes.length % 5) * 60,
        y: 80 + (nodes.length % 5) * 60,
      };

      const newNode = {
        id: uniqueId,
        type: 'workflowNode',
        position: defaultPos,
        data: {
          id: uniqueId,
          type: paletteItem.type,
          name: paletteItem.name,
          label: paletteItem.name,
          config: { ...paletteItem.defaultConfig },
        },
      };

      setNodes((nds) => [...nds, newNode]);
      setSelectedNodeId(uniqueId);
    },
    [nodes]
  );

  /**
   * Drag & Drop Handlers from sidebar
   */
  const onDragStart = (event, nodeType) => {
    event.dataTransfer.setData('application/reactflow', nodeType);
    event.dataTransfer.effectAllowed = 'move';
  };

  const onDragOver = useCallback((event) => {
    event.preventDefault();
    event.dataTransfer.dropEffect = 'move';
  }, []);

  const onDrop = useCallback(
    (event) => {
      event.preventDefault();
      const nodeType = event.dataTransfer.getData('application/reactflow');
      if (!nodeType) return;

      const paletteItem = PALETTE_NODES.find((p) => p.type === nodeType);
      if (!paletteItem) return;

      const position = screenToFlowPosition({
        x: event.clientX,
        y: event.clientY,
      });

      addNodeToCanvas(paletteItem, position);
    },
    [screenToFlowPosition, addNodeToCanvas]
  );

  /**
   * Update properties of the currently selected node
   */
  const updateSelectedNodeData = (updates) => {
    if (!selectedNodeId) return;

    setNodes((nds) =>
      nds.map((node) => {
        if (node.id === selectedNodeId) {
          const updatedData = {
            ...node.data,
            ...updates,
            config: {
              ...node.data.config,
              ...(updates.config || {}),
            },
          };
          if (updates.name) {
            updatedData.label = updates.name;
          }
          return {
            ...node,
            data: updatedData,
          };
        }
        return node;
      })
    );
  };

  /**
   * Delete the currently selected node
   */
  const deleteSelectedNode = () => {
    if (!selectedNodeId) return;

    setNodes((nds) => nds.filter((n) => n.id !== selectedNodeId));
    setEdges((eds) =>
      eds.filter(
        (e) => e.source !== selectedNodeId && e.target !== selectedNodeId
      )
    );
    setSelectedNodeId(null);
  };

  /**
   * Clear entire canvas with confirmation
   */
  const clearCanvas = () => {
    if (nodes.length === 0) return;

    const confirmed = window.confirm(
      'Are you sure you want to clear the entire canvas? All nodes and connections will be removed.'
    );
    if (!confirmed) return;

    setNodes([]);
    setEdges([]);
    setSelectedNodeId(null);
    idCounterRef.current = 1;
  };

  /**
   * Handle real workflow saving to Spring Boot REST backend
   * Supports both POST (create new) and PUT (safe update existing)
   */
  const handleSaveWorkflow = async () => {
    setSaveStatus(null);
    const trimmedName = workflowName.trim();

    // 1. Validate workflow name
    if (!trimmedName) {
      setSaveStatus({
        type: 'danger',
        message: 'Workflow name is required. Please enter a valid name.',
      });
      return;
    }
    if (trimmedName.length < 2) {
      setSaveStatus({
        type: 'danger',
        message: 'Workflow name must be at least 2 characters long.',
      });
      return;
    }
    if (trimmedName.length > 150) {
      setSaveStatus({
        type: 'danger',
        message: 'Workflow name cannot exceed 150 characters.',
      });
      return;
    }

    // 2. Validate nodes exist (reading latest live nodes and edges)
    const currentNodes = getNodes && getNodes().length > 0 ? getNodes() : nodes;
    const currentEdges = getEdges ? getEdges() : edges;

    if (currentNodes.length === 0) {
      setSaveStatus({
        type: 'danger',
        message: 'Cannot save an empty workflow. Add at least one node to the canvas.',
      });
      return;
    }

    // 3. Validate edge endpoints
    const nodeIds = new Set(currentNodes.map((n) => n.id));
    for (const edge of currentEdges) {
      if (!nodeIds.has(edge.source) || !nodeIds.has(edge.target)) {
        setSaveStatus({
          type: 'danger',
          message: `Edge connects to an invalid or missing node ID (${edge.source} -> ${edge.target}).`,
        });
        return;
      }
      if (edge.source === edge.target) {
        setSaveStatus({
          type: 'danger',
          message: `Self-referencing connection detected on node '${edge.source}'.`,
        });
        return;
      }
      const sourceNode = currentNodes.find((n) => n.id === edge.source);
      if (sourceNode?.data?.type === 'CONDITION' && !edge.sourceHandle) {
        setSaveStatus({
          type: 'danger',
          message: `Outgoing connection from Condition node '${sourceNode.data?.name || edge.source}' must originate from either the TRUE or FALSE handle.`,
        });
        return;
      }
    }

    // 3b. Validate workflow variables
    const serializedVariables = {};
    const seenVarKeys = new Set();
    for (let i = 0; i < variables.length; i++) {
      const v = variables[i];
      const trimmedKey = (v.key || '').trim();
      if (!trimmedKey) {
        setSaveStatus({
          type: 'danger',
          message: 'Workflow variable names cannot be empty. Please enter a name or remove the empty variable.',
        });
        setActiveSideTab('variables');
        return;
      }
      if (!VAR_NAME_REGEX.test(trimmedKey)) {
        setSaveStatus({
          type: 'danger',
          message: `Invalid variable name '${trimmedKey}'. Must start with a letter/underscore and contain only letters, numbers, and underscores.`,
        });
        setActiveSideTab('variables');
        return;
      }
      const lowerKey = trimmedKey.toLowerCase();
      if (seenVarKeys.has(lowerKey)) {
        setSaveStatus({
          type: 'danger',
          message: `Duplicate variable name detected: '${trimmedKey}'. Workflow variable names must be unique.`,
        });
        setActiveSideTab('variables');
        return;
      }
      seenVarKeys.add(lowerKey);
      serializedVariables[trimmedKey] = v.value ?? '';
    }

    // 4. Serialize nodes matching backend WorkflowNode structure
    const serializedNodes = currentNodes.map((node) => ({
      id: String(node.id),
      type: node.data.type,
      name: (node.data.name || node.data.label || node.id).trim(),
      config: node.data.config || {},
      position: {
        x: Math.round(node.position.x),
        y: Math.round(node.position.y),
      },
    }));

    // 5. Serialize edges matching backend WorkflowEdge structure: { id, source, target, sourceHandle, label }
    const serializedEdges = currentEdges.map((edge) => {
      const e = {
        id: edge.id || `e-${edge.source}-${edge.target}`,
        source: String(edge.source),
        target: String(edge.target),
      };
      if (edge.sourceHandle) {
        e.sourceHandle = String(edge.sourceHandle);
      }
      if (edge.label) {
        e.label = String(edge.label);
      }
      return e;
    });

    // 6. Construct payload conforming strictly to backend DTO
    const payload = {
      name: trimmedName,
      description: workflowDescription.trim() || null,
      active: isActive,
      workflowDefinition: {
        nodes: serializedNodes,
        edges: serializedEdges,
        variables: serializedVariables,
      },
    };

    setIsSaving(true);
    try {
      let response;
      const isUpdating = loadedWorkflowId !== null && loadedWorkflowId !== undefined;

      if (isUpdating) {
        // Safe update: update ONLY the explicitly loaded workflow ID
        response = await updateWorkflow(loadedWorkflowId, payload);
      } else {
        // Safe create: create new workflow
        response = await createWorkflow(payload);
      }

      if (response && response.success && response.data) {
        const resultId = response.data.id;
        const savedNodesCount = response.data.workflowDefinition?.nodes?.length ?? serializedNodes.length;
        const savedEdgesCount = response.data.workflowDefinition?.edges?.length ?? serializedEdges.length;
        const savedVariablesCount = response.data.workflowDefinition?.variables
          ? Object.keys(response.data.workflowDefinition.variables).length
          : Object.keys(serializedVariables).length;

        // If this was a new workflow, track its ID so further saves update it
        if (!isUpdating) {
          setLoadedWorkflowId(resultId);
        }

        // Update snapshot to mark clean saved state
        const savedSnapshot = computeSnapshot(trimmedName, workflowDescription, isActive, currentNodes, currentEdges, variables);
        setLastSavedSnapshot(savedSnapshot);

        const varMsgPart = savedVariablesCount > 0 ? `, ${savedVariablesCount} variables` : '';
        setSaveStatus({
          type: 'success',
          message: isUpdating
            ? `Changes saved successfully for Workflow #${resultId}! (${savedNodesCount} nodes, ${savedEdgesCount} connections${varMsgPart} persisted)`
            : `Workflow saved successfully! Workflow ID: ${resultId} (${savedNodesCount} nodes, ${savedEdgesCount} connections${varMsgPart} persisted)`,
          savedId: resultId,
        });

        // If a callback was passed (e.g. Save & Execute), call it
        if (typeof callbackOnSuccess === 'function') {
          callbackOnSuccess(resultId);
        }
      } else {
        setSaveStatus({
          type: 'danger',
          message: response?.message || 'Server did not return a successful confirmation.',
        });
      }
    } catch (err) {
      const errorMsg = getErrorMessage(err);
      setSaveStatus({
        type: 'danger',
        message: `Failed to save workflow: ${errorMsg}`,
      });
    } finally {
      setIsSaving(false);
    }
  };

  /**
   * Execute workflow via real backend POST /api/v1/workflows/{id}/execute
   */
  const runExecution = async (workflowIdToExecute) => {
    setIsExecuting(true);
    setExecutionError(null);
    setExecutionResult(null);
    setShowResultsPanel(true);
    setResultsTab('output');

    // Parse runtime input data if provided
    let inputDataPayload = null;
    if (executionInputJson && executionInputJson.trim()) {
      try {
        inputDataPayload = JSON.parse(executionInputJson.trim());
      } catch {
        inputDataPayload = { text: executionInputJson.trim() };
      }
    }

    // Immediately mark canvas nodes as RUNNING for active visual feedback
    setNodes((nds) =>
      nds.map((node) => ({
        ...node,
        data: {
          ...node.data,
          executionStatus: 'RUNNING',
        },
      }))
    );

    try {
      const response = await executeWorkflow(workflowIdToExecute, inputDataPayload);
      if (response && response.success && response.data) {
        const executionData = response.data;
        setExecutionResult(executionData);

        // Decorate nodes on canvas with step execution status (green/red borders)
        applyExecutionToCanvas(executionData);

        // Refresh execution history
        loadExecutionHistory(workflowIdToExecute);
      } else {
        const errMsg = response?.message || 'Execution completed without result data.';
        setExecutionError(errMsg);
        setNodes((nds) =>
          nds.map((node) => ({
            ...node,
            data: {
              ...node.data,
              executionStatus: undefined,
            },
          }))
        );
      }
    } catch (err) {
      const msg = getErrorMessage(err);
      // Clean up technical internal stack traces if any
      const cleanMsg = typeof msg === 'string' ? msg.split('\n')[0] : 'Execution failed';
      setExecutionError(cleanMsg);
      setNodes((nds) =>
        nds.map((node) => ({
          ...node,
          data: {
            ...node.data,
            executionStatus: undefined,
          },
        }))
      );
    } finally {
      setIsExecuting(false);
      loadExecutionHistory(workflowIdToExecute);
    }
  };

  /**
   * Handle user click on the Execute Workflow button
   * Enforces saved status, active status, and warns about unsaved changes
   */
  const handleExecuteClick = () => {
    // 1. If workflow is brand new / not saved in MySQL yet
    if (!loadedWorkflowId) {
      setShowExecutePrompt({
        type: 'unSavedWorkflow',
        title: 'Save Required Before Execution',
        message: 'This workflow has not been saved to MySQL yet. Workflows must be saved before the backend execution engine can run them.',
      });
      return;
    }

    // 2. If workflow is inactive
    if (!isActive) {
      setShowExecutePrompt({
        type: 'inactive',
        title: 'Workflow is Currently Inactive',
        message: 'The backend execution engine requires workflows to be ACTIVE before execution. Would you like to activate this workflow now and execute it?',
      });
      return;
    }

    // 3. If unsaved changes exist
    if (hasUnsavedChanges) {
      setShowExecutePrompt({
        type: 'unsaved',
        title: 'Unsaved Canvas Changes Detected',
        message: 'You have modified the pipeline since your last save. The backend will execute the version currently saved in MySQL. Would you like to save your changes first, or execute the saved version?',
      });
      return;
    }

    // Direct execution
    runExecution(loadedWorkflowId);
  };

  return (
    <div className="d-flex flex-column vh-100 bg-light overflow-hidden">
      {/* Top Header Bar */}
      <header className="navbar navbar-expand navbar-dark bg-dark px-3 py-2 shadow-sm border-bottom border-dark flex-shrink-0">
        <div className="container-fluid d-flex align-items-center justify-content-between flex-wrap gap-2">
          {/* Left: Back Link & Title */}
          <div className="d-flex align-items-center gap-3">
            <Link
              to="/"
              className="btn btn-outline-light btn-sm d-flex align-items-center gap-2"
              title="Return to Dashboard"
            >
              <i className="bi bi-arrow-left"></i>
              <span>Dashboard</span>
            </Link>

            <Link
              to="/jobs"
              className="btn btn-outline-light btn-sm d-flex align-items-center gap-2"
              title="Open Job Search Assistant"
            >
              <i className="bi bi-briefcase-fill text-warning"></i>
              <span className="d-none d-sm-inline">Job Search</span>
            </Link>

            <Link
              to="/chat"
              className="btn btn-outline-info btn-sm d-flex align-items-center gap-2"
              title="Open local AI Assistant"
            >
              <i className="bi bi-robot"></i>
              <span className="d-none d-sm-inline">AI Assistant</span>
            </Link>

            <div className="vr bg-secondary mx-1 d-none d-sm-block"></div>

            <div className="d-flex align-items-center gap-2">
              <span className="navbar-brand d-flex align-items-center gap-2 fs-5 fw-bold mb-0">
                <i className="bi bi-bezier2 text-primary"></i>
                Workflow Builder
              </span>

              {/* Mode indicator badge */}
              {loadedWorkflowId ? (
                <div className="d-flex align-items-center gap-2">
                  <span className="badge bg-warning text-dark border border-warning-subtle d-flex align-items-center gap-1 font-monospace small">
                    <i className="bi bi-pencil-fill"></i>
                    Editing ID #{loadedWorkflowId}
                  </span>
                  <Link
                    to="/builder"
                    className="btn btn-outline-secondary btn-sm py-0 px-2 small text-light"
                    title="Start a new blank workflow"
                    onClick={() => {
                      setLoadedWorkflowId(null);
                      setWorkflowName('Untitled Workflow');
                      setWorkflowDescription('');
                      setNodes([]);
                      setEdges([]);
                      setVariables([]);
                      setActiveSideTab('node');
                      setSaveStatus(null);
                      idCounterRef.current = 1;
                      varIdCounterRef.current = 1;
                    }}
                  >
                    <i className="bi bi-plus-circle me-1"></i>
                    New
                  </Link>
                </div>
              ) : (
                <span className="badge bg-success-subtle text-success border border-success-subtle d-flex align-items-center gap-1 small">
                  <i className="bi bi-plus-circle"></i>
                  New Workflow
                </span>
              )}
            </div>
          </div>

          {/* Center: Workflow Name, Description & Active Switch */}
          <div className="d-flex align-items-center gap-2 flex-grow-1 justify-content-center" style={{ maxWidth: '640px' }}>
            <div className="d-flex flex-column flex-grow-1" style={{ maxWidth: '280px' }}>
              <label htmlFor="workflow-name-input" className="visually-hidden">Workflow Name</label>
              <input
                id="workflow-name-input"
                type="text"
                className="form-control form-control-sm bg-dark text-white border-secondary fw-semibold"
                value={workflowName}
                onChange={(e) => setWorkflowName(e.target.value)}
                placeholder="Workflow name (required)..."
                title="Workflow Name (2-150 characters)"
              />
            </div>

            <div className="d-flex flex-column flex-grow-1" style={{ maxWidth: '240px' }}>
              <label htmlFor="workflow-description-input" className="visually-hidden">Workflow Description</label>
              <input
                id="workflow-description-input"
                type="text"
                className="form-control form-control-sm bg-dark text-white-50 border-secondary small"
                value={workflowDescription}
                onChange={(e) => setWorkflowDescription(e.target.value)}
                placeholder="Description (optional)..."
                title="Optional Workflow Description"
              />
            </div>

            {/* Active Toggle Switch */}
            <button
              type="button"
              className={`btn btn-sm ${isActive ? 'btn-outline-success' : 'btn-outline-secondary'} d-flex align-items-center gap-1`}
              onClick={() => setIsActive(!isActive)}
              title={`Workflow is currently ${isActive ? 'ACTIVE' : 'INACTIVE'}. Click to toggle.`}
            >
              <i className={`bi ${isActive ? 'bi-check-circle-fill' : 'bi-dash-circle'}`}></i>
              <span className="small">{isActive ? 'Active' : 'Inactive'}</span>
            </button>
          </div>

          {/* Right Action Buttons */}
          <div className="d-flex align-items-center gap-2">
            {/* Real Save / Save Changes Button */}
            <button
              id="save-workflow-btn"
              className={`btn ${loadedWorkflowId ? 'btn-success' : 'btn-primary'} btn-sm d-flex align-items-center gap-2 fw-semibold px-3 shadow-sm`}
              onClick={handleSaveWorkflow}
              disabled={isSaving || isLoadingWorkflow}
              title={loadedWorkflowId ? `Save changes to workflow #${loadedWorkflowId}` : 'Save new workflow definition to database'}
            >
              {isSaving ? (
                <>
                  <span className="spinner-border spinner-border-sm" role="status" aria-hidden="true"></span>
                  <span>{loadedWorkflowId ? 'Saving Changes...' : 'Saving...'}</span>
                </>
              ) : (
                <>
                  <i className={`bi ${loadedWorkflowId ? 'bi-check2-circle' : 'bi-floppy-fill'}`}></i>
                  <span>{loadedWorkflowId ? 'Save Changes' : 'Save Workflow'}</span>
                </>
              )}
            </button>

            {/* Real Execute Workflow Button */}
            <button
              id="execute-workflow-btn"
              className={`btn btn-sm ${
                loadedWorkflowId
                  ? isActive
                    ? 'btn-primary'
                    : 'btn-outline-warning'
                  : 'btn-outline-secondary'
              } d-flex align-items-center gap-2 fw-semibold px-3 shadow-sm`}
              onClick={handleExecuteClick}
              disabled={isExecuting || isLoadingWorkflow}
              title={
                !loadedWorkflowId
                  ? 'Save workflow to database before executing'
                  : !isActive
                  ? 'Workflow is currently inactive. Click to activate and execute.'
                  : hasUnsavedChanges
                  ? 'Unsaved changes detected. Click to choose execution option.'
                  : 'Execute workflow on backend'
              }
            >
              {isExecuting ? (
                <>
                  <span className="spinner-border spinner-border-sm" role="status" aria-hidden="true"></span>
                  <span>Executing...</span>
                </>
              ) : (
                <>
                  <i className="bi bi-play-fill fs-6"></i>
                  <span>Execute Workflow</span>
                  {hasUnsavedChanges && (
                    <span
                      className="badge bg-warning text-dark p-1 rounded-circle"
                      style={{ width: '8px', height: '8px', padding: 0 }}
                      title="Unsaved changes detected"
                    ></span>
                  )}
                </>
              )}
            </button>
          </div>
        </div>
      </header>

      {/* Workflow Load Error Banner */}
      {loadError && (
        <div className="alert alert-danger alert-dismissible fade show m-3 mb-0 py-2 px-3 shadow-sm d-flex align-items-center justify-content-between" role="alert">
          <div className="d-flex align-items-center gap-2">
            <i className="bi bi-exclamation-triangle-fill fs-5 text-danger"></i>
            <div>
              <strong>Error Loading Workflow:</strong> {loadError}
            </div>
          </div>
          <div className="d-flex align-items-center gap-2">
            <Link to="/builder" className="btn btn-sm btn-outline-danger py-1 px-2 small" onClick={() => setLoadError(null)}>
              Start Fresh Workflow
            </Link>
            <button type="button" className="btn-close" aria-label="Close" onClick={() => setLoadError(null)}></button>
          </div>
        </div>
      )}

      {/* Save Status Banner / Toast Alert */}
      {saveStatus && (
        <div
          className={`alert alert-${saveStatus.type} alert-dismissible fade show m-3 mb-0 py-2 px-3 shadow-sm d-flex align-items-center justify-content-between position-absolute top-0 start-50 translate-middle-x z-3`}
          style={{ minWidth: '420px', maxWidth: '680px', marginTop: '60px' }}
          role="alert"
        >
          <div className="d-flex align-items-center gap-2">
            <i
              className={`bi ${
                saveStatus.type === 'success'
                  ? 'bi-check-circle-fill text-success fs-5'
                  : 'bi-exclamation-triangle-fill text-danger fs-5'
              }`}
            ></i>
            <div>
              <div className="fw-bold small">{saveStatus.message}</div>
              {saveStatus.savedId && (
                <div className="text-muted" style={{ fontSize: '0.75rem' }}>
                  Stored in MySQL table <code>workflows</code> &bull; Verified ID #{saveStatus.savedId}
                </div>
              )}
            </div>
          </div>

          <div className="d-flex align-items-center gap-2 ms-3">
            {saveStatus.type === 'success' && (
              <Link to="/" className="btn btn-sm btn-outline-success py-1 px-2 small">
                <i className="bi bi-speedometer2 me-1"></i>
                View in Dashboard
              </Link>
            )}
            <button
              type="button"
              className="btn-close position-static p-2"
              aria-label="Close"
              onClick={() => setSaveStatus(null)}
            ></button>
          </div>
        </div>
      )}

      {/* Local Notification Toast */}
      {notification && (
        <div className="alert alert-warning alert-dismissible fade show m-2 mb-0 py-2 px-3 small shadow-sm position-absolute top-0 end-0 z-3" role="alert">
          <i className="bi bi-exclamation-circle me-1"></i>
          {notification.message}
          <button
            type="button"
            className="btn-close py-2"
            onClick={() => setNotification(null)}
          ></button>
        </div>
      )}

      {/* 3-Column Studio Workspace */}
      <div className="d-flex flex-grow-1 overflow-hidden position-relative">
        {/* Left Column: Node Palette / Sidebar */}
        <aside
          className="bg-white border-end d-flex flex-column p-3 flex-shrink-0 shadow-sm"
          style={{ width: '260px', zIndex: 10 }}
        >
          <div className="d-flex align-items-center justify-content-between mb-2">
            <h6 className="fw-bold text-dark text-uppercase small mb-0">
              <i className="bi bi-boxes text-primary me-2"></i>
              Node Palette
            </h6>
            <span className="badge bg-light text-muted border" style={{ fontSize: '0.65rem' }}>
              Click or Drag
            </span>
          </div>

          <p className="text-muted small mb-3" style={{ fontSize: '0.75rem' }}>
            Click or drag node types onto the canvas to construct your workflow chain.
          </p>

          <div className="d-flex flex-column gap-2 overflow-auto pe-1">
            {PALETTE_NODES.map((item) => (
              <div
                key={item.type}
                className="card border shadow-sm p-2 text-start cursor-pointer hover-shadow palette-item"
                style={{
                  cursor: 'grab',
                  borderColor: '#e5e7eb',
                  transition: 'all 0.15s ease',
                }}
                onClick={() => addNodeToCanvas(item)}
                draggable
                onDragStart={(e) => onDragStart(e, item.type)}
                title={`Click or drag to add ${item.name}`}
              >
                <div className="d-flex align-items-center gap-2">
                  <div
                    className="rounded-2 p-2 d-flex align-items-center justify-content-center"
                    style={{
                      backgroundColor: `${item.color}15`,
                      color: item.color,
                      width: '32px',
                      height: '32px',
                    }}
                  >
                    <i className={`bi ${item.icon} fs-6`}></i>
                  </div>
                  <div>
                    <div className="fw-semibold text-dark small leading-tight">
                      {item.name}
                    </div>
                    <div className="text-muted" style={{ fontSize: '0.7rem' }}>
                      {item.description}
                    </div>
                  </div>
                </div>
              </div>
            ))}
          </div>

          {/* Quick-Start Example Template */}
          <div className="mt-3 pt-3 border-top">
            <div className="d-flex align-items-center justify-content-between mb-2">
              <span className="fw-bold text-dark text-uppercase small" style={{ fontSize: '0.7rem' }}>
                <i className="bi bi-stars text-warning me-1"></i>
                Example Template
              </span>
            </div>
            <button
              id="load-example-template-sidebar-btn"
              type="button"
              className="btn btn-outline-info btn-sm w-100 text-start p-2 d-flex align-items-center gap-2 shadow-sm"
              onClick={loadJavaConceptExplainerTemplate}
              title="Load 4-node Java Concept Explainer workflow (Manual Trigger -> Text Input -> AI Processor -> Output)"
            >
              <i className="bi bi-robot fs-5 text-info"></i>
              <div className="overflow-hidden">
                <div className="fw-bold text-dark text-truncate" style={{ fontSize: '0.78rem' }}>
                  Java Concept Explainer
                </div>
                <div className="text-muted text-truncate" style={{ fontSize: '0.68rem' }}>
                  4-node local Ollama flow
                </div>
              </div>
            </button>
          </div>

          {/* Quick Tips */}
          <div className="mt-auto pt-3 border-top text-muted" style={{ fontSize: '0.7rem' }}>
            <div className="fw-semibold text-dark mb-1">
              <i className="bi bi-info-circle me-1 text-primary"></i>
              Canvas Tips:
            </div>
            <div>&bull; Connect source handle &rarr; target handle.</div>
            <div>&bull; Click any node to edit its config.</div>
            <div>&bull; Drag canvas background to pan.</div>
          </div>
        </aside>

        {/* Center Column: React Flow Canvas */}
        <div className="flex-grow-1 position-relative h-100" ref={reactFlowWrapper}>
          {/* Canvas Floating Top Toolbar */}
          <div
            className="position-absolute top-0 start-0 m-3 d-flex align-items-center gap-2 bg-white p-2 rounded-3 shadow-sm border"
            style={{ zIndex: 10 }}
          >
            <span className="badge bg-light text-dark border me-1 small">
              Nodes: {nodes.length} &bull; Edges: {edges.length}
            </span>

            <button
              className="btn btn-outline-secondary btn-sm d-flex align-items-center gap-1"
              onClick={() => fitView({ padding: 0.2 })}
              title="Fit View"
            >
              <i className="bi bi-aspect-ratio"></i>
              <span className="d-none d-md-inline">Fit View</span>
            </button>

            {/* Auto Connect Sequential Steps */}
            <button
              id="auto-connect-btn"
              className="btn btn-outline-primary btn-sm d-flex align-items-center gap-1"
              onClick={autoConnectPipeline}
              disabled={nodes.length < 2}
              title="Automatically connect all canvas nodes in sequence from left to right"
            >
              <i className="bi bi-link-45deg"></i>
              <span className="d-none d-md-inline">Connect Sequence</span>
            </button>

            {/* Load Example Workflow Template */}
            <button
              id="load-example-toolbar-btn"
              className="btn btn-outline-info btn-sm d-flex align-items-center gap-1"
              onClick={loadJavaConceptExplainerTemplate}
              title="Load Pre-built Example: Java Concept Explainer (MANUAL_TRIGGER -> TEXT_INPUT -> AI_TEXT_PROCESSOR -> OUTPUT)"
            >
              <i className="bi bi-stars text-info"></i>
              <span className="d-none d-lg-inline">Example: Java Explainer</span>
            </button>

            {/* Results & History Panel Button */}
            {(executionResult || executionHistory.length > 0) && (
              <button
                id="view-results-btn"
                className={`btn btn-sm ${showResultsPanel ? 'btn-primary' : 'btn-outline-primary'} d-flex align-items-center gap-1`}
                onClick={() => setShowResultsPanel(!showResultsPanel)}
                title="Toggle Execution Results Panel"
              >
                <i className="bi bi-terminal"></i>
                <span className="d-none d-md-inline">Results</span>
                {executionResult && (
                  <span className={`badge ${executionResult.status === 'COMPLETED' ? 'bg-success' : 'bg-danger'} ms-1`}>
                    {executionResult.status === 'COMPLETED' ? '✓' : '✗'}
                  </span>
                )}
              </button>
            )}

            {/* Workflow Variables Button */}
            <button
              id="toolbar-variables-btn"
              className={`btn btn-sm ${activeSideTab === 'variables' ? 'btn-primary' : 'btn-outline-secondary'} d-flex align-items-center gap-1`}
              onClick={() => setActiveSideTab('variables')}
              title="Configure Workflow Variables (reusable {{variables.name}} values)"
            >
              <i className="bi bi-braces-asterisk"></i>
              <span className="d-none d-md-inline">Variables</span>
              {variables.length > 0 && (
                <span className={`badge ${activeSideTab === 'variables' ? 'bg-light text-dark' : 'bg-primary'} ms-1`}>
                  {variables.length}
                </span>
              )}
            </button>

            {/* Schedule Studio Button */}
            <button
              id="toolbar-schedule-btn"
              className={`btn btn-sm ${activeSideTab === 'schedule' ? 'btn-primary' : 'btn-outline-secondary'} d-flex align-items-center gap-1`}
              onClick={() => setActiveSideTab('schedule')}
              title="Configure Automated Execution Schedule (Daily, Weekly, One-Time)"
            >
              <i className="bi bi-clock-history"></i>
              <span className="d-none d-md-inline">Schedule</span>
              {activeSchedule && (
                <span
                  className={`badge ${activeSchedule.enabled ? 'bg-success' : 'bg-secondary'} ms-1`}
                  style={{ fontSize: '0.65rem' }}
                >
                  {activeSchedule.enabled ? 'Active' : 'Paused'}
                </span>
              )}
            </button>

            {selectedNode && (
              <button
                className="btn btn-outline-danger btn-sm d-flex align-items-center gap-1"
                onClick={deleteSelectedNode}
                title="Delete Selected Node"
              >
                <i className="bi bi-trash"></i>
                <span className="d-none d-md-inline">Delete Node</span>
              </button>
            )}

            <button
              className="btn btn-outline-secondary btn-sm d-flex align-items-center gap-1 text-danger"
              onClick={clearCanvas}
              disabled={nodes.length === 0}
              title="Clear all nodes and connections"
            >
              <i className="bi bi-x-circle"></i>
              <span className="d-none d-md-inline">Clear Canvas</span>
            </button>
          </div>

          {/* Loading Existing Workflow Overlay */}
          {isLoadingWorkflow && (
            <div
              className="position-absolute top-50 start-50 translate-middle text-center p-4 rounded-4 bg-white shadow border"
              style={{ zIndex: 20, minWidth: '320px' }}
            >
              <div className="spinner-border text-primary mb-3" role="status">
                <span className="visually-hidden">Loading...</span>
              </div>
              <h6 className="fw-bold text-dark mb-1">Loading Workflow #{workflowIdParam}...</h6>
              <p className="text-muted small mb-0">Retrieving pipeline definition from database</p>
            </div>
          )}

          {/* Empty State Overlay */}
          {!isLoadingWorkflow && nodes.length === 0 && (
            <div
              className="position-absolute top-50 start-50 translate-middle text-center p-4 rounded-4 bg-white shadow-sm border pointer-events-none"
              style={{ zIndex: 5, maxWidth: '420px', pointerEvents: 'none' }}
            >
              <div className="text-primary mb-3 fs-1 opacity-75">
                <i className="bi bi-cursor-fill"></i>
              </div>
              <h5 className="fw-bold text-dark mb-1">Your Canvas is Empty</h5>
              <p className="text-muted small mb-0">
                Click any node type from the left <strong>Node Palette</strong> or drag it here to begin assembling your automated workflow.
              </p>
            </div>
          )}

          {/* React Flow Workspace */}
          <ReactFlow
            nodes={nodes}
            edges={edges}
            onNodesChange={onNodesChange}
            onEdgesChange={onEdgesChange}
            onConnect={onConnect}
            onNodeClick={onNodeClick}
            onPaneClick={onPaneClick}
            nodeTypes={nodeTypes}
            onDragOver={onDragOver}
            onDrop={onDrop}
            fitView
            proOptions={{ hideAttribution: true }}
          >
            <Controls position="bottom-left" />
            <MiniMap
              position="bottom-right"
              nodeStrokeWidth={3}
              zoomable
              pannable
              style={{ height: 100, width: 140 }}
            />
            <Background variant="dots" gap={16} size={1} color="#cbd5e1" />
          </ReactFlow>

          {/* Collapsed Execution Status Ribbon (when panel closed but result exists) */}
          {!showResultsPanel && executionResult && (
            <div
              className="position-absolute bottom-0 start-0 end-0 bg-white border-top shadow px-3 py-2 d-flex align-items-center justify-content-between z-3"
              style={{ minHeight: '44px' }}
            >
              <div className="d-flex align-items-center gap-2 overflow-hidden me-2">
                <span className="badge bg-light text-dark border font-monospace">
                  Execution #{executionResult.id}
                </span>
                <span
                  className={`badge ${
                    executionResult.status === 'COMPLETED'
                      ? 'bg-success-subtle text-success border border-success-subtle'
                      : 'bg-danger-subtle text-danger border border-danger-subtle'
                  } d-flex align-items-center gap-1`}
                >
                  <i className={`bi ${executionResult.status === 'COMPLETED' ? 'bi-check-circle-fill' : 'bi-x-circle-fill'}`}></i>
                  {executionResult.status}
                </span>
                {executionResult.outputData && (
                  <span className="text-muted small font-monospace text-truncate d-none d-md-inline">
                    Output: {typeof executionResult.outputData?.result === 'string'
                      ? executionResult.outputData.result
                      : JSON.stringify(executionResult.outputData?.result || executionResult.outputData)}
                  </span>
                )}
                {executionResult.errorMessage && (
                  <span className="text-danger small font-monospace text-truncate d-none d-md-inline">
                    Error: {executionResult.errorMessage}
                  </span>
                )}
              </div>

              <div className="d-flex align-items-center gap-2 flex-shrink-0">
                <button
                  id="expand-results-btn"
                  className="btn btn-primary btn-sm d-flex align-items-center gap-1 py-1 px-3 shadow-sm"
                  onClick={() => setShowResultsPanel(true)}
                  title="Expand Execution Details"
                >
                  <i className="bi bi-chevron-up"></i>
                  <span>View Details</span>
                </button>
              </div>
            </div>
          )}

          {/* Expanded Execution Results Drawer */}
          {showResultsPanel && (
            <div
              className="position-absolute bottom-0 start-0 end-0 bg-white border-top shadow-lg d-flex flex-column z-3"
              style={{ height: '340px' }}
            >
              {/* Drawer Header */}
              <div className="card-header bg-dark text-white py-2 px-3 d-flex align-items-center justify-content-between border-bottom flex-shrink-0">
                <div className="d-flex align-items-center gap-3">
                  <div className="d-flex align-items-center gap-2">
                    <i className="bi bi-terminal-fill text-primary fs-5"></i>
                    <span className="fw-bold small mb-0">Execution Results</span>
                    {executionResult && (
                      <>
                        <span className="badge bg-secondary font-monospace">
                          #{executionResult.id}
                        </span>
                        <span
                          className={`badge ${
                            executionResult.status === 'COMPLETED' ? 'bg-success' : 'bg-danger'
                          } d-flex align-items-center gap-1`}
                        >
                          <i
                            className={`bi ${
                              executionResult.status === 'COMPLETED'
                                ? 'bi-check-circle-fill'
                                : 'bi-x-circle-fill'
                            }`}
                          ></i>
                          {executionResult.status}
                        </span>
                        {executionResult.startedAt && executionResult.completedAt && (
                          <span className="badge bg-dark-subtle text-light border border-secondary small d-none d-sm-inline">
                            {new Date(executionResult.completedAt) - new Date(executionResult.startedAt)} ms
                          </span>
                        )}
                      </>
                    )}
                  </div>
                </div>

                {/* Tabs & Controls */}
                <div className="d-flex align-items-center gap-2">
                  <div className="btn-group btn-group-sm" role="group">
                    <button
                      type="button"
                      className={`btn btn-sm ${resultsTab === 'output' ? 'btn-primary' : 'btn-outline-light'}`}
                      onClick={() => setResultsTab('output')}
                    >
                      <i className="bi bi-code-square me-1"></i>
                      Output
                    </button>
                    <button
                      type="button"
                      className={`btn btn-sm ${resultsTab === 'trace' ? 'btn-primary' : 'btn-outline-light'}`}
                      onClick={() => setResultsTab('trace')}
                    >
                      <i className="bi bi-list-check me-1"></i>
                      Step Trace ({executionResult?.nodeLogs?.length || 0})
                    </button>
                    <button
                      type="button"
                      className={`btn btn-sm ${resultsTab === 'history' ? 'btn-primary' : 'btn-outline-light'}`}
                      onClick={() => {
                        setResultsTab('history');
                        loadExecutionHistory(loadedWorkflowId, historyFilter);
                      }}
                    >
                      <i className="bi bi-clock-history me-1"></i>
                      History ({executionHistory.length})
                    </button>
                  </div>

                  <button
                    type="button"
                    className="btn btn-outline-light btn-sm py-0 px-2"
                    onClick={() => setShowResultsPanel(false)}
                    title="Minimize Results Panel"
                  >
                    <i className="bi bi-chevron-down"></i>
                  </button>
                  <button
                    type="button"
                    className="btn-close btn-close-white"
                    aria-label="Close"
                    onClick={() => {
                      setShowResultsPanel(false);
                      setExecutionResult(null);
                      setExecutionError(null);
                    }}
                    title="Close and Clear Results"
                  ></button>
                </div>
              </div>

              {/* Drawer Content */}
              <div className="card-body p-3 overflow-auto flex-grow-1 bg-light">
                {/* Tab 1: Output & Overview */}
                {resultsTab === 'output' && (
                  <div>
                    {/* Workflow Execution Input Control with Presets */}
                    <div className="card shadow-sm border rounded-3 bg-white p-2 px-3 mb-3">
                      <div className="d-flex align-items-center justify-content-between mb-1">
                        <span className="text-secondary small fw-bold text-uppercase d-flex align-items-center gap-1" style={{ fontSize: '0.7rem' }}>
                          <i className="bi bi-input-cursor-text text-primary"></i>
                          Execution Input Data (JSON)
                        </span>
                        <div className="d-flex align-items-center gap-1">
                          <span className="text-muted small me-1" style={{ fontSize: '0.68rem' }}>Branch Presets:</span>
                          <button
                            type="button"
                            className="btn btn-outline-success btn-sm py-0 px-2"
                            style={{ fontSize: '0.68rem' }}
                            onClick={() => setExecutionInputJson('{"score": 85}')}
                            title='Set input: {"score": 85} (TRUE Branch)'
                          >
                            Score: 85 (True)
                          </button>
                          <button
                            type="button"
                            className="btn btn-outline-danger btn-sm py-0 px-2"
                            style={{ fontSize: '0.68rem' }}
                            onClick={() => setExecutionInputJson('{"score": 45}')}
                            title='Set input: {"score": 45} (FALSE Branch)'
                          >
                            Score: 45 (False)
                          </button>
                        </div>
                      </div>
                      <div className="input-group input-group-sm">
                        <input
                          id="execution-input-field"
                          type="text"
                          className="form-control font-monospace"
                          style={{ fontSize: '0.78rem' }}
                          value={executionInputJson}
                          onChange={(e) => setExecutionInputJson(e.target.value)}
                          placeholder='{"score": 85}'
                        />
                        <button
                          id="run-with-input-btn"
                          className="btn btn-primary btn-sm d-flex align-items-center gap-1 px-3 fw-semibold"
                          onClick={() => handleExecuteClick()}
                          disabled={isExecuting || isLoadingWorkflow}
                          title="Execute workflow with this JSON input"
                        >
                          <i className="bi bi-play-fill"></i>
                          <span>Run Execution</span>
                        </button>
                      </div>
                    </div>
                    {isExecuting ? (
                      <div className="card shadow-sm border-0 rounded-3 bg-white p-4 text-center my-3">
                        <div className="spinner-border text-info mx-auto mb-3" role="status" style={{ width: '2.5rem', height: '2.5rem' }}>
                          <span className="visually-hidden">Executing...</span>
                        </div>
                        <h6 className="fw-bold mb-1 text-dark">Workflow Execution in Progress</h6>
                        <p className="text-muted small mb-2">
                          Executing workflow steps in topological order on the backend engine.
                        </p>
                        <div className="d-inline-flex align-items-center gap-2 px-3 py-1 bg-info-subtle text-info-emphasis rounded-pill border border-info-subtle mx-auto small">
                          <i className="bi bi-robot text-info"></i>
                          <span>Local AI inference running on Ollama (<code>qwen2.5:3b</code>)...</span>
                        </div>
                      </div>
                    ) : executionError ? (
                      <div className="alert alert-danger shadow-sm mb-0">
                        <div className="d-flex align-items-center gap-2 mb-1">
                          <i className="bi bi-exclamation-triangle-fill fs-5"></i>
                          <h6 className="fw-bold mb-0">Execution Failed</h6>
                        </div>
                        <p className="mb-1 font-monospace small">{executionError}</p>
                        {executionError?.toLowerCase().includes('ollama') && (
                          <div className="alert alert-warning py-2 px-3 mt-2 mb-0 small border-warning-subtle text-dark">
                            <i className="bi bi-info-circle-fill me-1 text-warning"></i>
                            <strong>Local AI Provider:</strong> The <code>AI_TEXT_PROCESSOR</code> node runs locally on Ollama (<code>qwen2.5:3b</code>). Please verify Ollama is running on <code>http://localhost:11434</code>.
                          </div>
                        )}
                      </div>
                    ) : executionResult ? (
                      <div className="d-flex flex-column gap-3">
                        {/* Status Alert for Failed Executions */}
                        {executionResult.status === 'FAILED' && (
                          <div className="alert alert-danger shadow-sm mb-0 py-2 px-3">
                            <div className="d-flex align-items-start justify-content-between gap-2">
                              <div className="d-flex align-items-start gap-2">
                                <i className="bi bi-x-circle-fill fs-5 mt-1 text-danger"></i>
                                <div>
                                  <h6 className="fw-bold text-danger mb-1">Pipeline Execution Failed</h6>
                                  <p className="mb-1 font-monospace small">
                                    {executionResult.errorMessage || 'One or more steps failed during topological execution.'}
                                  </p>
                                  {executionResult.errorMessage?.toLowerCase().includes('ollama') && (
                                    <div className="alert alert-warning py-2 px-3 mt-2 mb-0 small border-warning-subtle text-dark">
                                      <i className="bi bi-info-circle-fill me-1 text-warning"></i>
                                      <strong>Local AI Provider:</strong> The <code>AI_TEXT_PROCESSOR</code> node runs locally on Ollama (<code>qwen2.5:3b</code>). Please verify Ollama is running on <code>http://localhost:11434</code>.
                                    </div>
                                  )}
                                </div>
                              </div>
                              <button
                                type="button"
                                className="btn btn-warning btn-sm d-flex align-items-center gap-1 flex-shrink-0 shadow-sm"
                                onClick={() => handleRetryExecution(executionResult.id)}
                                disabled={isRetrying}
                                title="Retry this failed execution with original input data"
                              >
                                {isRetrying ? (
                                  <span className="spinner-border spinner-border-sm" role="status"></span>
                                ) : (
                                  <i className="bi bi-arrow-clockwise"></i>
                                )}
                                <span>Retry Execution</span>
                              </button>
                            </div>
                          </div>
                        )}

                        {/* Success Output Card */}
                        {executionResult.status === 'COMPLETED' && (
                          <div className="card shadow-sm border-0 rounded-3 bg-white p-3">
                            <div className="d-flex align-items-center justify-content-between mb-2">
                              <span className="text-muted small fw-bold text-uppercase">
                                <i className="bi bi-box-arrow-right text-success me-1"></i>
                                Final Pipeline Output:
                              </span>
                              {executionResult.outputData?.result && (
                                <button
                                  className="btn btn-outline-secondary btn-sm py-0 px-2 small"
                                  onClick={() => {
                                    const val = typeof executionResult.outputData.result === 'string'
                                      ? executionResult.outputData.result
                                      : JSON.stringify(executionResult.outputData.result);
                                    navigator.clipboard.writeText(val);
                                    setNotification({ type: 'success', message: 'Output copied to clipboard!' });
                                  }}
                                  title="Copy Output"
                                >
                                  <i className="bi bi-clipboard me-1"></i>
                                  Copy
                                </button>
                              )}
                            </div>
                            <div className="p-3 bg-light rounded-3 border text-dark font-monospace fs-6 fw-normal text-break" style={{ whiteSpace: 'pre-wrap', maxHeight: '180px', overflowY: 'auto' }}>
                              {typeof executionResult.outputData?.result === 'string'
                                ? executionResult.outputData.result
                                : JSON.stringify(executionResult.outputData?.result || executionResult.outputData, null, 2)}
                            </div>
                          </div>
                        )}

                        {/* Dedicated AI Node Output Card (if pipeline includes AI_TEXT_PROCESSOR) */}
                        {(() => {
                          const aiLog = Array.isArray(executionResult.nodeLogs)
                            ? executionResult.nodeLogs.find((l) => l.nodeType === 'AI_TEXT_PROCESSOR')
                            : null;
                          if (!aiLog || !aiLog.outputData) return null;
                          return (
                            <div className="card shadow-sm border-0 rounded-3 bg-white p-3 border-start border-4 border-info">
                              <div className="d-flex align-items-center justify-content-between mb-2">
                                <div className="d-flex align-items-center gap-2">
                                  <i className="bi bi-robot text-info fs-5"></i>
                                  <span className="text-dark small fw-bold text-uppercase">
                                    AI Generated Output ({aiLog.nodeId})
                                  </span>
                                  <span className="badge bg-info-subtle text-info-emphasis border border-info-subtle font-monospace" style={{ fontSize: '0.65rem' }}>
                                    Ollama: {aiLog.outputData?.model || 'qwen2.5:3b'}
                                  </span>
                                </div>
                                {aiLog.outputData?.result && (
                                  <button
                                    className="btn btn-outline-secondary btn-sm py-0 px-2 small"
                                    onClick={() => {
                                      const val = typeof aiLog.outputData.result === 'string'
                                        ? aiLog.outputData.result
                                        : JSON.stringify(aiLog.outputData.result);
                                      navigator.clipboard.writeText(val);
                                      setNotification({ type: 'success', message: 'AI output copied to clipboard!' });
                                    }}
                                    title="Copy AI Output"
                                  >
                                    <i className="bi bi-clipboard me-1"></i>
                                    Copy AI Output
                                  </button>
                                )}
                              </div>
                              <div className="p-3 bg-light rounded-3 border text-dark font-monospace fs-6 fw-normal text-break" style={{ whiteSpace: 'pre-wrap', maxHeight: '180px', overflowY: 'auto' }}>
                                {typeof aiLog.outputData?.result === 'string'
                                  ? aiLog.outputData.result
                                  : JSON.stringify(aiLog.outputData?.result || aiLog.outputData, null, 2)}
                              </div>
                            </div>
                          );
                        })()}

                        {/* Raw JSON Data Accordion */}
                        <details className="card shadow-sm border-0 rounded-3 bg-white p-2">
                          <summary className="text-secondary small fw-semibold cursor-pointer py-1">
                            <i className="bi bi-code-slash me-1"></i>
                            View Raw JSON Response
                          </summary>
                          <pre className="bg-light p-3 rounded-2 border text-dark font-monospace small mb-0 mt-2 overflow-auto" style={{ maxHeight: '140px' }}>
                            {JSON.stringify(executionResult, null, 2)}
                          </pre>
                        </details>
                      </div>
                    ) : (
                      <div className="text-center text-muted py-4">
                        <i className="bi bi-play-circle fs-2 d-block mb-2 text-secondary opacity-50"></i>
                        <p className="small mb-0">No active execution. Click &quot;Execute Workflow&quot; in the header bar to run this pipeline.</p>
                      </div>
                    )}
                  </div>
                )}

                {/* Tab 2: Step Trace (nodeLogs) */}
                {resultsTab === 'trace' && (
                  <div>
                    {executionResult?.nodeLogs && executionResult.nodeLogs.length > 0 ? (
                      <div className="table-responsive">
                        <table className="table table-sm table-hover table-bordered align-middle mb-0 bg-white shadow-sm">
                          <thead className="table-light small text-uppercase">
                            <tr>
                              <th style={{ width: '50px' }}>#</th>
                              <th>Node ID</th>
                              <th>Node Type</th>
                              <th style={{ width: '120px' }}>Status</th>
                              <th style={{ width: '90px' }}>Duration</th>
                              <th>Step Output / Data</th>
                            </tr>
                          </thead>
                          <tbody className="small">
                            {executionResult.nodeLogs.map((log, idx) => {
                              const duration = log.startedAt && log.completedAt
                                ? `${new Date(log.completedAt) - new Date(log.startedAt)} ms`
                                : '-';
                              return (
                                <tr key={log.id || idx}>
                                  <td className="text-muted fw-bold">{idx + 1}</td>
                                  <td>
                                    <code className="text-dark fw-bold">{log.nodeId}</code>
                                  </td>
                                  <td>
                                    <span className="badge bg-light text-dark border">
                                      {log.nodeType}
                                    </span>
                                  </td>
                                  <td>
                                    <span
                                      className={`badge ${
                                        log.status === 'COMPLETED'
                                          ? 'bg-success-subtle text-success border border-success-subtle'
                                          : log.status === 'FAILED'
                                          ? 'bg-danger-subtle text-danger border border-danger-subtle'
                                          : log.status === 'SKIPPED'
                                          ? 'bg-secondary-subtle text-secondary border border-secondary-subtle'
                                          : 'bg-primary-subtle text-primary border border-primary-subtle'
                                      }`}
                                    >
                                      {log.status === 'COMPLETED'
                                        ? '✓ COMPLETED'
                                        : log.status === 'FAILED'
                                        ? '✗ FAILED'
                                        : log.status === 'SKIPPED'
                                        ? '⊘ SKIPPED'
                                        : log.status}
                                    </span>
                                  </td>
                                  <td className="font-monospace text-muted small">{duration}</td>
                                  <td className="font-monospace text-break" style={{ maxWidth: '420px' }}>
                                    {log.status === 'SKIPPED' ? (
                                      <span className="text-secondary fst-italic">
                                        <i className="bi bi-slash-circle me-1"></i>
                                        {log.outputData?.reason || 'Branch not taken (skipped)'}
                                      </span>
                                    ) : log.nodeType === 'CONDITION' && log.outputData ? (
                                      <div>
                                        <span className={`badge me-2 ${log.outputData.branch === 'TRUE' ? 'bg-success' : 'bg-danger'}`}>
                                          Branch: {log.outputData.branch}
                                        </span>
                                        <span className="text-muted small">
                                          ({log.outputData.operator} evaluated to {String(log.outputData.result)})
                                        </span>
                                      </div>
                                    ) : log.errorMessage ? (
                                      <span className="text-danger">{log.errorMessage}</span>
                                    ) : log.outputData ? (
                                      <span className="text-dark">
                                        {typeof log.outputData.result === 'string'
                                          ? log.outputData.result
                                          : JSON.stringify(log.outputData)}
                                      </span>
                                    ) : (
                                      <span className="text-muted fst-italic">None</span>
                                    )}
                                  </td>
                                </tr>
                              );
                            })}
                          </tbody>
                        </table>
                      </div>
                    ) : (
                      <div className="text-center text-muted py-4">
                        <p className="small mb-0">No step trace logs available for this execution.</p>
                      </div>
                    )}
                  </div>
                )}

                {/* Tab 3: Execution History */}
                {resultsTab === 'history' && (
                  <div>
                    <div className="d-flex flex-wrap align-items-center justify-content-between gap-2 mb-3">
                      <div className="d-flex align-items-center gap-2">
                        <span className="text-muted small fw-semibold">
                          Past Runs for Workflow #{loadedWorkflowId} ({executionHistory.length})
                        </span>
                        <div className="btn-group btn-group-sm" role="group">
                          {['ALL', 'COMPLETED', 'FAILED'].map((st) => (
                            <button
                              key={st}
                              type="button"
                              className={`btn btn-sm ${historyFilter === st ? 'btn-primary' : 'btn-outline-secondary'}`}
                              onClick={() => {
                                setHistoryFilter(st);
                                loadExecutionHistory(loadedWorkflowId, st);
                              }}
                            >
                              {st === 'ALL' ? 'All' : st}
                            </button>
                          ))}
                        </div>
                      </div>
                      <button
                        className="btn btn-outline-secondary btn-sm py-0 px-2 small"
                        onClick={() => loadExecutionHistory(loadedWorkflowId, historyFilter)}
                        disabled={loadingHistory}
                        title="Reload history"
                      >
                        <i className={`bi bi-arrow-clockwise ${loadingHistory ? 'spin' : ''} me-1`}></i>
                        Refresh
                      </button>
                    </div>

                    {executionHistory.length > 0 ? (
                      <div className="table-responsive">
                        <table className="table table-sm table-hover table-bordered align-middle mb-0 bg-white shadow-sm">
                          <thead className="table-light small text-uppercase">
                            <tr>
                              <th style={{ width: '70px' }}>Run #</th>
                              <th style={{ width: '120px' }}>Status</th>
                              <th>Started At</th>
                              <th>Duration</th>
                              <th>Final Output / Error</th>
                              <th style={{ width: '150px' }} className="text-end">Actions</th>
                            </tr>
                          </thead>
                          <tbody className="small">
                            {executionHistory.map((hist) => {
                              const duration = hist.startedAt && hist.completedAt
                                ? `${new Date(hist.completedAt) - new Date(hist.startedAt)} ms`
                                : '-';
                              const isCurrent = executionResult?.id === hist.id;
                              return (
                                <tr key={hist.id} className={isCurrent ? 'table-active' : ''}>
                                  <td className="fw-bold font-monospace">#{hist.id}</td>
                                  <td>
                                    <span
                                      className={`badge ${
                                        hist.status === 'COMPLETED'
                                          ? 'bg-success-subtle text-success border border-success-subtle'
                                          : 'bg-danger-subtle text-danger border border-danger-subtle'
                                      }`}
                                    >
                                      {hist.status}
                                    </span>
                                  </td>
                                  <td className="text-muted small">
                                    {hist.startedAt ? new Date(hist.startedAt).toLocaleString() : 'N/A'}
                                  </td>
                                  <td className="font-monospace text-muted">{duration}</td>
                                  <td className="font-monospace text-truncate text-break" style={{ maxWidth: '280px' }}>
                                    {hist.errorMessage ? (
                                      <span className="text-danger">{hist.errorMessage}</span>
                                    ) : hist.outputData?.result ? (
                                      String(hist.outputData.result)
                                    ) : hist.outputData ? (
                                      JSON.stringify(hist.outputData)
                                    ) : (
                                      '-'
                                    )}
                                  </td>
                                  <td className="text-end">
                                    <div className="d-inline-flex align-items-center gap-1">
                                      {hist.status === 'FAILED' && (
                                        <button
                                          className="btn btn-sm btn-outline-warning py-0 px-2 small d-inline-flex align-items-center gap-1"
                                          onClick={() => handleRetryExecution(hist.id)}
                                          disabled={isRetrying}
                                          title="Retry this failed execution as a new run"
                                        >
                                          {isRetrying ? (
                                            <span className="spinner-border spinner-border-sm" role="status"></span>
                                          ) : (
                                            <i className="bi bi-arrow-clockwise"></i>
                                          )}
                                          <span>Retry</span>
                                        </button>
                                      )}
                                      <button
                                        className={`btn btn-sm ${isCurrent ? 'btn-primary' : 'btn-outline-primary'} py-0 px-2 small`}
                                        onClick={() => {
                                          setExecutionResult(hist);
                                          setExecutionError(null);
                                          applyExecutionToCanvas(hist);
                                          setResultsTab('output');
                                          setNotification({
                                            type: 'info',
                                            message: `Inspecting past execution Run #${hist.id} (${hist.status})`,
                                          });
                                        }}
                                      >
                                        {isCurrent ? 'Viewing' : 'Inspect'}
                                      </button>
                                    </div>
                                  </td>
                                </tr>
                              );
                            })}
                          </tbody>
                        </table>
                      </div>
                    ) : (
                      <div className="text-center text-muted py-4">
                        <i className="bi bi-inbox fs-2 d-block mb-2 text-secondary opacity-50"></i>
                        <p className="small mb-0">No past executions recorded in MySQL for this workflow.</p>
                      </div>
                    )}
                  </div>
                )}
              </div>
            </div>
          )}
        </div>

        {/* Right Column: Properties & Configuration Panel */}
        <aside
          className="bg-white border-start d-flex flex-column p-3 flex-shrink-0 shadow-sm"
          style={{ width: '330px', zIndex: 10 }}
        >
          {/* Right Column Header: Tabs between Node Config, Workflow Variables & Schedule */}
          <div className="d-flex align-items-center justify-content-between mb-3 border-bottom pb-2">
            <div className="btn-group btn-group-sm w-100" role="group">
              <button
                type="button"
                id="tab-node-config"
                className={`btn btn-sm ${activeSideTab === 'node' ? 'btn-primary' : 'btn-outline-secondary'}`}
                onClick={() => setActiveSideTab('node')}
              >
                <i className="bi bi-sliders me-1"></i>
                Node Config
              </button>
              <button
                type="button"
                id="tab-workflow-variables"
                className={`btn btn-sm ${activeSideTab === 'variables' ? 'btn-primary' : 'btn-outline-secondary'}`}
                onClick={() => setActiveSideTab('variables')}
              >
                <i className="bi bi-braces-asterisk me-1"></i>
                Variables {variables.length > 0 ? `(${variables.length})` : ''}
              </button>
              <button
                type="button"
                id="tab-workflow-schedule"
                className={`btn btn-sm ${activeSideTab === 'schedule' ? 'btn-primary' : 'btn-outline-secondary'}`}
                onClick={() => setActiveSideTab('schedule')}
              >
                <i className="bi bi-clock-history me-1"></i>
                Schedule
              </button>
            </div>
            {activeSideTab === 'node' && selectedNode && (
              <button
                className="btn btn-link btn-sm text-muted p-0 ms-2"
                onClick={() => setSelectedNodeId(null)}
                title="Deselect"
              >
                <i className="bi bi-x-lg"></i>
              </button>
            )}
          </div>

          {activeSideTab === 'schedule' ? (
            /* Workflow Schedule Studio Section */
            <ScheduleEditor
              workflowId={loadedWorkflowId}
              isWorkflowActive={isActive}
              onScheduleUpdated={(sched) => {
                setActiveSchedule(sched);
              }}
            />
          ) : activeSideTab === 'variables' ? (
            /* Workflow Variables Management Section */
            <div className="d-flex flex-column gap-3 overflow-auto pe-1 flex-grow-1">
              <div className="d-flex align-items-center justify-content-between">
                <div>
                  <h6 className="fw-bold text-dark small mb-0">Workflow Variables</h6>
                  <span className="text-muted" style={{ fontSize: '0.72rem' }}>
                    Reusable dynamic context values
                  </span>
                </div>
                <button
                  id="add-variable-btn"
                  type="button"
                  className="btn btn-primary btn-sm py-1 px-2 d-flex align-items-center gap-1 shadow-sm"
                  style={{ fontSize: '0.75rem' }}
                  onClick={() => handleAddVariable()}
                >
                  <i className="bi bi-plus-lg"></i>
                  <span>Add Variable</span>
                </button>
              </div>

              {/* Explanatory Banner */}
              <div className="card bg-info-subtle border-info-subtle p-2 rounded-2">
                <div className="d-flex align-items-start gap-2">
                  <i className="bi bi-info-circle-fill text-info fs-6 mt-1 flex-shrink-0"></i>
                  <div style={{ fontSize: '0.72rem', lineHeight: '1.35' }}>
                    <span className="fw-bold text-dark">How to reference variables:</span>
                    <div className="text-muted mt-1">
                      Reference variables anywhere in AI prompts or text inputs as{' '}
                      <code className="text-primary fw-bold">{'{{variables.variableName}}'}</code>.
                    </div>
                  </div>
                </div>
              </div>

              {/* Quick Examples Bar */}
              <div className="bg-light p-2 rounded-2 border">
                <div className="d-flex align-items-center justify-content-between mb-1">
                  <span className="fw-bold text-secondary text-uppercase small" style={{ fontSize: '0.68rem' }}>
                    <i className="bi bi-lightning-charge-fill text-warning me-1"></i>
                    Suggested Examples:
                  </span>
                </div>
                <div className="d-flex flex-wrap gap-1">
                  {EXAMPLE_WORKFLOW_VARIABLES.map((ex) => (
                    <button
                      key={ex.key}
                      type="button"
                      className="badge bg-white text-dark border text-decoration-none btn btn-sm py-1 px-2"
                      style={{ fontSize: '0.68rem', cursor: 'pointer' }}
                      onClick={() => handleAddExampleVariable(ex)}
                      title={`Add example: ${ex.key} = "${ex.value}" (${ex.description})`}
                    >
                      + {ex.key}
                    </button>
                  ))}
                </div>
              </div>

              {/* Variables List */}
              {variables.length > 0 ? (
                <div className="d-flex flex-column gap-2">
                  {variables.map((v, idx) => {
                    const validationError = getVariableValidationError(v.key, variables, idx);
                    const isCopied = copiedVarKey === v.key;
                    return (
                      <div
                        key={v.id}
                        className={`card border p-2 rounded-2 shadow-sm ${validationError ? 'border-danger' : 'border-light-subtle'}`}
                        style={{ backgroundColor: validationError ? '#fff8f8' : '#fff' }}
                      >
                        <div className="d-flex align-items-center justify-content-between mb-1">
                          <span className="badge bg-light text-secondary border font-monospace" style={{ fontSize: '0.65rem' }}>
                            #{idx + 1}
                          </span>
                          <div className="d-flex align-items-center gap-1">
                            {v.key?.trim() && (
                              <button
                                type="button"
                                className="btn btn-link btn-sm p-0 text-decoration-none font-monospace small"
                                style={{ fontSize: '0.68rem' }}
                                onClick={() => handleCopyVariableReference(v.key)}
                                title="Click to copy {{variables.<name>}} placeholder"
                              >
                                <code className="text-primary">{`{{variables.${v.key.trim()}}}`}</code>
                                <i className={`bi ${isCopied ? 'bi-check-lg text-success' : 'bi-clipboard'} ms-1`}></i>
                              </button>
                            )}
                            <button
                              type="button"
                              className="btn btn-outline-danger btn-sm p-0 ms-1 d-flex align-items-center justify-content-center"
                              style={{ width: '22px', height: '22px' }}
                              onClick={() => handleRemoveVariable(v.id)}
                              title="Delete variable"
                            >
                              <i className="bi bi-x-lg" style={{ fontSize: '0.7rem' }}></i>
                            </button>
                          </div>
                        </div>

                        {/* Variable Name Input */}
                        <div className="mb-1">
                          <label className="form-label text-muted small mb-0 fw-semibold" style={{ fontSize: '0.68rem' }}>
                            Name
                          </label>
                          <input
                            type="text"
                            className={`form-control form-control-sm font-monospace py-1 ${
                              validationError ? 'is-invalid' : ''
                            }`}
                            style={{ fontSize: '0.78rem' }}
                            placeholder="e.g. targetRole"
                            value={v.key}
                            onChange={(e) => handleUpdateVariable(v.id, 'key', e.target.value)}
                          />
                          {validationError && (
                            <div className="text-danger small mt-1" style={{ fontSize: '0.68rem' }}>
                              {validationError}
                            </div>
                          )}
                        </div>

                        {/* Variable Value Input */}
                        <div>
                          <label className="form-label text-muted small mb-0 fw-semibold" style={{ fontSize: '0.68rem' }}>
                            Value
                          </label>
                          <input
                            type="text"
                            className="form-control form-control-sm py-1"
                            style={{ fontSize: '0.78rem' }}
                            placeholder="e.g. Senior Java Architect"
                            value={v.value}
                            onChange={(e) => handleUpdateVariable(v.id, 'value', e.target.value)}
                          />
                        </div>
                      </div>
                    );
                  })}

                  <button
                    type="button"
                    className="btn btn-outline-primary btn-sm w-100 py-1 d-flex align-items-center justify-content-center gap-1 mt-1"
                    style={{ fontSize: '0.75rem' }}
                    onClick={() => handleAddVariable()}
                  >
                    <i className="bi bi-plus-circle"></i>
                    <span>Add Another Variable</span>
                  </button>
                </div>
              ) : (
                <div className="text-center text-muted py-4 px-2 bg-light rounded-3 border">
                  <i className="bi bi-braces-asterisk fs-2 d-block mb-2 text-secondary opacity-50"></i>
                  <h6 className="fw-semibold small text-dark mb-1">No Variables Defined</h6>
                  <p className="small mb-3" style={{ fontSize: '0.72rem' }}>
                    Workflow variables allow you to set reusable values (e.g. role, company, topic) and interpolate them across your pipeline.
                  </p>
                  <button
                    type="button"
                    className="btn btn-primary btn-sm px-3 shadow-sm"
                    style={{ fontSize: '0.75rem' }}
                    onClick={() => handleAddVariable()}
                  >
                    <i className="bi bi-plus-lg me-1"></i>
                    Create First Variable
                  </button>
                </div>
              )}

              {/* Usage Hints in Nodes */}
              <div className="card bg-light border p-2 rounded-2 mt-auto text-muted" style={{ fontSize: '0.7rem' }}>
                <div className="fw-bold text-dark mb-1">
                  <i className="bi bi-lightbulb-fill text-warning me-1"></i>
                  Using Variables in Nodes:
                </div>
                <div>&bull; <strong>TEXT_INPUT</strong>: Type <code>{'{{variables.varName}}'}</code> in the text field.</div>
                <div>&bull; <strong>AI_TEXT_PROCESSOR</strong>: Type <code>{'{{variables.varName}}'}</code> anywhere in your instruction.</div>
                <div>&bull; <strong>CONDITION</strong>: Use in Left or Right operands (e.g. <code>{'{{variables.threshold}}'}</code>).</div>
                <div>&bull; <strong>Safe Fallback</strong>: Unrecognized variables are left intact.</div>
              </div>
            </div>
          ) : selectedNode ? (
            <div className="d-flex flex-column gap-3 overflow-auto pe-1">
              {/* Node ID & Type Pill */}
              <div className="d-flex align-items-center justify-content-between">
                <span className="badge bg-light text-dark border font-monospace">
                  ID: {selectedNode.id}
                </span>
                <span className="badge bg-primary-subtle text-primary border border-primary-subtle small">
                  {selectedNode.data.type}
                </span>
              </div>

              {/* Node Name / Label */}
              <div>
                <label htmlFor="node-name-input" className="form-label small fw-bold text-secondary mb-1">
                  Node Name / Label
                </label>
                <input
                  id="node-name-input"
                  type="text"
                  className="form-control form-control-sm"
                  value={selectedNode.data.name || ''}
                  onChange={(e) => updateSelectedNodeData({ name: e.target.value })}
                  placeholder="Enter friendly node name..."
                />
              </div>

              <hr className="my-1 text-muted" />

              {/* Specific Field Configuration by Node Type */}
              {selectedNode.data.type === 'TEXT_INPUT' && (
                <div>
                  <label htmlFor="text-input-field" className="form-label small fw-bold text-secondary mb-1">
                    Input Text Value
                  </label>
                  <textarea
                    id="text-input-field"
                    className="form-control form-control-sm font-monospace"
                    rows={4}
                    value={selectedNode.data.config?.text || ''}
                    onChange={(e) =>
                      updateSelectedNodeData({
                        config: { text: e.target.value },
                      })
                    }
                    placeholder="Enter raw text to feed into the workflow..."
                  ></textarea>
                  <small className="text-muted" style={{ fontSize: '0.7rem' }}>
                    This text will be forwarded to connected downstream nodes.
                  </small>

                  {/* Workflow Variables Quick Insert */}
                  <div className="bg-light p-2 rounded-2 border mt-2">
                    <div className="d-flex align-items-center justify-content-between mb-1">
                      <span className="small fw-bold text-secondary" style={{ fontSize: '0.7rem' }}>
                        <i className="bi bi-braces-asterisk text-primary me-1"></i>
                        Workflow Variables
                      </span>
                      <button
                        type="button"
                        className="btn btn-link btn-sm p-0 text-primary small text-decoration-none"
                        style={{ fontSize: '0.68rem' }}
                        onClick={() => setActiveSideTab('variables')}
                      >
                        Manage &rarr;
                      </button>
                    </div>
                    {variables.length > 0 ? (
                      <div className="d-flex flex-wrap gap-1">
                        {variables
                          .filter((v) => v.key && v.key.trim())
                          .map((v) => (
                            <button
                              key={v.id}
                              type="button"
                              className="badge bg-white text-primary border text-decoration-none btn btn-sm py-1 px-2"
                              style={{ fontSize: '0.68rem', cursor: 'pointer' }}
                              title={`Insert {{variables.${v.key.trim()}}} into input text`}
                              onClick={() => {
                                const tag = `{{variables.${v.key.trim()}}}`;
                                const cur = selectedNode.data.config?.text || '';
                                updateSelectedNodeData({
                                  config: { text: cur ? `${cur} ${tag}` : tag },
                                });
                              }}
                            >
                              + {`{{variables.${v.key.trim()}}}`}
                            </button>
                          ))}
                      </div>
                    ) : (
                      <div className="text-muted" style={{ fontSize: '0.68rem' }}>
                        No variables defined yet. Use <code>{'{{variables.name}}'}</code> or{' '}
                        <button
                          type="button"
                          className="btn btn-link btn-sm p-0 text-primary text-decoration-none"
                          style={{ fontSize: '0.68rem' }}
                          onClick={() => setActiveSideTab('variables')}
                        >
                          add one now
                        </button>.
                      </div>
                    )}
                  </div>
                </div>
              )}

              {selectedNode.data.type === 'TEXT_TRANSFORM' && (
                <div className="d-flex flex-column gap-2">
                  <div>
                    <label htmlFor="transform-operation-select" className="form-label small fw-bold text-secondary mb-1">
                      Transformation Operation
                    </label>
                    <select
                      id="transform-operation-select"
                      className="form-select form-select-sm"
                      value={selectedNode.data.config?.operation || 'UPPERCASE'}
                      onChange={(e) =>
                        updateSelectedNodeData({
                          config: { operation: e.target.value },
                        })
                      }
                    >
                      <option value="UPPERCASE">UPPERCASE (e.g. HELLO)</option>
                      <option value="LOWERCASE">LOWERCASE (e.g. hello)</option>
                      <option value="TRIM">TRIM (remove outer whitespace)</option>
                      <option value="REPLACE">REPLACE (find &amp; substitute text)</option>
                    </select>
                  </div>

                  {selectedNode.data.config?.operation === 'REPLACE' && (
                    <div className="bg-light p-2 rounded-2 border mt-1">
                      <div className="mb-2">
                        <label htmlFor="replace-search-field" className="form-label small fw-bold text-secondary mb-1">
                          Search Text (find)
                        </label>
                        <input
                          id="replace-search-field"
                          type="text"
                          className="form-control form-control-sm font-monospace"
                          value={selectedNode.data.config?.search || ''}
                          onChange={(e) =>
                            updateSelectedNodeData({
                              config: { search: e.target.value },
                            })
                          }
                          placeholder="Text to look for..."
                        />
                      </div>
                      <div>
                        <label htmlFor="replace-replacement-field" className="form-label small fw-bold text-secondary mb-1">
                          Replacement Text
                        </label>
                        <input
                          id="replace-replacement-field"
                          type="text"
                          className="form-control form-control-sm font-monospace"
                          value={selectedNode.data.config?.replace || ''}
                          onChange={(e) =>
                            updateSelectedNodeData({
                              config: { replace: e.target.value },
                            })
                          }
                          placeholder="Text to replace with..."
                        />
                      </div>
                    </div>
                  )}
                </div>
              )}

              {selectedNode.data.type === 'CONDITION' && (
                <div className="d-flex flex-column gap-3">
                  {/* Condition Overview Banner */}
                  <div className="alert alert-warning border border-warning-subtle small py-2 px-3 mb-0">
                    <div className="d-flex align-items-center gap-1 fw-bold text-warning-emphasis mb-1">
                      <i className="bi bi-signpost-split-fill"></i>
                      <span>Condition Branching Node</span>
                    </div>
                    <p className="mb-0 text-secondary" style={{ fontSize: '0.72rem' }}>
                      Evaluates the left operand against the right operand using the selected operator. Routes execution downstream to the <strong>TRUE</strong> or <strong>FALSE</strong> handle.
                    </p>
                  </div>

                  {/* 1. Left Operand */}
                  <div>
                    <div className="d-flex align-items-center justify-content-between mb-1">
                      <label htmlFor="condition-left-operand" className="form-label small fw-bold text-secondary mb-0">
                        Left Operand
                      </label>
                      <span className="badge bg-warning-subtle text-warning-emphasis border border-warning-subtle" style={{ fontSize: '0.65rem' }}>
                        Required
                      </span>
                    </div>
                    <input
                      id="condition-left-operand"
                      type="text"
                      className="form-control form-control-sm font-monospace"
                      value={selectedNode.data.config?.leftOperand ?? ''}
                      onChange={(e) =>
                        updateSelectedNodeData({
                          config: { leftOperand: e.target.value },
                        })
                      }
                      placeholder="e.g. {{input.score}} or {{variables.minScore}}"
                    />
                    <small className="text-muted d-block mt-1" style={{ fontSize: '0.7rem' }}>
                      Value, number, string, or variable reference (<code>{'{{input.field}}'}</code>, <code>{'{{variables.name}}'}</code>, <code>{'{{nodes.id.field}}'}</code>).
                    </small>

                    {/* Quick Insert Helpers for Left Operand */}
                    <div className="bg-light p-2 rounded-2 border mt-2">
                      <div className="d-flex align-items-center justify-content-between mb-1">
                        <span className="small fw-bold text-secondary" style={{ fontSize: '0.7rem' }}>
                          <i className="bi bi-braces-asterisk text-primary me-1"></i>
                          Quick-Insert Variable Reference
                        </span>
                        <button
                          type="button"
                          className="btn btn-link btn-sm p-0 text-primary small text-decoration-none"
                          style={{ fontSize: '0.68rem' }}
                          onClick={() => setActiveSideTab('variables')}
                        >
                          Variables &rarr;
                        </button>
                      </div>

                      {/* Common Input Templates */}
                      <div className="d-flex flex-wrap gap-1 mb-1">
                        {['{{input.score}}', '{{input.text}}', '{{input.status}}'].map((item) => (
                          <button
                            key={item}
                            type="button"
                            className="badge bg-white text-dark border text-decoration-none btn btn-sm py-1 px-2"
                            style={{ fontSize: '0.68rem', cursor: 'pointer' }}
                            title={`Insert ${item} into left operand`}
                            onClick={() =>
                              updateSelectedNodeData({
                                config: { leftOperand: item },
                              })
                            }
                          >
                            + {item}
                          </button>
                        ))}
                      </div>

                      {/* Upstream Node Output References if connected */}
                      {selectedNodeIncomingNodes.length > 0 && (
                        <div className="d-flex flex-wrap gap-1 mb-1">
                          {selectedNodeIncomingNodes.map((srcNode) => (
                            <button
                              key={srcNode.id}
                              type="button"
                              className="badge bg-white text-info-emphasis border border-info-subtle text-decoration-none btn btn-sm py-1 px-2"
                              style={{ fontSize: '0.68rem', cursor: 'pointer' }}
                              title={`Insert output from ${srcNode.data?.name || srcNode.id}`}
                              onClick={() => {
                                const ref = `{{nodes.${srcNode.id}.result}}`;
                                updateSelectedNodeData({
                                  config: { leftOperand: ref },
                                });
                              }}
                            >
                              + {`{{nodes.${srcNode.id}.result}}`}
                            </button>
                          ))}
                        </div>
                      )}

                      {/* Defined Workflow Variables */}
                      {variables.length > 0 ? (
                        <div className="d-flex flex-wrap gap-1">
                          {variables
                            .filter((v) => v.key && v.key.trim())
                            .map((v) => (
                              <button
                                key={v.id}
                                type="button"
                                className="badge bg-white text-primary border text-decoration-none btn btn-sm py-1 px-2"
                                style={{ fontSize: '0.68rem', cursor: 'pointer' }}
                                title={`Insert {{variables.${v.key.trim()}}} into left operand`}
                                onClick={() =>
                                  updateSelectedNodeData({
                                    config: { leftOperand: `{{variables.${v.key.trim()}}}` },
                                  })
                                }
                              >
                                + {`{{variables.${v.key.trim()}}}`}
                              </button>
                            ))}
                        </div>
                      ) : (
                        <div className="text-muted" style={{ fontSize: '0.68rem' }}>
                          No workflow variables defined.{' '}
                          <button
                            type="button"
                            className="btn btn-link btn-sm p-0 text-primary text-decoration-none"
                            style={{ fontSize: '0.68rem' }}
                            onClick={() => setActiveSideTab('variables')}
                          >
                            Add one
                          </button>
                        </div>
                      )}
                    </div>
                  </div>

                  {/* 2. Operator Dropdown */}
                  <div>
                    <label htmlFor="condition-operator-select" className="form-label small fw-bold text-secondary mb-1">
                      Operator
                    </label>
                    <select
                      id="condition-operator-select"
                      className="form-select form-select-sm"
                      value={selectedNode.data.config?.operator || 'GREATER_THAN'}
                      onChange={(e) =>
                        updateSelectedNodeData({
                          config: { operator: e.target.value },
                        })
                      }
                    >
                      <optgroup label="Comparison">
                        <option value="EQUALS">EQUALS (==)</option>
                        <option value="NOT_EQUALS">NOT EQUALS (!=)</option>
                      </optgroup>
                      <optgroup label="Numeric Comparison">
                        <option value="GREATER_THAN">GREATER THAN (&gt;)</option>
                        <option value="GREATER_THAN_OR_EQUAL">GREATER THAN OR EQUAL (&gt;=)</option>
                        <option value="LESS_THAN">LESS THAN (&lt;)</option>
                        <option value="LESS_THAN_OR_EQUAL">LESS THAN OR EQUAL (&lt;=)</option>
                      </optgroup>
                      <optgroup label="Text &amp; Array">
                        <option value="CONTAINS">CONTAINS</option>
                        <option value="NOT_CONTAINS">NOT CONTAINS</option>
                      </optgroup>
                      <optgroup label="Emptiness (Unary)">
                        <option value="IS_EMPTY">IS EMPTY (Unary)</option>
                        <option value="IS_NOT_EMPTY">IS NOT EMPTY (Unary)</option>
                      </optgroup>
                    </select>

                    {/* Operator Explanation Card */}
                    {(() => {
                      const curOp = selectedNode.data.config?.operator || 'GREATER_THAN';
                      const opInfo = CONDITION_OPERATORS.find((o) => o.value === curOp);
                      return opInfo ? (
                        <div className="bg-light p-2 rounded-2 border mt-2" style={{ fontSize: '0.72rem' }}>
                          <div className="fw-semibold text-dark mb-1">
                            <i className="bi bi-info-circle text-primary me-1"></i>
                            Operator: <code>{opInfo.value}</code>
                          </div>
                          <div className="text-secondary">{opInfo.description}</div>
                        </div>
                      ) : null;
                    })()}
                  </div>

                  {/* 3. Right Operand (Hidden/Disabled for Unary Operators) */}
                  {(() => {
                    const curOp = selectedNode.data.config?.operator || 'GREATER_THAN';
                    const isUnary = curOp === 'IS_EMPTY' || curOp === 'IS_NOT_EMPTY';

                    if (isUnary) {
                      return (
                        <div className="card bg-light border p-2 rounded-2 text-muted" style={{ fontSize: '0.72rem' }}>
                          <div className="fw-semibold text-secondary mb-1">
                            <i className="bi bi-check2-circle text-success me-1"></i>
                            Unary Operator Active
                          </div>
                          <div>
                            <code>{curOp}</code> inspects only the <strong>Left Operand</strong>. No Right Operand is required or evaluated.
                          </div>
                        </div>
                      );
                    }

                    return (
                      <div>
                        <div className="d-flex align-items-center justify-content-between mb-1">
                          <label htmlFor="condition-right-operand" className="form-label small fw-bold text-secondary mb-0">
                            Right Operand
                          </label>
                          <span className="badge bg-warning-subtle text-warning-emphasis border border-warning-subtle" style={{ fontSize: '0.65rem' }}>
                            Required for binary
                          </span>
                        </div>
                        <input
                          id="condition-right-operand"
                          type="text"
                          className="form-control form-control-sm font-monospace"
                          value={selectedNode.data.config?.rightOperand ?? ''}
                          onChange={(e) =>
                            updateSelectedNodeData({
                              config: { rightOperand: e.target.value },
                            })
                          }
                          placeholder="e.g. 75, true, or {{variables.threshold}}"
                        />
                        <small className="text-muted d-block mt-1" style={{ fontSize: '0.7rem' }}>
                          Target value or variable to compare against Left Operand.
                        </small>

                        {/* Quick Insert Helpers for Right Operand */}
                        {variables.length > 0 && (
                          <div className="bg-light p-2 rounded-2 border mt-2">
                            <div className="d-flex align-items-center justify-content-between mb-1">
                              <span className="small fw-bold text-secondary" style={{ fontSize: '0.7rem' }}>
                                <i className="bi bi-braces-asterisk text-primary me-1"></i>
                                Insert Variable into Right Operand
                              </span>
                            </div>
                            <div className="d-flex flex-wrap gap-1">
                              {variables
                                .filter((v) => v.key && v.key.trim())
                                .map((v) => (
                                  <button
                                    key={v.id}
                                    type="button"
                                    className="badge bg-white text-primary border text-decoration-none btn btn-sm py-1 px-2"
                                    style={{ fontSize: '0.68rem', cursor: 'pointer' }}
                                    title={`Insert {{variables.${v.key.trim()}}} into right operand`}
                                    onClick={() =>
                                      updateSelectedNodeData({
                                        config: { rightOperand: `{{variables.${v.key.trim()}}}` },
                                      })
                                    }
                                  >
                                    + {`{{variables.${v.key.trim()}}}`}
                                  </button>
                                ))}
                            </div>
                          </div>
                        )}
                      </div>
                    );
                  })()}

                  {/* 4. Branch Routing Guide */}
                  <div className="card bg-light border p-2 rounded-2">
                    <div className="fw-bold small text-dark mb-1 d-flex align-items-center gap-1">
                      <i className="bi bi-signpost-split text-warning"></i>
                      <span>Branch Output Routing:</span>
                    </div>
                    <div className="d-flex flex-column gap-1 small" style={{ fontSize: '0.72rem' }}>
                      <div className="d-flex align-items-center gap-2">
                        <span className="badge bg-success text-white px-2 py-0">TRUE</span>
                        <span className="text-secondary">Upper green handle: executes downstream steps when condition is met.</span>
                      </div>
                      <div className="d-flex align-items-center gap-2">
                        <span className="badge bg-danger text-white px-2 py-0">FALSE</span>
                        <span className="text-secondary">Lower red handle: executes downstream steps when condition is not met.</span>
                      </div>
                    </div>
                    <div className="text-muted mt-2" style={{ fontSize: '0.68rem' }}>
                      <i className="bi bi-slash-circle me-1 text-secondary"></i>
                      Nodes on the branch not taken will be skipped with <code>SKIPPED</code> status.
                    </div>
                  </div>
                </div>
              )}

              {selectedNode.data.type === 'AI_TEXT_PROCESSOR' && (
                <div className="d-flex flex-column gap-3">
                  {/* 1. AI Instruction / Prompt */}
                  <div>
                    <div className="d-flex align-items-center justify-content-between mb-1">
                      <label htmlFor="ai-prompt-field" className="form-label small fw-bold text-secondary mb-0">
                        AI Instruction / Prompt
                      </label>
                      <span className="badge bg-info-subtle text-info-emphasis border border-info-subtle" style={{ fontSize: '0.65rem' }}>
                        Required if no upstream text
                      </span>
                    </div>
                    <textarea
                      id="ai-prompt-field"
                      className="form-control form-control-sm font-monospace"
                      rows={4}
                      value={selectedNode.data.config?.prompt || ''}
                      onChange={(e) =>
                        updateSelectedNodeData({
                          config: { prompt: e.target.value },
                        })
                      }
                      placeholder="e.g. Explain the supplied Java concept in simple terms. Include a short example when appropriate."
                    ></textarea>
                    <small className="text-muted d-block mt-1" style={{ fontSize: '0.7rem' }}>
                      Instructions for the AI agent. Combines automatically with connected upstream inputs.
                    </small>

                    {/* Workflow Variables Quick Insert */}
                    <div className="bg-light p-2 rounded-2 border mt-2">
                      <div className="d-flex align-items-center justify-content-between mb-1">
                        <span className="small fw-bold text-secondary" style={{ fontSize: '0.7rem' }}>
                          <i className="bi bi-braces-asterisk text-primary me-1"></i>
                          Workflow Variables
                        </span>
                        <button
                          type="button"
                          className="btn btn-link btn-sm p-0 text-primary small text-decoration-none"
                          style={{ fontSize: '0.68rem' }}
                          onClick={() => setActiveSideTab('variables')}
                        >
                          Manage &rarr;
                        </button>
                      </div>
                      {variables.length > 0 ? (
                        <div className="d-flex flex-wrap gap-1">
                          {variables
                            .filter((v) => v.key && v.key.trim())
                            .map((v) => (
                              <button
                                key={v.id}
                                type="button"
                                className="badge bg-white text-primary border text-decoration-none btn btn-sm py-1 px-2"
                                style={{ fontSize: '0.68rem', cursor: 'pointer' }}
                                title={`Insert {{variables.${v.key.trim()}}} into prompt`}
                                onClick={() => {
                                  const tag = `{{variables.${v.key.trim()}}}`;
                                  const cur = selectedNode.data.config?.prompt || '';
                                  updateSelectedNodeData({
                                    config: { prompt: cur ? `${cur} ${tag}` : tag },
                                  });
                                }}
                              >
                                + {`{{variables.${v.key.trim()}}}`}
                              </button>
                            ))}
                        </div>
                      ) : (
                        <div className="text-muted" style={{ fontSize: '0.68rem' }}>
                          No variables defined yet. Use <code>{'{{variables.name}}'}</code> or{' '}
                          <button
                            type="button"
                            className="btn btn-link btn-sm p-0 text-primary text-decoration-none"
                            style={{ fontSize: '0.68rem' }}
                            onClick={() => setActiveSideTab('variables')}
                          >
                            add one now
                          </button>.
                        </div>
                      )}
                    </div>
                  </div>

                  {/* 2. Upstream Input Description */}
                  <div>
                    <label className="form-label small fw-bold text-secondary mb-1 d-flex align-items-center gap-1">
                      <i className="bi bi-box-arrow-in-right text-primary"></i>
                      <span>Upstream Input</span>
                    </label>
                    {selectedNodeIncomingNodes.length > 0 ? (
                      <div className="bg-light p-2 rounded-2 border">
                        <div className="d-flex align-items-center justify-content-between mb-1">
                          <span className="small text-muted" style={{ fontSize: '0.72rem' }}>
                            Connected upstream node{selectedNodeIncomingNodes.length > 1 ? 's' : ''}:
                          </span>
                          <span className="badge bg-primary-subtle text-primary border" style={{ fontSize: '0.65rem' }}>
                            {selectedNodeIncomingNodes.length} source{selectedNodeIncomingNodes.length > 1 ? 's' : ''}
                          </span>
                        </div>
                        <div className="d-flex flex-column gap-1">
                          {selectedNodeIncomingNodes.map((srcNode) => (
                            <div key={srcNode.id} className="d-flex align-items-center justify-content-between bg-white px-2 py-1 rounded border small">
                              <span className="fw-semibold text-dark text-truncate" style={{ maxWidth: '140px' }}>
                                {srcNode.data?.name || srcNode.id}
                              </span>
                              <span className="badge bg-secondary-subtle text-secondary font-monospace" style={{ fontSize: '0.65rem' }}>
                                {srcNode.data?.type}
                              </span>
                            </div>
                          ))}
                        </div>
                        <small className="text-muted d-block mt-2" style={{ fontSize: '0.7rem' }}>
                          <i className="bi bi-info-circle me-1 text-primary"></i>
                          The output text from {selectedNodeIncomingNodes.map((n) => `"${n.data?.name || n.id}"`).join(', ')} will be forwarded to this AI node.
                        </small>
                      </div>
                    ) : (
                      <div className="alert alert-light border small text-muted py-2 px-3 mb-0">
                        <div className="d-flex align-items-center gap-1 text-secondary fw-semibold mb-1">
                          <i className="bi bi-info-circle text-info"></i>
                          <span>Prompt-Only Mode:</span>
                        </div>
                        <p className="mb-0" style={{ fontSize: '0.72rem' }}>
                          No upstream node is connected. This node will run using only the configured instruction above.
                        </p>
                      </div>
                    )}
                  </div>

                  {/* 3. Optional Model Override */}
                  <div>
                    <label htmlFor="ai-model-field" className="form-label small fw-bold text-secondary mb-1 d-flex align-items-center justify-content-between">
                      <span>Ollama Model (Optional)</span>
                      <span className="badge bg-light text-muted border" style={{ fontSize: '0.65rem' }}>
                        Default: qwen2.5:3b
                      </span>
                    </label>
                    <div className="input-group input-group-sm">
                      <span className="input-group-text bg-white text-muted">
                        <i className="bi bi-cpu"></i>
                      </span>
                      <input
                        id="ai-model-field"
                        type="text"
                        className="form-control form-control-sm font-monospace"
                        value={selectedNode.data.config?.model || ''}
                        onChange={(e) =>
                          updateSelectedNodeData({
                            config: { model: e.target.value },
                          })
                        }
                        placeholder="qwen2.5:3b"
                      />
                    </div>
                    <small className="text-muted d-block mt-1" style={{ fontSize: '0.7rem' }}>
                      Leave blank to use the configured local model (<code>qwen2.5:3b</code>).
                    </small>
                  </div>

                  {/* 4. Prompt & Input Combination Explanation */}
                  <div className="card bg-info-subtle border-info-subtle p-2 rounded-2">
                    <div className="d-flex align-items-center gap-1 text-info-emphasis small fw-bold mb-1">
                      <i className="bi bi-diagram-3-fill"></i>
                      <span>How Ollama Processes This Step:</span>
                    </div>
                    <p className="small mb-1 text-dark" style={{ fontSize: '0.72rem' }}>
                      {selectedNodeIncomingNodes.length > 0
                        ? 'Instruction and upstream output are combined in this predictable structure:'
                        : 'In prompt-only mode, your instruction is sent directly to Ollama:'}
                    </p>
                    <div className="p-2 bg-white rounded border font-monospace text-secondary" style={{ fontSize: '0.68rem', lineHeight: '1.3' }}>
                      {selectedNodeIncomingNodes.length > 0 ? (
                        <>
                          <div className="text-primary fw-bold">
                            {selectedNode.data.config?.prompt
                              ? `"${selectedNode.data.config.prompt.slice(0, 36)}${selectedNode.data.config.prompt.length > 36 ? '...' : ''}"`
                              : '<Configured Instruction>'}
                          </div>
                          <div className="text-muted my-1 fw-bold">Input:</div>
                          <div className="text-success">&lt;Output text from upstream node&gt;</div>
                        </>
                      ) : (
                        <div className="text-primary fw-bold">
                          {selectedNode.data.config?.prompt
                            ? `"${selectedNode.data.config.prompt.slice(0, 48)}${selectedNode.data.config.prompt.length > 48 ? '...' : ''}"`
                            : '<Configured Instruction>'}
                        </div>
                      )}
                    </div>
                    <small className="text-muted d-block mt-1" style={{ fontSize: '0.68rem' }}>
                      Generated response is passed downstream in both <code>text</code> and <code>result</code> fields.
                    </small>
                  </div>
                </div>
              )}

              {selectedNode.data.type === 'MANUAL_TRIGGER' && (
                <div className="alert alert-light border small text-muted mb-0">
                  <i className="bi bi-info-circle text-warning me-1"></i>
                  The <strong>Manual Trigger</strong> is the entry point of your pipeline. When executed, execution begins at this node.
                </div>
              )}

              {selectedNode.data.type === 'OUTPUT' && (
                <div className="alert alert-light border small text-muted mb-0">
                  <i className="bi bi-info-circle text-success me-1"></i>
                  The <strong>Output</strong> node captures the final result of your pipeline chain and saves it into the database record.
                </div>
              )}

              {/* Action Buttons */}
              <div className="pt-2 border-top mt-auto d-flex gap-2">
                <button
                  className="btn btn-outline-danger btn-sm w-100 d-flex align-items-center justify-content-center gap-1"
                  onClick={deleteSelectedNode}
                >
                  <i className="bi bi-trash"></i>
                  Delete Node
                </button>
              </div>
            </div>
          ) : (
            <div className="text-center text-muted my-auto py-5">
              <i className="bi bi-mouse2 fs-2 d-block mb-2 text-secondary opacity-50"></i>
              <h6 className="fw-semibold small text-dark mb-1">No Node Selected</h6>
              <p className="small mb-3" style={{ fontSize: '0.75rem' }}>
                Click on any node on the canvas to configure its parameters and options.
              </p>
              <button
                type="button"
                className="btn btn-outline-primary btn-sm px-3"
                style={{ fontSize: '0.75rem' }}
                onClick={() => setActiveSideTab('variables')}
              >
                <i className="bi bi-braces-asterisk me-1"></i>
                Manage Workflow Variables ({variables.length})
              </button>
            </div>
          )}
        </aside>
      </div>

      {/* Execution Confirmation & Edge Case Modal */}
      {showExecutePrompt && (
        <div
          className="position-fixed top-0 start-0 w-100 h-100 d-flex align-items-center justify-content-center"
          style={{ backgroundColor: 'rgba(0, 0, 0, 0.5)', zIndex: 1060 }}
        >
          <div className="card shadow-lg border-0 rounded-4 p-4 bg-white" style={{ maxWidth: '480px', width: '90%' }}>
            <div className="d-flex align-items-start gap-3 mb-3">
              <div
                className={`rounded-circle p-2 d-flex align-items-center justify-content-center flex-shrink-0 ${
                  showExecutePrompt.type === 'inactive'
                    ? 'bg-warning-subtle text-warning'
                    : showExecutePrompt.type === 'unsaved'
                    ? 'bg-primary-subtle text-primary'
                    : 'bg-info-subtle text-info'
                }`}
                style={{ width: '42px', height: '42px' }}
              >
                <i
                  className={`bi ${
                    showExecutePrompt.type === 'inactive'
                      ? 'bi-dash-circle fs-4'
                      : showExecutePrompt.type === 'unsaved'
                      ? 'bi-exclamation-diamond fs-4'
                      : 'bi-floppy fs-4'
                  }`}
                ></i>
              </div>
              <div className="flex-grow-1">
                <h6 className="fw-bold mb-1 text-dark">{showExecutePrompt.title}</h6>
                <p className="text-muted small mb-0">{showExecutePrompt.message}</p>
              </div>
            </div>

            <div className="d-flex align-items-center justify-content-end gap-2 pt-3 border-top">
              <button
                type="button"
                className="btn btn-outline-secondary btn-sm px-3"
                onClick={() => setShowExecutePrompt(null)}
              >
                Cancel
              </button>

              {showExecutePrompt.type === 'unSavedWorkflow' && (
                <button
                  type="button"
                  className="btn btn-primary btn-sm px-3"
                  onClick={() => {
                    setShowExecutePrompt(null);
                    handleSaveWorkflow();
                  }}
                >
                  <i className="bi bi-floppy me-1"></i>
                  Save Workflow Now
                </button>
              )}

              {showExecutePrompt.type === 'inactive' && (
                <button
                  type="button"
                  className="btn btn-success btn-sm px-3"
                  onClick={async () => {
                    setShowExecutePrompt(null);
                    try {
                      await setWorkflowActive(loadedWorkflowId, true);
                      setIsActive(true);
                      runExecution(loadedWorkflowId);
                    } catch (err) {
                      setNotification({
                        type: 'danger',
                        message: `Failed to activate workflow: ${getErrorMessage(err)}`,
                      });
                    }
                  }}
                >
                  <i className="bi bi-check-circle me-1"></i>
                  Activate &amp; Execute
                </button>
              )}

              {showExecutePrompt.type === 'unsaved' && (
                <>
                  <button
                    type="button"
                    className="btn btn-outline-primary btn-sm px-3"
                    onClick={() => {
                      setShowExecutePrompt(null);
                      runExecution(loadedWorkflowId);
                    }}
                    title="Run the definition currently saved in MySQL"
                  >
                    Execute Saved Version
                  </button>
                  <button
                    type="button"
                    className="btn btn-primary btn-sm px-3"
                    onClick={() => {
                      setShowExecutePrompt(null);
                      handleSaveWorkflow(runExecution);
                    }}
                    title="Save current canvas changes to MySQL, then execute"
                  >
                    <i className="bi bi-floppy me-1"></i>
                    Save &amp; Execute
                  </button>
                </>
              )}
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

// Wrapper with ReactFlowProvider so hooks like screenToFlowPosition work properly
export default function WorkflowBuilder() {
  return (
    <ReactFlowProvider>
      <WorkflowCanvasInner />
    </ReactFlowProvider>
  );
}
