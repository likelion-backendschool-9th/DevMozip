import { createContext, useContext, useState, type ReactNode } from 'react';
import { defaultSettings, loadSettings, saveSettings } from '../../services/settingsService';
import type { PrototypeSettings } from '../../types';
interface PrototypeContextValue { settings: PrototypeSettings; toggleGuide: () => void; reset: () => void; notify: (message: string) => void; toast: string }
const PrototypeContext = createContext<PrototypeContextValue | null>(null);
export function PrototypeProvider({ children }: { children: ReactNode }) {
 const [settings, setSettings] = useState(loadSettings);
 const [toast, setToast] = useState('');
 const update = (next: PrototypeSettings, message: string) => { setSettings(next); setToast(saveSettings(next) ? message : `${message} (브라우저 저장 공간을 사용할 수 없어 이번 세션에만 유지됩니다.)`); };
 return <PrototypeContext.Provider value={{ settings, toast, notify: setToast, toggleGuide: () => update({ ...settings, showGuide: !settings.showGuide }, '안내 표시 설정을 변경했습니다.'), reset: () => update({ ...defaultSettings }, '발표용 초기 설정으로 복원했습니다.') }}>{children}</PrototypeContext.Provider>;
}
export function usePrototype() { const context = useContext(PrototypeContext); if (!context) throw new Error('PrototypeProvider가 필요합니다.'); return context; }
