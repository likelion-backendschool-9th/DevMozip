import Link from 'next/link';
import { portfolioReasons } from '@/lib/matching/portfolioPresentation';
import type { AiPortfolioAnalysis } from '@/types/aiPortfolioAnalysis';
import styles from './PortfolioAnalysis.module.css';

export function PortfolioAnalysisContent({ data, nickname }: { data: AiPortfolioAnalysis; nickname?: string | null }) {
    const reasons = portfolioReasons(data);
    if (!reasons.length) return null;
    const name = nickname ? `${nickname}님` : '이 개발자';
    return <div className={styles.content}>
        <h5>이 개발자를 추천하는 이유</h5>
        {reasons.map(({ portfolio, implementations, skills, roles, cautions }) => <article className={styles.evidence} key={portfolio.id}>
            {implementations.map((e, index) => <div key={`${e.quote}:${index}`}>
                <p><strong>{name}</strong>의 포트폴리오에는 “{e.quote}”라고 소개되어 있어요.
                    {e.partial ? ' 요청하신 내용과 일부 관련이 있어, 구체적인 구현 범위와 현재 완성 상태를 확인해 보면 좋겠어요.' : ' 요청하신 내용과 연결되는 사례예요.'}</p>
                <p className={styles.connection}>요청하신 내용: {e.requirements.join(' · ')}</p>
            </div>)}
            {(skills.length > 0 || roles.length > 0) && <p>{nickname ? `${nickname}님은` : '이 개발자는'} 이 포트폴리오에
                {roles.length > 0 && ` ${roles.join(' · ')} 역할을 맡았다고`}
                {roles.length > 0 && skills.length > 0 && ', 그리고'}
                {skills.length > 0 && ` ${skills.join(', ')} 기술을 사용했다고`} 작성했어요.</p>}
            {cautions.map(c => <p className={styles.caution} key={`${c.requirement}:${c.quote}`}>협업 전 확인할 차이가 있어요.
                요청하신 ‘{c.requirement}’와 달리, 포트폴리오에는 “{c.quote}”라고 작성되어 있어요.</p>)}
            <p className={styles.source}>출처: <Link href={`/portfolios/${portfolio.id}`} prefetch={false}>{portfolio.title || `포트폴리오 #${portfolio.id}`}</Link></p>
            <details className={styles.selection}><summary>이 자료를 참고한 이유</summary><p>요청하신 조건과 이 자료의 제목·요약·담당 역할·기술에서 관련 표현을 비교해 선택했고, 위 인용을 근거로 참고했어요.</p>
                {portfolio.truncated && <p>본문 앞 6,000자를 분석했어요.</p>}
            </details>
        </article>)}
        <p className={styles.caption}>작성자가 공개한 포트폴리오를 바탕으로 정리했어요.</p>
    </div>;
}
