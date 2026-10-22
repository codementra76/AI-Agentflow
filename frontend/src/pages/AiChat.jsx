import React, { useState, useEffect, useRef, useCallback } from 'react';
import { Link } from 'react-router-dom';
import { chatWithAi, getHealth, getErrorMessage } from '../services/api';

const EXAMPLE_PROMPTS = [
  {
    icon: 'bi-code-slash',
    title: 'Explain Java Polymorphism',
    prompt: 'Explain Java polymorphism in simple terms with a clear code example.',
    category: 'Programming',
  },
  {
    icon: 'bi-diagram-3',
    title: 'Design an AgentFlow Workflow',
    prompt: 'How do I design a text processing pipeline in AgentFlow that transforms input text to UPPERCASE and summarizes it?',
    category: 'Workflows',
  },
  {
    icon: 'bi-file-earmark-text',
    title: 'Customer Support Summary Prompt',
    prompt: 'Write an effective system prompt for summarizing incoming customer support tickets into key bullet points and urgency rating.',
    category: 'Prompts',
  },
  {
    icon: 'bi-cpu',
    title: 'Local Ollama Advantages',
    prompt: 'What are the main architectural benefits of using local Ollama models compared to cloud LLM APIs?',
    category: 'Architecture',
  },
];

const timeFormatter = new Intl.DateTimeFormat([], { hour: '2-digit', minute: '2-digit' });

function getFormattedTime() {
  return timeFormatter.format(Date.now());
}

/**
 * Helper to render message content with code blocks and paragraphs formatted cleanly
 */
function FormattedContent({ text, isError = false }) {
  if (isError) {
    return (
      <div className="alert alert-danger py-2 px-3 mb-0 small border-danger-subtle">
        <div className="d-flex align-items-center gap-2 fw-semibold mb-1">
          <i className="bi bi-exclamation-triangle-fill text-danger"></i>
          AI Service Notice
        </div>
        <div className="font-monospace text-wrap" style={{ fontSize: '0.85rem' }}>
          {text}
        </div>
      </div>
    );
  }

  // Split text by markdown-style code fences: ```language ... ```
  const codeBlockRegex = /```([a-zA-Z0-9_-]*)\n?([\s\S]*?)```/g;
  const elements = [];
  let lastIndex = 0;
  let match;

  while ((match = codeBlockRegex.exec(text)) !== null) {
    // Text before code block
    if (match.index > lastIndex) {
      const beforeText = text.substring(lastIndex, match.index);
      elements.push(
        <div key={`text-${lastIndex}`} className="mb-2 text-break" style={{ whiteSpace: 'pre-wrap' }}>
          {beforeText}
        </div>
      );
    }

    const lang = match[1] || 'code';
    const code = match[2];
    const blockKey = `code-${match.index}`;

    elements.push(
      <div key={blockKey} className="card bg-dark text-light border-0 my-2 overflow-hidden shadow-sm">
        <div className="card-header bg-dark-subtle text-white-50 py-1 px-3 d-flex justify-content-between align-items-center border-bottom border-secondary small">
          <span className="font-monospace text-uppercase" style={{ fontSize: '0.75rem' }}>
            <i className="bi bi-terminal me-1 text-info"></i>
            {lang}
          </span>
          <button
            type="button"
            className="btn btn-outline-secondary btn-sm py-0 px-2 text-white-50"
            style={{ fontSize: '0.7rem' }}
            onClick={() => navigator.clipboard.writeText(code)}
            title="Copy code"
          >
            <i className="bi bi-clipboard me-1"></i>Copy
          </button>
        </div>
        <pre className="p-3 mb-0 text-white font-monospace" style={{ fontSize: '0.85rem', overflowX: 'auto', margin: 0 }}>
          <code>{code}</code>
        </pre>
      </div>
    );

    lastIndex = match.index + match[0].length;
  }

  // Trailing text after last code block
  if (lastIndex < text.length) {
    elements.push(
      <div key={`text-${lastIndex}`} className="mb-0 text-break" style={{ whiteSpace: 'pre-wrap' }}>
        {text.substring(lastIndex)}
      </div>
    );
  }

  return <div>{elements}</div>;
}

