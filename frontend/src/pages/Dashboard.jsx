import React, { useState, useEffect, useCallback } from 'react';
import { Link } from 'react-router-dom';
import {
  getWorkflows,
  setWorkflowActive,
  deleteWorkflow,
  executeWorkflow,
  getHealth,
  getErrorMessage
} from '../services/api';

function Dashboard() {
  const [workflows, setWorkflows] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [actionMessage, setActionMessage] = useState(null);

  // Backend connection status
  const [backendHealth, setBackendHealth] = useState({ online: false, checking: true, details: null });

  // Execution state
  const [executingId, setExecutingId] = useState(null);
  const [executionResult, setExecutionResult] = useState(null);
  const [executionError, setExecutionError] = useState(null);

  // Toggle active state tracker (to show spinner per-row)
  const [togglingId, setTogglingId] = useState(null);
  const [deletingId, setDeletingId] = useState(null);

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

  // Initial load on component mount
  useEffect(() => {
    loadWorkflows();
  }, [loadWorkflows]);

  /**
   * Execute a workflow
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
      } else {
        setExecutionError('Execution completed without detailed response data.');
      }
    } catch (err) {
      const msg = getErrorMessage(err);
      setExecutionError(msg);
    } finally {
      setExecutingId(null);
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

  // Compute summary metrics
  const totalCount = workflows.length;
  const activeCount = workflows.filter((w) => w.active).length;
  const inactiveCount = totalCount - activeCount;

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
              onClick={loadWorkflows}
              disabled={loading}
              title="Reload workflows from backend"
            >
              <i className={`bi bi-arrow-clockwise ${loading ? 'spin' : ''}`}></i>
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
            <h2 className="fw-bold mb-1 text-dark">Workflow Dashboard</h2>
            <p className="text-muted small mb-0">
              Live workflow definitions and pipeline execution status from <code>agentflow_db1</code>
            </p>
          </div>
          <div>
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

        {/* Execution Result Box (Displayed when a run completes) */}
        {executionResult && (
          <div className="card shadow-sm border-0 rounded-3 mb-4 bg-white border-start border-4 border-success">
            <div className="card-header bg-white py-3 d-flex align-items-center justify-content-between border-bottom">
              <div className="d-flex align-items-center gap-2">
                <i className="bi bi-lightning-charge-fill text-success fs-5"></i>
                <h6 className="fw-bold mb-0 text-dark">
                  Execution #{executionResult.id} Result &mdash; {executionResult.workflowName || 'Workflow'}
                </h6>
                <span className={`badge ${executionResult.status === 'COMPLETED' ? 'bg-success' : 'bg-danger'} ms-2`}>
                  {executionResult.status}
                </span>
              </div>
              <button
                type="button"
                className="btn-close"
                aria-label="Close"
                onClick={() => setExecutionResult(null)}
              ></button>
            </div>
            <div className="card-body">
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
                          <th>Node ID</th>
                          <th>Type</th>
                          <th>Status</th>
                          <th>Output</th>
                        </tr>
                      </thead>
                      <tbody className="small">
                        {executionResult.nodeLogs.map((log) => (
                          <tr key={log.nodeId}>
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
                                {JSON.stringify(log.outputData)}
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
      </main>

      {/* Footer */}
      <footer className="py-3 bg-white border-top text-center text-muted small mt-auto">
        <div className="container d-flex flex-wrap align-items-center justify-content-between gap-2">
          <span>AgentFlow AI Automation Platform &bull; Phase 2 Dashboard Live</span>
          <span>
            Database: <code>agentflow_db1</code> &bull; API: <code>/api/v1</code>
          </span>
        </div>
      </footer>
    </div>
  );
}

export default Dashboard;
