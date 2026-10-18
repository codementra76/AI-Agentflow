import React, { memo } from 'react';
import { Handle, Position } from '@xyflow/react';

// Meta definitions for the 5 backend node types
const NODE_META = {
  MANUAL_TRIGGER: {
    label: 'Manual Trigger',
    icon: 'bi-play-circle-fill',
    color: '#f59e0b',
    badgeClass: 'bg-warning-subtle text-warning-emphasis border-warning-subtle',
    hasTarget: false,
    hasSource: true,
  },
  TEXT_INPUT: {
    label: 'Text Input',
    icon: 'bi-input-cursor-text',
    color: '#3b82f6',
    badgeClass: 'bg-primary-subtle text-primary border-primary-subtle',
    hasTarget: true,
    hasSource: true,
  },
  TEXT_TRANSFORM: {
    label: 'Text Transform',
    icon: 'bi-arrow-repeat',
    color: '#8b5cf6',
    badgeClass: 'bg-purple-subtle text-purple border-purple-subtle',
    hasTarget: true,
    hasSource: true,
  },
  AI_TEXT_PROCESSOR: {
    label: 'AI Text Processor',
    icon: 'bi-robot',
    color: '#06b6d4',
    badgeClass: 'bg-info-subtle text-info-emphasis border-info-subtle',
    hasTarget: true,
    hasSource: true,
  },
  OUTPUT: {
    label: 'Output',
    icon: 'bi-box-arrow-right',
    color: '#10b981',
    badgeClass: 'bg-success-subtle text-success border-success-subtle',
    hasTarget: true,
    hasSource: false,
  },
};

const CustomWorkflowNode = memo(({ data, selected }) => {
  const nodeType = data.type || 'TEXT_INPUT';
  const meta = NODE_META[nodeType] || NODE_META.TEXT_INPUT;
  const config = data.config || {};

  // Generate a subtitle/preview based on node configuration
  const renderConfigPreview = () => {
    switch (nodeType) {
      case 'TEXT_INPUT':
        return config.text ? `"${config.text.slice(0, 24)}${config.text.length > 24 ? '...' : ''}"` : 'No text entered';
      case 'TEXT_TRANSFORM':
        return config.operation ? `Op: ${config.operation}` : 'Op: UPPERCASE';
      case 'AI_TEXT_PROCESSOR':
        return config.prompt ? `Prompt: ${config.prompt.slice(0, 20)}...` : 'LLM Instruction';
      case 'MANUAL_TRIGGER':
        return 'Pipeline Entry';
      case 'OUTPUT':
        return 'Pipeline Terminal';
      default:
        return '';
    }
  };

  return (
    <div
      className={`card shadow-sm rounded-3 border ${
        data.executionStatus === 'FAILED'
          ? 'border-danger'
          : data.executionStatus === 'COMPLETED'
          ? 'border-success'
          : selected
          ? 'border-primary ring-selected'
          : 'border-secondary-subtle'
      }`}
      style={{
        minWidth: '220px',
        maxWidth: '280px',
        backgroundColor: '#ffffff',
        transition: 'all 0.15s ease-in-out',
        boxShadow: data.executionStatus === 'FAILED'
          ? '0 0 0 3px rgba(239, 68, 68, 0.25)'
          : data.executionStatus === 'COMPLETED'
          ? '0 0 0 3px rgba(16, 185, 129, 0.25)'
          : selected
          ? '0 0 0 3px rgba(59, 130, 246, 0.25)'
          : undefined,
      }}
    >
      {/* Target Handle (Left) - for incoming data */}
      {meta.hasTarget && (
        <Handle
          type="target"
          position={Position.Left}
          style={{
            top: '50%',
            transform: 'translateY(-50%)',
            width: '14px',
            height: '14px',
            background: meta.color,
            border: '2px solid #ffffff',
            boxShadow: '0 0 0 2px rgba(0, 0, 0, 0.15)',
            cursor: 'crosshair',
            zIndex: 10,
          }}
        />
      )}

      {/* Node Header */}
      <div
        className="card-header py-2 px-3 d-flex align-items-center justify-content-between border-bottom"
        style={{
          backgroundColor: '#fafafa',
          borderTopLeftRadius: 'calc(0.375rem - 1px)',
          borderTopRightRadius: 'calc(0.375rem - 1px)',
        }}
      >
        <div className="d-flex align-items-center gap-2">
          <i
            className={`bi ${meta.icon} fs-5`}
            style={{ color: meta.color }}
          ></i>
          <span className="badge rounded-pill border px-2 py-1 small fw-semibold" style={{ fontSize: '0.7rem', backgroundColor: `${meta.color}15`, color: meta.color, borderColor: `${meta.color}30` }}>
            {meta.label}
          </span>
          {data.executionStatus && (
            <span
              className={`badge rounded-pill px-2 py-0 small ${
                data.executionStatus === 'COMPLETED'
                  ? 'bg-success text-white'
                  : data.executionStatus === 'FAILED'
                  ? 'bg-danger text-white'
                  : 'bg-primary text-white'
              }`}
              style={{ fontSize: '0.65rem' }}
              title={`Step execution status: ${data.executionStatus}`}
            >
              {data.executionStatus === 'COMPLETED' ? '✓ OK' : data.executionStatus === 'FAILED' ? '✗ Fail' : 'Running'}
            </span>
          )}
        </div>
        <small className="text-muted font-monospace" style={{ fontSize: '0.65rem' }}>
          {data.id || ''}
        </small>
      </div>

      {/* Node Body */}
      <div className="card-body p-2 px-3">
        <div className="fw-bold text-dark text-truncate" style={{ fontSize: '0.9rem' }}>
          {data.name || meta.label}
        </div>
        <div className="text-muted small text-truncate mt-1" style={{ fontSize: '0.75rem' }}>
          {renderConfigPreview()}
        </div>
      </div>

      {/* Source Handle (Right) - for outgoing data */}
      {meta.hasSource && (
        <Handle
          type="source"
          position={Position.Right}
          style={{
            top: '50%',
            transform: 'translateY(-50%)',
            width: '14px',
            height: '14px',
            background: meta.color,
            border: '2px solid #ffffff',
            boxShadow: '0 0 0 2px rgba(0, 0, 0, 0.15)',
            cursor: 'crosshair',
            zIndex: 10,
          }}
        />
      )}
    </div>
  );
});

CustomWorkflowNode.displayName = 'CustomWorkflowNode';

export default CustomWorkflowNode;
