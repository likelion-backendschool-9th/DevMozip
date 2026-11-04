'use client';
import { createContext, useContext, useEffect, useState, type ReactNode } from 'react';
import { useAuth } from '@/lib/state/authStore';
import { fetchOwnProfile } from '@/lib/profiles/profileUpdate';
import type { ProfileDetail } from '@/types/profileDetail';
type OwnState = { profile: ProfileDetail | null; loading: boolean; error: string; retry: () => void; update: (profile: ProfileDetail) => void };
const OwnProfileContext = createContext<OwnState>({ profile: null, loading: false, error: '', retry: () => {}, update: () => {} });
export const useOwnProfile = () => useContext(OwnProfileContext);
export function OwnProfileProvider({ children }: { children: ReactNode }) {
    const { user } = useAuth();
    const email = user?.email;
    const [state, setState] = useState<{ email?: string; profile: ProfileDetail | null; loading: boolean; error: string }>({ profile: null, loading: false, error: '' });
    const [attempt, setAttempt] = useState(0);
    useEffect(() => {
        if (!email) return;
        const controller = new AbortController();
        setState({ email, profile: null, loading: true, error: '' });
        void fetchOwnProfile(controller.signal).then(profile => {
            if (!controller.signal.aborted) setState({ email, profile, loading: false, error: '' });
        }).catch(error => {
            if (!controller.signal.aborted) setState({ email, profile: null, loading: false, error: error instanceof Error ? error.message : '내 프로필을 불러오지 못했어요.' });
        });
        return () => controller.abort();
    }, [email, attempt]);
    const visible = email && state.email === email ? state : { profile: null, loading: !!email, error: '' };
    return <OwnProfileContext.Provider value={{ ...visible, retry: () => setAttempt(v => v + 1), update: profile => setState({ email, profile, loading: false, error: '' }) }}>{children}</OwnProfileContext.Provider>;
}
