import type { PrototypeSettings } from '../types';
export const SETTINGS_KEY = 'devmozip:prototype:v1';
export const defaultSettings: PrototypeSettings = { version: 1, showGuide: true };
export function parseSettings(raw: string | null): PrototypeSettings {
 try { const value: unknown = JSON.parse(raw || 'null');
  if (value && typeof value === 'object' && 'version' in value && value.version === 1 && 'showGuide' in value && typeof value.showGuide === 'boolean') return { version: 1, showGuide: value.showGuide };
 } catch { /* 손상된 저장값은 초기 상태로 복원 */ }
 return { ...defaultSettings };
}
export function loadSettings(): PrototypeSettings {
 try { return parseSettings(localStorage.getItem(SETTINGS_KEY)); } catch { return { ...defaultSettings }; }
}
export function saveSettings(value: PrototypeSettings): boolean {
 try { localStorage.setItem(SETTINGS_KEY, JSON.stringify(value)); return true; } catch { return false; }
}
