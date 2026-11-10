'use client';
import styles from '../_components/SubscriptionPage.module.css';
import { useEffect, useState } from 'react';
import { useSearchParams } from 'next/navigation';
import { CreditCard } from 'lucide-react';
import { useAuth } from '@/lib/state/authStore';
import { SubscriptionPage } from '../_components/SubscriptionPage';
import { CheckoutForm } from './_components/CheckoutForm';
import { fetchMySubscription, fetchPlans, startSubscription } from '@/lib/subscription/subscriptionClient';
import { checkoutBlockReason, checkoutFailureCode } from '@/lib/subscription/subscriptionView';
import type { CheckoutFailureCode, MySubscription, PaymentProvider, SubscriptionPlan } from '@/types/subscription';

const LOAD_ERROR = '결제 정보를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.';

export default function CheckoutPage() {
    const { user } = useAuth();
    const params = useSearchParams();
    const [plan, setPlan] = useState<SubscriptionPlan | null>(null);
    const [subscription, setSubscription] = useState<MySubscription | null>(null);
    const [loadError, setLoadError] = useState('');
    const [provider, setProvider] = useState<PaymentProvider | null>(null);
    const [agreed, setAgreed] = useState(false);
    const [busy, setBusy] = useState(false);
    const [failure, setFailure] = useState<CheckoutFailureCode | null>(() => checkoutFailureCode(params.get('payment')));
    useEffect(() => {
        let active = true;
        Promise.all([fetchPlans(), fetchMySubscription()])
            .then(([plans, mine]) => {
                if (!active) return;
                setPlan(plans[0] ?? null);
                setSubscription(mine);
                if (!plans[0]) setLoadError('판매 중인 상품이 없습니다.');
            })
            .catch(() => { if (active) setLoadError(LOAD_ERROR); });
        return () => { active = false; };
    }, []);

    async function submit() {
        if (!plan || !provider || !agreed || busy) return;
        setBusy(true);
        setFailure(null);
        try {
            const result = await startSubscription(plan.id, provider);
            if (result.status === 'success') {
                // BFF가 바뀐 역할(PREMIUM)로 토큰을 재발급했다. 새 문서로 이동해 로그인 상태를 다시 읽는다.
                // 뒤로 가기로 결제 페이지에 돌아오지 않도록 기록을 바꾼다.
                window.location.replace('/subscription/manage?payment=success');
                return;
            }
            // 모바일은 결제창이 결과 주소로 이동한다. 이동하는 동안 버튼을 막아 둔다.
            if (result.status === 'redirecting') return;
            setFailure(result.code);
        } catch {
            setFailure('failed');
        }
        setBusy(false);
    }

    return (
        <SubscriptionPage eyebrow={<><CreditCard size={16} /> PREMIUM CHECKOUT</>} title="프리미엄 결제" description="결제 수단을 고르고 안내를 확인한 뒤 결제해 주세요.">
            {loadError ? (
                <p className={styles.error} role="alert">{loadError}</p>
            ) : !plan || !subscription ? (
                <p className={styles.panel} role="status">결제 정보를 불러오고 있어요…</p>
            ) : (
                <CheckoutForm
                    plan={plan}
                    provider={provider}
                    agreed={agreed}
                    busy={busy}
                    blockReason={checkoutBlockReason(user?.role, subscription)}
                    failure={failure}
                    today={new Date()}
                    onProvider={setProvider}
                    onAgreed={setAgreed}
                    onSubmit={submit}
                />
            )}
        </SubscriptionPage>
    );
}
