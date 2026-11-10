'use client';
import {useState} from 'react';
import {Share2} from 'lucide-react';
import {Modal} from '@/components/common/Modal';
import {Button} from '@/components/common/Button';
import {Input} from '@/components/common/Input';
import {notify} from '@/lib/state/prototypeStore';
import styles from './ProjectIconActions.module.css';
export function ProjectShareButton({projectId}:{projectId:string|number}){
 const [open,setOpen]=useState(false);
 const url=typeof window==='undefined'?'/projects/'+projectId:new URL('/projects/'+projectId,window.location.origin).href;
 return <><button type="button" className={styles.icon} title="공유" aria-label="프로젝트 공유" onClick={()=>setOpen(true)}><Share2/></button>{open&&<Modal title="프로젝트 공유" onClose={()=>setOpen(false)}><p>프로젝트 주소를 복사해 공유하세요.</p><Input aria-label="프로젝트 공유 주소" value={url} readOnly onFocus={e=>e.target.select()}/><div className="modal-actions"><Button variant="secondary" onClick={()=>setOpen(false)}>닫기</Button><Button onClick={async()=>{try{await navigator.clipboard.writeText(url);notify('프로젝트 주소를 복사했습니다.');setOpen(false);}catch{notify('주소 입력창을 선택한 뒤 직접 복사해 주세요.');}}}>주소 복사</Button></div></Modal>}</>;
}