function AiChat() {
  const [messages, setMessages] = useState([]);
  const [inputPrompt, setInputPrompt] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [copiedId, setCopiedId] = useState(null);
  const [backendHealth, setBackendHealth] = useState({ online: false, checking: true });

  const messageCounterRef = useRef(1);
  const messagesEndRef = useRef(null);
  const textareaRef = useRef(null);

  /**
   * Scroll smoothly to the newest message
   */
  const scrollToBottom = useCallback(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, []);

  useEffect(() => {
    scrollToBottom();
  }, [messages, isLoading, scrollToBottom]);

  /**
   * Check backend connection status on mount
   */
  useEffect(() => {
    let isMounted = true;
    getHealth()
      .then((res) => {
        if (isMounted) {
          setBackendHealth({ online: res?.success === true, checking: false });
        }
      })
      .catch(() => {
        if (isMounted) {
          setBackendHealth({ online: false, checking: false });
        }
      });

    return () => {
      isMounted = false;
    };
  }, []);

  /**
   * Focus input after response completes
   */
  useEffect(() => {
    if (!isLoading && textareaRef.current) {
      textareaRef.current.focus();
    }
  }, [isLoading]);

  /**
   * Dispatch chat prompt to local Ollama API via backend
   */
  const handleSend = async (overridePrompt = null) => {
    const textToSend = overridePrompt !== null ? overridePrompt : inputPrompt;
    const trimmed = textToSend.trim();

    if (!trimmed || isLoading) {
      return;
    }

    const nextId = messageCounterRef.current++;
    const formattedTime = getFormattedTime();

    const userMessage = {
      id: `user-${nextId}`,
      role: 'user',
      content: trimmed,
      timestamp: formattedTime,
    };

    setMessages((prev) => [...prev, userMessage]);
    setInputPrompt('');
    setIsLoading(true);

    try {
      const res = await chatWithAi(trimmed);

      // Backend returns ApiResponse: { success: true, message: "...", data: { response: "...", model: "qwen2.5:3b" } }
      const generatedResponse = res?.data?.response || res?.data || res?.message || 'No response returned from model.';
      const modelName = res?.data?.model || 'qwen2.5:3b';
      const assistantId = messageCounterRef.current++;

      const assistantMessage = {
        id: `assistant-${assistantId}`,
        role: 'assistant',
        content: generatedResponse,
        model: modelName,
        timestamp: getFormattedTime(),
        isError: false,
      };

      setMessages((prev) => [...prev, assistantMessage]);

    } catch (err) {
      const errorMsg = getErrorMessage(err);
      const errorId = messageCounterRef.current++;
      const assistantErrorMessage = {
        id: `error-${errorId}`,
        role: 'assistant',
        content: errorMsg,
        model: 'qwen2.5:3b',
        timestamp: getFormattedTime(),
        isError: true,
      };

      setMessages((prev) => [...prev, assistantErrorMessage]);
    } finally {
      setIsLoading(false);
    }
  };

  /**
   * Handle Enter to send, Shift+Enter for newline
   */
  const handleKeyDown = (e) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  };

  /**
   * Reset conversation
   */
  const handleClearChat = () => {
    if (messages.length === 0) return;
    if (window.confirm('Clear all conversation messages?')) {
      setMessages([]);
      if (textareaRef.current) {
        textareaRef.current.focus();
      }
    }
  };

  /**
   * Copy message text to clipboard
   */
  const handleCopyMessage = (id, text) => {
    navigator.clipboard.writeText(text);
    setCopiedId(id);
    setTimeout(() => {
      setCopiedId(null);
    }, 2000);
  };

  return (
    <div className="d-flex flex-column vh-100 bg-light">
      {/* Top Navbar */}
      <nav className="navbar navbar-expand-lg navbar-dark bg-dark shadow-sm py-2 flex-shrink-0">
        <div className="container">
          <div className="d-flex align-items-center gap-2">
            <Link to="/" className="navbar-brand d-flex align-items-center gap-2 fs-5 fw-bold mb-0 text-white text-decoration-none">
              <i className="bi bi-diagram-3-fill text-primary"></i>
              AgentFlow
            </Link>
            <span className="badge bg-secondary-subtle text-light-emphasis border border-secondary-subtle d-none d-sm-inline-block">
              Visual AI Automation
            </span>
          </div>

          <div className="d-flex align-items-center gap-2">
            {/* Nav Links */}
            <Link
              to="/"
              className="btn btn-outline-light btn-sm d-flex align-items-center gap-1 px-3"
              id="nav-dashboard-link"
              title="Return to Dashboard"
            >
              <i className="bi bi-grid-fill"></i>
              <span className="d-none d-md-inline">Dashboard</span>
            </Link>

            <Link
              to="/builder"
              className="btn btn-outline-light btn-sm d-flex align-items-center gap-1 px-3"
              id="nav-builder-link"
              title="Open Workflow Builder"
            >
              <i className="bi bi-bezier2"></i>
              <span className="d-none d-md-inline">Workflow Builder</span>
            </Link>

            {/* Active AI Assistant indicator */}
            <span className="btn btn-primary btn-sm d-flex align-items-center gap-1 px-3 active disabled shadow-sm">
              <i className="bi bi-robot"></i>
              <span>AI Assistant</span>
            </span>

            {/* Backend connection pill */}
            {backendHealth.checking ? (
              <span className="badge bg-warning-subtle text-warning border border-warning-subtle d-none d-lg-inline-flex align-items-center gap-1 px-2 py-1">
                <span className="spinner-border spinner-border-sm" role="status"></span>
                Checking...
              </span>
            ) : backendHealth.online ? (
              <span className="badge bg-success-subtle text-success border border-success-subtle d-none d-lg-inline-flex align-items-center gap-1 px-2 py-1">
                <i className="bi bi-circle-fill text-success" style={{ fontSize: '0.55rem' }}></i>
                Backend :8085
              </span>
            ) : (
              <span className="badge bg-danger-subtle text-danger border border-danger-subtle d-none d-lg-inline-flex align-items-center gap-1 px-2 py-1">
                <i className="bi bi-circle-fill text-danger" style={{ fontSize: '0.55rem' }}></i>
                Backend Offline
              </span>
            )}
          </div>
        </div>
      </nav>

      {/* Main Page Header */}
      <header className="bg-white border-bottom py-3 px-3 shadow-xs flex-shrink-0">
        <div className="container d-flex flex-wrap align-items-center justify-content-between gap-2">
          <div>
            <div className="d-flex align-items-center gap-2">
              <h1 className="h4 fw-bold mb-0 text-dark d-flex align-items-center gap-2">
                <i className="bi bi-robot text-primary"></i>
                AI Assistant
              </h1>
              <span className="badge bg-info-subtle text-info-emphasis border border-info-subtle d-flex align-items-center gap-1">
                <i className="bi bi-cpu-fill"></i>
                Ollama: qwen2.5:3b
              </span>
              <span className="badge bg-success-subtle text-success border border-success-subtle d-none d-sm-inline-block">
                100% Local & Free
              </span>
            </div>
            <p className="text-muted small mb-0 mt-1">
              Test prompts and converse with your local Qwen2.5 3B model via Ollama (<code>http://localhost:11434</code>). Zero cloud latency or paid API keys.
            </p>
          </div>

          <div className="d-flex align-items-center gap-2">
            <button
              type="button"
              className="btn btn-outline-secondary btn-sm d-flex align-items-center gap-1"
              onClick={handleClearChat}
              disabled={messages.length === 0 || isLoading}
              title="Clear all messages from this session"
              id="clear-chat-btn"
            >
              <i className="bi bi-trash3"></i>
              <span>Clear Chat</span>
            </button>
          </div>
        </div>
      </header>

      {/* Scrollable Chat Area */}
      <div className="flex-grow-1 overflow-y-auto px-3 py-4" style={{ minHeight: 0 }}>
        <div className="container" style={{ maxWidth: '860px' }}>
          {/* Empty State */}
          {messages.length === 0 && (
            <div className="py-4 text-center">
              <div className="d-inline-flex align-items-center justify-content-center bg-primary-subtle text-primary rounded-circle mb-3 p-3" style={{ width: '64px', height: '64px' }}>
                <i className="bi bi-robot fs-2"></i>
              </div>
              <h2 className="h5 fw-bold text-dark mb-1">Local Ollama AI Copilot</h2>
              <p className="text-muted small mb-4" style={{ maxWidth: '540px', margin: '0 auto' }}>
                Ask questions about your workflow pipelines, draft node prompt templates, or test Qwen2.5 3B reasoning right on your device.
              </p>

              {/* Example Prompts Grid */}
              <div className="row g-3 text-start mt-2">
                {EXAMPLE_PROMPTS.map((ex, idx) => (
                  <div key={idx} className="col-12 col-md-6">
                    <button
                      type="button"
                      className="card h-100 p-3 bg-white border border-secondary-subtle rounded-3 shadow-xs text-start w-100 btn text-dark text-decoration-none hover-shadow"
                      style={{ transition: 'all 0.15s ease' }}
                      onClick={() => handleSend(ex.prompt)}
                      disabled={isLoading}
                    >
                      <div className="d-flex align-items-center justify-content-between mb-2">
                        <span className="badge bg-light text-secondary border small font-monospace">
                          {ex.category}
                        </span>
                        <i className={`bi ${ex.icon} text-primary fs-5`}></i>
                      </div>
                      <h3 className="h6 fw-semibold text-dark mb-1">{ex.title}</h3>
                      <p className="text-muted small mb-0 font-monospace" style={{ fontSize: '0.8rem' }}>
                        &quot;{ex.prompt.length > 70 ? `${ex.prompt.slice(0, 70)}...` : ex.prompt}&quot;
                      </p>
                    </button>
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* Messages List */}
          {messages.map((msg) => {
            const isUser = msg.role === 'user';
            return (
              <div
                key={msg.id}
                className={`d-flex mb-3 ${isUser ? 'justify-content-end' : 'justify-content-start'}`}
              >
                {!isUser && (
                  <div
                    className="rounded-circle bg-dark text-white d-flex align-items-center justify-content-center flex-shrink-0 me-2 mt-1 shadow-xs"
                    style={{ width: '36px', height: '36px' }}
                  >
                    <i className="bi bi-robot text-info"></i>
                  </div>
                )}

                <div
                  className={`card border-0 shadow-sm rounded-3 ${
                    isUser
                      ? 'bg-primary text-white'
                      : 'bg-white border text-dark'
                  }`}
                  style={{
                    maxWidth: '82%',
                    backgroundColor: isUser ? '#3b82f6' : '#ffffff',
                  }}
                >
                  {/* Message Header */}
                  <div
                    className={`card-header py-1 px-3 d-flex align-items-center justify-content-between border-0 rounded-top-3 ${
                      isUser ? 'bg-transparent text-white-50' : 'bg-light text-muted'
                    }`}
                    style={{ fontSize: '0.75rem' }}
                  >
                    <span className="fw-semibold">
                      {isUser ? 'You' : `AgentFlow Assistant (${msg.model || 'qwen2.5:3b'})`}
                    </span>
                    <div className="d-flex align-items-center gap-2">
                      <span>{msg.timestamp}</span>
                      {!isUser && !msg.isError && (
                        <button
                          type="button"
                          className="btn btn-link btn-sm p-0 text-muted"
                          style={{ fontSize: '0.75rem' }}
                          onClick={() => handleCopyMessage(msg.id, msg.content)}
                          title="Copy message"
                        >
                          {copiedId === msg.id ? (
                            <i className="bi bi-check2 text-success"></i>
                          ) : (
                            <i className="bi bi-clipboard"></i>
                          )}
                        </button>
                      )}
                    </div>
                  </div>

                  {/* Message Body */}
                  <div className="card-body py-2 px-3">
                    <FormattedContent text={msg.content} isError={msg.isError} />
                  </div>
                </div>

                {isUser && (
                  <div
                    className="rounded-circle bg-primary-subtle text-primary d-flex align-items-center justify-content-center flex-shrink-0 ms-2 mt-1 shadow-xs border border-primary-subtle"
                    style={{ width: '36px', height: '36px' }}
                  >
                    <i className="bi bi-person-fill fs-5"></i>
                  </div>
                )}
              </div>
            );
          })}

          {/* Assistant Loading / Thinking Bubble */}
          {isLoading && (
            <div className="d-flex mb-3 justify-content-start align-items-center">
              <div
                className="rounded-circle bg-dark text-white d-flex align-items-center justify-content-center flex-shrink-0 me-2 shadow-xs"
                style={{ width: '36px', height: '36px' }}
              >
                <i className="bi bi-robot text-info"></i>
              </div>
              <div className="card border bg-white shadow-sm rounded-3 p-3">
                <div className="d-flex align-items-center gap-2 text-muted small">
                  <span className="spinner-grow spinner-grow-sm text-primary" role="status" aria-hidden="true"></span>
                  <span className="fw-medium font-monospace">
                    Ollama (qwen2.5:3b) is thinking...
                  </span>
                </div>
              </div>
            </div>
          )}

          <div ref={messagesEndRef} />
        </div>
      </div>

      {/* Bottom Sticky Input Container */}
      <footer className="bg-white border-top py-3 px-3 shadow-lg flex-shrink-0">
        <div className="container" style={{ maxWidth: '860px' }}>
          <form
            onSubmit={(e) => {
              e.preventDefault();
              handleSend();
            }}
          >
            <div className="input-group shadow-sm rounded-3 border">
              <label htmlFor="ai-chat-input" className="visually-hidden">
                Message AI Assistant
              </label>
              <textarea
                id="ai-chat-input"
                ref={textareaRef}
                className="form-control border-0 py-2 px-3 font-monospace"
                rows={2}
                placeholder="Ask AI Assistant anything... (Enter to send, Shift+Enter for new line)"
                value={inputPrompt}
                onChange={(e) => setInputPrompt(e.target.value)}
                onKeyDown={handleKeyDown}
                disabled={isLoading}
                style={{ resize: 'none', fontSize: '0.9rem' }}
              />
              <button
                type="submit"
                id="send-ai-prompt-btn"
                className="btn btn-primary d-flex align-items-center gap-2 px-4 fw-semibold"
                disabled={isLoading || !inputPrompt.trim()}
                title="Send prompt to local Ollama"
              >
                {isLoading ? (
                  <>
                    <span className="spinner-border spinner-border-sm" role="status" aria-hidden="true"></span>
                    <span className="d-none d-sm-inline">Generating...</span>
                  </>
                ) : (
                  <>
                    <i className="bi bi-send-fill"></i>
                    <span className="d-none d-sm-inline">Send</span>
                  </>
                )}
              </button>
            </div>
          </form>

          <div className="d-flex flex-wrap align-items-center justify-content-between mt-2 text-muted" style={{ fontSize: '0.75rem' }}>
            <span>
              <i className="bi bi-keyboard me-1"></i>
              Press <kbd className="bg-light text-dark border">Enter</kbd> to send, <kbd className="bg-light text-dark border">Shift + Enter</kbd> for newline
            </span>
            <span>
              <i className="bi bi-shield-check text-success me-1"></i>
              Local endpoint: <code>POST /api/v1/ai/chat</code> &bull; Model: <code>qwen2.5:3b</code>
            </span>
          </div>
        </div>
      </footer>
    </div>
  );
}

export default AiChat;
