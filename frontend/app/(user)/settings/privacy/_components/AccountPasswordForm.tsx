'use client';
import styles from '../page.module.css';
import linkStyles from './AccountPasswordForm.module.css';
import Link from 'next/link';
import { Button } from '@/components/common/Button';
import { Input } from '@/components/common/Input';
import type { PasswordChangeInput } from '@/types/account';

// 비밀번호 없는 카카오 회원의 첫 설정과 비밀번호가 있는 회원의 변경은 입력 칸과 안내가 다르다.
export const passwordCopy = {
    first: {
        heading: '비밀번호 설정',
        hint: '비밀번호를 설정하면 이메일과 비밀번호로도 로그인할 수 있습니다.',
        submit: '비밀번호 설정',
        modalTitle: '비밀번호를 설정할까요?',
        modalBody: '설정한 비밀번호로 이메일 로그인도 할 수 있습니다.',
    },
    change: {
        heading: '비밀번호 변경',
        hint: '변경하면 이 기기를 포함한 모든 기기에서 로그아웃되고, 새 비밀번호로 다시 로그인합니다.',
        submit: '비밀번호 변경',
        modalTitle: '비밀번호를 변경할까요?',
        modalBody: '현재 비밀번호를 확인한 뒤 변경합니다. 이 기기를 포함한 모든 기기에서 로그아웃되고, 새 비밀번호로 다시 로그인해야 합니다.',
    },
};

const fields = [
    { key: 'currentPassword', label: '현재 비밀번호' },
    { key: 'newPassword', label: '새 비밀번호' },
    { key: 'confirmPassword', label: '새 비밀번호 확인' },
] as const;

export function AccountPasswordForm({ firstPassword, value, error, success, busy, onChange, onSubmit }: {
    firstPassword: boolean;
    value: PasswordChangeInput;
    error: string;
    success: string;
    busy: boolean;
    onChange: (value: PasswordChangeInput) => void;
    onSubmit: () => void;
}) {
    const copy = firstPassword ? passwordCopy.first : passwordCopy.change;
    return (
        <>
            <p className={[styles.scope, 'auth-hint'].join(' ')}>{copy.hint}</p>
            <form
                className={[styles.scope, 'profile-form account-password-form'].join(' ')}
                onSubmit={e => {
                    e.preventDefault();
                    onSubmit();
                }}
            >
                {fields
                    .filter(f => !firstPassword || f.key !== 'currentPassword')
                    .map(f => (
                        <label key={f.key}>
                            {f.label}
                            <Input
                                type="password"
                                autoComplete="off"
                                maxLength={64}
                                value={value[f.key]}
                                disabled={busy}
                                onChange={e => onChange({ ...value, [f.key]: e.target.value })}
                            />
                        </label>
                    ))}
                {!firstPassword && (
                    <p className={[styles.scope, 'auth-hint'].join(' ')}>
                        현재 비밀번호를 모른다면 <Link className={linkStyles.link} href="/forgot-password">메일로 재설정</Link>할 수 있습니다.
                    </p>
                )}
                {error && (
                    <p className={[styles.scope, 'form-error'].join(' ')} role="alert">
                        {error}
                    </p>
                )}
                {success && (
                    <p role="status" className={[styles.scope, 'check-success'].join(' ')}>
                        {success}
                    </p>
                )}
                <Button type="submit" disabled={busy}>
                    {copy.submit}
                </Button>
            </form>
        </>
    );
}
