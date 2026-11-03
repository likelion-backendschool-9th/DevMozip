'use client';
import styles from './ProjectActions.module.css';
import { useState } from 'react';
import Link from 'next/link';
import { Bookmark, Flag, Share2 } from 'lucide-react';
import { useAuth } from '@/lib/state/authStore';
import { useProjects } from '@/lib/state/projectStore';
import { usePrototype } from '@/lib/state/prototypeStore';
import { Modal } from '@/components/common/Modal';
import { Button } from '@/components/common/Button';
import { Input, Textarea } from '@/components/common/Input';
import { reportReasons } from '@/mocks/project';
import type { RecruitmentProject } from '@/types/project';
export function BookmarkButton({ projectId, compact = false }: {
    projectId: string;
    compact?: boolean;
}) { const { user } = useAuth(); const { state, act } = useProjects(); const saved = !!user && !!state.bookmarks[user.id]?.includes(projectId); return user ? <button className={[styles.scope, compact ? 'icon-button' : 'button secondary'].join(" ")} aria-label={saved ? '북마크 해제' : '북마크 저장'} aria-pressed={saved} onClick={() => act({ type: 'bookmark', projectId })}><Bookmark size={16} fill={saved ? 'currentColor' : 'none'}/>{!compact && (saved ? '북마크됨' : '북마크')}</button> : <Link className={[styles.scope, compact ? 'icon-button' : 'button secondary'].join(" ")} href="/login" aria-label="로그인 후 북마크"><Bookmark size={16}/>{!compact && '북마크'}</Link>; }
export function ProjectActions({ project }: {
    project: RecruitmentProject;
}) { const { user } = useAuth(); const { state, act } = useProjects(); const { notify } = usePrototype(); const [modal, setModal] = useState<'report' | 'share' | null>(null); const [reason, setReason] = useState(reportReasons[0]); const [detail, setDetail] = useState(''); const [error, setError] = useState(''); const reported = !!user && state.reports.some(r => r.projectId === project.id && r.userId === user.id); const url = typeof window === 'undefined' ? `/projects/${project.id}` : new URL(`/projects/${project.id}`, window.location.origin).href; return <><div className={[styles.scope, "project-detail-actions"].join(" ")}><BookmarkButton projectId={project.id}/><Button variant="secondary" onClick={() => setModal('share')}><Share2 size={14}/>공유</Button>{project.hostId !== user?.id && (user ? <Button variant="secondary" disabled={reported} onClick={() => setModal('report')}><Flag size={14}/>{reported ? '신고됨' : '신고'}</Button> : <Link href="/login" className={[styles.scope, "button secondary"].join(" ")}><Flag size={14}/>신고</Link>)}</div>{modal === 'share' && <Modal title="프로젝트 공유" onClose={() => setModal(null)}><p>아래 주소를 복사해 프로젝트를 공유하세요.</p><Input aria-label="프로젝트 공유 주소" value={url} readOnly onFocus={e => e.target.select()}/><div className={[styles.scope, "modal-actions"].join(" ")}><Button variant="secondary" onClick={() => setModal(null)}>닫기</Button><Button onClick={async () => { try {
    if (!navigator.clipboard?.writeText)
        throw new Error();
    await navigator.clipboard.writeText(url);
    notify('프로젝트 주소를 복사했습니다.');
}
catch {
    notify('주소 입력창을 선택한 뒤 직접 복사해 주세요.');
} }}>주소 복사</Button></div></Modal>}{modal === 'report' && <Modal title="프로젝트 신고" onClose={() => setModal(null)}><form className={[styles.scope, "profile-form"].join(" ")} onSubmit={e => { e.preventDefault(); const result = act({ type: 'report', projectId: project.id, reason, detail }); if (result.ok)
    setModal(null);
else
    setError(result.error || '신고 내용을 확인해 주세요.'); }}><label>신고 사유<select className={[styles.scope, "form-input"].join(" ")} value={reason} onChange={e => setReason(e.target.value)}>{reportReasons.map(r => <option key={r}>{r}</option>)}</select></label><label>추가 설명 (선택)<Textarea maxLength={1000} rows={3} value={detail} onChange={e => setDetail(e.target.value)}/></label><p className={[styles.scope, "auth-hint"].join(" ")}>계정당 같은 공고 1회 신고를 허용하는 임시 Mock 정책입니다.</p>{error && <p role="alert" className={[styles.scope, "form-error"].join(" ")}>{error}</p>}<div className={[styles.scope, "modal-actions"].join(" ")}><Button type="button" variant="secondary" onClick={() => setModal(null)}>취소</Button><Button type="submit">신고 기록</Button></div></form></Modal>}</>; }
