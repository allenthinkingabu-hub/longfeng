import React from 'react';
import { Routes, Route, Navigate } from 'react-router-dom';
import { CapturePage } from './pages/Capture';
import { ListPage } from './pages/List';
import { DetailPage } from './pages/Detail';

/** S7 · H5 路由 · 双端 route name 对齐（ADR 0014）· home/capture/wrongbook/detail/review/profile */
export const App: React.FC = () => (
  <Routes>
    <Route path="/" element={<Navigate to="/wrongbook" replace />} />
    <Route path="/capture" element={<CapturePage />} />
    <Route path="/wrongbook" element={<ListPage />} />
    <Route path="/wrongbook/:id" element={<DetailPage />} />
  </Routes>
);
