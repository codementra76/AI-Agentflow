import React from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import Dashboard from './pages/Dashboard';
import WorkflowBuilder from './pages/WorkflowBuilder';
import AiChat from './pages/AiChat';

function App() {
  return (
    <BrowserRouter>
      <Routes>
        {/* Main Dashboard Route */}
        <Route path="/" element={<Dashboard />} />

        {/* Visual Workflow Builder Route */}
        <Route path="/builder" element={<WorkflowBuilder />} />

        {/* AI Assistant Chat Route */}
        <Route path="/chat" element={<AiChat />} />
        <Route path="/ai" element={<Navigate to="/chat" replace />} />
        <Route path="/ai-chat" element={<Navigate to="/chat" replace />} />

        {/* Catch-all redirect to Dashboard */}
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </BrowserRouter>
  );
}


export default App;
