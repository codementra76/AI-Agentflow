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
 * Execute a workflow by its ID
 * Payload: optional { inputData }
 * Backend returns: { success: true, message: "...", data: WorkflowExecutionResponse }
 */
export const executeWorkflow = async (id, inputData = null) => {
  const payload = inputData ? { inputData } : {};
  const response = await api.post(`/workflows/${id}/execute`, payload);
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

export default api;
