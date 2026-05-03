import React from 'react';
import { Routes, Route, Navigate } from 'react-router-dom';
import { CapturePage } from './pages/Capture';
import { ListPage } from './pages/List';
import { DetailPage } from './pages/Detail';
import { LandingPage } from './pages/Landing';
import { GuestCapturePage } from './pages/GuestCapture';
import { SharedPage } from './pages/Shared';

/** S7/S8 · H5 路由 · 双端 route name 对齐（ADR 0014）· home/capture/wrongbook/detail/review/profile + FE-06 anon pages */
export const App: React.FC = () => (
  <Routes>
    <Route path="/" element={<Navigate to="/wrongbook" replace />} />
    <Route path="/capture" element={<CapturePage />} />
    <Route path="/wrongbook" element={<ListPage />} />
    <Route path="/wrongbook/:id" element={<DetailPage />} />
    {/* FE-06 · Anonymous pages (S7/S8 cross-phase) */}
    <Route path="/welcome" element={<LandingPage />} />
    <Route path="/guest/capture" element={<GuestCapturePage />} />
    <Route path="/s/:shareToken" element={<SharedPage />} />
  </Routes>
);
