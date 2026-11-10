import styles from '../../_components/SubscriptionPage.module.css';
import Link from 'next/link';
import { Button } from '@/components/common/Button';
import { STATUS_LABELS, formatDate, formatWon, isCurrentSubscription } from '@/lib/subscription/subscriptionView';
import type { MySubscription } from '@/types/subscription';

const BENEFITS = ['AI 추천 월 100회와 상세 추천 이유', '상세 포트폴리오 열람과 AI 피드백', '고급 검색(신뢰 온도 조건·정렬)', '프로젝트 모집 공고 작성', '프로필 PRO 뱃지'];

/** 구독 상태(13.3)와 해지 버튼(13.4). 해지 확인은 페이지의 모달이 맡는다 */
export function SubscriptionStatusPanel({ subscription, paid, busy, error, onCancel }: {
    subscription: MySubscription;
    paid: boolean;
    busy: boolean;
    error: string;
    onCancel: () => void;
}) {
    const { status, plan } = subscription;
    const current = isCurrentSubscription(status);
    // 결제 기록 없이 역할만 PREMIUM인 계정은 구독 정보 없이 이용 중으로만 보여 준다.
    const grantedOnly = subscription.premium && !current;
    return (
        <section className={styles.panel} aria-label="내 구독 상태">
            {paid && <p className={styles.success} role="status">결제를 완료했습니다. 지금부터 프리미엄 기능을 이용할 수 있습니다.</p>}
            {status === 'CANCELED' && !subscription.premium && (
                <p className={styles.error} role="alert">매월 결제가 실패해 구독이 취소되었습니다. 다시 이용하려면 새로 결제해 주세요.</p>
            )}
            <div className={styles.summary}>
                <h2>내 구독</h2>
                <span className={[styles.status, current || grantedOnly ? '' : styles.statusMuted].join(' ')}>
                    {grantedOnly ? '프리미엄 이용 중' : STATUS_LABELS[status]}
                </span>
            </div>
            {current && plan ? (
                <dl className={styles.details}>
                    <div><dt>상품</dt><dd>{plan.name} 월 구독 · {formatWon(plan.price)}</dd></div>
                    <div><dt>시작일</dt><dd>{formatDate(subscription.startsAt)}</dd></div>
                    {status === 'CANCEL_REQUESTED' ? (
                        <div><dt>이용 종료일</dt><dd>{formatDate(subscription.currentPeriodEnd)}까지 이용 후 무료로 바뀝니다</dd></div>
                    ) : (
                        <div><dt>다음 결제일</dt><dd>{formatDate(subscription.nextBillingAt ?? subscription.currentPeriodEnd)} · {formatWon(plan.price)}</dd></div>
                    )}
                    <div><dt>결제 수단</dt><dd>{subscription.paymentMethod?.display ?? '등록된 결제 수단 없음'}</dd></div>
                </dl>
            ) : grantedOnly ? (
                <p>결제 기록 없이 프리미엄 권한이 부여된 계정입니다.</p>
            ) : (
                <p>
                    {status === 'EXPIRED' ? `${formatDate(subscription.endedAt)}에 프리미엄 이용이 끝났습니다.` : '지금은 무료 요금제를 이용하고 있습니다.'}
                </p>
            )}
            {(current || grantedOnly) && (
                <>
                    <h3>이용 중인 프리미엄 혜택</h3>
                    <ul className={styles.benefits}>{BENEFITS.map(item => <li key={item}>{item}</li>)}</ul>
                    <Link className={styles.link} href="/ai/matching">AI 추천 사용량 확인 →</Link>
                </>
            )}
            {error && <p className={styles.error} role="alert">{error}</p>}
            <div className={styles.actions}>
                {status === 'ACTIVE' && (
                    <Button variant="secondary" disabled={busy} onClick={onCancel}>구독 해지</Button>
                )}
                {!current && !subscription.premium && <Link className="button" href="/subscription/checkout">프리미엄 시작하기</Link>}
            </div>
        </section>
    );
}
