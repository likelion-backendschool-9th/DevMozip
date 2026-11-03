'use client';
import { AuthShell } from '@/app/_components/AuthShell';
import styles from './page.module.css';
import { useState } from 'react';
import Link from 'next/link';
import { useNavigation as useNavigate } from '@/lib/navigation';
import { MessageCircle } from 'lucide-react';
import { useAuth } from '@/lib/state/authStore';
import { usePrototype } from '@/lib/state/prototypeStore';
import { validEmail } from '@/lib/account/authService';
import { Input } from '@/components/common/Input';
import { Button } from '@/components/common/Button';
import { Modal } from '@/components/common/Modal';
import { DemoAccounts } from '@/components/auth/DemoAccounts';
function LoginForm() {
    const { user, login } = useAuth();
    const { notify } = usePrototype();
    const navigate = useNavigate();
    const [email, setEmail] = useState('');
    const [password, setPassword] = useState('');
    const [remember, setRemember] = useState(true);
    const [error, setError] = useState('');
    const [busy, setBusy] = useState(false);
    const [kakao, setKakao] = useState(false);
    const submit = async (e: React.FormEvent) => {
        e.preventDefault();
        setError('');
        if (!validEmail(email) || !password) {
            setError('이메일과 비밀번호를 확인해 주세요.');
            return;
        }
        setBusy(true);
        try {
            await login(email, password, remember);
            setPassword('');
            navigate('/');
            notify('로그인되었습니다.');
        } catch (err) {
            setError(err instanceof Error ? err.message : '로그인하지 못했습니다.');
        } finally {
            setBusy(false);
        }
    };
    return (
        <>
            {user && (
                <div className={[styles.scope, 'auth-info'].join(' ')}>
                    현재 {user.nickname} 계정으로 로그인되어 있습니다. 다른 계정으로 전환할 수 있어요.
                </div>
            )}
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
                    <label className={[styles.scope, 'checkbox-label'].join(' ')}>
                        <input
                            type="checkbox"
                            checked={remember}
                            onChange={e => setRemember(e.target.checked)}
                            disabled={busy}
                        />
                        로그인 상태 유지
                    </label>
                    <Link href="/forgot-password">비밀번호 찾기</Link>
                </div>
                <p className={[styles.scope, 'auth-hint'].join(' ')}>
                    체크 해제 시에도 새로고침은 유지되며, 현재 탭을 닫으면 세션이 종료됩니다.
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
            <Button type="button" className={[styles.scope, 'kakao-button'].join(' ')} onClick={() => setKakao(true)}>
                <MessageCircle size={17} />
                카카오로 시작하기
            </Button>
            <p className={[styles.scope, 'auth-switch'].join(' ')}>
                아직 DevMozip 회원이 아니신가요? <Link href="/signup">회원가입</Link>
            </p>
            <DemoAccounts showCredentials onSelected={() => navigate('/')} />
            {kakao && (
                <Modal title="카카오 로그인 데모 안내" onClose={() => setKakao(false)}>
                    <p>
                        카카오 OAuth는 연결하지 않았습니다. 데모 계정을 선택하거나 Mock 이메일 로그인을 이용해 주세요.
                    </p>
                    <Button onClick={() => setKakao(false)}>확인</Button>
                </Modal>
            )}
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
