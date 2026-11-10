'use client';
import styles from './ProjectActions.module.css';
import icons from '@/components/projects/ProjectIconActions.module.css';
import {ProjectReportButton} from '@/components/projects/ProjectReportButton';
import {ProjectIconActions} from '@/components/projects/ProjectIconActions';
import Link from 'next/link';
import { Bookmark, Heart } from 'lucide-react';
import { useAuth } from '@/lib/state/authStore';
import { useProjects } from '@/lib/state/projectStore';
import { ProjectShareButton } from '@/components/projects/ProjectShareButton';
import type { RecruitmentProject } from '@/types/project';
export function BookmarkButton({ projectId, compact = false }: {
    projectId: string;
    compact?: boolean;
}) { const { user } = useAuth(); const { state, act } = useProjects(); const saved = !!user && !!state.bookmarks[user.id]?.includes(projectId); return user ? <button className={[icons.icon,compact?icons.compact:''].join(' ')} aria-label={saved ? '북마크 해제' : '북마크 저장'} aria-pressed={saved} onClick={() => act({ type: 'bookmark', projectId })}><Bookmark size={21} fill={saved ? 'currentColor' : 'none'}/></button> : <Link className={[icons.icon,compact?icons.compact:''].join(' ')} href="/login" aria-label="로그인 후 북마크"><Bookmark size={21}/></Link>; }
export function ProjectActions({project}:{project:RecruitmentProject}){
 const {user}=useAuth();const {state,act}=useProjects();
 const liked=!!user&&!!state.likes?.[user.id]?.includes(project.id);
 const reported=!!user&&state.reports.some(r=>r.projectId===project.id&&r.userId===user.id);
 return <><div className={[styles.scope,'project-detail-actions'].join(' ')}>
  <ProjectIconActions>
   {user?<button type="button" className={icons.icon} aria-label={liked?'좋아요 취소':'좋아요'} title={liked?'좋아요 취소':'좋아요'} aria-pressed={liked} onClick={()=>act({type:'like',projectId:project.id})}><Heart fill={liked?'currentColor':'none'}/></button>:<Link className={icons.icon} href="/login" title="좋아요" aria-label="로그인 후 좋아요"><Heart/></Link>}
   <ProjectShareButton projectId={project.id}/><BookmarkButton projectId={project.id}/>
   {project.hostId!==user?.id&&<ProjectReportButton projectId={project.id} reported={reported}/>}
  </ProjectIconActions>
 </div></>;
}
