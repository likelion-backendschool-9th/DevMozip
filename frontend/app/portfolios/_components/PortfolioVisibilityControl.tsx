'use client';
import { useRef, useState } from 'react';
import { Button } from '@/components/common/Button';
import { changePortfolioVisibility } from '@/lib/portfolios/portfolioVisibility';
import type { PortfolioDetail, PortfolioSummary } from '@/types/portfolioApi';
import styles from './PortfolioVisibilityControl.module.css';

export function PortfolioVisibilityControl({ detail, onChanged, onReload }: {
    detail: PortfolioDetail; onChanged: (detail: PortfolioDetail) => void; onReload: () => void;
}) {
    const [visibility, setVisibility] = useState<PortfolioSummary['visibility']>(detail.portfolio.visibility);
    const [pending, setPending] = useState(false); const [error, setError] = useState(''); const lock = useRef(false);
    if (!detail.canEdit) return null;
    async function save() {
        if (lock.current || visibility === detail.portfolio.visibility) return;
        lock.current = true; setPending(true); setError('');
        try { onChanged(await changePortfolioVisibility(detail, visibility)); }
        catch (e) { setError(e instanceof Error ? e.message : '공개 범위를 변경하지 못했어요.'); }
        finally { lock.current = false; setPending(false); }
    }
    return <section className={styles.panel} aria-label="포트폴리오 공개 범위 변경">
        <label>공개 범위 <select aria-label="포트폴리오 공개 범위" value={visibility} disabled={pending}
            onChange={e => { setVisibility(e.target.value as PortfolioSummary['visibility']); setError(''); }}>
            <option value="PRIVATE">비공개</option><option value="PUBLIC">공개</option>
        </select></label>{' '}
        <Button variant="secondary" disabled={pending || visibility === detail.portfolio.visibility} onClick={() => void save()}>{pending ? '변경 중…' : '공개 범위 저장'}</Button>
        <p>{visibility === 'PRIVATE' ? '본인만 목록·상세·첨부파일을 볼 수 있어요.' : '프로필 목록에 표시되며 프리미엄 회원이 상세를 볼 수 있어요.'}</p>
        <p className="auth-hint">프로젝트에 제출한 포트폴리오는 정책 확인 전까지 공개 범위를 변경할 수 없어요.</p>
        {error && <p role="alert">{error}{' '}<Button variant="secondary" onClick={onReload}>최신 내용 다시 불러오기</Button></p>}
    </section>;
}
