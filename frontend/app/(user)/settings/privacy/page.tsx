'use client';
import styles from './page.module.css';
import { useEffect, useState } from 'react';
import Link from 'next/link';
import { ShieldCheck } from 'lucide-react';
import { useAuth } from '@/lib/state/authStore';
import { KakaoNotice } from '@/app/_components/KakaoNotice';
import { Button } from '@/components/common/Button';
import { Input } from '@/components/common/Input';
import { Modal } from '@/components/common/Modal';
import { mockDataEnabled } from '@/lib/demo/mockVisibility';
import { mockPaymentMethod } from '@/mocks/account';
import { needsFirstPassword, passwordChangeErrors, passwordSetErrors } from '@/lib/account/accountAreaService';
import { kakaoAccountStatusAction, setPasswordAction, startKakaoAction } from '@/lib/auth/actions';
import type { PasswordChangeInput } from '@/types/account';
import type { KakaoAccountStatus } from '@/types/auth';
const blankPassword = (): PasswordChangeInput => ({ currentPassword: '', newPassword: '', confirmPassword: '' });
const passwordFields = [
    { key: 'currentPassword', label: '현재 비밀번호' },
    { key: 'newPassword', label: '새 비밀번호' },
    { key: 'confirmPassword', label: '새 비밀번호 확인' },
] as const;
const ACCOUNT_LOAD_ERROR = '계정 정보를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.';
function AccountSettingsContent() {
    const { accountUser: user, accountArea, accountAct, confirmPasswordChange, administration } = useAuth();
    const [password, setPassword] = useState(blankPassword);
    const [error, setError] = useState('');
    const [success, setSuccess] = useState('');
    const [busy, setBusy] = useState(false);
    const [modal, setModal] = useState<'password' | 'kakao' | 'privacy' | null>(null);
    const [account, setAccount] = useState<KakaoAccountStatus | null>(null);
    const [accountError, setAccountError] = useState('');
    const [kakaoError, setKakaoError] = useState('');
    useEffect(() => {
        let active = true;
        kakaoAccountStatusAction()
            .then(result => {
                if (!active) return;
                if ('error' in result) setAccountError(result.error);
                else setAccount(result);
            })
            .catch(() => {
                if (active) setAccountError(ACCOUNT_LOAD_ERROR);
            });
        return () => {
            active = false;
        };
    }, []);
    // 서버 상태를 받기 전에는 비밀번호 입력을 보여 주지 않는다. 현재 비밀번호 칸이 보였다가 사라지지 않게 하기 위해서다.
    const firstPassword = needsFirstPassword(account);
    const kakaoLinked = !!account?.kakaoLinked;
    const prefs = user ? accountArea.accounts[user.id] : undefined;
    const isPrivate = !!prefs?.isPrivate;
    const premium = user?.role === 'premium';
    const payments = administration.payments.filter(p => p.userId === user?.id);
    return (
        <>
            <header className={[styles.scope, 'personal-page-heading'].join(' ')}>
                <span className={[styles.scope, 'eyebrow'].join(' ')}>YOUR ACCOUNT, YOUR CONTROL</span>
                <h1>개인정보 및 계정 설정</h1>
                <p>가상 계정으로 설정을 체험해 보세요. 실제 개인정보·결제 정보는 입력하지 마세요.</p>
            </header>
            <>
                <section className={[styles.scope, 'account-panel'].join(' ')}>
                    <h2>계정 정보</h2>
                    <label>
                        이메일
                        <Input value={user?.email || ''} readOnly />
                    </label>
                    <p className={[styles.scope, 'auth-hint'].join(' ')}>
                        표시된 이메일은 기존 Mock 계정 정보입니다. 실제 개인정보를 추가로 저장하지 않습니다.
                    </p>
                    <h3>{firstPassword ? '비밀번호 설정' : '비밀번호 변경 확인'}</h3>
                    {!account ? (
                        <p className={[styles.scope, accountError ? 'form-error' : 'auth-hint'].join(' ')} role={accountError ? 'alert' : 'status'}>
                            {accountError || '계정 정보를 불러오고 있어요…'}
                        </p>
                    ) : (
                        <>
                            <p className={[styles.scope, 'auth-hint'].join(' ')}>
                                {firstPassword
                                    ? '비밀번호를 설정하면 이메일과 비밀번호로도 로그인할 수 있습니다.'
                                    : '입력 검증만 체험합니다. 원문·새 비밀번호 해시를 저장하지 않으며 실제 로그인 비밀번호도 바뀌지 않습니다.'}
                            </p>
                            <form
                                className={[styles.scope, 'profile-form account-password-form'].join(' ')}
                                onSubmit={e => {
                                    e.preventDefault();
                                    setSuccess('');
                                    const errors = firstPassword ? passwordSetErrors(password) : passwordChangeErrors(password);
                                    setError(errors.join(' '));
                                    if (!errors.length) setModal('password');
                                }}
                            >
                                {passwordFields
                                    .filter(f => !firstPassword || f.key !== 'currentPassword')
                                    .map(f => (
                                        <label key={f.key}>
                                            {f.label}
                                            <Input
                                                type="password"
                                                autoComplete="off"
                                                maxLength={64}
                                                value={password[f.key]}
                                                disabled={busy}
                                                onChange={e => {
                                                    setPassword({ ...password, [f.key]: e.target.value });
                                                    setError('');
                                                }}
                                            />
                                        </label>
                                    ))}
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
                                    {firstPassword ? '비밀번호 설정' : '비밀번호 변경 확인'}
                                </Button>
                            </form>
                        </>
                    )}
                    {prefs?.passwordConfirmedAt && (
                        <p className={[styles.scope, 'auth-hint'].join(' ')}>
                            최근 데모 확인: {new Date(prefs.passwordConfirmedAt).toLocaleString('ko-KR')}
                        </p>
                    )}
                </section>
                <section className={[styles.scope, 'account-panel'].join(' ')}>
                    <h2>카카오 계정</h2>
                    <KakaoNotice />
                    <div className={[styles.scope, 'account-setting-row'].join(' ')}>
                        <div>
                            <strong>{kakaoLinked ? '연동됨' : '연동되지 않음'}</strong>
                            <p>
                                {firstPassword
                                    ? '비밀번호를 먼저 설정해야 연동을 해제할 수 있습니다.'
                                    : kakaoLinked
                                      ? '카카오 계정으로도 로그인할 수 있습니다.'
                                      : '연동하면 카카오 계정으로도 로그인할 수 있습니다.'}
                            </p>
                        </div>
                        <Button variant="secondary" disabled={!account || firstPassword || busy} onClick={() => setModal('kakao')}>
                            {kakaoLinked ? '연동 해제' : '카카오 연동'}
                        </Button>
                    </div>
                    {kakaoError && (
                        <p className={[styles.scope, 'form-error'].join(' ')} role="alert">
                            {kakaoError}
                        </p>
                    )}
                </section>
                <section className={[styles.scope, 'account-panel'].join(' ')}>
                    <h2>결제 정보</h2>
                    <dl className={[styles.scope, 'account-details'].join(' ')}>
                        <div>
                            <dt>현재 구독</dt>
                            <dd>{user?.role === 'admin' ? '관리자' : premium ? '프리미엄 활성' : '무료 요금제'}</dd>
                        </div>
                        <div>
                            <dt>결제 수단</dt>
                            <dd>{premium && mockDataEnabled() ? mockPaymentMethod : '등록된 결제 수단 없음'}</dd>
                        </div>
                    </dl>
                    <h3>결제 내역 예시</h3>
                    {payments.length ? (
                        <ul className={[styles.scope, 'payment-list'].join(' ')}>
                            {payments.map(p => (
                                <li key={p.id}>
                                    <time>{p.date}</time>
                                    <span>{p.status}</span>
                                    <b>₩{p.amount.toLocaleString()}</b>
                                </li>
                            ))}
                        </ul>
                    ) : (
                        <p>표시할 결제 내역이 없습니다.</p>
                    )}
                    <Link className={[styles.scope, 'button secondary'].join(' ')} href="/subscription">
                        구독 관리 페이지로 이동
                    </Link>
                </section>
                <section className={[styles.scope, 'account-panel'].join(' ')}>
                    <h2>
                        <ShieldCheck size={20} /> 계정 공개 설정
                    </h2>
                    <div className={[styles.scope, 'account-setting-row'].join(' ')}>
                        <div>
                            <strong>{isPrivate ? '비공개 계정' : '공개 계정'}</strong>
                            <p>
                                {isPrivate
                                    ? '다른 사용자에게 프로필과 작성한 SNS 게시글이 표시되지 않습니다. 본인은 모든 기능을 그대로 이용할 수 있습니다.'
                                    : '다른 사용자가 내 프로필과 SNS 게시글을 볼 수 있습니다.'}
                            </p>
                        </div>
                        <Button variant="secondary" onClick={() => setModal('privacy')}>
                            {isPrivate ? '공개 계정으로 전환' : '비공개 계정으로 전환'}
                        </Button>
                    </div>
                </section>
            </>
            {modal && (
                <Modal
                    title={
                        modal === 'password'
                            ? firstPassword
                                ? '비밀번호를 설정할까요?'
                                : '비밀번호 변경을 확인할까요?'
                            : modal === 'privacy'
                              ? isPrivate
                                  ? '계정을 공개할까요?'
                                  : '계정을 비공개로 전환할까요?'
                              : kakaoLinked
                                ? '카카오 연동을 해제할까요?'
                                : '카카오 계정을 연동할까요?'
                    }
                    onClose={() => setModal(null)}
                >
                    <p>
                        {modal === 'password'
                            ? firstPassword
                                ? '설정한 비밀번호로 이메일 로그인도 할 수 있습니다.'
                                : '현재 비밀번호가 맞는지 확인하고 데모 변경 확인 시각만 기록합니다. 입력한 새 비밀번호와 실제 로그인 정보는 저장·변경하지 않습니다.'
                            : modal === 'privacy'
                              ? isPrivate
                                  ? '다른 사용자가 내 프로필과 작성한 SNS 게시글을 다시 볼 수 있습니다.'
                                  : '프로필 상세와 작성한 SNS 게시글을 다른 사용자에게 숨깁니다. 로그인과 본인의 활동은 그대로 유지됩니다.'
                              : kakaoLinked
                                ? '카카오 인증을 한 번 더 진행한 뒤 연동을 해제합니다. 이후에는 이메일과 비밀번호로 로그인합니다.'
                                : '카카오 로그인 화면으로 이동합니다. 인증을 마치면 이 계정에 연동됩니다.'}
                    </p>
                    <div className={[styles.scope, 'modal-actions'].join(' ')}>
                        <Button variant="secondary" disabled={busy} onClick={() => setModal(null)}>
                            취소
                        </Button>
                        <Button
                            disabled={busy}
                            onClick={async () => {
                                if (modal === 'password') {
                                    const input = password;
                                    setPassword(blankPassword());
                                    setBusy(true);
                                    setModal(null);
                                    try {
                                        if (firstPassword) {
                                            const result = await setPasswordAction(input);
                                            if (result?.error) throw new Error(result.error);
                                            setAccount({ kakaoLinked, passwordSet: true });
                                            setSuccess('비밀번호를 설정했습니다. 이제 이메일과 비밀번호로도 로그인할 수 있습니다.');
                                        } else {
                                            await confirmPasswordChange(input);
                                            setSuccess(
                                                '데모 변경 확인을 완료했습니다. 실제 로그인 비밀번호는 변경되지 않았습니다.',
                                            );
                                        }
                                    } catch (e) {
                                        setError(e instanceof Error ? e.message : '입력을 확인해 주세요.');
                                    } finally {
                                        setBusy(false);
                                    }
                                } else if (modal === 'kakao') {
                                    setBusy(true);
                                    setKakaoError('');
                                    try {
                                        const result = await startKakaoAction(kakaoLinked ? 'unlink' : 'link');
                                        if ('url' in result) {
                                            window.location.assign(result.url);
                                            return;
                                        }
                                        setKakaoError(result.error);
                                    } catch {
                                        setKakaoError('카카오 인증을 시작하지 못했습니다. 잠시 후 다시 시도해 주세요.');
                                    }
                                    setBusy(false);
                                    setModal(null);
                                } else {
                                    if (accountAct({ type: 'privacy', isPrivate: !isPrivate })) setModal(null);
                                }
                            }}
                        >
                            변경 확인
                        </Button>
                    </div>
                </Modal>
            )}
        </>
    );
}
export default function AccountSettingsPage() {
    const { accountUser } = useAuth();
    return <AccountSettingsContent key={accountUser?.id || 'guest'} />;
}
