// S7 · jest-axe ambient shim
declare module 'jest-axe' {
  export function axe(element: Element | string, options?: unknown): Promise<{ violations: unknown[] }>;
  export const toHaveNoViolations: Record<string, (...args: unknown[]) => unknown>;
  export function configureAxe(options?: unknown): (element: Element | string) => Promise<{ violations: unknown[] }>;
}
