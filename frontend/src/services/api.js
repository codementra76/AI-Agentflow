import axios from 'axios';

// Configure Axios with relative baseURL so requests use Vite development proxy (/api/v1 -> http://localhost:8085/api/v1)
const api = axios.create({
  baseURL: '/api/v1',
  headers: {
    'Content-Type': 'application/json',
  },
  timeout: 10000,
});

/**
 * Helper to safely extract a user-readable error message from an API response
 */
export const getErrorMessage = (error) => {
  if (error.response?.data?.message) {
    return error.response.data.message;
  }
  if (error.response?.data?.error) {
    return `${error.response.data.error} (Status ${error.response.status})`;
  }
  if (error.message) {
    if (error.code === 'ECONNABORTED') {
      return 'Request timed out connecting to backend server';
    }
    if (error.message.includes('Network Error')) {
      return 'Cannot reach backend server. Please verify the Spring Boot backend is running on http://localhost:8085.';
    }
    return error.message;
  }
  return 'An unexpected error occurred while communicating with the backend';
};

/**
 * Get all workflows (optionally filter by active status: true/false)
 * Backend returns: { success: true, message: "...", data: [WorkflowResponse, ...] }
 */
export const getWorkflows = async (active) => {
  const params = active !== undefined && active !== null ? { active } : {};
  const response = await api.get('/workflows', { params });
  return response.data;
};

/**
 * Get workflow by its ID
 * Backend returns: { success: true, message: "...", data: WorkflowResponse }
 */
export const getWorkflowById = async (id) => {
  const response = await api.get(`/workflows/${id}`);
  return response.data;
};

/**
 * Create a new workflow
 * Payload: { name, description, workflowDefinition, active }
 */
export const createWorkflow = async (data) => {
  const response = await api.post('/workflows', data);
  return response.data;
};

/**
 * Timeout in milliseconds for workflow execution and retry endpoints.
 * Local LLM inference (e.g. Ollama with qwen2.5:3b) typically takes 10-30 seconds.
 * 90 seconds gives the backend enough time to complete processing or return its own
 * 60-second Ollama timeout error before the client aborts.
 */
export const WORKFLOW_EXECUTION_TIMEOUT_MS = 90000;

/**
 * Execute a workflow by its ID
 * Payload: optional { inputData }
 * Backend returns: { success: true, message: "...", data: WorkflowExecutionResponse }
 */
export const executeWorkflow = async (id, inputData = null) => {
  const payload = inputData ? { inputData } : {};
  const response = await api.post(`/workflows/${id}/execute`, payload, {
    timeout: WORKFLOW_EXECUTION_TIMEOUT_MS,
  });
  return response.data;
};

/**
 * Get execution history for a specific workflow
 * Backend returns: { success: true, message: "...", data: [WorkflowExecutionResponse, ...] }
 */
export const getWorkflowExecutions = async (id) => {
  const response = await api.get(`/workflows/${id}/executions`);
  return response.data;
};

/**
 * Get execution details by execution ID (includes nodeLogs)
 * Backend returns: { success: true, message: "...", data: WorkflowExecutionResponse }
 */
export const getExecutionById = async (id) => {
  const response = await api.get(`/executions/${id}`);
  return response.data;
};

/**
 * Get all executions across workflows with optional filtering by workflowId and status
 * Backend returns: { success: true, message: "...", data: [WorkflowExecutionResponse, ...] }
 */
export const getAllExecutions = async (workflowId = null, status = null) => {
  const params = {};
  if (workflowId !== undefined && workflowId !== null && workflowId !== '') {
    params.workflowId = workflowId;
  }
  if (status !== undefined && status !== null && status !== '' && status !== 'ALL') {
    params.status = status;
  }
  const response = await api.get('/executions', { params });
  return response.data;
};

/**
 * Retry a failed execution by its ID
 * Backend creates a new execution row and preserves the original failure
 * Backend returns: { success: true, message: "...", data: WorkflowExecutionResponse }
 */
export const retryExecution = async (id) => {
  const response = await api.post(`/executions/${id}/retry`, {}, {
    timeout: WORKFLOW_EXECUTION_TIMEOUT_MS,
  });
  return response.data;
};

/**
 * Update an existing workflow
 * Payload: { name, description, workflowDefinition, active }
 */
export const updateWorkflow = async (id, data) => {
  const response = await api.put(`/workflows/${id}`, data);
  return response.data;
};

/**
 * Update workflow active status
 * Payload: { active: boolean }
 */
export const setWorkflowActive = async (id, active) => {
  const response = await api.patch(`/workflows/${id}/active`, { active });
  return response.data;
};

/**
 * Delete a workflow by ID
 * Backend returns 204 No Content
 */
export const deleteWorkflow = async (id) => {
  const response = await api.delete(`/workflows/${id}`);
  return response.data;
};

/**
 * Check backend health status
 * Backend returns: { success: true, message: "System healthy", data: { status: "UP", ... } }
 */
export const getHealth = async () => {
  const response = await api.get('/health');
  return response.data;
};

/**
 * Send a chat prompt to the local AI provider (Ollama)
 * Payload: { prompt, model? }
 * Configured with an extended 60-second timeout for local LLM inference
 * Backend returns: { success: true, message: "...", data: { response: "...", model: "..." } }
 */
export const chatWithAi = async (prompt, model = null) => {
  const payload = { prompt };
  if (model) {
    payload.model = model;
  }
  const response = await api.post('/ai/chat', payload, {
    timeout: 60000,
  });
  return response.data;
};

export default api;
