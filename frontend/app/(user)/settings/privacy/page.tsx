'use client';
import styles from './page.module.css';
import { useEffect, useState } from 'react';
import { useAuth } from '@/lib/state/authStore';
import { KakaoNotice } from '@/app/_components/KakaoNotice';
import { Button } from '@/components/common/Button';
import { Input } from '@/components/common/Input';
import { Modal } from '@/components/common/Modal';
import { needsFirstPassword, passwordChangeErrors, passwordSetErrors } from '@/lib/account/accountAreaService';
import { changePasswordAction, kakaoAccountStatusAction, setPasswordAction, startKakaoAction } from '@/lib/auth/actions';
import { AccountLockPanel } from './_components/AccountLockPanel';
import { AccountPasswordForm, passwordCopy } from './_components/AccountPasswordForm';
import { PaymentInfoPanel } from './_components/PaymentInfoPanel';
import type { PasswordChangeInput } from '@/types/account';
import type { KakaoAccountStatus } from '@/types/auth';
const blankPassword = (): PasswordChangeInput => ({ currentPassword: '', newPassword: '', confirmPassword: '' });
const ACCOUNT_LOAD_ERROR = '계정 정보를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.';
function AccountSettingsContent() {
    const { accountUser: user } = useAuth();
    const [password, setPassword] = useState(blankPassword);
    const [error, setError] = useState('');
    const [success, setSuccess] = useState('');
    const [busy, setBusy] = useState(false);
    const [modal, setModal] = useState<'password' | 'kakao' | null>(null);
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
    const passwordText = firstPassword ? passwordCopy.first : passwordCopy.change;
    const kakaoLinked = !!account?.kakaoLinked;
    return (
        <>
            <header className={[styles.scope, 'personal-page-heading'].join(' ')}>
                <span className={[styles.scope, 'eyebrow'].join(' ')}>YOUR ACCOUNT, YOUR CONTROL</span>
                <h1>개인정보 및 계정 설정</h1>
                <p>실제 개인정보·결제 정보는 입력하지 마세요.</p>
            </header>
            <>
                <section className={[styles.scope, 'account-panel'].join(' ')}>
                    <h2>계정 정보</h2>
                    <label>
                        이메일
                        <Input value={user?.email || ''} readOnly />
                    </label>
                    <p className={[styles.scope, 'auth-hint'].join(' ')}>
                        로그인한 계정의 이메일입니다.
                    </p>
                    <h3>{passwordText.heading}</h3>
                    {!account ? (
                        <p className={[styles.scope, accountError ? 'form-error' : 'auth-hint'].join(' ')} role={accountError ? 'alert' : 'status'}>
                            {accountError || '계정 정보를 불러오고 있어요…'}
                        </p>
                    ) : (
                        <AccountPasswordForm
                            firstPassword={firstPassword}
                            value={password}
                            error={error}
                            success={success}
                            busy={busy}
                            onChange={value => {
                                setPassword(value);
                                setError('');
                            }}
                            onSubmit={() => {
                                setSuccess('');
                                const errors = firstPassword ? passwordSetErrors(password) : passwordChangeErrors(password);
                                setError(errors.join(' '));
                                if (!errors.length) setModal('password');
                            }}
                        />
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
                <PaymentInfoPanel role={user?.role} />
                <AccountLockPanel />
            </>
            {modal && (
                <Modal
                    title={
                        modal === 'password'
                            ? passwordText.modalTitle
                            : kakaoLinked
                              ? '카카오 연동을 해제할까요?'
                              : '카카오 계정을 연동할까요?'
                    }
                    onClose={() => setModal(null)}
                >
                    <p>
                        {modal === 'password'
                            ? passwordText.modalBody
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
                                            const result = await changePasswordAction(input);
                                            if (result?.error) throw new Error(result.error);
                                            // 쿠키가 지워진 뒤 새 문서로 이동해 로그인 상태를 다시 확인한다.
                                            window.location.replace('/login?password=changed');
                                        }
                                    } catch (e) {
                                        setError(e instanceof Error ? e.message : '입력을 확인해 주세요.');
                                    } finally {
                                        setBusy(false);
                                    }
                                } else {
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
