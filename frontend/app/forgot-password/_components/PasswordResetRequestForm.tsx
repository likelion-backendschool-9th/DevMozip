'use client';
import styles from '../page.module.css';
import { useState } from 'react';
import Link from 'next/link';
import { validEmail } from '@/lib/account/authService';
import { requestPasswordResetAction } from '@/lib/auth/actions';
import { Input } from '@/components/common/Input';
import { Button } from '@/components/common/Button';

// 가입 여부와 관계없이 같은 안내를 보여 준다. 오류는 요청 자체를 처리하지 못했을 때만 표시한다.
export function PasswordResetRequestForm() {
    const [email, setEmail] = useState('');
    const [error, setError] = useState('');
    const [busy, setBusy] = useState(false);
    const [done, setDone] = useState(false);
    const submit = async (e: React.FormEvent) => {
        e.preventDefault();
        if (!validEmail(email)) {
            setError('올바른 이메일 주소를 입력해 주세요.');
            return;
        }
        setBusy(true);
        try {
            const result = await requestPasswordResetAction(email);
            if (result) setError(result.error);
            else setDone(true);
        } catch {
            setError('요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.');
        }
        setBusy(false);
    };
    return done ? (
        <div className={[styles.scope, 'recovery-done'].join(' ')} role="status">
            <strong>메일함을 확인해 주세요.</strong>
            <p>가입된 이메일이라면 안내 메일을 보냈습니다. 메일이 보이지 않으면 스팸함도 확인해 주세요.</p>
            <Link className={[styles.scope, 'button'].join(' ')} href="/login">
                로그인으로 돌아가기
            </Link>
            <Button
                variant="secondary"
                onClick={() => {
                    setDone(false);
                    setEmail('');
                }}
            >
                다시 입력
            </Button>
        </div>
    ) : (
        <form className={[styles.scope, 'auth-form'].join(' ')} noValidate onSubmit={submit}>
            <label htmlFor="recovery-email">가입한 이메일</label>
            <Input
                id="recovery-email"
                type="email"
                autoComplete="email"
                maxLength={254}
                value={email}
                disabled={busy}
                onChange={e => {
                    setEmail(e.target.value);
                    setError('');
                }}
                placeholder="you@example.com"
                aria-invalid={!!error}
            />
            {error && (
                <p className={[styles.scope, 'form-error'].join(' ')} role="alert">
                    {error}
                </p>
            )}
            <Button type="submit" disabled={busy} className={[styles.scope, 'auth-submit'].join(' ')}>
                {busy ? '요청 중…' : '재설정 메일 받기'}
            </Button>
            <p className={[styles.scope, 'auth-switch'].join(' ')}>
                <Link href="/login">로그인으로 돌아가기</Link>
            </p>
        </form>
    );
}
