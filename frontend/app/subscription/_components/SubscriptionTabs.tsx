import Link from 'next/link';
import styles from './SubscriptionPage.module.css';

const tabs = [
    { href: '/subscription/manage', label: '구독 관리' },
    { href: '/subscription/history', label: '결제 내역' },
];

/** 구독 관리와 결제 내역 사이를 오가는 탭. 상품 안내(/subscription)는 탭 없이 따로 둔다 */
export function SubscriptionTabs({ current }: { current: string }) {
    return (
        <nav className={styles.tabs} aria-label="프리미엄 메뉴">
            {tabs.map(tab => (
                <Link key={tab.href} href={tab.href} className={[styles.tab, tab.href === current ? styles.tabActive : ''].join(' ')} aria-current={tab.href === current ? 'page' : undefined}>
                    {tab.label}
                </Link>
            ))}
        </nav>
    );
}
