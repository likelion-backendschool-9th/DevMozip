import styles from '../../_components/SubscriptionPage.module.css';
import Link from 'next/link';
import { Button } from '@/components/common/Button';
import { CHECKOUT_FAILURE_MESSAGES, PROVIDER_OPTIONS, TEST_PAYMENT_NOTICES, checkoutNotices, cycleLabel, formatWon } from '@/lib/subscription/subscriptionView';
import type { CheckoutFailureCode, PaymentProvider, SubscriptionPlan } from '@/types/subscription';

/**
 * 결제 화면. 상품 요약, 결제 수단 선택, 결제 전 고지와 동의, 결제 버튼을 보여 준다.
 * 상태와 결제 요청은 페이지가 맡고, 이 컴포넌트는 받은 값만 그린다.
 */
export function CheckoutForm({ plan, provider, agreed, busy, blockReason, failure, today, onProvider, onAgreed, onSubmit }: {
    plan: SubscriptionPlan;
    provider: PaymentProvider | null;
    agreed: boolean;
    busy: boolean;
    blockReason: CheckoutFailureCode | null;
    failure: CheckoutFailureCode | null;
    today: Date;
    onProvider: (provider: PaymentProvider) => void;
    onAgreed: (agreed: boolean) => void;
    onSubmit: () => void;
}) {
    if (blockReason) {
        return (
            <section className={styles.panel} aria-label="결제할 수 없음">
                <p className={styles.error} role="alert">{CHECKOUT_FAILURE_MESSAGES[blockReason]}</p>
                <div className={styles.actions}>
                    <Link className="button secondary" href={blockReason === 'already-subscribed' ? '/subscription/manage' : '/subscription'}>
                        {blockReason === 'already-subscribed' ? '구독 관리로 이동' : '상품 안내로 돌아가기'}
                    </Link>
                </div>
            </section>
        );
    }
    return (
        <form
            className={styles.panel}
            aria-label="프리미엄 결제"
            onSubmit={event => {
                event.preventDefault();
                onSubmit();
            }}
        >
            <div className={styles.summary}>
                <div>
                    <h2>{plan.name} 월 구독</h2>
                    <p>{cycleLabel(plan)} 자동 결제 · 언제든 해지 가능</p>
                </div>
                <strong className={styles.price}>
                    {formatWon(plan.price)} <small>/ 월</small>
                </strong>
            </div>
            {failure && <p className={styles.error} role="alert">{CHECKOUT_FAILURE_MESSAGES[failure]}</p>}
            <fieldset className={styles.methods} disabled={busy}>
                <legend className={styles.heading}><h3>결제 수단</h3></legend>
                {PROVIDER_OPTIONS.map(option => (
                    <label key={option.provider} className={[styles.method, provider === option.provider ? styles.methodSelected : ''].join(' ')}>
                        <input type="radio" name="provider" value={option.provider} checked={provider === option.provider} onChange={() => onProvider(option.provider)} />
                        <strong>{option.title}</strong>
                        <span>{option.description}</span>
                    </label>
                ))}
            </fieldset>
            {provider && TEST_PAYMENT_NOTICES[provider] && <p className={styles.info} role="note">{TEST_PAYMENT_NOTICES[provider]}</p>}
            <h3>결제 전 확인해 주세요</h3>
            <ul className={styles.notices}>
                {checkoutNotices(plan, today).map(notice => <li key={notice}>{notice}</li>)}
            </ul>
            <label className={styles.agree}>
                <input type="checkbox" checked={agreed} disabled={busy} onChange={event => onAgreed(event.target.checked)} />
                <span>위 내용을 확인했으며 매월 자동 결제에 동의합니다.</span>
            </label>
            <div className={styles.actions}>
                <Button type="submit" disabled={busy || !provider || !agreed}>
                    {busy ? '결제를 진행하고 있어요…' : `${formatWon(plan.price)} 결제하기`}
                </Button>
                <Link className="button secondary" href="/subscription">취소</Link>
            </div>
        </form>
    );
}
