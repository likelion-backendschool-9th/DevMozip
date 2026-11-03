'use client';
import { useState } from 'react';
import Link from 'next/link';
import { adminStyles as styles } from '../_components/AdminShell';
import { useAuth } from '@/lib/state/authStore';
import { useProjects } from '@/lib/state/projectStore';
import { Button } from '@/components/common/Button';
import { Modal } from '@/components/common/Modal';

export default function AdminReportsPage() {
    const { administration, administer } = useAuth();
    const { state: recruitment, projects } = useProjects();
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
                {!recruitment.reports.length && (
                    <p>접수된 신고가 없습니다. 프로젝트 상세에서 신고를 접수해 시연할 수 있습니다.</p>
                )}
                {recruitment.reports.map(r => {
                    const outcome = administration.resolutions[`project:${r.id}`];
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
                                    onClick={() => {
                                        setNote('');
                                        setReport({ id: `project:${r.id}`, status: 'resolved' });
                                    }}
                                >
                                    처리 완료
                                </Button>
                                <Button
                                    variant="secondary"
                                    onClick={() => {
                                        setNote('');
                                        setReport({ id: `project:${r.id}`, status: 'dismissed' });
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
