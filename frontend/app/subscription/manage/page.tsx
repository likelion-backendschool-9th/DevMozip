'use client';
import styles from '../_components/SubscriptionPage.module.css';
import { useEffect, useState } from 'react';
import { useSearchParams } from 'next/navigation';
import { Crown } from 'lucide-react';
import { Button } from '@/components/common/Button';
import { Modal } from '@/components/common/Modal';
import { SubscriptionPage } from '../_components/SubscriptionPage';
import { SubscriptionStatusPanel } from './_components/SubscriptionStatusPanel';
import { cancelSubscription, fetchMySubscription } from '@/lib/subscription/subscriptionClient';
import { formatDate } from '@/lib/subscription/subscriptionView';
import type { MySubscription } from '@/types/subscription';

const LOAD_ERROR = '구독 정보를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.';

export default function SubscriptionManagePage() {
    const params = useSearchParams();
    const [subscription, setSubscription] = useState<MySubscription | null>(null);
    const [loadError, setLoadError] = useState('');
    const [confirming, setConfirming] = useState(false);
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState('');
    const [reload, setReload] = useState(0);
    useEffect(() => {
        let active = true;
        fetchMySubscription()
            .then(value => { if (active) { setSubscription(value); setLoadError(''); } })
            .catch(() => { if (active) setLoadError(LOAD_ERROR); });
        return () => { active = false; };
    }, [reload]);

    async function cancel() {
        setBusy(true);
        setError('');
        try {
            setSubscription(await cancelSubscription());
            setConfirming(false);
        } catch (e) {
            setError(e instanceof Error ? e.message : '해지하지 못했습니다. 잠시 후 다시 시도해 주세요.');
            setConfirming(false);
        }
        setBusy(false);
    }

    return (
        <SubscriptionPage eyebrow={<><Crown size={16} /> MY SUBSCRIPTION</>} title="구독 관리" description="구독 상태와 다음 결제일, 결제 수단을 확인하고 해지할 수 있습니다." tab="/subscription/manage">
            {loadError ? (
                <div className={styles.panel}>
                    <p className={styles.error} role="alert">{loadError}</p>
                    <div className={styles.actions}><Button variant="secondary" onClick={() => setReload(value => value + 1)}>다시 시도</Button></div>
                </div>
            ) : !subscription ? (
                <p className={styles.panel} role="status">구독 정보를 불러오고 있어요…</p>
            ) : (
                <SubscriptionStatusPanel
                    subscription={subscription}
                    paid={params.get('payment') === 'success'}
                    busy={busy}
                    error={error}
                    onCancel={() => setConfirming(true)}
                />
            )}
            {confirming && subscription && (
                <Modal title="구독을 해지할까요?" onClose={() => !busy && setConfirming(false)}>
                    <p>
                        해지하면 등록한 결제 수단이 삭제되고 다음 결제가 일어나지 않습니다. {formatDate(subscription.currentPeriodEnd)}까지는 프리미엄을 그대로 이용할 수 있습니다.
                    </p>
                    <p>이미 결제한 금액은 환불되지 않습니다. 해지 예약 중에는 새로 결제할 수 없으며, 이용 기간이 끝난 뒤 다시 구독할 수 있습니다.</p>
                    <div className={styles.actions}>
                        <Button variant="secondary" disabled={busy} onClick={() => setConfirming(false)}>돌아가기</Button>
                        <Button disabled={busy} onClick={cancel}>{busy ? '해지하고 있어요…' : '해지하기'}</Button>
                    </div>
                </Modal>
            )}
        </SubscriptionPage>
    );
}
