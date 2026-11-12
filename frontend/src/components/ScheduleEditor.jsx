import React, { useState, useEffect, useCallback } from 'react';
import {
  getWorkflowSchedule,
  saveWorkflowSchedule,
  updateScheduleStatus,
  deleteWorkflowSchedule,
  getErrorMessage,
} from '../services/api';
import {
  POPULAR_TIMEZONES,
  WEEKDAYS,
  getDefaultTimeZone,
  localDateTimeToUtcInstant,
  utcInstantToLocalDateTime,
  formatTimestampInTz,
} from '../utils/scheduleUtils';

/**
 * Visual Schedule Studio for configuring One-Time, Daily, and Weekly automated triggers.
 */
export default function ScheduleEditor({ workflowId, isWorkflowActive, onScheduleUpdated }) {
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [toggling, setToggling] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const [statusMessage, setStatusMessage] = useState(null);
  const [confirmDelete, setConfirmDelete] = useState(false);

  // Existing saved schedule (or null if none)
  const [existingSchedule, setExistingSchedule] = useState(null);

  // Form Fields
  const [scheduleType, setScheduleType] = useState('DAILY');
  const [enabled, setEnabled] = useState(true);
  const [timeZone, setTimeZone] = useState(getDefaultTimeZone());
  const [customTimeZone, setCustomTimeZone] = useState('');
  const [scheduledTime, setScheduledTime] = useState('09:00');
  const [selectedDays, setSelectedDays] = useState(['MONDAY', 'WEDNESDAY', 'FRIDAY']);
  const [oneTimeDate, setOneTimeDate] = useState('');
  const [oneTimeTime, setOneTimeTime] = useState('10:00');
  const [inputDataJson, setInputDataJson] = useState('');
  const [jsonValidationError, setJsonValidationError] = useState(null);

  // Reset form to defaults
  const resetFormToDefaults = useCallback(() => {
    setExistingSchedule(null);
    setScheduleType('DAILY');
    setEnabled(true);
    const defaultTz = getDefaultTimeZone();
    setTimeZone(defaultTz);
    setCustomTimeZone('');
    setScheduledTime('09:00');
    setSelectedDays(['MONDAY', 'WEDNESDAY', 'FRIDAY']);

    // Default one-time to tomorrow at 10:00
    const tomorrow = new Date();
    tomorrow.setDate(tomorrow.getDate() + 1);
    const yyyy = tomorrow.getFullYear();
    const mm = String(tomorrow.getMonth() + 1).padStart(2, '0');
    const dd = String(tomorrow.getDate()).padStart(2, '0');
    setOneTimeDate(`${yyyy}-${mm}-${dd}`);
    setOneTimeTime('10:00');
    setInputDataJson('');
    setJsonValidationError(null);
  }, []);

  // Fetch schedule from backend when workflowId changes
  const loadSchedule = useCallback(async () => {
    if (!workflowId) {
      resetFormToDefaults();
      return;
    }

    setLoading(true);
    setStatusMessage(null);
    setConfirmDelete(false);

    try {
      const response = await getWorkflowSchedule(workflowId);
      if (response && response.success && response.data) {
        const sched = response.data;
        setExistingSchedule(sched);
        setScheduleType(sched.scheduleType || 'DAILY');
        setEnabled(!!sched.enabled);

        const tz = sched.timeZone || getDefaultTimeZone();
        if (POPULAR_TIMEZONES.includes(tz)) {
          setTimeZone(tz);
          setCustomTimeZone('');
        } else {
          setTimeZone('CUSTOM');
          setCustomTimeZone(tz);
        }

        if (sched.scheduledTime) {
          setScheduledTime(sched.scheduledTime.slice(0, 5));
        }

        if (Array.isArray(sched.daysOfWeek) && sched.daysOfWeek.length > 0) {
          setSelectedDays(sched.daysOfWeek);
        } else if (sched.dayOfWeek) {
          setSelectedDays(sched.dayOfWeek.split(',').map((d) => d.trim()));
        }

        if (sched.scheduleType === 'ONE_TIME' && sched.oneTimeExecutionAt) {
          const { dateStr, timeStr } = utcInstantToLocalDateTime(sched.oneTimeExecutionAt, tz);
          setOneTimeDate(dateStr);
          setOneTimeTime(timeStr);
        }

        if (sched.inputData) {
          setInputDataJson(JSON.stringify(sched.inputData, null, 2));
        } else {
          setInputDataJson('');
        }

        if (onScheduleUpdated) {
          onScheduleUpdated(sched);
        }
      } else {
        resetFormToDefaults();
      }
    } catch (err) {
      // 404 means no schedule configured yet - this is normal
      if (err.response?.status === 404) {
        resetFormToDefaults();
        if (onScheduleUpdated) {
          onScheduleUpdated(null);
        }
      } else {
        setStatusMessage({
          type: 'danger',
          text: `Failed to load schedule: ${getErrorMessage(err)}`,
        });
      }
    } finally {
      setLoading(false);
    }
  }, [workflowId, onScheduleUpdated, resetFormToDefaults]);

  useEffect(() => {
    loadSchedule();
  }, [loadSchedule]);

  // Active time zone resolution
  const resolvedTimeZone = timeZone === 'CUSTOM' ? (customTimeZone || 'UTC').trim() : timeZone;

  // Weekday toggle
  const toggleWeekday = (day) => {
    setSelectedDays((prev) => {
      if (prev.includes(day)) {
        if (prev.length === 1) return prev; // Keep at least one
        return prev.filter((d) => d !== day);
      }
      return [...prev, day];
    });
  };

  // Validate JSON input on change
  const handleJsonChange = (val) => {
    setInputDataJson(val);
    if (!val.trim()) {
      setJsonValidationError(null);
      return;
    }
    try {
      JSON.parse(val);
      setJsonValidationError(null);
    } catch (err) {
      setJsonValidationError(`Invalid JSON syntax: ${err.message}`);
    }
  };

  // Save or update schedule
  const handleSaveSchedule = async (e) => {
    if (e) e.preventDefault();
    if (!workflowId) return;

    setStatusMessage(null);

    // Validation
    if (jsonValidationError) {
      setStatusMessage({ type: 'danger', text: 'Please resolve the JSON input error before saving.' });
      return;
    }

    if (scheduleType === 'WEEKLY' && selectedDays.length === 0) {
      setStatusMessage({ type: 'danger', text: 'Please select at least one weekday for weekly schedule.' });
      return;
    }

    if (scheduleType === 'ONE_TIME') {
      if (!oneTimeDate || !oneTimeTime) {
        setStatusMessage({ type: 'danger', text: 'Please provide both date and time for one-time execution.' });
        return;
      }
      const targetInstant = localDateTimeToUtcInstant(oneTimeDate, oneTimeTime, resolvedTimeZone);
      if (enabled && new Date(targetInstant) <= new Date()) {
        setStatusMessage({ type: 'danger', text: 'One-time execution time must be in the future.' });
        return;
      }
    }

    setSaving(true);
    try {
      let parsedInput = null;
      if (inputDataJson.trim()) {
        parsedInput = JSON.parse(inputDataJson);
      }

      const formattedTime = scheduledTime.length === 5 ? `${scheduledTime}:00` : scheduledTime;

      const payload = {
        scheduleType,
        timeZone: resolvedTimeZone,
        enabled,
        scheduledTime: scheduleType === 'DAILY' || scheduleType === 'WEEKLY' ? formattedTime : null,
        daysOfWeek: scheduleType === 'WEEKLY' ? selectedDays : null,
        oneTimeExecutionAt: scheduleType === 'ONE_TIME'
          ? localDateTimeToUtcInstant(oneTimeDate, oneTimeTime, resolvedTimeZone)
          : null,
        inputData: parsedInput,
      };

      const response = await saveWorkflowSchedule(workflowId, payload);
      if (response && response.success) {
        setExistingSchedule(response.data);
        setStatusMessage({
          type: 'success',
          text: 'Schedule saved successfully!',
        });
        if (onScheduleUpdated) {
          onScheduleUpdated(response.data);
        }
      }
    } catch (err) {
      setStatusMessage({
        type: 'danger',
        text: getErrorMessage(err),
      });
    } finally {
      setSaving(false);
    }
  };

  // Quick enable/disable toggle
  const handleToggleStatus = async () => {
    if (!workflowId || !existingSchedule) return;

    const targetEnabled = !existingSchedule.enabled;
    setToggling(true);
    setStatusMessage(null);

    try {
      const response = await updateScheduleStatus(workflowId, targetEnabled);
      if (response && response.success) {
        setExistingSchedule(response.data);
        setEnabled(response.data.enabled);
        setStatusMessage({
          type: 'success',
          text: targetEnabled ? 'Schedule resumed and next run calculated.' : 'Schedule paused.',
        });
        if (onScheduleUpdated) {
          onScheduleUpdated(response.data);
        }
      }
    } catch (err) {
      setStatusMessage({
        type: 'danger',
        text: getErrorMessage(err),
      });
    } finally {
      setToggling(false);
    }
  };

  // Delete schedule
  const handleDeleteSchedule = async () => {
    if (!workflowId || !existingSchedule) return;

    setDeleting(true);
    setStatusMessage(null);

    try {
      await deleteWorkflowSchedule(workflowId);
      resetFormToDefaults();
      setConfirmDelete(false);
      setStatusMessage({
        type: 'success',
        text: 'Schedule deleted successfully.',
      });
      if (onScheduleUpdated) {
        onScheduleUpdated(null);
      }
    } catch (err) {
      setStatusMessage({
        type: 'danger',
        text: getErrorMessage(err),
      });
    } finally {
      setDeleting(false);
    }
  };

  // If workflow is not saved yet
  if (!workflowId) {
    return (
      <div className="d-flex flex-column align-items-center justify-content-center text-center p-4 h-100">
        <div className="bg-light p-3 rounded-circle text-muted mb-3">
          <i className="bi bi-clock-history fs-2"></i>
        </div>
        <h6 className="fw-bold text-dark mb-2">Workflow Not Saved Yet</h6>
        <p className="text-muted small mb-3" style={{ maxWidth: '260px' }}>
          Please save this workflow first. Once saved with a workflow ID, you can configure automated execution schedules.
        </p>
      </div>
    );
  }

  // Loading state
  if (loading) {
    return (
      <div className="d-flex flex-column align-items-center justify-content-center p-5 text-muted">
        <div className="spinner-border spinner-border-sm text-primary mb-2" role="status"></div>
        <span className="small">Loading schedule configuration...</span>
      </div>
    );
  }

  return (
    <div className="d-flex flex-column gap-3 overflow-auto pe-1 flex-grow-1" id="schedule-studio-panel">
      {/* Studio Header */}
      <div className="d-flex align-items-center justify-content-between">
        <div>
          <h6 className="fw-bold text-dark small mb-0">Schedule Studio</h6>
          <span className="text-muted" style={{ fontSize: '0.72rem' }}>
            Automate execution across time zones
          </span>
        </div>
        {existingSchedule && (
          <span
            className={`badge ${existingSchedule.enabled ? 'bg-success-subtle text-success border border-success-subtle' : 'bg-secondary-subtle text-secondary'}`}
            style={{ fontSize: '0.7rem' }}
          >
            <i className={`bi ${existingSchedule.enabled ? 'bi-check-circle-fill' : 'bi-pause-circle-fill'} me-1`}></i>
            {existingSchedule.enabled ? 'Active' : 'Paused'}
          </span>
        )}
      </div>

      {/* Inactive Workflow Notice */}
      {!isWorkflowActive && (
        <div className="alert alert-warning py-2 px-3 small d-flex align-items-start gap-2 mb-0 border-0 rounded-3 shadow-xs">
          <i className="bi bi-exclamation-triangle-fill text-warning fs-6 mt-0"></i>
          <div>
            <strong>Workflow is Inactive:</strong> Scheduled triggers will be skipped until this workflow is activated.
          </div>
        </div>
      )}

      {/* Status Alert */}
      {statusMessage && (
        <div
          className={`alert alert-${statusMessage.type} alert-dismissible fade show py-2 px-3 small mb-0 rounded-3 shadow-xs`}
          role="alert"
        >
          {statusMessage.text}
          <button
            type="button"
            className="btn-close py-2"
            onClick={() => setStatusMessage(null)}
            aria-label="Close"
          ></button>
        </div>
      )}

      {/* Next Run & Status Preview Card */}
      {existingSchedule && (
        <div className="card border-0 bg-light rounded-3 p-3 shadow-xs">
          <div className="d-flex align-items-center justify-content-between mb-2">
            <span className="text-uppercase fw-bold text-muted" style={{ fontSize: '0.68rem', letterSpacing: '0.5px' }}>
              Execution Status
            </span>
            <button
              id="btn-toggle-schedule-status"
              type="button"
              className={`btn btn-sm py-0 px-2 ${existingSchedule.enabled ? 'btn-outline-warning text-dark' : 'btn-outline-success'}`}
              style={{ fontSize: '0.75rem' }}
              onClick={handleToggleStatus}
              disabled={toggling}
            >
              {toggling ? (
                <span className="spinner-border spinner-border-sm me-1" role="status"></span>
              ) : existingSchedule.enabled ? (
                <>
                  <i className="bi bi-pause-fill me-1"></i>Pause
                </>
              ) : (
                <>
                  <i className="bi bi-play-fill me-1"></i>Resume
                </>
              )}
            </button>
          </div>

          {/* Next Run */}
          <div className="mb-2">
            <div className="text-muted small" style={{ fontSize: '0.72rem' }}>
              Next Scheduled Execution:
            </div>
            {existingSchedule.enabled && existingSchedule.nextRunAt ? (
              <div className="fw-semibold text-primary small d-flex align-items-center gap-1">
                <i className="bi bi-clock-fill"></i>
                <span>{formatTimestampInTz(existingSchedule.nextRunAt, existingSchedule.timeZone)}</span>
              </div>
            ) : (
              <div className="text-muted small fst-italic">
                {existingSchedule.enabled ? 'No upcoming run scheduled' : 'Schedule is paused'}
              </div>
            )}
          </div>

          {/* Last Run & Status */}
          {existingSchedule.lastRunAt && (
            <div className="pt-2 border-top border-secondary-subtle">
              <div className="d-flex align-items-center justify-content-between">
                <span className="text-muted" style={{ fontSize: '0.7rem' }}>
                  Last run: {formatTimestampInTz(existingSchedule.lastRunAt, existingSchedule.timeZone)}
                </span>
                {existingSchedule.lastStatus && (
                  <span
                    className={`badge ${
                      existingSchedule.lastStatus === 'COMPLETED'
                        ? 'bg-success'
                        : existingSchedule.lastStatus === 'FAILED'
                        ? 'bg-danger'
                        : existingSchedule.lastStatus === 'MISSED'
                        ? 'bg-dark'
                        : 'bg-warning text-dark'
                    }`}
                    style={{ fontSize: '0.65rem' }}
                  >
                    {existingSchedule.lastStatus}
                  </span>
                )}
              </div>
            </div>
          )}
        </div>
      )}

      {/* Schedule Configuration Form */}
      <form onSubmit={handleSaveSchedule} className="d-flex flex-column gap-3">
        {/* Schedule Type Selection */}
        <div>
          <label className="form-label fw-semibold small text-dark mb-1">Schedule Frequency</label>
          <div className="btn-group btn-group-sm w-100" role="group">
            <button
              id="btn-schedule-type-daily"
              type="button"
              className={`btn ${scheduleType === 'DAILY' ? 'btn-primary' : 'btn-outline-secondary'}`}
              onClick={() => setScheduleType('DAILY')}
            >
              Daily
            </button>
            <button
              id="btn-schedule-type-weekly"
              type="button"
              className={`btn ${scheduleType === 'WEEKLY' ? 'btn-primary' : 'btn-outline-secondary'}`}
              onClick={() => setScheduleType('WEEKLY')}
            >
              Weekly
            </button>
            <button
              id="btn-schedule-type-onetime"
              type="button"
              className={`btn ${scheduleType === 'ONE_TIME' ? 'btn-primary' : 'btn-outline-secondary'}`}
              onClick={() => setScheduleType('ONE_TIME')}
            >
              One-Time
            </button>
          </div>
        </div>

        {/* Time Zone Picker */}
        <div>
          <label htmlFor="schedule-timezone" className="form-label fw-semibold small text-dark mb-1">
            Time Zone
          </label>
          <select
            id="schedule-timezone"
            className="form-select form-select-sm"
            value={timeZone}
            onChange={(e) => setTimeZone(e.target.value)}
          >
            {POPULAR_TIMEZONES.map((tz) => (
              <option key={tz} value={tz}>
                {tz}
              </option>
            ))}
            <option value="CUSTOM">Custom IANA Zone...</option>
          </select>
          {timeZone === 'CUSTOM' && (
            <input
              id="custom-timezone-input"
              type="text"
              className="form-control form-control-sm mt-1"
              placeholder="e.g. Asia/Seoul"
              value={customTimeZone}
              onChange={(e) => setCustomTimeZone(e.target.value)}
            />
          )}
          <div className="form-text text-muted" style={{ fontSize: '0.7rem' }}>
            Zone: <code>{resolvedTimeZone}</code>
          </div>
        </div>

        {/* DAILY Execution Form */}
        {scheduleType === 'DAILY' && (
          <div>
            <label htmlFor="daily-time-input" className="form-label fw-semibold small text-dark mb-1">
              Execution Time (Local)
            </label>
            <input
              id="daily-time-input"
              type="time"
              step="60"
              className="form-control form-control-sm"
              value={scheduledTime}
              onChange={(e) => setScheduledTime(e.target.value)}
              required
            />
            <div className="form-text text-muted" style={{ fontSize: '0.7rem' }}>
              Runs once every day at {scheduledTime || '--:--'} ({resolvedTimeZone}).
            </div>
          </div>
        )}

        {/* WEEKLY Execution Form */}
        {scheduleType === 'WEEKLY' && (
          <div className="d-flex flex-column gap-2">
            <div>
              <label htmlFor="weekly-time-input" className="form-label fw-semibold small text-dark mb-1">
                Execution Time (Local)
              </label>
              <input
                id="weekly-time-input"
                type="time"
                step="60"
                className="form-control form-control-sm"
                value={scheduledTime}
                onChange={(e) => setScheduledTime(e.target.value)}
                required
              />
            </div>

            <div>
              <label className="form-label fw-semibold small text-dark mb-1">Select Weekdays</label>
              <div className="d-flex flex-wrap gap-1">
                {WEEKDAYS.map((day) => {
                  const isSelected = selectedDays.includes(day.value);
                  return (
                    <button
                      key={day.value}
                      id={`weekday-btn-${day.shortLabel.toLowerCase()}`}
                      type="button"
                      className={`btn btn-sm py-1 px-2 ${
                        isSelected ? 'btn-primary' : 'btn-outline-secondary'
                      }`}
                      style={{ fontSize: '0.75rem', minWidth: '38px' }}
                      onClick={() => toggleWeekday(day.value)}
                    >
                      {day.shortLabel}
                    </button>
                  );
                })}
              </div>
              <div className="form-text text-muted" style={{ fontSize: '0.7rem' }}>
                Runs on {selectedDays.map((d) => d.slice(0, 3)).join(', ')} at {scheduledTime} ({resolvedTimeZone}).
              </div>
            </div>
          </div>
        )}

        {/* ONE_TIME Execution Form */}
        {scheduleType === 'ONE_TIME' && (
          <div className="d-flex flex-column gap-2">
            <div className="row g-2">
              <div className="col-7">
                <label htmlFor="onetime-date-input" className="form-label fw-semibold small text-dark mb-1">
                  Execution Date
                </label>
                <input
                  id="onetime-date-input"
                  type="date"
                  className="form-control form-control-sm"
                  value={oneTimeDate}
                  onChange={(e) => setOneTimeDate(e.target.value)}
                  required
                />
              </div>
              <div className="col-5">
                <label htmlFor="onetime-time-input" className="form-label fw-semibold small text-dark mb-1">
                  Time
                </label>
                <input
                  id="onetime-time-input"
                  type="time"
                  step="60"
                  className="form-control form-control-sm"
                  value={oneTimeTime}
                  onChange={(e) => setOneTimeTime(e.target.value)}
                  required
                />
              </div>
            </div>
            <div className="form-text text-muted" style={{ fontSize: '0.7rem' }}>
              Executes once at the specified local instant, then automatically pauses.
            </div>
          </div>
        )}

        {/* Enabled Checkbox */}
        <div className="form-check form-switch pt-1">
          <input
            id="schedule-enabled-switch"
            className="form-check-input"
            type="checkbox"
            role="switch"
            checked={enabled}
            onChange={(e) => setEnabled(e.target.checked)}
          />
          <label className="form-check-label small fw-semibold text-dark" htmlFor="schedule-enabled-switch">
            Enable Schedule
          </label>
          <div className="text-muted" style={{ fontSize: '0.7rem' }}>
            {enabled ? 'Automatic runs will be scheduled' : 'Schedule will be saved in paused state'}
          </div>
        </div>

        {/* Optional JSON Input Data */}
        <div className="pt-2 border-top">
          <div className="d-flex align-items-center justify-content-between mb-1">
            <label htmlFor="schedule-input-json" className="form-label fw-semibold small text-dark mb-0">
              Execution Input Data (JSON)
            </label>
            <span className="text-muted" style={{ fontSize: '0.68rem' }}>
              Optional
            </span>
          </div>
          <textarea
            id="schedule-input-json"
            rows="3"
            className={`form-control form-control-sm font-monospace ${jsonValidationError ? 'is-invalid' : ''}`}
            style={{ fontSize: '0.75rem' }}
            placeholder='e.g. {"env": "production", "batchSize": 50}'
            value={inputDataJson}
            onChange={(e) => handleJsonChange(e.target.value)}
          ></textarea>
          {jsonValidationError ? (
            <div className="invalid-feedback" style={{ fontSize: '0.7rem' }}>
              {jsonValidationError}
            </div>
          ) : (
            <div className="form-text text-muted" style={{ fontSize: '0.7rem' }}>
              Payload passed to workflow nodes during scheduled execution.
            </div>
          )}
        </div>

        {/* Action Buttons */}
        <div className="d-flex flex-column gap-2 pt-2">
          <button
            id="btn-save-schedule"
            type="submit"
            className="btn btn-primary btn-sm d-flex align-items-center justify-content-center gap-2 shadow-sm"
            disabled={saving || !!jsonValidationError}
          >
            {saving ? (
              <>
                <span className="spinner-border spinner-border-sm" role="status"></span>
                <span>Saving Schedule...</span>
              </>
            ) : (
              <>
                <i className="bi bi-cloud-arrow-up-fill"></i>
                <span>{existingSchedule ? 'Update Schedule' : 'Create Schedule'}</span>
              </>
            )}
          </button>

          {/* Delete Schedule Action */}
          {existingSchedule && !confirmDelete && (
            <button
              id="btn-delete-schedule-init"
              type="button"
              className="btn btn-outline-danger btn-sm"
              onClick={() => setConfirmDelete(true)}
            >
              <i className="bi bi-trash3 me-1"></i>
              Delete Schedule
            </button>
          )}

          {/* Inline Delete Confirmation */}
          {confirmDelete && (
            <div className="p-2 border border-danger-subtle bg-danger-subtle rounded-3 text-center">
              <span className="small text-danger fw-semibold d-block mb-2">
                Permanently delete this schedule?
              </span>
              <div className="d-flex justify-content-center gap-2">
                <button
                  id="btn-confirm-delete-schedule"
                  type="button"
                  className="btn btn-danger btn-sm py-0 px-2"
                  style={{ fontSize: '0.75rem' }}
                  onClick={handleDeleteSchedule}
                  disabled={deleting}
                >
                  {deleting ? 'Deleting...' : 'Yes, Delete'}
                </button>
                <button
                  type="button"
                  className="btn btn-outline-secondary btn-sm py-0 px-2"
                  style={{ fontSize: '0.75rem' }}
                  onClick={() => setConfirmDelete(false)}
                >
                  Cancel
                </button>
              </div>
            </div>
          )}
        </div>
      </form>
    </div>
  );
}
