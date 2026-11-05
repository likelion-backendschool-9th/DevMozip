'use client';
import { loginAction } from '@/lib/auth/actions';
import { useSearchParams } from 'next/navigation';
import { AuthShell } from '@/app/_components/AuthShell';
import { KakaoNotice } from '@/app/_components/KakaoNotice';
import { KakaoStartButton } from '@/app/_components/KakaoStartButton';
import styles from './page.module.css';
import { startTransition, useState } from 'react';
import Link from 'next/link';
import { useAuth } from '@/lib/state/authStore';
import { validEmail } from '@/lib/account/authService';
import { Input } from '@/components/common/Input';
import { Button } from '@/components/common/Button';
function LoginForm() {
    const { user } = useAuth();
    const searchParams = useSearchParams();
    const [email, setEmail] = useState('');
    const [password, setPassword] = useState('');
    const [error, setError] = useState('');
    const [busy, setBusy] = useState(false);
    const submit = (e: React.FormEvent) => {
        e.preventDefault();
        setError('');
        if (!validEmail(email) || !password) {
            setError('이메일과 비밀번호를 확인해 주세요.');
            return;
        }
        const form = new FormData();
        form.set('username', email);
        form.set('password', password);
        form.set('next', searchParams.get('next') ?? '/');
        setBusy(true);
        // 쿠키 설정 응답을 받은 후 새 문서로 이동해 로그인 세션을 다시 확인한다.
        startTransition(async () => {
            try {
                const result = await loginAction(form);
                if ('error' in result) {
                    setError(result.error);
                    setBusy(false);
                    return;
                }
                window.location.replace(result.redirectTo);
            } catch {
                setError('로그인 처리 중 문제가 발생했습니다. 다시 시도해 주세요.');
                setBusy(false);
            }
        });
    };
    return (
        <>
            {user && (
                <div className={[styles.scope, 'auth-info'].join(' ')}>
                    현재 {user.nickname} 계정으로 로그인되어 있습니다. 다른 계정으로 전환할 수 있어요.
                </div>
            )}
            <KakaoNotice />
            <form className={[styles.scope, 'auth-form'].join(' ')} onSubmit={submit} noValidate>
                <label htmlFor="login-email">이메일</label>
                <Input
                    id="login-email"
                    type="email"
                    autoComplete="username"
                    placeholder="you@example.com"
                    value={email}
                    onChange={e => {
                        setEmail(e.target.value);
                        setError('');
                    }}
                    disabled={busy}
                    maxLength={254}
                    aria-invalid={!!error}
                    aria-describedby={error ? 'login-error' : undefined}
                    required
                />
                <label htmlFor="login-password">비밀번호</label>
                <Input
                    id="login-password"
                    type="password"
                    autoComplete="current-password"
                    placeholder="비밀번호를 입력해 주세요"
                    value={password}
                    onChange={e => {
                        setPassword(e.target.value);
                        setError('');
                    }}
                    disabled={busy}
                    maxLength={64}
                    aria-invalid={!!error}
                    aria-describedby={error ? 'login-error' : undefined}
                    required
                />
                <div className={[styles.scope, 'auth-options'].join(' ')}>
                    <Link href="/forgot-password">비밀번호 찾기</Link>
                </div>
                <p className={[styles.scope, 'auth-hint'].join(' ')}>
                    서버에 등록된 계정으로 로그인합니다. 세션은 보안 쿠키로 관리됩니다.
                </p>
                {error && (
                    <p id="login-error" className={[styles.scope, 'form-error'].join(' ')} role="alert">
                        {error}
                    </p>
                )}
                <Button className={[styles.scope, 'auth-submit'].join(' ')} disabled={busy} type="submit">
                    {busy ? '로그인 중…' : '로그인'}
                </Button>
            </form>
            <KakaoStartButton intent="login" next={searchParams.get('next') ?? '/'}>
                카카오로 시작하기
            </KakaoStartButton>
            <p className={[styles.scope, 'auth-switch'].join(' ')}>
                아직 DevMozip 회원이 아니신가요? <Link href="/signup">회원가입</Link>
            </p>
        </>
    );
}

export default function LoginPage() {
    return (
        <AuthShell
            eyebrow="WELCOME BACK"
            title="다시 만나 반가워요"
            description="함께 만들 다음 프로젝트가 기다리고 있어요."
        >
            <LoginForm />
        </AuthShell>
    );
}
