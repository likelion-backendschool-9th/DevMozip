'use client';
import { useMessaging } from '@/lib/state/messagingStore';
import { useProfiles } from '@/lib/state/profileStore';
import { useState } from 'react';
import Link from 'next/link';
import { adminStyles as styles } from '../../_components/AdminShell';
import { useAuth } from '@/lib/state/authStore';
import { useProjects } from '@/lib/state/projectStore';
import { Button } from '@/components/common/Button';
import { Modal } from '@/components/common/Modal';

/** Mock 토글 ON의 프로젝트 신고 처리. 브라우저 저장값만 바꾼다. */
export function MockAdminReports() {
    const { administration, administer } = useAuth();
    const { state: recruitment, projects } = useProjects();
    const {state:messaging}=useMessaging();const {state:profiles}=useProfiles();
    const reports=[...recruitment.reports.map(r=>({...r,key:`project:${r.id}`})),...messaging.userReports.filter(r=>r.projectId).map(r=>({id:r.id,key:`member:${r.id}`,projectId:r.projectId!,reason:'팀원 신고 · '+(profiles.profiles[r.targetId]?.nickname||r.targetId)+' · '+r.reason,detail:''}))];
    const [report, setReport] = useState<{ id: string; status: 'resolved' | 'dismissed' } | null>(null);
    const [note, setNote] = useState('');

    return (
        <>
            <section
                className={[styles.scope, 'governance-panel admin-main'].join(' ')}
                aria-label="프로젝트 신고 관리"
            >
                <h2>프로젝트 신고 처리</h2>
                <p>검토 결과를 저장합니다. 신고 처리만으로 공고나 지원 내역을 삭제하지 않습니다.</p>
                {!reports.length && (
                    <p>접수된 신고가 없습니다. 프로젝트 상세에서 신고를 접수해 시연할 수 있습니다.</p>
                )}
                {reports.map(r => {
                    const outcome = administration.resolutions[r.key];
                    return (
                        <article className={[styles.scope, 'admin-row'].join(' ')} key={r.id}>
                            <div>
                                <Link href={`/projects/${r.projectId}`}>
                                    {projects.find(p => p.id === r.projectId)?.title || r.projectId}
                                </Link>
                                <p>
                                    {r.reason} · {r.detail}
                                </p>
                                <small>
                                    {outcome ? (outcome.status === 'resolved' ? '처리 완료' : '기각') : '미처리'}{' '}
                                    {outcome?.note}
                                </small>
                            </div>
                            <div className={[styles.scope, 'governance-actions'].join(' ')}>
                                <Button
                                    variant="secondary"
                                    disabled={!!outcome}
                                    onClick={() => {
                                        setNote('');
                                        setReport({ id: r.key, status: 'resolved' });
                                    }}
                                >
                                    처리 완료
                                </Button>
                                <Button
                                    variant="secondary"
                                    disabled={!!outcome}
                                    onClick={() => {
                                        setNote('');
                                        setReport({ id: r.key, status: 'dismissed' });
                                    }}
                                >
                                    기각
                                </Button>
                            </div>
                        </article>
                    );
                })}
            </section>
            {report && (
                <Modal title="신고 검토 결과" onClose={() => setReport(null)}>
                    <label>
                        처리 메모
                        <textarea
                            className={[styles.scope, 'form-input'].join(' ')}
                            maxLength={300}
                            value={note}
                            onChange={e => setNote(e.target.value)}
                        />
                    </label>
                    <Button
                        onClick={() => {
                            if (administer({ type: 'resolve', reportId: report.id, status: report.status, note }))
                                setReport(null);
                        }}
                    >
                        결과 저장
                    </Button>
                </Modal>
            )}
        </>
    );
}
