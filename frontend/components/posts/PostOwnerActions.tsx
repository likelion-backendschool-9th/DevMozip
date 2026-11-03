'use client';
import Link from 'next/link';
import { Eye, EyeOff, Pencil, Trash2 } from 'lucide-react';
import styles from './PostOwnerActions.module.css';
export function PostOwnerActions({hidden,onToggle,onRemove,editHref,onEdit,removeLabel='삭제'}:{hidden:boolean;onToggle:()=>void;onRemove:()=>void;editHref?:string;onEdit?:()=>void;removeLabel?:string}){
 const visibility=hidden?'숨김 해제':'게시글 숨기기';
 return <div className={styles.actions} aria-label="포스팅 관리"><button type="button" aria-label={visibility} title={visibility} aria-pressed={hidden} onClick={onToggle}>{hidden?<EyeOff size={18}/>:<Eye size={18}/>}</button>{editHref?<Link href={editHref} aria-label="게시글 수정" title="게시글 수정"><Pencil size={18}/></Link>:<button type="button" onClick={onEdit} aria-label="의견 수정" title="의견 수정"><Pencil size={18}/></button>}<button type="button" className={styles.remove} aria-label={removeLabel} title={removeLabel} onClick={onRemove}><Trash2 size={18}/></button></div>;
}
