'use client';
import styles from './ProjectNotices.module.css';
import { projectNoticeHref } from '@/lib/projects/projectNoticeView';
import Link from 'next/link';
import { useAuth } from '@/lib/state/AuthProvider';
import { useProjects } from '@/lib/state/ProjectProvider';
export function ProjectNotices({ projectId }: {
    projectId?: string;
}) { const { user } = useAuth(); const { state } = useProjects(); const items = state.notifications.filter(n => n.recipientId === user?.id && (!projectId || n.projectId === projectId)); return <div className={[styles.scope, "project-notices"].join(" ")}><h2>프로젝트 알림 <span>{items.length}</span></h2><p className={[styles.scope, "auth-hint"].join(" ")}>현재 계정에 생성된 Mock 프로젝트 알림입니다.</p>{!items.length && <p className={[styles.scope, "empty-state"].join(" ")}>아직 프로젝트 알림이 없습니다.</p>}{items.map(n => <article key={n.id}><div><strong>{n.title} {n.resolvedAt && <small>처리 완료</small>}</strong><time>{new Date(n.createdAt).toLocaleString('ko-KR', { timeZone: 'Asia/Seoul' })}</time></div><p>{n.body}</p><Link href={projectNoticeHref(n, state)}>내용 확인 →</Link>{!n.resolvedAt && n.actions?.map(a => <Link className={[styles.scope, "button secondary small"].join(" ")} key={a.label} href={a.href}>{a.label}</Link>)}{state.deletedIds.includes(n.projectId) && <small>삭제된 공고입니다.</small>}</article>)}</div>; }
