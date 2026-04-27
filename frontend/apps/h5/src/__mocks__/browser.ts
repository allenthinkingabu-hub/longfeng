import { setupWorker } from 'msw/browser';
import { wrongbookHandlers } from './handlers/wrongbook';
import { captureHandlers } from './handlers/capture';
import { detailHandlers } from './handlers/detail';

export const worker = setupWorker(...wrongbookHandlers, ...captureHandlers, ...detailHandlers);
