'use client';
import styles from './page.module.css';
import { useEffect, useState } from 'react';
import { Crown, Check, Sparkles } from 'lucide-react';
import { useAuth } from '@/lib/state/authStore';
import { PlanComparison } from '@/app/subscription/_components/PlanComparison';
import { PremiumAction } from '@/app/subscription/_components/PremiumAction';
import { fetchPlans } from '@/lib/subscription/subscriptionClient';
import { formatWon } from '@/lib/subscription/subscriptionView';
import type { SubscriptionPlan } from '@/types/subscription';

export default function PremiumPage() {
    const { user } = useAuth();
    const [plan, setPlan] = useState<SubscriptionPlan | null>(null);
    const [error, setError] = useState('');
    useEffect(() => {
        let active = true;
        fetchPlans()
            .then(plans => { if (active) setPlan(plans[0] ?? null); })
            .catch(() => { if (active) setError('상품 정보를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.'); });
        return () => { active = false; };
    }, []);
    return (
        <div className={[styles.scope, 'container premium-page'].join(' ')}>
            <header className={[styles.scope, 'premium-hero'].join(' ')}>
                <span className={[styles.scope, 'eyebrow'].join(' ')}>
                    <Sparkles size={16} /> YOUR NEXT POSSIBILITY
                </span>
                <h1>좋은 동료를 찾는 더 넓은 가능성</h1>
                <p>포트폴리오의 깊이부터 팀의 시작까지, DevMozip 프리미엄.</p>
            </header>
            <div className={[styles.scope, 'plan-grid'].join(' ')}>
                <article className={[styles.scope, 'plan-card'].join(' ')}>
                    <span>FREE</span>
                    <h2>가볍게 시작하세요</h2>
                    <strong className={[styles.scope, 'plan-price'].join(' ')}>
                        ₩0 <small>/ 월</small>
                    </strong>
                    <p>개발자 탐색과 프로젝트 참여의 시작</p>
                    <ul>
                        <li>
                            <Check size={16} />
                            AI 추천 월 5회
                        </li>
                        <li>
                            <Check size={16} />
                            기본 검색·SNS·프로젝트 지원
                        </li>
                    </ul>
                    {user?.role === 'free' && <span className={[styles.scope, 'demo-pill'].join(' ')}>현재 요금제</span>}
                </article>
                <article className={[styles.scope, 'plan-card featured'].join(' ')}>
                    <span>
                        <Crown size={18} /> PREMIUM <b className={[styles.scope, 'premium-label'].join(' ')}>PRO</b>
                    </span>
                    <h2>다음 기회를 더 가까이</h2>
                    <strong className={[styles.scope, 'plan-price'].join(' ')}>
                        {plan ? formatWon(plan.price) : '₩9,900'} <small>/ 월</small>
                    </strong>
                    <p>매월 자동으로 결제되며 언제든 해지할 수 있습니다.</p>
                    <ul>
                        <li>
                            <Check size={16} />
                            AI 추천 월 100회·상세 추천 이유
                        </li>
                        <li>
                            <Check size={16} />
                            상세 포트폴리오·AI 피드백
                        </li>
                        <li>
                            <Check size={16} />
                            고급 검색·프로젝트 모집 공고 작성
                        </li>
                    </ul>
                    {error ? <p role="alert">{error}</p> : <PremiumAction role={user?.role} />}
                </article>
            </div>
            <section className={[styles.scope, 'governance-panel'].join(' ')}>
                <h2>나에게 맞는 요금제를 비교하세요</h2>
                <PlanComparison />
            </section>
        </div>
    );
}
