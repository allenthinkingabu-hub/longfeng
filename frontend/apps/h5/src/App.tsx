import React from 'react';
import { Routes, Route, Navigate } from 'react-router-dom';
import { CapturePage } from './pages/Capture';
import { AnalyzingPage } from './pages/Analyzing';
import { ResultPage } from './pages/Result';
import { ListPage } from './pages/List';
import { DetailPage } from './pages/Detail';

/**
 * S7 · H5 路由
 * P02 /capture              → CapturePage  (Mood C dark-camera)
 * P03 /analyzing/:taskId    → AnalyzingPage (Mood C + SSE 4-step)
 * P04 /question/:qid/result → ResultPage   (Mood B pure-warm)
 * P05 /wrongbook            → ListPage
 * P06 /wrongbook/:id        → DetailPage
 */
export const App: React.FC = () => (
  <Routes>
    <Route path="/" element={<Navigate to="/wrongbook" replace />} />
    <Route path="/capture" element={<CapturePage />} />
    <Route path="/analyzing/:taskId" element={<AnalyzingPage />} />
    <Route path="/question/:qid/result" element={<ResultPage />} />
    <Route path="/wrongbook" element={<ListPage />} />
    <Route path="/wrongbook/:id" element={<DetailPage />} />
  </Routes>
);
