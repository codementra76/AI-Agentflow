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

/**
 * Get schedule for a specific workflow
 * Backend returns: { success: true, message: "...", data: WorkflowScheduleResponse }
 */
export const getWorkflowSchedule = async (workflowId) => {
  const response = await api.get(`/workflows/${workflowId}/schedule`);
  return response.data;
};

/**
 * Save or update schedule for a specific workflow
 * Payload: { scheduleType, scheduledTime, daysOfWeek, oneTimeExecutionAt, timeZone, inputData, enabled }
 * Backend returns: { success: true, message: "...", data: WorkflowScheduleResponse }
 */
export const saveWorkflowSchedule = async (workflowId, scheduleData) => {
  const response = await api.put(`/workflows/${workflowId}/schedule`, scheduleData);
  return response.data;
};

/**
 * Update schedule status (enabled: true/false)
 * Payload: { enabled: boolean }
 * Backend returns: { success: true, message: "...", data: WorkflowScheduleResponse }
 */
export const updateScheduleStatus = async (workflowId, enabled) => {
  const response = await api.patch(`/workflows/${workflowId}/schedule/status`, { enabled });
  return response.data;
};

/**
 * Delete schedule for a workflow
 * Backend returns 204 No Content
 */
export const deleteWorkflowSchedule = async (workflowId) => {
  const response = await api.delete(`/workflows/${workflowId}/schedule`);
  return response.data;
};

// ==========================================
// Job Search Assistant APIs
// ==========================================

/**
 * Get candidate profile
 * If id is provided, fetches /candidate-profile/{id}, otherwise fetches current /candidate-profile
 * Backend returns: { success: true, message: "...", data: CandidateProfileResponse }
 */
export const getCandidateProfile = async (id = null) => {
  const url = id ? `/candidate-profile/${id}` : '/candidate-profile';
  const response = await api.get(url);
  return response.data;
};

/**
 * Create candidate profile
 * Payload: { fullName, email, phone, headline, location, portfolioUrl, linkedinUrl, githubUrl, summary }
 * Backend returns: { success: true, message: "...", data: CandidateProfileResponse }
 */
export const createCandidateProfile = async (data) => {
  const response = await api.post('/candidate-profile', data);
  return response.data;
};

/**
 * Update candidate profile
 * Payload: { fullName, email, phone, headline, location, portfolioUrl, linkedinUrl, githubUrl, summary }
 * Backend returns: { success: true, message: "...", data: CandidateProfileResponse }
 */
export const updateCandidateProfile = async (id, data) => {
  const response = await api.put(`/candidate-profile/${id}`, data);
  return response.data;
};

/**
 * Get candidate resume versions
 * Backend returns: { success: true, message: "...", data: [CandidateResumeResponse, ...] }
 */
export const getCandidateResumes = async (profileId = null) => {
  const params = profileId !== null && profileId !== undefined ? { profileId } : {};
  const response = await api.get('/candidate-profile/resumes', { params });
  return response.data;
};

/**
 * Get candidate primary resume version
 * Backend returns: { success: true, message: "...", data: CandidateResumeResponse }
 */
export const getPrimaryResume = async (profileId = null) => {
  const params = profileId !== null && profileId !== undefined ? { profileId } : {};
  const response = await api.get('/candidate-profile/resumes/primary', { params });
  return response.data;
};

/**
 * Get resume details by ID
 * Backend returns: { success: true, message: "...", data: CandidateResumeResponse }
 */
export const getResumeById = async (id) => {
  const response = await api.get(`/candidate-profile/resumes/${id}`);
  return response.data;
};

/**
 * Create a new resume version for candidate profile
 * Payload: { versionName, rawText, isPrimary?, parsedSkills? }
 * Query params: profileId (optional)
 * Backend returns: { success: true, message: "...", data: CandidateResumeResponse }
 */
export const createResume = async (data, profileId = null) => {
  const params = profileId !== null && profileId !== undefined ? { profileId } : {};
  const response = await api.post('/candidate-profile/resumes', data, { params });
  return response.data;
};

/**
 * Update an existing resume version
 * Payload: { versionName, rawText, isPrimary?, parsedSkills? }
 * Backend returns: { success: true, message: "...", data: CandidateResumeResponse }
 */
export const updateResume = async (id, data) => {
  const response = await api.put(`/candidate-profile/resumes/${id}`, data);
  return response.data;
};

/**
 * Designate a specific resume version as primary
 * Backend returns: { success: true, message: "...", data: CandidateResumeResponse }
 */
export const setPrimaryResume = async (id) => {
  const response = await api.put(`/candidate-profile/resumes/${id}/primary`);
  return response.data;
};

/**
 * Delete a resume version
 * Backend returns: 204 No Content
 */
export const deleteResume = async (id) => {
  const response = await api.delete(`/candidate-profile/resumes/${id}`);
  return response.data;
};

/**
 * Get candidate job preferences
 * Backend returns: { success: true, message: "...", data: CandidateJobPreferenceResponse }
 */
