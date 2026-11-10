import styles from '../page.module.css';
import Link from 'next/link';

const CHECKOUT = '/subscription/checkout';

/** 프리미엄 카드의 버튼. 로그인·역할에 따라 결제, 구독 관리, 안내 중 하나를 보여 준다 */
export function PremiumAction({ role }: { role: string | undefined }) {
    const button = [styles.scope, 'button'].join(' ');
    if (!role) return <Link className={button} href={`/login?next=${encodeURIComponent(CHECKOUT)}`}>로그인하고 구독하기</Link>;
    if (role === 'admin') return <p>관리자 계정은 구독 결제 대상이 아닙니다.</p>;
    if (role === 'premium') return <Link className={button} href="/subscription/manage">구독 관리</Link>;
    return <Link className={button} href={CHECKOUT}>프리미엄 시작하기</Link>;
}
