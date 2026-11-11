'use client';

import { useState } from 'react';
import { useSearchParams } from 'next/navigation';
import { Button } from '@/components/common/Button';
import { Input } from '@/components/common/Input';
import { startKakaoAction, withdrawAccountAction } from '@/lib/auth/actions';
import { getUser, syncServerSession } from '@/lib/state/authStore';
import { clearWithdrawalLocalState } from '@/lib/auth/withdrawalLocalState';
import type { KakaoAccountStatus } from '@/types/auth';
import styles from './AccountWithdrawalPanel.module.css';

export function AccountWithdrawalPanel({ account }: { account: KakaoAccountStatus | null }) {
    const [password, setPassword] = useState('');
    const [confirmed, setConfirmed] = useState(false);
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState('');
    const [kakaoExpired, setKakaoExpired] = useState(false);
    const searchParams = useSearchParams();
    const kakaoReady = searchParams.get('withdrawal') === 'kakao-ready' && !kakaoExpired;

    async function authenticate() {
        setBusy(true); setError('');
        try {
            const result = await startKakaoAction('withdraw', '/settings/privacy');
            if ('error' in result) setError(result.error);
            else { window.location.assign(result.url); return; }
        } catch { setError('카카오 인증을 시작하지 못했습니다. 다시 시도해 주세요.'); }
        setBusy(false);
    }

    async function withdraw() {
        setBusy(true); setError('');
        const supplied = password;
        const currentUser = getUser();
        setPassword('');
        try {
            const result = await withdrawAccountAction({ currentPassword: supplied, confirmed });
            if (result) {
                setError(result.error); setKakaoExpired(true);
            } else {
                const cleared = clearWithdrawalLocalState(currentUser);
                syncServerSession({ identity: null });
                window.location.replace(`/login?withdrawal=complete${cleared ? '' : '&local=cleanup-failed'}`);
                return;
            }
        } catch { setError('탈퇴 결과를 확인하지 못했습니다. 로그인 상태를 확인해 주세요.'); }
        setBusy(false);
    }

    return <section className={styles.panel} aria-labelledby="withdrawal-title">
        <h2 id="withdrawal-title">회원 탈퇴</h2>
        <p>탈퇴하면 로그인 정보와 프로필 개인정보가 삭제됩니다. 프로젝트·참여·리뷰·대화 등 기존 기록에는 닉네임이 남으며, 재가입해도 과거 기록은 연결되지 않습니다.</p>
        <p>주최 중인 미종료 프로젝트는 먼저 종료하거나 삭제해 주세요. 프리미엄은 즉시 종료되며 남은 이용기간과 결제 금액은 환불되지 않습니다. 거래 기록은 개인정보를 제거한 상태로 보관됩니다.</p>
        {!account ? <p role="status">계정 정보를 불러온 후 탈퇴할 수 있습니다.</p> : <>
            {account.kakaoLinked && <div className={styles.authentication}>
                <p>{kakaoReady ? '카카오 인증에서 돌아왔습니다. 아래 내용을 확인하고 탈퇴를 완료해 주세요.' : '연동된 카카오 계정으로 다시 인증해 주세요.'}</p>
                <Button variant="secondary" disabled={busy} onClick={authenticate}>{kakaoReady ? '카카오 다시 인증' : '카카오 본인 확인'}</Button>
            </div>}
            {account.passwordSet && <label className={styles.password}>현재 비밀번호
                <Input type="password" autoComplete="current-password" value={password} disabled={busy} maxLength={256} onChange={event => setPassword(event.target.value)} />
            </label>}
            <label className={styles.confirmation}>
                <input type="checkbox" checked={confirmed} disabled={busy} onChange={event => setConfirmed(event.target.checked)} />
                <span>개인정보 삭제, 기존 기록의 닉네임 보존, 재가입 시 기록 미연결 및 환불 불가를 확인했습니다.</span>
            </label>
            {error && <p role="alert" className={styles.error}>{error}</p>}
            <Button className={styles.danger} disabled={busy || !confirmed || (account.passwordSet && !password) || (account.kakaoLinked && !kakaoReady)} onClick={withdraw}>
                {busy ? '처리 중…' : '회원 탈퇴하기'}
            </Button>
        </>}
    </section>;
}
