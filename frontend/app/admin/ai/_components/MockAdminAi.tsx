'use client';
import { adminStyles as styles } from '../../_components/AdminShell';
import { useHome } from '@/lib/state/homeStore';
import { useProfiles } from '@/lib/state/profileStore';
import { visibleSeeds } from '@/lib/demo/mockVisibility';
import { aiErrorExamples } from '@/mocks/administration';

/** Mock 토글 ON의 AI 사용량. 같은 브라우저의 Mock 사용량과 고정 오류 예시를 보여 준다. */
export function MockAdminAi() {
    const { state: profiles } = useProfiles();
    const { state: home } = useHome();

    return (
        <section className={[styles.scope, 'governance-panel admin-main'].join(' ')} aria-label="AI 사용량 관리">
            <h2>AI 사용량</h2>
            <p>동일 브라우저에 저장된 월별 실제 Mock 추천 사용량입니다.</p>
            {!Object.keys(home.matching.usage).length && <p>아직 추천 사용 이력이 없습니다.</p>}
            {Object.entries(home.matching.usage).map(([id, months]) => (
                <article className={[styles.scope, 'admin-row'].join(' ')} key={id}>
                    <b>{profiles.profiles[id]?.nickname || id}</b>
                    <div>
                        {Object.entries(months).map(([month, count]) => (
                            <p key={month}>
                                {month}: {count}회
                            </p>
                        ))}
                    </div>
                </article>
            ))}
            <h3>오류 로그 예시</h3>
            {visibleSeeds(aiErrorExamples).map(l => (
                <article key={l.id} className={[styles.scope, 'admin-log'].join(' ')}>
                    <time>{l.date}</time>
                    <code>{l.code}</code>
                    <p>{l.message}</p>
                </article>
            ))}
        </section>
    );
}
