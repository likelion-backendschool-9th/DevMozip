'use client';
import Link from 'next/link';
import { ArrowUpRight, CalendarDays, Users } from 'lucide-react';
import styles from './ProjectResults.module.css';
import { Card } from '@/components/common/Card';
import { ProjectBookmarkButton } from '@/components/projects/ProjectBookmarkButton';
import { projectRoleLabels, projectSearchStatusLabels } from '@/lib/search/searchApi';
import type { ProjectSearchItem } from '@/types/searchApi';

const scope = (name: string) => [styles.scope, name].join(' ');

/**
 * Mock 끄기의 프로젝트 카드(WBS 6.3·6.8·6.9). 모양은 Mock 카드(ProjectResults)와 같다.
 * 표시 글자는 API에 없으므로 /projects 목록처럼 제목 첫 글자를 쓴다. 북마크는 프로젝트 북마크(7.16) 버튼과 상태를 그대로 쓴다.
 */
export function ApiProjectResults({ items }: { items: ProjectSearchItem[] }) {
    return <div className={scope('search-project-list')}>{items.map(project => {
        const href = `/projects/${project.id}`;
        return <Card key={project.id} className={scope('search-project-card')}>
            <span className={scope('search-project-mark lavender')} aria-hidden="true">{project.title.charAt(0)}</span>
            <div className={scope('search-project-info')}>
                <div className={scope('search-project-eyebrow')}>
                    <span>{project.interests.join(' · ') || '분야 미입력'}</span>
                    <span className={scope('outline-badge')}>{projectSearchStatusLabels[project.status]}</span>
                </div>
                <Link href={href}><h3>{project.title}</h3></Link>
                <p>{project.summary}</p>
                <div className={scope('tags')}>{project.techStacks.map((stack, index) => <span key={`${stack}-${index}`}>{stack}</span>)}</div>
                <div className={scope('search-project-facts')}>
                    <span>모집 직군 · {projectRoleLabels(project.jobRoles).join(' / ') || '—'}</span>
                    <span><Users size={12} />{project.memberCount}/{project.totalCapacity}명</span>
                    <span><CalendarDays size={12} />{project.developmentStart} — {project.developmentEnd}</span>
                </div>
            </div>
            <ProjectBookmarkButton projectId={project.id} compact />
            <Link className={scope('icon-button')} href={href} aria-label={`${project.title} 상세 보기`}><ArrowUpRight size={19} /></Link>
        </Card>;
    })}</div>;
}
