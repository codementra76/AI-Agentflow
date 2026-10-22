import React, { useState, useEffect, useCallback } from 'react';
import { Link } from 'react-router-dom';
import {
  getWorkflows,
  setWorkflowActive,
  deleteWorkflow,
  executeWorkflow,
  getAllExecutions,
  retryExecution,
  getHealth,
  getErrorMessage
} from '../services/api';

function Dashboard() {
  const [activeTab, setActiveTab] = useState('workflows'); // 'workflows' | 'history'
  const [workflows, setWorkflows] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [actionMessage, setActionMessage] = useState(null);

  // Backend connection status
  const [backendHealth, setBackendHealth] = useState({ online: false, checking: true, details: null });

  // Execution state (from running a workflow from the table)
  const [executingId, setExecutingId] = useState(null);
  const [executionResult, setExecutionResult] = useState(null);
  const [executionError, setExecutionError] = useState(null);

  // Toggle active state tracker (to show spinner per-row)
  const [togglingId, setTogglingId] = useState(null);
  const [deletingId, setDeletingId] = useState(null);

  // Phase 5: Execution History State
  const [executions, setExecutions] = useState([]);
  const [loadingExecutions, setLoadingExecutions] = useState(false);
  const [workflowFilter, setWorkflowFilter] = useState('');
  const [statusFilter, setStatusFilter] = useState('ALL');
  const [selectedExecution, setSelectedExecution] = useState(null);
  const [retryingId, setRetryingId] = useState(null);

  /**
   * Check backend health status
   */
  const checkBackendHealth = useCallback(async () => {
    try {
      const response = await getHealth();
      if (response && response.success) {
        setBackendHealth({ online: true, checking: false, details: response.data });
      } else {
        setBackendHealth({ online: false, checking: false, details: null });
      }
    } catch {
      setBackendHealth({ online: false, checking: false, details: null });
    }
  }, []);

  /**
   * Load workflows from the backend
   */
  const loadWorkflows = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [workflowRes] = await Promise.all([
        getWorkflows(),
        checkBackendHealth()
      ]);

      if (workflowRes && workflowRes.success && Array.isArray(workflowRes.data)) {
        setWorkflows(workflowRes.data);
      } else {
        setWorkflows([]);
        setError('Received unexpected data format from backend.');
      }
    } catch (err) {
      const msg = getErrorMessage(err);
      setError(msg);
      setWorkflows([]);
    } finally {
      setLoading(false);
    }
  }, [checkBackendHealth]);

  /**
   * Load execution history with optional workflow and status filters
   */
  const loadExecutions = useCallback(async (wfFilter = workflowFilter, stFilter = statusFilter) => {
    setLoadingExecutions(true);
    try {
      const response = await getAllExecutions(
        wfFilter ? parseInt(wfFilter, 10) : null,
        stFilter !== 'ALL' ? stFilter : null
      );
      if (response && response.success && Array.isArray(response.data)) {
        setExecutions(response.data);
      } else {
        setExecutions([]);
      }
    } catch (err) {
      setActionMessage({
        type: 'danger',
        text: `Failed to load execution history: ${getErrorMessage(err)}`
      });
    } finally {
      setLoadingExecutions(false);
    }
  }, [workflowFilter, statusFilter]);

  // Initial load on component mount
  useEffect(() => {
    loadWorkflows();
  }, [loadWorkflows]);

  // Load executions when switching to history tab
  useEffect(() => {
    if (activeTab === 'history') {
      loadExecutions();
    }
  }, [activeTab, loadExecutions]);

  /**
   * Execute a workflow directly from the dashboard
   */
  const handleExecute = async (workflow) => {
    setExecutingId(workflow.id);
    setExecutionResult(null);
    setExecutionError(null);
    setActionMessage(null);

    try {
      const response = await executeWorkflow(workflow.id);
      if (response && response.success && response.data) {
        setExecutionResult(response.data);
        // Refresh executions in background if loaded
        if (executions.length > 0) {
          loadExecutions();
        }
      } else {
        setExecutionError('Execution completed without detailed response data.');
      }
    } catch (err) {
      const msg = getErrorMessage(err);
      setExecutionError(msg);
    } finally {
      setExecutingId(null);
      if (activeTab === 'history' || executions.length > 0) {
        loadExecutions();
      }
    }
  };

  /**
   * Retry a failed execution
   */
  const handleRetryExecution = async (executionId) => {
    setRetryingId(executionId);
    setActionMessage(null);
    try {
      const response = await retryExecution(executionId);
      if (response && response.success && response.data) {
        const newExec = response.data;
        setActionMessage({
          type: newExec.status === 'COMPLETED' ? 'success' : 'warning',
          text: `Execution #${executionId} retried successfully as new Run #${newExec.id} (${newExec.status}). Original failure record preserved.`
        });
        // Reload executions list
        await loadExecutions();
        // Update inspected execution if open
        if (selectedExecution && selectedExecution.id === executionId) {
          setSelectedExecution(newExec);
        }
      } else {
        setActionMessage({
          type: 'danger',
          text: response?.message || `Failed to retry execution #${executionId}.`
        });
      }
    } catch (err) {
      setActionMessage({
        type: 'danger',
        text: `Retry failed: ${getErrorMessage(err)}`
      });
    } finally {
      setRetryingId(null);
    }
  };

  /**
   * Toggle workflow active/inactive
   */
  const handleToggleActive = async (workflow) => {
    const newStatus = !workflow.active;
    setTogglingId(workflow.id);
    setActionMessage(null);

    try {
      const response = await setWorkflowActive(workflow.id, newStatus);
      if (response && response.success) {
        setWorkflows((prev) =>
          prev.map((w) => (w.id === workflow.id ? { ...w, active: newStatus } : w))
        );
        setActionMessage({
          type: 'success',
          text: `Workflow "${workflow.name}" is now ${newStatus ? 'ACTIVE' : 'INACTIVE'}.`
        });
      }
    } catch (err) {
      setActionMessage({
        type: 'danger',
        text: `Failed to update status: ${getErrorMessage(err)}`
      });
    } finally {
      setTogglingId(null);
    }
  };

  /**
   * Delete a workflow with user confirmation
   */
  const handleDelete = async (workflow) => {
    const confirmed = window.confirm(
      `Are you sure you want to delete workflow "${workflow.name}" (ID #${workflow.id})?\n\nThis action cannot be undone and will permanently remove its execution logs.`
    );

    if (!confirmed) return;

    setDeletingId(workflow.id);
    setActionMessage(null);

    try {
      await deleteWorkflow(workflow.id);
      setWorkflows((prev) => prev.filter((w) => w.id !== workflow.id));
      if (executionResult && executionResult.workflowId === workflow.id) {
        setExecutionResult(null);
      }
      setActionMessage({
        type: 'success',
        text: `Workflow "${workflow.name}" was deleted successfully.`
      });
    } catch (err) {
      setActionMessage({
        type: 'danger',
        text: `Failed to delete workflow: ${getErrorMessage(err)}`
      });
    } finally {
      setDeletingId(null);
    }
  };

  /**
   * Helper to format duration between startedAt and completedAt
   */
  const formatDuration = (startedAt, completedAt) => {
    if (!startedAt || !completedAt) return '-';
    const ms = new Date(completedAt) - new Date(startedAt);
    if (ms < 0) return '-';
    if (ms < 1000) return `${ms} ms`;
    return `${(ms / 1000).toFixed(2)} s`;
  };

  /**
   * Helper to format ISO timestamps
   */
  const formatDate = (isoStr) => {
    if (!isoStr) return 'N/A';
    try {
      return new Date(isoStr).toLocaleString(undefined, {
        month: 'short',
        day: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
        second: '2-digit',
      });
    } catch {
      return isoStr;
    }
  };

  // Compute summary metrics for workflows
  const totalCount = workflows.length;
  const activeCount = workflows.filter((w) => w.active).length;
  const inactiveCount = totalCount - activeCount;

  // Compute summary metrics for executions
  const totalExecutionsCount = executions.length;
  const completedExecutionsCount = executions.filter((e) => e.status === 'COMPLETED').length;
  const failedExecutionsCount = executions.filter((e) => e.status === 'FAILED').length;

  return (
    <div className="d-flex flex-column min-vh-100 bg-light">
      {/* Navigation Header */}
      <nav className="navbar navbar-expand-lg navbar-dark bg-dark shadow-sm py-3">
        <div className="container">
          <div className="d-flex align-items-center gap-2">
            <span className="navbar-brand d-flex align-items-center gap-2 fs-4 fw-bold mb-0">
              <i className="bi bi-diagram-3-fill text-primary"></i>
              AgentFlow
            </span>
            <span className="badge bg-secondary-subtle text-light-emphasis border border-secondary-subtle d-none d-sm-inline-block">
              Visual AI Workflow Automation
            </span>
          </div>

          <div className="d-flex align-items-center gap-3">
            {/* Link to AI Assistant */}
            <Link
              to="/chat"
              className="btn btn-outline-info btn-sm d-flex align-items-center gap-2 px-3 fw-medium shadow-sm"
              id="nav-ai-assistant-btn"
              title="Open local AI Assistant"
            >
              <i className="bi bi-robot"></i>
              <span>AI Assistant</span>
            </Link>

            {/* Link to Workflow Builder */}
            <Link
              to="/builder"
              className="btn btn-primary btn-sm d-flex align-items-center gap-2 px-3 fw-medium shadow-sm"
              id="nav-create-workflow-btn"
            >
              <i className="bi bi-plus-circle-fill"></i>
              <span>Workflow Builder</span>
            </Link>

            {/* Backend connection status indicator */}
            {backendHealth.checking ? (
              <span className="badge bg-warning-subtle text-warning border border-warning-subtle d-flex align-items-center gap-1 px-3 py-2">
                <span className="spinner-border spinner-border-sm" role="status"></span>
                Connecting...
              </span>
            ) : backendHealth.online ? (
              <span className="badge bg-success-subtle text-success border border-success-subtle d-flex align-items-center gap-2 px-3 py-2">
                <i className="bi bi-circle-fill text-success" style={{ fontSize: '0.65rem' }}></i>
                Backend: Online (Port 8085)
              </span>
            ) : (
              <span className="badge bg-danger-subtle text-danger border border-danger-subtle d-flex align-items-center gap-2 px-3 py-2">
                <i className="bi bi-circle-fill text-danger" style={{ fontSize: '0.65rem' }}></i>
                Backend: Disconnected
              </span>
            )}

            {/* Refresh Button */}
            <button
              className="btn btn-outline-light btn-sm d-flex align-items-center gap-2 px-3"
              onClick={() => {
                if (activeTab === 'workflows') {
                  loadWorkflows();
                } else {
                  loadExecutions();
                }
              }}
              disabled={loading || loadingExecutions}
              title="Reload current data from backend"
            >
              <i className={`bi bi-arrow-clockwise ${(loading || loadingExecutions) ? 'spin' : ''}`}></i>
              Refresh
            </button>
          </div>
        </div>
      </nav>

      {/* Main Container */}
      <main className="container flex-grow-1 py-4">
        {/* Backend offline warning banner */}
        {!backendHealth.checking && !backendHealth.online && (
          <div className="alert alert-warning border-warning-subtle shadow-sm mb-4 d-flex align-items-start gap-3">
            <i className="bi bi-exclamation-triangle-fill fs-4 text-warning mt-1"></i>
            <div>
              <h6 className="alert-heading fw-bold mb-1">Backend Server Not Detected</h6>
              <p className="mb-1 small">
                Cannot reach the Spring Boot backend at <code>http://localhost:8085</code>.
                Make sure your backend is running:
              </p>
              <code className="text-dark bg-warning-subtle p-1 rounded small">
                cd /d &quot;D:\Spring projects\AI-agentflow\backend&quot; &amp;&amp; mvn spring-boot:run
              </code>
            </div>
          </div>
        )}

        {/* Action feedback message */}
        {actionMessage && (
          <div
            className={`alert alert-${actionMessage.type} alert-dismissible fade show shadow-sm mb-4`}
            role="alert"
          >
            {actionMessage.text}
            <button
              type="button"
              className="btn-close"
              aria-label="Close"
              onClick={() => setActionMessage(null)}
            ></button>
          </div>
        )}

        {/* Dashboard Title & Quick Actions */}
        <div className="d-flex flex-wrap align-items-center justify-content-between mb-4 gap-2">
          <div>
            <h2 className="fw-bold mb-1 text-dark">AgentFlow Dashboard</h2>
            <p className="text-muted small mb-0">
              Live workflow definitions, pipeline execution history, and retry management from <code>agentflow_db1</code>
            </p>
          </div>
          <div className="d-flex align-items-center gap-2">
            <Link
              to="/chat"
              className="btn btn-outline-info d-inline-flex align-items-center gap-2"
              id="header-ai-assistant-btn"
            >
              <i className="bi bi-robot"></i>
              AI Assistant
            </Link>
            <Link
              to="/builder"
              className="btn btn-outline-primary d-inline-flex align-items-center gap-2"
              id="header-create-workflow-btn"
            >
              <i className="bi bi-bezier2"></i>
              Design New Workflow
            </Link>
          </div>
        </div>

        {/* Navigation Tabs: Workflows vs Execution History */}
        <ul className="nav nav-pills mb-4 gap-2">
          <li className="nav-item">
            <button
              className={`btn ${activeTab === 'workflows' ? 'btn-primary shadow-sm' : 'btn-outline-secondary'} d-inline-flex align-items-center gap-2 px-3 fw-medium`}
              onClick={() => setActiveTab('workflows')}
              id="tab-workflows-btn"
            >
              <i className="bi bi-diagram-3-fill"></i>
              <span>Workflows</span>
              <span className={`badge ${activeTab === 'workflows' ? 'bg-light text-primary' : 'bg-secondary-subtle text-secondary'} ms-1`}>
                {workflows.length}
              </span>
            </button>
          </li>
          <li className="nav-item">
            <button
              className={`btn ${activeTab === 'history' ? 'btn-primary shadow-sm' : 'btn-outline-secondary'} d-inline-flex align-items-center gap-2 px-3 fw-medium`}
              onClick={() => {
                setActiveTab('history');
                loadExecutions();
              }}
              id="tab-execution-history-btn"
            >
              <i className="bi bi-clock-history"></i>
              <span>Execution History</span>
              {executions.length > 0 && (
                <span className={`badge ${activeTab === 'history' ? 'bg-light text-primary' : 'bg-secondary-subtle text-secondary'} ms-1`}>
                  {executions.length}
                </span>
              )}
            </button>
          </li>
        </ul>

        {/* ============================================================== */}
        {/* TAB 1: WORKFLOWS VIEW                                          */}
        {/* ============================================================== */}
        {activeTab === 'workflows' && (
          <div>
            {/* Metrics Overview Cards */}
            <div className="row g-3 mb-4">
              <div className="col-sm-6 col-lg-3">
                <div className="card shadow-sm border-0 rounded-3 h-100 p-3 bg-white">
                  <div className="d-flex align-items-center justify-content-between">
                    <div>
                      <span className="text-muted small text-uppercase fw-semibold">Total Workflows</span>
                      <h3 className="fw-bold text-dark mb-0 mt-1">{totalCount}</h3>
                    </div>
                    <div className="bg-primary-subtle text-primary rounded-3 p-3 fs-4">
                      <i className="bi bi-diagram-3"></i>
                    </div>
                  </div>
                </div>
              </div>

              <div className="col-sm-6 col-lg-3">
                <div className="card shadow-sm border-0 rounded-3 h-100 p-3 bg-white">
                  <div className="d-flex align-items-center justify-content-between">
                    <div>
                      <span className="text-muted small text-uppercase fw-semibold">Active Pipelines</span>
                      <h3 className="fw-bold text-success mb-0 mt-1">{activeCount}</h3>
                    </div>
                    <div className="bg-success-subtle text-success rounded-3 p-3 fs-4">
                      <i className="bi bi-check2-circle"></i>
                    </div>
                  </div>
                </div>
              </div>

              <div className="col-sm-6 col-lg-3">
                <div className="card shadow-sm border-0 rounded-3 h-100 p-3 bg-white">
                  <div className="d-flex align-items-center justify-content-between">
                    <div>
                      <span className="text-muted small text-uppercase fw-semibold">Inactive Pipelines</span>
                      <h3 className="fw-bold text-secondary mb-0 mt-1">{inactiveCount}</h3>
                    </div>
                    <div className="bg-secondary-subtle text-secondary rounded-3 p-3 fs-4">
                      <i className="bi bi-pause-circle"></i>
                    </div>
                  </div>
                </div>
              </div>

              <div className="col-sm-6 col-lg-3">
                <div className="card shadow-sm border-0 rounded-3 h-100 p-3 bg-white">
                  <div className="d-flex align-items-center justify-content-between">
                    <div>
                      <span className="text-muted small text-uppercase fw-semibold">Database Schema</span>
                      <h6 className="fw-bold text-dark mb-0 mt-2">agentflow_db1</h6>
                      <span className="badge bg-light text-muted border mt-1">MySQL 9.x</span>
                    </div>
                    <div className="bg-info-subtle text-info rounded-3 p-3 fs-4">
                      <i className="bi bi-database"></i>
                    </div>
                  </div>
                </div>
              </div>
            </div>

            {/* Execution Result Box (Displayed when a run completes from dashboard) */}
            {executionResult && (
              <div className={`card shadow-sm border-0 rounded-3 mb-4 bg-white border-start border-4 ${executionResult.status === 'COMPLETED' ? 'border-success' : 'border-danger'}`}>
                <div className="card-header bg-white py-3 d-flex align-items-center justify-content-between border-bottom">
                  <div className="d-flex align-items-center gap-2">
                    <i className={`bi ${executionResult.status === 'COMPLETED' ? 'bi-check-circle-fill text-success' : 'bi-x-circle-fill text-danger'} fs-5`}></i>
                    <h6 className="fw-bold mb-0 text-dark">
                      Execution #{executionResult.id} Result &mdash; {executionResult.workflowName || 'Workflow'}
                    </h6>
                    <span className={`badge ${executionResult.status === 'COMPLETED' ? 'bg-success' : 'bg-danger'} ms-2`}>
                      {executionResult.status}
                    </span>
                  </div>
                  <div className="d-flex align-items-center gap-2">
                    <button
                      type="button"
                      className="btn btn-outline-primary btn-sm d-flex align-items-center gap-1"
                      onClick={() => setSelectedExecution(executionResult)}
                    >
                      <i className="bi bi-eye"></i>
                      <span>Inspect Details</span>
                    </button>
                    {executionResult.status === 'FAILED' && (
                      <button
                        type="button"
                        className="btn btn-warning btn-sm d-flex align-items-center gap-1"
                        onClick={() => handleRetryExecution(executionResult.id)}
                        disabled={retryingId === executionResult.id}
                      >
                        {retryingId === executionResult.id ? (
                          <span className="spinner-border spinner-border-sm" role="status"></span>
                        ) : (
                          <i className="bi bi-arrow-clockwise"></i>
                        )}
                        <span>Retry</span>
                      </button>
                    )}
                    <button
                      type="button"
                      className="btn-close ms-2"
                      aria-label="Close"
                      onClick={() => setExecutionResult(null)}
                    ></button>
                  </div>
                </div>
                <div className="card-body">
                  {executionResult.status === 'FAILED' && executionResult.errorMessage && (
                    <div className="alert alert-danger py-2 px-3 mb-3 small">
                      <strong>Failure:</strong> {executionResult.errorMessage}
                    </div>
                  )}

                  {/* Output Display */}
                  <div className="mb-3">
                    <label className="form-label text-muted small fw-bold text-uppercase mb-1">
                      Pipeline Output Data:
                    </label>
                    <pre className="bg-light p-3 rounded-3 border text-dark mb-0 font-monospace small">
                      {JSON.stringify(executionResult.outputData, null, 2)}
                    </pre>
                  </div>

                  {/* Step Execution Logs */}
                  {executionResult.nodeLogs && executionResult.nodeLogs.length > 0 && (
                    <div>
                      <label className="form-label text-muted small fw-bold text-uppercase mb-2">
                        Executed Node Trace ({executionResult.nodeLogs.length} steps):
                      </label>
                      <div className="table-responsive">
                        <table className="table table-sm table-bordered align-middle mb-0">
                          <thead className="table-light small">
                            <tr>
                              <th style={{ width: '40px' }}>#</th>
                              <th>Node ID</th>
                              <th>Type</th>
                              <th>Status</th>
                              <th>Output</th>
                            </tr>
                          </thead>
                          <tbody className="small">
                            {executionResult.nodeLogs.map((log, idx) => (
                              <tr key={log.nodeId || idx}>
                                <td className="text-muted fw-bold">{idx + 1}</td>
                                <td>
                                  <code>{log.nodeId}</code>
                                </td>
                                <td>
                                  <span className="badge bg-secondary-subtle text-secondary border">
                                    {log.nodeType}
                                  </span>
                                </td>
                                <td>
                                  <span
                                    className={`badge ${
                                      log.status === 'COMPLETED'
                                        ? 'bg-success-subtle text-success'
                                        : 'bg-danger-subtle text-danger'
                                    } border`}
                                  >
                                    {log.status}
                                  </span>
                                </td>
                                <td>
                                  <span className="font-monospace small text-muted">
                                    {typeof log.outputData?.result === 'string'
                                      ? log.outputData.result
                                      : JSON.stringify(log.outputData)}
                                  </span>
                                </td>
                              </tr>
                            ))}
                          </tbody>
                        </table>
                      </div>
                    </div>
                  )}
                </div>
              </div>
            )}

            {/* Execution Error Box */}
            {executionError && (
              <div className="alert alert-danger alert-dismissible fade show shadow-sm mb-4" role="alert">
                <div className="d-flex align-items-center gap-2">
                  <i className="bi bi-x-circle-fill fs-5"></i>
                  <strong>Execution Failed:</strong> {executionError}
                </div>
                <button
                  type="button"
                  className="btn-close"
                  aria-label="Close"
                  onClick={() => setExecutionError(null)}
                ></button>
              </div>
            )}

            {/* General Loading Indicator */}
            {loading && (
              <div className="card shadow-sm border-0 rounded-3 p-5 text-center bg-white my-3">
                <div className="spinner-border text-primary mx-auto mb-3" role="status">
                  <span className="visually-hidden">Loading...</span>
                </div>
                <h6 className="text-secondary fw-semibold">Loading workflows from MySQL database (agentflow_db1)...</h6>
              </div>
            )}

            {/* General Error Message */}
            {!loading && error && (
              <div className="card shadow-sm border-0 rounded-3 p-4 bg-white border-start border-4 border-danger my-3">
                <div className="d-flex align-items-start gap-3">
                  <i className="bi bi-cloud-slash-fill text-danger fs-3 mt-1"></i>
                  <div>
                    <h6 className="text-danger fw-bold mb-1">Failed to Load Workflows</h6>
                    <p className="text-muted small mb-3">{error}</p>
                    <button className="btn btn-outline-danger btn-sm" onClick={loadWorkflows}>
                      <i className="bi bi-arrow-clockwise me-1"></i>
                      Retry Request
                    </button>
                  </div>
                </div>
              </div>
            )}

            {/* Empty State */}
            {!loading && !error && workflows.length === 0 && (
              <div className="card shadow-sm border-0 rounded-3 p-5 text-center bg-white my-3">
                <div className="text-muted mb-3" style={{ fontSize: '3.5rem' }}>
                  <i className="bi bi-inbox"></i>
                </div>
                <h5 className="fw-bold text-dark mb-1">No Workflows Found in MySQL</h5>
                <p className="text-muted small mx-auto" style={{ maxWidth: '460px' }}>
                  Your <code>agentflow_db1</code> database currently has no workflow records.
                  You can create one via Postman or use the Visual Workflow Builder.
                </p>
                <div className="mt-3 d-flex justify-content-center gap-2">
                  <Link to="/builder" className="btn btn-primary btn-sm px-3">
                    <i className="bi bi-bezier2 me-1"></i>
                    Open Workflow Builder
                  </Link>
                  <button className="btn btn-outline-secondary btn-sm px-3" onClick={loadWorkflows}>
                    <i className="bi bi-arrow-clockwise me-1"></i>
                    Check Again
                  </button>
                </div>
              </div>
            )}

            {/* Workflows Table */}
            {!loading && !error && workflows.length > 0 && (
              <div className="card shadow-sm border-0 rounded-3 overflow-hidden bg-white">
                <div className="card-header bg-white py-3 border-bottom d-flex align-items-center justify-content-between">
                  <h6 className="fw-bold mb-0 text-dark">
                    Saved Workflows <span className="badge bg-secondary-subtle text-secondary ms-1">{workflows.length}</span>
                  </h6>
                  <span className="text-muted small">
                    Table: <code>workflows</code>
                  </span>
                </div>
                <div className="table-responsive">
                  <table className="table table-hover align-middle mb-0">
                    <thead className="table-light small text-uppercase">
                      <tr>
                        <th scope="col" style={{ width: '80px' }}>ID</th>
                        <th scope="col">Workflow Name &amp; Description</th>
                        <th scope="col" style={{ width: '130px' }}>Nodes</th>
                        <th scope="col" style={{ width: '130px' }}>Status</th>
                        <th scope="col" style={{ width: '180px' }}>Created</th>
                        <th scope="col" className="text-end" style={{ width: '280px' }}>Actions</th>
                      </tr>
                    </thead>
                    <tbody>
                      {workflows.map((workflow) => {
                        const nodeCount = workflow.workflowDefinition?.nodes?.length || 0;
                        const isExecutingThis = executingId === workflow.id;
                        const isTogglingThis = togglingId === workflow.id;
                        const isDeletingThis = deletingId === workflow.id;

                        return (
                          <tr key={workflow.id}>
                            {/* ID */}
                            <td className="fw-semibold text-muted">
                              #{workflow.id}
                            </td>

                            {/* Name & Description */}
                            <td>
                              <div>
                                <Link
                                  to={`/builder?workflowId=${workflow.id}`}
                                  className="fw-bold text-dark text-decoration-none hover-primary"
                                  title={`Open "${workflow.name}" in visual builder`}
                                >
                                  {workflow.name}
                                </Link>
                              </div>
                              {workflow.description ? (
                                <div className="text-muted small text-truncate" style={{ maxWidth: '380px' }}>
                                  {workflow.description}
                                </div>
                              ) : (
                                <div className="text-muted small fst-italic">No description provided</div>
                              )}
                            </td>

                            {/* Node Count */}
                            <td>
                              <span className="badge bg-primary-subtle text-primary border border-primary-subtle">
                                <i className="bi bi-bezier2 me-1"></i>
                                {nodeCount} {nodeCount === 1 ? 'node' : 'nodes'}
                              </span>
                            </td>

                            {/* Status Toggle Switch */}
                            <td>
                              <button
                                type="button"
                                className={`btn btn-sm ${
                                  workflow.active
                                    ? 'btn-outline-success'
                                    : 'btn-outline-secondary'
                                } d-inline-flex align-items-center gap-1`}
                                onClick={() => handleToggleActive(workflow)}
                                disabled={isTogglingThis}
                                title="Click to toggle active status"
                              >
                                {isTogglingThis ? (
                                  <span className="spinner-border spinner-border-sm" role="status"></span>
                                ) : workflow.active ? (
                                  <>
                                    <i className="bi bi-check-circle-fill"></i>
                                    Active
                                  </>
                                ) : (
                                  <>
                                    <i className="bi bi-dash-circle"></i>
                                    Inactive
                                  </>
                                )}
                              </button>
                            </td>

                            {/* Created Date */}
                            <td className="small text-muted">
                              {workflow.createdAt
                                ? new Date(workflow.createdAt).toLocaleString(undefined, {
                                    month: 'short',
                                    day: 'numeric',
                                    hour: '2-digit',
                                    minute: '2-digit'
                                  })
                                : 'N/A'}
                            </td>

                            {/* Action Buttons */}
                            <td className="text-end">
                              <div className="d-inline-flex align-items-center gap-2">
                                {/* Open in Builder Button */}
                                <Link
                                  to={`/builder?workflowId=${workflow.id}`}
                                  className="btn btn-sm btn-outline-primary d-inline-flex align-items-center gap-1"
                                  title={`Open workflow #${workflow.id} in visual builder`}
                                >
                                  <i className="bi bi-pencil-square"></i>
                                  <span>Open in Builder</span>
                                </Link>

                                {/* Execute Button */}
                                <button
                                  type="button"
                                  className="btn btn-sm btn-primary d-inline-flex align-items-center gap-1"
                                  onClick={() => handleExecute(workflow)}
                                  disabled={isExecutingThis || !workflow.active}
                                  title={workflow.active ? 'Run pipeline' : 'Activate workflow to run'}
                                >
                                  {isExecutingThis ? (
                                    <>
                                      <span className="spinner-border spinner-border-sm" role="status"></span>
                                      <span>Running...</span>
                                    </>
                                  ) : (
                                    <>
                                      <i className="bi bi-play-fill fs-6"></i>
                                      <span>Execute</span>
                                    </>
                                  )}
                                </button>

                                {/* Delete Button */}
                                <button
                                  type="button"
                                  className="btn btn-sm btn-outline-danger d-inline-flex align-items-center"
                                  onClick={() => handleDelete(workflow)}
                                  disabled={isDeletingThis || isExecutingThis}
                                  title="Delete workflow"
                                >
                                  {isDeletingThis ? (
                                    <span className="spinner-border spinner-border-sm" role="status"></span>
                                  ) : (
                                    <i className="bi bi-trash3"></i>
                                  )}
                                </button>
                              </div>
                            </td>
                          </tr>
                        );
                      })}
                    </tbody>
                  </table>
                </div>
              </div>
            )}
          </div>
        )}

        {/* ============================================================== */}
        {/* TAB 2: EXECUTION HISTORY VIEW (Phase 5)                        */}
        {/* ============================================================== */}
        {activeTab === 'history' && (
          <div>
            {/* Execution Metrics Cards */}
            <div className="row g-3 mb-4">
              <div className="col-sm-6 col-lg-4">
                <div className="card shadow-sm border-0 rounded-3 h-100 p-3 bg-white">
                  <div className="d-flex align-items-center justify-content-between">
                    <div>
                      <span className="text-muted small text-uppercase fw-semibold">Total Recorded Runs</span>
                      <h3 className="fw-bold text-dark mb-0 mt-1">{totalExecutionsCount}</h3>
                    </div>
                    <div className="bg-primary-subtle text-primary rounded-3 p-3 fs-4">
                      <i className="bi bi-clock-history"></i>
                    </div>
                  </div>
                </div>
              </div>

              <div className="col-sm-6 col-lg-4">
                <div className="card shadow-sm border-0 rounded-3 h-100 p-3 bg-white">
                  <div className="d-flex align-items-center justify-content-between">
                    <div>
                      <span className="text-muted small text-uppercase fw-semibold">Completed Successfully</span>
                      <h3 className="fw-bold text-success mb-0 mt-1">{completedExecutionsCount}</h3>
                    </div>
                    <div className="bg-success-subtle text-success rounded-3 p-3 fs-4">
                      <i className="bi bi-check-circle-fill"></i>
                    </div>
                  </div>
                </div>
              </div>

              <div className="col-sm-6 col-lg-4">
                <div className="card shadow-sm border-0 rounded-3 h-100 p-3 bg-white">
                  <div className="d-flex align-items-center justify-content-between">
                    <div>
                      <span className="text-muted small text-uppercase fw-semibold">Failed Runs</span>
                      <h3 className="fw-bold text-danger mb-0 mt-1">{failedExecutionsCount}</h3>
                    </div>
                    <div className="bg-danger-subtle text-danger rounded-3 p-3 fs-4">
                      <i className="bi bi-exclamation-octagon-fill"></i>
                    </div>
                  </div>
                </div>
              </div>
            </div>

            {/* Filters Bar */}
            <div className="card shadow-sm border-0 rounded-3 mb-4 bg-white p-3">
              <div className="row g-3 align-items-center">
                {/* Workflow Filter */}
                <div className="col-md-5">
                  <label htmlFor="history-workflow-filter" className="form-label small text-muted fw-bold text-uppercase mb-1">
                    Filter by Workflow:
                  </label>
                  <select
                    id="history-workflow-filter"
                    className="form-select form-select-sm"
                    value={workflowFilter}
                    onChange={(e) => {
                      const val = e.target.value;
                      setWorkflowFilter(val);
                      loadExecutions(val, statusFilter);
                    }}
                  >
                    <option value="">All Workflows</option>
                    {workflows.map((w) => (
                      <option key={w.id} value={w.id}>
                        #{w.id} &mdash; {w.name}
                      </option>
                    ))}
                  </select>
                </div>

                {/* Status Filter */}
                <div className="col-md-4">
                  <label htmlFor="history-status-filter" className="form-label small text-muted fw-bold text-uppercase mb-1">
                    Filter by Status:
                  </label>
                  <select
                    id="history-status-filter"
                    className="form-select form-select-sm"
                    value={statusFilter}
                    onChange={(e) => {
                      const val = e.target.value;
                      setStatusFilter(val);
                      loadExecutions(workflowFilter, val);
                    }}
                  >
                    <option value="ALL">All Statuses (COMPLETED, FAILED, RUNNING)</option>
                    <option value="COMPLETED">COMPLETED only</option>
                    <option value="FAILED">FAILED only</option>
                    <option value="RUNNING">RUNNING only</option>
                  </select>
                </div>

                {/* Actions */}
                <div className="col-md-3 d-flex align-items-end gap-2 pt-md-4">
                  <button
                    className="btn btn-outline-secondary btn-sm d-flex align-items-center gap-1 flex-grow-1 justify-content-center"
                    onClick={() => loadExecutions(workflowFilter, statusFilter)}
                    disabled={loadingExecutions}
                  >
                    <i className={`bi bi-arrow-clockwise ${loadingExecutions ? 'spin' : ''}`}></i>
                    <span>Reload</span>
                  </button>
                  {(workflowFilter || statusFilter !== 'ALL') && (
                    <button
                      className="btn btn-link btn-sm text-secondary text-decoration-none px-2"
                      onClick={() => {
                        setWorkflowFilter('');
                        setStatusFilter('ALL');
                        loadExecutions('', 'ALL');
                      }}
                      title="Clear filters"
                    >
                      Reset
                    </button>
                  )}
                </div>
              </div>
            </div>

            {/* Execution History Table */}
            {loadingExecutions ? (
              <div className="card shadow-sm border-0 rounded-3 p-5 text-center bg-white my-3">
                <div className="spinner-border text-primary mx-auto mb-3" role="status">
                  <span className="visually-hidden">Loading...</span>
                </div>
                <h6 className="text-secondary fw-semibold">Loading execution history from agentflow_db1...</h6>
              </div>
            ) : executions.length === 0 ? (
              <div className="card shadow-sm border-0 rounded-3 p-5 text-center bg-white my-3">
                <div className="text-muted mb-3" style={{ fontSize: '3rem' }}>
                  <i className="bi bi-clock-history"></i>
                </div>
                <h5 className="fw-bold text-dark mb-1">No Executions Found</h5>
                <p className="text-muted small mx-auto" style={{ maxWidth: '460px' }}>
                  {workflowFilter || statusFilter !== 'ALL'
                    ? 'No executions match your active filters. Try clearing the filter options.'
                    : 'No workflow executions have been recorded yet in MySQL.'}
                </p>
                {(workflowFilter || statusFilter !== 'ALL') && (
                  <div className="mt-2">
                    <button
                      className="btn btn-outline-primary btn-sm px-3"
                      onClick={() => {
                        setWorkflowFilter('');
                        setStatusFilter('ALL');
                        loadExecutions('', 'ALL');
                      }}
                    >
                      Clear Filters
                    </button>
                  </div>
                )}
              </div>
            ) : (
              <div className="card shadow-sm border-0 rounded-3 overflow-hidden bg-white">
                <div className="card-header bg-white py-3 border-bottom d-flex align-items-center justify-content-between">
                  <h6 className="fw-bold mb-0 text-dark">
                    Executions <span className="badge bg-secondary-subtle text-secondary ms-1">{executions.length}</span>
                  </h6>
                  <span className="text-muted small">
                    Table: <code>workflow_executions</code>
                  </span>
                </div>
                <div className="table-responsive">
                  <table className="table table-hover align-middle mb-0">
                    <thead className="table-light small text-uppercase">
                      <tr>
                        <th style={{ width: '80px' }}>Run #</th>
                        <th>Workflow</th>
                        <th style={{ width: '130px' }}>Status</th>
                        <th style={{ width: '180px' }}>Started At</th>
                        <th style={{ width: '100px' }}>Duration</th>
                        <th style={{ width: '90px' }}>Steps</th>
                        <th>Output / Failure Summary</th>
                        <th style={{ width: '230px' }} className="text-end">Actions</th>
                      </tr>
                    </thead>
                    <tbody className="small">
                      {executions.map((exec) => {
                        const isRetrying = retryingId === exec.id;
                        const duration = formatDuration(exec.startedAt, exec.completedAt);
                        const stepCount = exec.nodeLogs ? exec.nodeLogs.length : 0;

                        return (
                          <tr key={exec.id}>
                            {/* Run ID */}
                            <td className="fw-bold font-monospace text-muted">
                              #{exec.id}
                            </td>

                            {/* Workflow Name */}
                            <td>
                              <Link
                                to={`/builder?workflowId=${exec.workflowId}`}
                                className="fw-bold text-dark text-decoration-none hover-primary"
                                title={`Open workflow #${exec.workflowId}`}
                              >
                                {exec.workflowName || `Workflow #${exec.workflowId}`}
                              </Link>
                              <div className="text-muted small font-monospace">
                                ID: #{exec.workflowId}
                              </div>
                            </td>

                            {/* Status */}
                            <td>
                              <span
                                className={`badge d-inline-flex align-items-center gap-1 ${
                                  exec.status === 'COMPLETED'
                                    ? 'bg-success-subtle text-success border border-success-subtle'
                                    : exec.status === 'FAILED'
                                    ? 'bg-danger-subtle text-danger border border-danger-subtle'
                                    : 'bg-primary-subtle text-primary border border-primary-subtle'
                                }`}
                              >
                                {exec.status === 'COMPLETED' && <i className="bi bi-check-circle-fill"></i>}
                                {exec.status === 'FAILED' && <i className="bi bi-x-circle-fill"></i>}
                                {exec.status === 'RUNNING' && <span className="spinner-border spinner-border-sm" role="status"></span>}
                                {exec.status}
                              </span>
                            </td>

                            {/* Started At */}
                            <td className="text-muted">
                              {formatDate(exec.startedAt)}
                            </td>

                            {/* Duration */}
                            <td className="font-monospace text-muted">
                              {duration}
                            </td>

                            {/* Steps count */}
                            <td>
                              <span className="badge bg-light text-dark border">
                                {stepCount} {stepCount === 1 ? 'step' : 'steps'}
                              </span>
                            </td>

                            {/* Summary */}
                            <td className="font-monospace text-break" style={{ maxWidth: '300px' }}>
                              {exec.errorMessage ? (
                                <span className="text-danger text-truncate d-block" title={exec.errorMessage}>
                                  <i className="bi bi-exclamation-triangle me-1"></i>
                                  {exec.errorMessage}
                                </span>
                              ) : exec.outputData?.result ? (
                                <span className="text-dark text-truncate d-block" title={String(exec.outputData.result)}>
                                  {String(exec.outputData.result)}
                                </span>
                              ) : exec.outputData ? (
                                <span className="text-muted small text-truncate d-block">
                                  {JSON.stringify(exec.outputData)}
                                </span>
                              ) : (
                                <span className="text-muted fst-italic">-</span>
                              )}
                            </td>

                            {/* Actions */}
                            <td className="text-end">
                              <div className="d-inline-flex align-items-center gap-1">
                                {/* Inspect Details */}
                                <button
                                  className="btn btn-sm btn-outline-primary py-0 px-2 small d-inline-flex align-items-center gap-1"
                                  onClick={() => setSelectedExecution(exec)}
                                  title="Inspect full execution trace and outputs"
                                >
                                  <i className="bi bi-eye"></i>
                                  <span>Inspect</span>
                                </button>

                                {/* Retry button for failed runs */}
                                {exec.status === 'FAILED' && (
                                  <button
                                    className="btn btn-sm btn-outline-warning py-0 px-2 small d-inline-flex align-items-center gap-1"
                                    onClick={() => handleRetryExecution(exec.id)}
                                    disabled={isRetrying}
                                    title="Retry execution with original input data (creates a new run)"
                                  >
                                    {isRetrying ? (
                                      <span className="spinner-border spinner-border-sm" role="status"></span>
                                    ) : (
                                      <i className="bi bi-arrow-clockwise"></i>
                                    )}
                                    <span>Retry</span>
                                  </button>
                                )}

                                {/* Open in Builder */}
                                <Link
                                  to={`/builder?workflowId=${exec.workflowId}`}
                                  className="btn btn-sm btn-outline-secondary py-0 px-2 small"
                                  title="Open workflow in builder"
                                >
                                  <i className="bi bi-bezier2"></i>
                                </Link>
                              </div>
                            </td>
                          </tr>
                        );
                      })}
                    </tbody>
                  </table>
                </div>
              </div>
            )}
          </div>
        )}

        {/* ============================================================== */}
        {/* EXECUTION DETAILS MODAL (Phase 5 Inspection Modal)             */}
        {/* ============================================================== */}
        {selectedExecution && (
          <div
            className="modal show d-block"
            tabIndex="-1"
            style={{ backgroundColor: 'rgba(15, 23, 42, 0.65)', backdropFilter: 'blur(3px)', zIndex: 1055 }}
          >
            <div className="modal-dialog modal-xl modal-dialog-scrollable modal-dialog-centered">
              <div className="modal-content shadow-lg border-0 rounded-3">
                {/* Modal Header */}
                <div className="modal-header bg-dark text-white py-3">
                  <div className="d-flex align-items-center gap-2">
                    <i className="bi bi-card-checklist text-primary fs-5"></i>
                    <h5 className="modal-title fw-bold mb-0">
                      Execution Run #{selectedExecution.id} &mdash; {selectedExecution.workflowName || `Workflow #${selectedExecution.workflowId}`}
                    </h5>
                    <span
                      className={`badge ms-2 ${
                        selectedExecution.status === 'COMPLETED'
                          ? 'bg-success'
                          : selectedExecution.status === 'FAILED'
                          ? 'bg-danger'
                          : 'bg-primary'
                      }`}
                    >
                      {selectedExecution.status}
                    </span>
                  </div>
                  <button
                    type="button"
                    className="btn-close btn-close-white"
                    aria-label="Close"
                    onClick={() => setSelectedExecution(null)}
                  ></button>
                </div>

                {/* Modal Body */}
                <div className="modal-body p-4 bg-light">
                  {/* Metadata Stats Row */}
                  <div className="row g-3 mb-4">
                    <div className="col-sm-6 col-lg-3">
                      <div className="card shadow-sm border-0 rounded-3 p-3 bg-white h-100">
                        <span className="text-muted small text-uppercase fw-semibold">Started At</span>
                        <div className="fw-bold text-dark mt-1 small">
                          {formatDate(selectedExecution.startedAt)}
                        </div>
                      </div>
                    </div>
                    <div className="col-sm-6 col-lg-3">
                      <div className="card shadow-sm border-0 rounded-3 p-3 bg-white h-100">
                        <span className="text-muted small text-uppercase fw-semibold">Completed At</span>
                        <div className="fw-bold text-dark mt-1 small">
                          {selectedExecution.completedAt ? formatDate(selectedExecution.completedAt) : 'In Progress / Incomplete'}
                        </div>
                      </div>
                    </div>
                    <div className="col-sm-6 col-lg-3">
                      <div className="card shadow-sm border-0 rounded-3 p-3 bg-white h-100">
                        <span className="text-muted small text-uppercase fw-semibold">Total Duration</span>
                        <div className="fw-bold font-monospace text-dark mt-1">
                          {formatDuration(selectedExecution.startedAt, selectedExecution.completedAt)}
                        </div>
                      </div>
                    </div>
                    <div className="col-sm-6 col-lg-3">
                      <div className="card shadow-sm border-0 rounded-3 p-3 bg-white h-100">
                        <span className="text-muted small text-uppercase fw-semibold">Workflow Target</span>
                        <div className="mt-1">
                          <Link
                            to={`/builder?workflowId=${selectedExecution.workflowId}`}
                            className="fw-bold text-primary text-decoration-none small"
                          >
                            #{selectedExecution.workflowId} &bull; {selectedExecution.workflowName || 'View Graph'}
                          </Link>
                        </div>
                      </div>
                    </div>
                  </div>

                  {/* Failure Alert Banner (if run failed) */}
                  {selectedExecution.status === 'FAILED' && (
                    <div className="alert alert-danger shadow-sm mb-4 border-danger-subtle">
                      <div className="d-flex align-items-start gap-2">
                        <i className="bi bi-x-octagon-fill fs-5 mt-1 text-danger"></i>
                        <div className="flex-grow-1">
                          <h6 className="fw-bold mb-1 text-danger">Execution Failed</h6>
                          <div className="font-monospace small text-break">
                            {selectedExecution.errorMessage || 'Execution encountered an unexpected step failure.'}
                          </div>
                          {selectedExecution.errorMessage?.toLowerCase().includes('ollama') && (
                            <div className="alert alert-warning py-2 px-3 mt-2 mb-0 small border-warning-subtle text-dark">
                              <i className="bi bi-info-circle-fill me-1 text-warning"></i>
                              <strong>Local AI Provider:</strong> The <code>AI_TEXT_PROCESSOR</code> node runs locally on Ollama (<code>qwen2.5:3b</code>). Please verify Ollama is running on <code>http://localhost:11434</code>.
                            </div>
                          )}
                        </div>
                      </div>
                    </div>
                  )}

                  {/* AI Generated Output Card (if pipeline includes AI_TEXT_PROCESSOR) */}
                  {(() => {
                    const aiLog = Array.isArray(selectedExecution.nodeLogs)
                      ? selectedExecution.nodeLogs.find((l) => l.nodeType === 'AI_TEXT_PROCESSOR')
                      : null;
                    if (!aiLog || !aiLog.outputData) return null;
                    return (
                      <div className="card shadow-sm border-0 rounded-3 bg-white p-3 mb-4 border-start border-4 border-info">
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
                                setActionMessage({ type: 'success', text: 'AI output copied to clipboard!' });
                              }}
                              title="Copy AI Output"
                            >
                              <i className="bi bi-clipboard me-1"></i>
                              Copy AI Output
                            </button>
                          )}
                        </div>
                        <div className="p-3 bg-light rounded-3 border text-dark font-monospace fs-6 fw-normal text-break" style={{ whiteSpace: 'pre-wrap', maxHeight: '200px', overflowY: 'auto' }}>
                          {typeof aiLog.outputData?.result === 'string'
                            ? aiLog.outputData.result
                            : JSON.stringify(aiLog.outputData?.result || aiLog.outputData, null, 2)}
                        </div>
                      </div>
                    );
                  })()}

                  {/* Pipeline Final Output Data */}
                  {selectedExecution.outputData && (
                    <div className="card shadow-sm border-0 rounded-3 bg-white p-3 mb-4">
                      <div className="d-flex align-items-center justify-content-between mb-2">
                        <span className="text-muted small fw-bold text-uppercase">
                          <i className="bi bi-box-arrow-right text-success me-1"></i>
                          Final Pipeline Output Data:
                        </span>
                        {selectedExecution.outputData?.result && (
                          <button
                            className="btn btn-outline-secondary btn-sm py-0 px-2 small"
                            onClick={() => {
                              const val = typeof selectedExecution.outputData.result === 'string'
                                ? selectedExecution.outputData.result
                                : JSON.stringify(selectedExecution.outputData.result);
                              navigator.clipboard.writeText(val);
                              setActionMessage({ type: 'success', text: 'Output copied to clipboard!' });
                            }}
                            title="Copy output"
                          >
                            <i className="bi bi-clipboard me-1"></i>
                            Copy
                          </button>
                        )}
                      </div>
                      <pre className="bg-light p-3 rounded-3 border text-dark font-monospace small mb-0 overflow-auto" style={{ maxHeight: '160px' }}>
                        {JSON.stringify(selectedExecution.outputData, null, 2)}
                      </pre>
                    </div>
                  )}

                  {/* Executed Step Trace Table */}
                  <div className="card shadow-sm border-0 rounded-3 bg-white p-3 mb-4">
                    <div className="d-flex align-items-center justify-content-between mb-2">
                      <span className="text-muted small fw-bold text-uppercase">
                        <i className="bi bi-list-check text-primary me-1"></i>
                        Executed Step Trace ({selectedExecution.nodeLogs?.length || 0} steps in topological order):
                      </span>
                    </div>

                    {selectedExecution.nodeLogs && selectedExecution.nodeLogs.length > 0 ? (
                      <div className="table-responsive">
                        <table className="table table-sm table-hover table-bordered align-middle mb-0">
                          <thead className="table-light small text-uppercase">
                            <tr>
                              <th style={{ width: '40px' }}>#</th>
                              <th>Node ID</th>
                              <th>Node Type</th>
                              <th style={{ width: '130px' }}>Status</th>
                              <th style={{ width: '90px' }}>Duration</th>
                              <th>Step Output / Data</th>
                            </tr>
                          </thead>
                          <tbody className="small">
                            {selectedExecution.nodeLogs.map((log, idx) => {
                              const stepDuration = formatDuration(log.startedAt, log.completedAt);
                              return (
                                <tr key={log.id || idx}>
                                  <td className="text-muted fw-bold">{idx + 1}</td>
                                  <td>
                                    <code className="fw-bold text-dark">{log.nodeId}</code>
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
                                          : 'bg-primary-subtle text-primary border border-primary-subtle'
                                      }`}
                                    >
                                      {log.status === 'COMPLETED' ? '✓ COMPLETED' : log.status === 'FAILED' ? '✗ FAILED' : log.status}
                                    </span>
                                  </td>
                                  <td className="font-monospace text-muted">{stepDuration}</td>
                                  <td className="font-monospace text-break" style={{ maxWidth: '360px' }}>
                                    {log.errorMessage ? (
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
                      <div className="text-center text-muted py-3 small">
                        No node logs recorded for this execution.
                      </div>
                    )}
                  </div>

                  {/* Input Data Accordion (Collapsible) */}
                  {selectedExecution.inputData && (
                    <details className="card shadow-sm border-0 rounded-3 bg-white p-2 mb-3">
                      <summary className="text-secondary small fw-semibold cursor-pointer py-1">
                        <i className="bi bi-input-cursor-text me-1"></i>
                        View Original Input Data
                      </summary>
                      <pre className="bg-light p-3 rounded-2 border text-dark font-monospace small mb-0 mt-2 overflow-auto" style={{ maxHeight: '140px' }}>
                        {JSON.stringify(selectedExecution.inputData, null, 2)}
                      </pre>
                    </details>
                  )}

                  {/* Raw JSON Accordion (Collapsible) */}
                  <details className="card shadow-sm border-0 rounded-3 bg-white p-2">
                    <summary className="text-secondary small fw-semibold cursor-pointer py-1">
                      <i className="bi bi-code-slash me-1"></i>
                      View Full Execution JSON
                    </summary>
                    <pre className="bg-light p-3 rounded-2 border text-dark font-monospace small mb-0 mt-2 overflow-auto" style={{ maxHeight: '160px' }}>
                      {JSON.stringify(selectedExecution, null, 2)}
                    </pre>
                  </details>
                </div>

                {/* Modal Footer */}
                <div className="modal-footer bg-white py-2 d-flex justify-content-between">
                  <div>
                    {selectedExecution.status === 'FAILED' && (
                      <button
                        className="btn btn-warning btn-sm d-inline-flex align-items-center gap-1"
                        onClick={() => handleRetryExecution(selectedExecution.id)}
                        disabled={retryingId === selectedExecution.id}
                      >
                        {retryingId === selectedExecution.id ? (
                          <span className="spinner-border spinner-border-sm" role="status"></span>
                        ) : (
                          <i className="bi bi-arrow-clockwise"></i>
                        )}
                        <span>Retry Failed Execution</span>
                      </button>
                    )}
                  </div>
                  <div className="d-flex align-items-center gap-2">
                    <Link
                      to={`/builder?workflowId=${selectedExecution.workflowId}`}
                      className="btn btn-outline-primary btn-sm d-inline-flex align-items-center gap-1"
                    >
                      <i className="bi bi-bezier2"></i>
                      <span>Open in Builder</span>
                    </Link>
                    <button
                      type="button"
                      className="btn btn-secondary btn-sm"
                      onClick={() => setSelectedExecution(null)}
                    >
                      Close
                    </button>
                  </div>
                </div>
              </div>
            </div>
          </div>
        )}
      </main>

      {/* Footer */}
      <footer className="py-3 bg-white border-top text-center text-muted small mt-auto">
        <div className="container d-flex flex-wrap align-items-center justify-content-between gap-2">
          <span>AgentFlow AI Automation Platform &bull; Phase 5 Live</span>
          <span>
            Database: <code>agentflow_db1</code> &bull; API: <code>/api/v1</code>
          </span>
        </div>
      </footer>
    </div>
  );
}

export default Dashboard;
