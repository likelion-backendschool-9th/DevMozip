'use client';
import styles from './ActivityTab.module.css';
import { useAuth } from '@/lib/state/AuthProvider';
import Link from 'next/link';
import { ArrowUpRight } from 'lucide-react';
import { useProjects } from '@/lib/state/ProjectProvider';
import { Card } from '@/components/common/Card';
export function ActivityTab({ profileId }: {
    profileId: string;
}) { const { projects, history } = useProjects(); const { user } = useAuth(); const items = history.filter(c => c.members.some(m => m.userId === profileId)).sort((a, b) => b.start.localeCompare(a.start)); return <div><div className={[styles.scope, "tab-section-title"].join(" ")}><div><h2>함께 만들어온 프로젝트</h2><p>DevMozip에서 모집하거나 참여한 프로젝트 이력만 표시합니다.</p></div></div>{items.length === 0 && <div className={[styles.scope, "empty-state"].join(" ")}>아직 DevMozip 프로젝트 참여 기록이 없습니다.</div>}<div className={[styles.scope, "activity-list"].join(" ")}>{items.map(c => { const p = projects.find(p => p.id === c.projectId)!; return <Card className={[styles.scope, "activity-card"].join(" ")} key={c.projectId}><span className={[styles.scope, `activity-mark ${p.color}`].join(" ")}>{p.mark}</span><div><span className={[styles.scope, `activity-status ${c.status === '프로젝트 종료' ? 'ended' : ''}`].join(" ")}>{c.status}</span><h3><Link href={`/projects/${p.id}`} className={styles.projectTitle}>{p.title}</Link></h3><p>계획 {c.start} — {c.end}{c.endedAt ? ` · 종료 ${new Date(c.endedAt).toLocaleDateString('ko-KR', { timeZone: 'Asia/Seoul' })}` : ''} · {c.members.find(m => m.userId === profileId)?.role}</p></div></Card>; })}</div>{user?.id === profileId && <div className={styles.footer}><Link className="button secondary" href="/my-projects">내 프로젝트 관리<ArrowUpRight size={18} aria-hidden="true"/></Link></div>}</div>; }
