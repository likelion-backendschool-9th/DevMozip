'use client';
import styles from './ActivityTab.module.css';
import { useAuth } from '@/lib/state/authStore';
import Link from 'next/link';
import { ArrowUpRight } from 'lucide-react';
import { useProjects } from '@/lib/state/projectStore';
import { ActivityProjectCard } from './ActivityProjectCard';
import { projectDetailHref } from '@/lib/projects/projectNavigation';
export function ActivityTab({ profileId }: {
    profileId: string;
}) { const { projects, history } = useProjects(); const { user } = useAuth(); const items = history.filter(c => c.members.some(m => m.userId === profileId)).sort((a, b) => b.start.localeCompare(a.start)); return <div><div className={[styles.scope,styles.heading, "tab-section-title"].join(" ")}><div><h2>함께 만들어온 프로젝트</h2><p>DevMozip에서 모집하거나 참여한 프로젝트 이력만 표시합니다.</p></div>{user?.id === profileId && <Link className={`button secondary ${styles.manageLink}`} href="/my-projects">내 프로젝트 관리<ArrowUpRight size={18} aria-hidden="true"/></Link>}</div>{items.length === 0 && <div className={[styles.scope, "empty-state"].join(" ")}>아직 DevMozip 프로젝트 참여 기록이 없습니다.</div>}<div className={[styles.scope, "activity-list"].join(" ")}>{items.map(c => { const p = projects.find(p => p.id === c.projectId)!; return <ActivityProjectCard key={c.projectId} title={p.title} href={projectDetailHref(p.id,user?.id===profileId?'/profile/me?tab=activity':`/users/${encodeURIComponent(profileId)}?tab=activity`)} status={c.status} start={c.start} end={c.end} completedAt={c.endedAt} role={c.members.find(m => m.userId === profileId)?.role} mark={p.mark} color={p.color} />; })}</div></div>; }
