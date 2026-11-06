'use client';
import {useId,useState} from 'react';
import Link from 'next/link';
import {Button} from '@/components/common/Button';
import type {ProjectApiError} from '@/lib/projects/useProjectApi';
import styles from './ApiCommon.module.css';
export function ApiError({error,retry}:{error:ProjectApiError;retry?:()=>void}){return <div className={styles.error} role="alert"><p>{error.message}</p>{error.status===401&&<Link href="/login">로그인하기</Link>}{retry&&<Button variant="secondary" onClick={retry}>다시 조회</Button>}</div>;}
export function TagsInput({label,values,onChange,options=[],max=5}:{label:string;values:string[];onChange:(values:string[])=>void;options?:string[];max?:number}){
 const [text,setText]=useState('');const id=useId();const add=(value:string)=>{const next=value.normalize('NFKC').trim();if(next&&next.length<=30&&values.length<max&&!values.some(v=>v.toLowerCase()===next.toLowerCase())){onChange([...values,next]);setText('');}};
 return <div><label>{label}<input aria-label={label+' 입력'} list={id} value={text} maxLength={30} onChange={e=>setText(e.target.value)} onKeyDown={e=>{if(e.key==='Enter'){e.preventDefault();add(text);}}} placeholder="검색 또는 직접 입력 후 Enter" disabled={values.length>=max}/></label><datalist id={id}>{options.filter(v=>!values.includes(v)).map(v=><option key={v} value={v}/>)}</datalist><div className={styles.actions}><Button type="button" variant="secondary" disabled={!text.trim()||values.length>=max} onClick={()=>add(text)}>+</Button><span className={styles.muted}>{values.length}/{max}</span></div><div className={styles.tags}>{values.map(v=><button key={v} type="button" aria-label={v+' 삭제'} onClick={()=>onChange(values.filter(x=>x!==v))}>{v} ×</button>)}</div></div>;
}
