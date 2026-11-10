import Link from 'next/link';
import { Card } from '@/components/common/Card';
import styles from './ActivityTab.module.css';

/** Mock/API 모두 기존 활동 카드 배치를 공유한다. */
export function ActivityProjectCard({ title, href, status, start, end, completedAt, role, mark, color }: {
    title: string; href: string; status: string; start: string; end: string;
    completedAt?: string | null; role?: string; mark: string; color: string;
}) {
    return <Card className={`${styles.scope} activity-card`}>
        <span className={`${styles.scope} activity-mark ${color}`} aria-hidden="true">{mark}</span>
        <div><span className={`${styles.scope} activity-status ${status === '프로젝트 종료' ? 'ended' : ''}`}>{status}</span>
            <h3><Link href={href} className={styles.projectTitle}>{title}</Link></h3>
            <p>계획 {start} — {end}{completedAt ? ` · 종료 ${new Date(completedAt).toLocaleDateString('ko-KR', { timeZone: 'Asia/Seoul' })}` : ''}{role ? ` · ${role}` : ''}</p>
        </div>
    </Card>;
}
