// Presentation-only record IDs; no credentials or authentication tokens.
export function createMockId(){return globalThis.crypto?.randomUUID?.() || `mock-${Date.now().toString(36)}-${Math.random().toString(36).slice(2,12)}`;}
