'use client';
import styles from '../page.module.css';
import { useEffect, useState } from 'react';
import { ShieldCheck } from 'lucide-react';
import { Button } from '@/components/common/Button';
import { Modal } from '@/components/common/Modal';
import { changeAccountLock, loadAccountLock, type AccountLockState } from '@/lib/account/accountLock';
import { accountLockStatusAction, updateAccountLockAction } from '@/lib/account/accountLockActions';
import { useAuth } from '@/lib/state/authStore';
import { useMockVisibility } from '@/lib/state/mockVisibilityStore';

const scope = (name: string) => [styles.scope, name].join(' ');

/** 계정 공개 영역의 표시(WBS 17.7). 상태와 버튼 동작만 받는다. 문구는 기존 Mock 화면과 같다. */
export function AccountLockView({ state, busy, error, onRequest, onRetry }: {
    state: AccountLockState;
    busy: boolean;
    error: string;
    onRequest: () => void;
    onRetry?: () => void;
}) {
    const isPrivate = state.status === 'ready' && state.isPrivate;
    return (
        <section className={scope('account-panel')}>
            <h2>
                <ShieldCheck size={20} /> 계정 공개 설정
            </h2>
            {state.status === 'loading' ? (
                <p className={scope('auth-hint')} role="status">공개 설정을 불러오고 있어요…</p>
            ) : state.status === 'error' ? (
                <div className={scope('account-setting-row')}>
                    <p className={scope('form-error')} role="alert">{state.message}</p>
                    {onRetry && <Button variant="secondary" onClick={onRetry}>다시 시도</Button>}
                </div>
            ) : (
                <div className={scope('account-setting-row')}>
                    <div>
                        <strong>{isPrivate ? '비공개 계정' : '공개 계정'}</strong>
                        <p>
                            {isPrivate
                                ? '다른 사용자에게 프로필과 작성한 SNS 게시글이 표시되지 않습니다. 본인은 모든 기능을 그대로 이용할 수 있습니다.'
                                : '다른 사용자가 내 프로필과 SNS 게시글을 볼 수 있습니다.'}
                        </p>
                    </div>
                    <Button variant="secondary" disabled={busy} aria-busy={busy} onClick={onRequest}>
                        {isPrivate ? '공개 계정으로 전환' : '비공개 계정으로 전환'}
                    </Button>
                </div>
            )}
            {error && (
                <p className={scope('form-error')} role="alert">
                    {error}
                </p>
            )}
        </section>
    );
}

/** 전환 확인 창. 현재 값의 반대로 바꾼다. */
export function AccountLockConfirm({ isPrivate, busy, onCancel, onConfirm }: {
    isPrivate: boolean;
    busy: boolean;
    onCancel: () => void;
    onConfirm: () => void;
}) {
    return (
        <Modal title={isPrivate ? '계정을 공개할까요?' : '계정을 비공개로 전환할까요?'} onClose={onCancel}>
            <p>
                {isPrivate
                    ? '다른 사용자가 내 프로필과 작성한 SNS 게시글을 다시 볼 수 있습니다.'
                    : '프로필 상세와 작성한 SNS 게시글을 다른 사용자에게 숨깁니다. 로그인과 본인의 활동은 그대로 유지됩니다.'}
            </p>
            <div className={scope('modal-actions')}>
                <Button variant="secondary" disabled={busy} onClick={onCancel}>
                    취소
                </Button>
                <Button disabled={busy} onClick={onConfirm}>
                    변경 확인
                </Button>
            </div>
        </Modal>
    );
}

/** 토글 ON: 기존 Mock 저장값(devmozip:account-area:v1의 isPrivate)만 읽고 바꾼다. 실제 계정에는 저장하지 않는다(D4 B). */
export function MockAccountLock() {
    const { accountUser: user, accountArea, accountAct } = useAuth();
    const [confirming, setConfirming] = useState(false);
    const isPrivate = !!(user && accountArea.accounts[user.id]?.isPrivate);
    return (
        <>
            <AccountLockView state={{ status: 'ready', isPrivate }} busy={false} error="" onRequest={() => setConfirming(true)} />
            {confirming && (
                <AccountLockConfirm
                    isPrivate={isPrivate}
                    busy={false}
                    onCancel={() => setConfirming(false)}
                    onConfirm={() => {
                        if (accountAct({ type: 'privacy', isPrivate: !isPrivate })) setConfirming(false);
                    }}
                />
            )}
        </>
    );
}

/** 토글 OFF: 서버 값을 불러와 표시하고 확인 창에서 변경을 요청한다. Mock 저장값은 건드리지 않는다(D4 B). */
export function ServerAccountLock() {
    const [attempt, setAttempt] = useState(0);
    const [loaded, setLoaded] = useState<{ attempt: number; state: AccountLockState }>({ attempt: -1, state: { status: 'loading' } });
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState('');
    const [confirming, setConfirming] = useState(false);
    useEffect(() => {
        let active = true;
        void loadAccountLock(accountLockStatusAction).then(state => {
            if (active) setLoaded({ attempt, state });
        });
        return () => {
            active = false;
        };
    }, [attempt]);
    // 다시 시도하면 이전 결과 대신 불러오는 중을 보여 준다.
    const state: AccountLockState = loaded.attempt === attempt ? loaded.state : { status: 'loading' };

    async function confirm() {
        if (state.status !== 'ready' || busy) return;
        setConfirming(false);
        setBusy(true);
        setError('');
        // 실패하면 이전 값을 그대로 두고 안내만 보여 준다.
        const result = await changeAccountLock(state.isPrivate, updateAccountLockAction);
        setLoaded({ attempt, state: { status: 'ready', isPrivate: result.isPrivate } });
        setError(result.error);
        setBusy(false);
    }

    return (
        <>
            <AccountLockView
                state={state}
                busy={busy}
                error={error}
                onRequest={() => {
                    setError('');
                    setConfirming(true);
                }}
                onRetry={() => setAttempt(value => value + 1)}
            />
            {confirming && state.status === 'ready' && (
                <AccountLockConfirm isPrivate={state.isPrivate} busy={busy} onCancel={() => setConfirming(false)} onConfirm={() => void confirm()} />
            )}
        </>
    );
}

/** 개인정보 설정의 계정 공개 영역. Mock 토글에 따라 값을 읽고 바꾸는 곳을 고른다. */
export function AccountLockPanel() {
    const { enabled } = useMockVisibility();
    const { accountUser } = useAuth();
    // 로그인한 계정이 바뀌면 새로 불러온다. Mock 계정 ID는 여러 서버 계정이 나눠 쓰므로 이메일로 구분한다.
    return enabled ? <MockAccountLock /> : <ServerAccountLock key={accountUser?.email ?? 'guest'} />;
}
