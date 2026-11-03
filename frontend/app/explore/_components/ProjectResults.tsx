'use client';
import styles from './ProjectResults.module.css';
import Link from 'next/link';
import { ArrowUpRight, CalendarDays, Users } from 'lucide-react';
import { projectStatus } from '@/lib/projects/projectService';
import { BookmarkButton } from '@/app/projects/[projectId]/_components/ProjectActions';
import { Card } from '@/components/common/Card';
import type { Project } from '@/types/home';
export function ProjectResults({ items }: {
    items: Project[];
}) { return <div className={[styles.scope, "search-project-list"].join(" ")}>{items.map(p => <Card key={p.id} className={[styles.scope, "search-project-card"].join(" ")}><span className={[styles.scope, `search-project-mark ${p.color}`].join(" ")}>{p.mark}</span><div className={[styles.scope, "search-project-info"].join(" ")}><div className={[styles.scope, "search-project-eyebrow"].join(" ")}><span>{p.field}</span><span className={[styles.scope, "outline-badge"].join(" ")}>{projectStatus(p)}</span></div><Link href={`/projects/${p.id}`}><h3>{p.title}</h3></Link><p>{p.summary}</p><div className={[styles.scope, "tags"].join(" ")}>{p.stacks.map(s => <span key={s}>{s}</span>)}</div><div className={[styles.scope, "search-project-facts"].join(" ")}><span>모집 직군 · {p.roles.join(' / ')}</span><span><Users size={12}/>{p.members}/{p.capacity}명</span><span><CalendarDays size={12}/>{p.duration}</span></div></div><BookmarkButton projectId={p.id} compact/><Link className={[styles.scope, "icon-button"].join(" ")} href={`/projects/${p.id}`} aria-label={`${p.title} 상세 보기`}><ArrowUpRight size={19}/></Link></Card>)}</div>; }