export const getJobPreferences = async (profileId = null) => {
  const params = profileId !== null && profileId !== undefined ? { profileId } : {};
  const response = await api.get('/candidate-profile/preferences', { params });
  return response.data;
};

/**
 * Save or update candidate job preferences
 * Payload: { targetTitles, preferredLocations, workplaceTypes, employmentTypes, minSalary, currency }
 * Backend returns: { success: true, message: "...", data: CandidateJobPreferenceResponse }
 */
export const saveJobPreferences = async (data, profileId = null) => {
  const params = profileId !== null && profileId !== undefined ? { profileId } : {};
  const response = await api.put('/candidate-profile/preferences', data, { params });
  return response.data;
};

/**
 * Search job listings with pagination and filters
 * Params: { search, workplaceType, employmentType, page, size, sort }
 * Backend returns: { success: true, message: "...", data: Page<JobListingResponse> }
 */
export const searchJobListings = async (params = {}) => {
  const response = await api.get('/jobs', { params });
  return response.data;
};

/**
 * Get job listing details by ID
 * Backend returns: { success: true, message: "...", data: JobListingResponse }
 */
export const getJobListingById = async (id) => {
  const response = await api.get(`/jobs/${id}`);
  return response.data;
};

/**
 * Create a new job listing
 * Payload: JobListingCreateRequest { title, company, location, workplaceType, employmentType, salaryRange, source, sourceUrl, rawDescription, parsedRequirements?, initialApplicationStatus? }
 * Backend returns: { success: true, message: "...", data: JobListingSaveResult }
 */
export const createJobListing = async (data) => {
  const response = await api.post('/jobs', data);
  return response.data;
};

/**
 * Update an existing job listing
 * Payload: JobListingUpdateRequest { title, company, location, workplaceType, employmentType, salaryRange, source, sourceUrl, rawDescription, parsedRequirements? }
 * Backend returns: { success: true, message: "...", data: JobListingResponse }
 */
export const updateJobListing = async (id, data) => {
  const response = await api.put(`/jobs/${id}`, data);
  return response.data;
};

/**
 * Delete a job listing
 * Backend returns: 204 No Content
 */
export const deleteJobListing = async (id) => {
  const response = await api.delete(`/jobs/${id}`);
  return response.data;
};

/**
 * Get job applications with pagination and status filter
 * Params: { status, page, size, sort }
 * Backend returns: { success: true, message: "...", data: Page<JobApplicationResponse> }
 */
export const getApplications = async (params = {}) => {
  const response = await api.get('/applications', { params });
  return response.data;
};

/**
 * Get application count metrics across statuses
 * Backend returns: { success: true, message: "...", data: JobApplicationCountsResponse }
 */
export const getApplicationStats = async () => {
  const response = await api.get('/applications/stats');
  return response.data;
};

/**
 * Get due follow-up applications
 * Params: { through } (ISO date string YYYY-MM-DD)
 * Backend returns: { success: true, message: "...", data: [JobApplicationResponse, ...] }
 */
export const getDueFollowUps = async (through = null) => {
  const params = through !== null && through !== undefined ? { through } : {};
  const response = await api.get('/applications/follow-ups', { params });
  return response.data;
};

/**
 * Get application details by ID
 * Backend returns: { success: true, message: "...", data: JobApplicationResponse }
 */
export const getApplicationById = async (id) => {
  const response = await api.get(`/applications/${id}`);
  return response.data;
};

/**
 * Get application details associated with a specific job ID
 * Backend returns: { success: true, message: "...", data: JobApplicationResponse }
 */
export const getApplicationByJobId = async (jobId) => {
  const response = await api.get(`/applications/by-job/${jobId}`);
  return response.data;
};

/**
 * Update application status, dates, notes, and associated resume
 * Payload: JobApplicationUpdateRequest { status, appliedDate, followUpDate, notes, resumeId, matchScore?, matchBreakdown? }
 * Backend returns: { success: true, message: "...", data: JobApplicationResponse }
 */
export const updateApplication = async (id, data) => {
  const response = await api.put(`/applications/${id}`, data);
  return response.data;
};

/**
 * Timeout in milliseconds for AI Resume-to-Job Matching endpoint (90s)
 */
export const MATCH_JOB_TIMEOUT_MS = 90000;

/**
 * Analyze resume-to-job match using local AI (Ollama)
 * Payload: optional { resumeId }
 * Backend returns: { success: true, message: "...", data: JobMatchResultResponse }
 */
export const matchJob = async (jobId, data = {}) => {
  const response = await api.post(`/jobs/${jobId}/match`, data, {
    timeout: MATCH_JOB_TIMEOUT_MS,
  });
  return response.data;
};

/**
 * Retrieve previously saved match result for a job listing
 * Backend returns: { success: true, message: "...", data: JobMatchResultResponse }
 */
export const getJobMatchResult = async (jobId) => {
  const response = await api.get(`/jobs/${jobId}/match`);
  return response.data;
};

export default api;


