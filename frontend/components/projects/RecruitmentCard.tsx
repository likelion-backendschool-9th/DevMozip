'use client';
import styles from './RecruitmentCard.module.css';
import Link from 'next/link';
import { ArrowUpRight, CalendarDays } from 'lucide-react';
import { useAuth } from '@/lib/state/AuthProvider';
import { useProjects } from '@/lib/state/ProjectProvider';
import { applicationFor, projectStatus } from '@/lib/projects/projectService';
import { Card } from '@/components/common/Card';
import { BookmarkButton } from '@/app/projects/[projectId]/_components/ProjectActions';
import type { RecruitmentProject } from '@/types/project';
export function RecruitmentCard({ project: p }: {
    project: RecruitmentProject;
}) { const { user } = useAuth(); const { state, now } = useProjects(); const application = applicationFor(state, p.id, user?.id); return <Card className={[styles.scope, "recruitment-card"].join(" ")}><div className={[styles.scope, `project-cover ${p.color}`].join(" ")}><span>{p.field}</span><div className={[styles.scope, "cover-glyph"].join(" ")}>{p.mark}<span>↗</span></div><span className={[styles.scope, "recruit-badge"].join(" ")}>{projectStatus(p, now)}</span></div><div className={[styles.scope, "project-content"].join(" ")}><div className={[styles.scope, "recruitment-card-title"].join(" ")}><Link href={`/projects/${p.id}`}><h3>{p.title}</h3></Link><BookmarkButton projectId={p.id} compact/></div><p>{p.summary}</p><div className={[styles.scope, "tags"].join(" ")}>{p.stacks.map(s => <span key={s}>{s}</span>)}</div><div className={[styles.scope, "recruitment-slots"].join(" ")}>{p.slots.map(s => <div key={s.role}><strong>{s.role}</strong><span>{s.filled}/{s.count}명</span><small>{s.minYears ? `${s.minYears}년 이상` : '경력 무관'}</small></div>)}</div><div className={[styles.scope, "recruitment-card-dates"].join(" ")}><CalendarDays size={12}/>{p.developmentStart} — {p.developmentEnd}</div><div className={[styles.scope, "project-bottom"].join(" ")}><span>모집 마감 {p.deadline}</span><Link href={`/projects/${p.id}`}>{application ? application.status : '상세 보기'}<ArrowUpRight size={14}/></Link></div></div></Card>; }
