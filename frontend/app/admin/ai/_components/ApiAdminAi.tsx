'use client';
import { useState } from 'react';
import Link from 'next/link';
import { AdminLoadView, AdminPager, cls } from '../../_components/AdminParts';
import { adminTime, parseAdminPage } from '@/lib/administration/adminApi';
import { aiMemberName, parseAiFailure, parseAiUsagePage } from '@/lib/administration/adminAi';
import { useAdminResource, type AdminLoad } from '@/lib/administration/useAdminResource';
import type { AdminAiFailure, AdminAiUsage, AdminAiUsageSummary, AdminPage, AdminSummaryPage } from '@/types/adminApi';

const parseFailures = (raw: unknown) => parseAdminPage(raw, parseAiFailure);

/** Mock 토글 OFF의 AI 사용량·오류 (WBS 14.10·14.11). 서버의 월별 사용량과 추천 실패 기록을 읽기만 한다. */
export function ApiAdminAi() {
    const [month, setMonth] = useState('');
    const [usagePage, setUsagePage] = useState(0);
    const [failurePage, setFailurePage] = useState(0);
    const usageQuery = new URLSearchParams({ page: String(usagePage), size: '20' });
    if (month) usageQuery.set('month', month);
    const usage = useAdminResource(`/ai/usage?${usageQuery}`, parseAiUsagePage);
    const failures = useAdminResource(`/ai/logs?page=${failurePage}&size=20`, parseFailures);

    return (
        <section className={cls('governance-panel admin-main')} aria-label="AI 사용량 관리">
            <h2>AI 사용량</h2>
            <p>추천 결과를 만든 횟수(사용)와 처리 중인 요청(예약)입니다. 월은 한국 시각 기준입니다. AI 추천 조건과 대화 내용은 관리자에게도 보이지 않습니다.</p>
            <div className={cls('admin-filters')}>
                <label>
                    월
                    <input className="form-input" type="month" value={month} onChange={e => { setMonth(e.target.value); setUsagePage(0); }} />
                </label>
            </div>
            <AiUsageView load={usage.load} onRetry={usage.reload} onPage={setUsagePage} />
            <h3>추천 실패 기록</h3>
            <p>실패한 추천 요청은 사용량에서 차감하지 않습니다. 오류 원인은 저장하지 않아 시각과 회원만 보입니다.</p>
            <AiFailuresView load={failures.load} onRetry={failures.reload} onPage={setFailurePage} />
        </section>
    );
}

export function AiUsageView({ load, onRetry, onPage }:
    { load: AdminLoad<AdminSummaryPage<AdminAiUsageSummary, AdminAiUsage>>; onRetry: () => void; onPage: (page: number) => void }) {
    return (
        <AdminLoadView load={load} onRetry={onRetry} loadingText="사용량을 불러오고 있어요.">
            {data => (
                <>
                    <div className={cls('admin-summary')}>
                        <span className="demo-pill">{data.summary.month}</span>
                        <span className="demo-pill">사용 {data.summary.totalUsed}회</span>
                        <span className="demo-pill">예약 {data.summary.totalPending}회</span>
                        <span className="demo-pill">회원 {data.summary.members}명</span>
                    </div>
                    {!data.items.length && <p>이 달의 추천 사용 기록이 없습니다.</p>}
                    {data.items.map(item => (
                        <article className={cls('admin-row')} key={item.userId}>
                            <div>
                                {item.tag ? <Link href={`/users/${item.tag}`}>{aiMemberName(item.nickname, item.withdrawn, item.userId)}</Link>
                                    : <b>{aiMemberName(item.nickname, item.withdrawn, item.userId)}</b>}
                                {item.role && <small>{item.role}</small>}
                            </div>
                            <span>사용 {item.usedCount}회 · 예약 {item.pendingCount}회</span>
                        </article>
                    ))}
                    <AdminPager page={data.page} totalPages={data.totalPages} onChange={onPage} />
                </>
            )}
        </AdminLoadView>
    );
}

export function AiFailuresView({ load, onRetry, onPage }:
    { load: AdminLoad<AdminPage<AdminAiFailure>>; onRetry: () => void; onPage: (page: number) => void }) {
    return (
        <AdminLoadView load={load} onRetry={onRetry} loadingText="실패 기록을 불러오고 있어요.">
            {data => (
                <>
                    {!data.items.length && <p>실패한 추천 요청이 없습니다.</p>}
                    {data.items.map(item => (
                        <article className={cls('admin-log')} key={item.requestId}>
                            <time>{adminTime(item.createdAt)}</time>
                            <code>{item.status}</code>
                            <p>{aiMemberName(item.nickname, item.withdrawn, item.userId)} · 요청 #{item.requestId} · 대화 #{item.conversationId} · 끝난 시각 {adminTime(item.completedAt)}</p>
                        </article>
                    ))}
                    <AdminPager page={data.page} totalPages={data.totalPages} onChange={onPage} />
                </>
            )}
        </AdminLoadView>
    );
}
