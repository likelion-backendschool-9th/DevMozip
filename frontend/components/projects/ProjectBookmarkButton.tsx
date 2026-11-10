'use client';
import Link from 'next/link';
import {Bookmark} from 'lucide-react';
import styles from './ProjectIconActions.module.css';
import {useProjectBookmarks} from '@/lib/projects/projectBookmarkStore';
export function ProjectBookmarkButton({projectId,compact=false}:{projectId:number;compact?:boolean}){
 const state=useProjectBookmarks();const saved=state.items.some(p=>p.id===projectId);const icon=<Bookmark size={16} fill={saved?'currentColor':'none'}/>;const className=[styles.icon,compact?styles.compact:''].join(' ');
 return state.owner?<button type="button" className={className} title={state.error?.message||(saved?'북마크 해제':'북마크 저장')} disabled={!state.ready||state.loading||state.pending||!!state.error} aria-label={saved?'북마크 해제':'북마크 저장'} aria-pressed={saved} onClick={()=>state.toggle(projectId)}>{icon}</button>:<Link className={className} href="/login" aria-label="로그인 후 북마크">{icon}</Link>;
}
