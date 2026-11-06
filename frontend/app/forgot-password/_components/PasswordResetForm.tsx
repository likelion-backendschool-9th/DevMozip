'use client';
import styles from '../page.module.css';
import { useState } from 'react';
import { passwordSetErrors } from '@/lib/account/accountAreaService';
import { resetPasswordAction } from '@/lib/auth/actions';
import { Input } from '@/components/common/Input';
import { Button } from '@/components/common/Button';

export function PasswordResetForm({ token, onRestart }: { token: string; onRestart: () => void }) {
    const [newPassword, setNewPassword] = useState('');
    const [confirmPassword, setConfirmPassword] = useState('');
    const [error, setError] = useState('');
    const [expired, setExpired] = useState(false);
    const [busy, setBusy] = useState(false);
    const submit = async (e: React.FormEvent) => {
        e.preventDefault();
        const errors = passwordSetErrors({ newPassword, confirmPassword });
        if (errors.length) {
            setError(errors.join(' '));
            return;
        }
        setBusy(true);
        try {
            const result = await resetPasswordAction({ token, newPassword, confirmPassword });
            if (!result) {
                // 쿠키가 지워진 뒤 새 문서로 이동해 로그인 상태를 다시 확인한다.
                window.location.replace('/login?password=reset');
                return;
            }
            setError(result.error);
            setExpired(!!result.expired);
        } catch {
            setError('요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.');
        }
        setBusy(false);
    };
    if (expired) {
        return (
            <div className={[styles.scope, 'recovery-done'].join(' ')} role="alert">
                <strong>이 링크는 쓸 수 없습니다.</strong>
                <p>{error}</p>
                <Button onClick={onRestart}>재설정 메일 다시 받기</Button>
            </div>
        );
    }
    return (
        <form className={[styles.scope, 'auth-form'].join(' ')} noValidate onSubmit={submit}>
            <label htmlFor="reset-password">새 비밀번호</label>
            <Input
                id="reset-password"
                type="password"
                autoComplete="new-password"
                maxLength={64}
                value={newPassword}
                disabled={busy}
                onChange={e => {
                    setNewPassword(e.target.value);
                    setError('');
                }}
                aria-invalid={!!error}
            />
            <label htmlFor="reset-confirm">새 비밀번호 확인</label>
            <Input
                id="reset-confirm"
                type="password"
                autoComplete="new-password"
                maxLength={64}
                value={confirmPassword}
                disabled={busy}
                onChange={e => {
                    setConfirmPassword(e.target.value);
                    setError('');
                }}
                aria-invalid={!!error}
            />
            {error && (
                <p className={[styles.scope, 'form-error'].join(' ')} role="alert">
                    {error}
                </p>
            )}
            <Button type="submit" disabled={busy} className={[styles.scope, 'auth-submit'].join(' ')}>
                {busy ? '저장 중…' : '비밀번호 재설정'}
            </Button>
        </form>
    );
}
