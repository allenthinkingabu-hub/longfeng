/**
 * MSW · 全 handler 注册（B 轨 e2e 用）
 * S9 扩展：review / guest / share / observer / ai-models / calendar
 * S7 扩展：auth (wechat-login)
 */
import { setupWorker } from 'msw/browser';
import { wrongbookHandlers } from './handlers/wrongbook';
import { captureHandlers } from './handlers/capture';
import { detailHandlers } from './handlers/detail';
import { analyzingHandlers } from './handlers/analyzing';
import { reviewHandlers } from './handlers/review';
import { guestHandlers } from './handlers/guest';
import { shareHandlers } from './handlers/share';
import { observerHandlers } from './handlers/observer';
import { aiModelsHandlers } from './handlers/ai-models';
import { calendarHandlers } from './handlers/calendar';
import { authHandlers } from './handlers/auth';

export const worker = setupWorker(
  ...wrongbookHandlers,
  ...captureHandlers,
  ...detailHandlers,
  ...analyzingHandlers,
  ...reviewHandlers,
  ...guestHandlers,
  ...shareHandlers,
  ...observerHandlers,
  ...aiModelsHandlers,
  ...calendarHandlers,
  ...authHandlers,
);
