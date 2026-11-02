'use client';
import styles from './ApplicantPortfolioButton.module.css';
import { useState } from 'react';
import { useAuth } from '@/lib/state/AuthProvider';
import { useProfiles } from '@/lib/state/ProfileProvider';
import { canViewApplicantPortfolio } from '@/lib/projects/projectPermissionsService';
import { Button } from '@/components/common/Button';
import { Modal } from '@/components/common/Modal';
import { PremiumProjectGate } from '@/components/projects/PremiumProjectGate';
import type { ProjectApplication, RecruitmentProject } from '@/types/project';
export function ApplicantPortfolioButton({ project, application }: {
    project: RecruitmentProject;
    application: ProjectApplication;
}) { const { user } = useAuth(); const { state } = useProfiles(); const [open, setOpen] = useState(false); const portfolio = state.portfolios.find(p => p.id === application.portfolioId && p.ownerId === application.userId); if (user?.id !== project.hostId)
    return null; const allowed = canViewApplicantPortfolio(user, project, application, portfolio); return <><Button variant="secondary" onClick={() => setOpen(true)}>지원자 포트폴리오 상세</Button>{open && (user?.role !== 'premium' ? <PremiumProjectGate purpose="portfolio" onClose={() => setOpen(false)}/> : <Modal className={[styles.scope, "profile-dialog"].join(" ")} title="지원자 포트폴리오 상세" onClose={() => setOpen(false)}>{allowed && portfolio ? <div className={[styles.scope, "portfolio-details"].join(" ")}><h3>{portfolio.title}</h3><p>{portfolio.summary}</p><p>역할 · {portfolio.role} · {portfolio.start} — {portfolio.end}</p><div className={[styles.scope, "tags"].join(" ")}>{portfolio.stacks.map(s => <span key={s}>{s}</span>)}</div><dl>{([{ key: 'description', label: '프로젝트 설명' }, { key: 'features', label: '주요 구현 기능' }, { key: 'challenge', label: '문제 해결 경험' }, { key: 'outcome', label: '개발 과정 및 결과물' }] as const).map(f => <div key={f.key}><dt>{f.label}</dt><dd className={[styles.scope, "preserve-text"].join(" ")}>{portfolio[f.key] || '미등록'}</dd></div>)}</dl></div> : <p>제출한 포트폴리오가 삭제되었거나 열람할 수 없습니다. 지원서에는 제출 당시 제목만 보관합니다.</p>}<Button variant="secondary" onClick={() => setOpen(false)}>확인</Button></Modal>)}</>; }
