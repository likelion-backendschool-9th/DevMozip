'use client';
import styles from './KakaoStartButton.module.css';
import { useState, type ReactNode } from 'react';
import { MessageCircle } from 'lucide-react';
import { Button } from '@/components/common/Button';
import { startKakaoAction } from '@/lib/auth/actions';
import type { KakaoIntent } from '@/types/auth';

export function KakaoStartButton({ intent, next, children }: { intent: Extract<KakaoIntent, 'login' | 'signup'>; next?: string; children: ReactNode }) {
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState('');
    const start = async () => {
        setBusy(true);
        setError('');
        try {
            const result = await startKakaoAction(intent, next);
            if ('url' in result) {
                window.location.assign(result.url);
                return;
            }
            setError(result.error);
        } catch {
            setError('카카오 인증을 시작하지 못했습니다. 잠시 후 다시 시도해 주세요.');
        }
        setBusy(false);
    };
    return (
        <>
            <Button type="button" className={[styles.scope, 'kakao-button'].join(' ')} disabled={busy} onClick={start}>
                <MessageCircle size={17} />
                {children}
            </Button>
            {error && <p className="form-error" role="alert">{error}</p>}
        </>
    );
}
