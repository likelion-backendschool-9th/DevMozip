'use client';
import {useRef,useState} from 'react';
import {Plus,X,ImageIcon} from 'lucide-react';
import {readAvatar} from '@/lib/account/onboardingService';
import {avatarPolicy} from '@/mocks/account';
import styles from './PortfolioCoverEditor.module.css';
export function PortfolioCoverEditor({value,onChange,onBusy}:{value:string;onChange:(value:string)=>void;onBusy:(busy:boolean)=>void}){
 const input=useRef<HTMLInputElement>(null);const [busy,setBusy]=useState(false);const [error,setError]=useState('');
 return <section className={styles.editor} aria-label="대표 이미지 편집"><div className={styles.heading}><strong>대표 이미지</strong><span>PNG · JPEG · WebP / 256KB 이하</span></div><div className={styles.banner}>{value?<img src={value} alt="대표 이미지 미리보기"/>:<div className={styles.placeholder}><ImageIcon size={26}/><span>프로젝트를 보여줄 대표 이미지를 추가하세요.</span></div>}<div className={styles.actions}><button type="button" disabled={busy} aria-label="대표 이미지 변경" title="대표 이미지 변경" onClick={()=>input.current?.click()}><Plus size={22}/></button>{value&&<button type="button" disabled={busy} className={styles.remove} aria-label="대표 이미지 삭제" title="대표 이미지 삭제" onClick={()=>{onChange('');setError('');}}><X size={17}/></button>}</div></div><input ref={input} hidden type="file" aria-label="대표 이미지" accept={avatarPolicy.types.join(',')} disabled={busy} onChange={async e=>{const file=e.target.files?.[0];e.target.value='';if(!file)return;setBusy(true);onBusy(true);setError('');try{const src=await readAvatar(file);await new Promise<void>((resolve,reject)=>{const image=new Image();image.onload=()=>resolve();image.onerror=()=>reject(new Error());image.src=src;});onChange(src);}catch{setError('PNG/JPEG/WebP 이미지, 256KB 이하의 파일을 선택해 주세요.');}finally{setBusy(false);onBusy(false);}}}/>{busy&&<p role="status">이미지를 확인하고 있어요.</p>}{error&&<p role="alert" className="form-error">{error}</p>}</section>;
}
