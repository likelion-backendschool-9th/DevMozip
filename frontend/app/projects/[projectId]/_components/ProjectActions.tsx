'use client';
import styles from './ProjectActions.module.css';
import icons from '@/components/projects/ProjectIconActions.module.css';
import {ProjectIconActions} from '@/components/projects/ProjectIconActions';
import { useState } from 'react';
import Link from 'next/link';
import { Bookmark, Flag, Heart } from 'lucide-react';
import { useAuth } from '@/lib/state/authStore';
import { useProjects } from '@/lib/state/projectStore';
import { ProjectShareButton } from '@/components/projects/ProjectShareButton';
import { Modal } from '@/components/common/Modal';
import { Button } from '@/components/common/Button';
import { Textarea } from '@/components/common/Input';
import { reportReasons } from '@/mocks/project';
import type { RecruitmentProject } from '@/types/project';
export function BookmarkButton({ projectId, compact = false }: {
    projectId: string;
    compact?: boolean;
}) { const { user } = useAuth(); const { state, act } = useProjects(); const saved = !!user && !!state.bookmarks[user.id]?.includes(projectId); return user ? <button className={[icons.icon,compact?icons.compact:''].join(' ')} aria-label={saved ? '북마크 해제' : '북마크 저장'} aria-pressed={saved} onClick={() => act({ type: 'bookmark', projectId })}><Bookmark size={21} fill={saved ? 'currentColor' : 'none'}/></button> : <Link className={[icons.icon,compact?icons.compact:''].join(' ')} href="/login" aria-label="로그인 후 북마크"><Bookmark size={21}/></Link>; }
export function ProjectActions({project}:{project:RecruitmentProject}){
 const {user}=useAuth();const {state,act}=useProjects();const [open,setOpen]=useState(false);
 const [reason,setReason]=useState(reportReasons[0]);const [detail,setDetail]=useState('');const [error,setError]=useState('');
 const liked=!!user&&!!state.likes?.[user.id]?.includes(project.id);
 const reported=!!user&&state.reports.some(r=>r.projectId===project.id&&r.userId===user.id);
 return <><div className={[styles.scope,'project-detail-actions'].join(' ')}>
  <ProjectIconActions>
   {user?<button type="button" className={icons.icon} aria-label={liked?'좋아요 취소':'좋아요'} title={liked?'좋아요 취소':'좋아요'} aria-pressed={liked} onClick={()=>act({type:'like',projectId:project.id})}><Heart fill={liked?'currentColor':'none'}/></button>:<Link className={icons.icon} href="/login" title="좋아요" aria-label="로그인 후 좋아요"><Heart/></Link>}
   <ProjectShareButton projectId={project.id}/><BookmarkButton projectId={project.id}/>
  </ProjectIconActions>
  {project.hostId!==user?.id&&(user?<Button variant="secondary" disabled={reported} onClick={()=>setOpen(true)}><Flag size={14}/>{reported?'신고됨':'신고'}</Button>:<Link href="/login" className="button secondary"><Flag size={14}/>신고</Link>)}
 </div>{open&&<Modal title="프로젝트 신고" onClose={()=>setOpen(false)}><form className={[styles.scope,'profile-form'].join(' ')} onSubmit={e=>{e.preventDefault();const result=act({type:'report',projectId:project.id,reason,detail});if(result.ok)setOpen(false);else setError(result.error||'신고 내용을 확인해 주세요.');}}>
  <label>신고 사유<select className="form-input" value={reason} onChange={e=>setReason(e.target.value)}>{reportReasons.map(r=><option key={r}>{r}</option>)}</select></label>
  <label>추가 설명 (선택)<Textarea maxLength={1000} rows={3} value={detail} onChange={e=>setDetail(e.target.value)}/></label>
  <p className="auth-hint">계정당 같은 공고 1회 신고를 허용하는 임시 Mock 정책입니다.</p>{error&&<p role="alert" className="form-error">{error}</p>}
  <div className="modal-actions"><Button type="button" variant="secondary" onClick={()=>setOpen(false)}>취소</Button><Button type="submit">신고 기록</Button></div>
 </form></Modal>}</>;
}
