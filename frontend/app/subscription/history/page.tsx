'use client';
import styles from '../_components/SubscriptionPage.module.css';
import { useEffect, useState } from 'react';
import { Receipt } from 'lucide-react';
import { Button } from '@/components/common/Button';
import { SubscriptionPage } from '../_components/SubscriptionPage';
import { PaymentHistoryList } from './_components/PaymentHistoryList';
import { fetchPaymentHistory } from '@/lib/subscription/subscriptionClient';
import type { PaymentHistoryItem } from '@/types/subscription';

const LOAD_ERROR = '결제 내역을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.';

export default function PaymentHistoryPage() {
    const [items, setItems] = useState<PaymentHistoryItem[]>([]);
    // attempt는 같은 페이지를 다시 요청할 때 올린다.
    const [request, setRequest] = useState({ page: 0, attempt: 0 });
    const [hasNext, setHasNext] = useState(false);
    const [loaded, setLoaded] = useState(false);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');
    useEffect(() => {
        let active = true;
        fetchPaymentHistory(request.page)
            .then(result => {
                if (!active) return;
                // 다음 페이지를 이어 붙인다. 그사이 새 결제가 생겨 같은 항목이 다시 오면 넣지 않는다.
                setItems(previous => {
                    const seen = new Set(previous.map(item => item.id));
                    return [...previous, ...result.items.filter(item => !seen.has(item.id))];
                });
                setHasNext(result.hasNext);
                setLoaded(true);
                setError('');
            })
            .catch(() => { if (active) setError(LOAD_ERROR); })
            .finally(() => { if (active) setLoading(false); });
        return () => { active = false; };
    }, [request]);

    function next() {
        setLoading(true);
        setError('');
        setRequest(current => error ? { ...current, attempt: current.attempt + 1 } : { page: current.page + 1, attempt: 0 });
    }

    return (
        <SubscriptionPage eyebrow={<><Receipt size={16} /> PAYMENT HISTORY</>} title="결제 내역" description="첫 결제와 매월 자동 결제, 실패한 결제를 최신순으로 보여 줍니다." tab="/subscription/history">
            <section className={styles.panel} aria-label="결제 내역">
                {loaded && <PaymentHistoryList items={items} />}
                {loading && <p role="status">결제 내역을 불러오고 있어요…</p>}
                {error && <p className={styles.error} role="alert">{error}</p>}
                {!loading && (hasNext || error) && (
                    <div className={styles.actions}>
                        <Button variant="secondary" onClick={next}>
                            {error ? '다시 시도' : '다음 내역 보기'}
                        </Button>
                    </div>
                )}
            </section>
        </SubscriptionPage>
    );
}
