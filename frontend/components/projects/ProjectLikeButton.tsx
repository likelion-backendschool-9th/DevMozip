'use client';
import Link from 'next/link';
import {Heart} from 'lucide-react';
import {useProjectLikes} from '@/lib/projects/projectLikeStore';
import styles from './ProjectIconActions.module.css';
export function ProjectLikeButton({projectId}:{projectId:number}){
 const state=useProjectLikes();const {owner}=state;const liked=state.items.some(p=>p.id===projectId);const toggle=()=>state.toggle(projectId);const title=liked?'좋아요 취소':'좋아요';
 return owner?<button type="button" className={styles.icon} title={state.error?.message||title} disabled={!state.ready||state.loading||state.pending||!!state.error} aria-label={title} aria-pressed={liked} onClick={toggle}><Heart fill={liked?'currentColor':'none'}/></button>:<Link className={styles.icon} href="/login" title="좋아요" aria-label="로그인 후 좋아요"><Heart/></Link>;
}
