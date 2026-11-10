'use client';
import {useEffect,useRef,useState} from 'react';
import Link from 'next/link';
import {adminStyles as styles} from '../../_components/AdminShell';
import {Button} from '@/components/common/Button';
import {Modal} from '@/components/common/Modal';
import {useAuth} from '@/lib/state/authStore';
import type {ManagedProjectReport,ProjectReportPage} from '@/types/projectReportAdmin';
const labels:Record<string,string>={RECEIVED:'접수',REVIEWING:'검토 중',RESOLVED:'처리 완료',REJECTED:'기각'};
async function request<T>(path:string,init?:RequestInit):Promise<T>{const response=await fetch('/api/admin/project-reports'+path,{...init,cache:'no-store'});const body=await response.json();if(!response.ok)throw Error(body.message||'신고 처리 상태를 확인해 주세요.');return body;}
export function ApiProjectReports(){
 const {user}=useAuth();const [status,setStatus]=useState('');const [page,setPage]=useState(0);const [version,setVersion]=useState(0);const [data,setData]=useState<ProjectReportPage|null>(null);const [error,setError]=useState('');
 const [selected,setSelected]=useState<ManagedProjectReport|null>(null);const [decision,setDecision]=useState('REVIEWING');const [note,setNote]=useState('');const [pending,setPending]=useState(false);const lock=useRef(false);
 useEffect(()=>{setData(null);setError('');setSelected(null);const abort=new AbortController();if(user?.role!=='admin')return;
 request<ProjectReportPage>('?page='+page+'&size=20'+(status?'&status='+status:''),{signal:abort.signal}).then(v=>{if(!abort.signal.aborted)setData(v);}).catch(e=>{if(!abort.signal.aborted)setError(e instanceof Error?e.message:'신고를 불러오지 못했습니다.');});return()=>abort.abort();},[page,status,version,user?.email,user?.role]);
 const cls=(name:string)=>[styles.scope,name].join(' ');
 if(user?.role!=='admin')return null;
 return <section className={cls('governance-panel admin-main')} aria-label="프로젝트 신고 관리"><h2>프로젝트 신고 처리</h2><p>공고 및 프로젝트 팀원 신고를 검토합니다. 처리 결과 저장만으로 강퇴·정지·감점하지 않습니다.</p><label>처리 상태<select className="form-input" value={status} onChange={e=>{setStatus(e.target.value);setPage(0);}}><option value="">전체</option>{Object.entries(labels).map(([v,label])=><option value={v} key={v}>{label}</option>)}</select></label>
 {error&&<div role="alert">{error}<Button variant="secondary" onClick={()=>setVersion(v=>v+1)}>다시 조회</Button></div>}{!data&&!error&&<p role="status">신고를 불러오고 있습니다.</p>}{data?.items.length===0&&<p>접수된 신고가 없습니다.</p>}
 {data?.items.map(r=><article className={cls('admin-row')} key={r.id}><div><Link href={'/projects/'+r.projectId}>{r.projectTitle}</Link><p>{r.type==='MEMBER'?'팀원 신고 · '+r.targetNickname:'프로젝트 공고 신고'} · 신고자 {r.reporterNickname}</p><p className="preserve-text">{r.reason}</p><small>{labels[r.status]} · {r.createdAt.replace('T',' ')}</small>{r.resolution&&<p>처리 결과: {r.resolution}</p>}{r.handledAt&&<small>처리자 #{r.handledBy} · {r.handledAt.replace('T',' ')}</small>}</div>{!['RESOLVED','REJECTED'].includes(r.status)&&<Button variant="secondary" onClick={()=>{setSelected(r);setDecision('REVIEWING');setNote('');setError('');}}>검토 및 처리</Button>}</article>)}
 {data&&data.totalPages>1&&<div className={cls('governance-actions')}><Button disabled={page===0} variant="secondary" onClick={()=>setPage(p=>p-1)}>이전</Button><span>{page+1}/{data.totalPages}</span><Button disabled={page+1>=data.totalPages} variant="secondary" onClick={()=>setPage(p=>p+1)}>다음</Button></div>}
 {selected&&<Modal title="신고 검토 결과" onClose={()=>{if(!pending)setSelected(null);}}><form className="profile-form" onSubmit={async e=>{e.preventDefault();if(lock.current)return;lock.current=true;setPending(true);try{await request('/'+selected.id,{method:'PATCH',headers:{'Content-Type':'application/json'},body:JSON.stringify({status:decision,resolution:note})});setSelected(null);setVersion(v=>v+1);}catch(e){setError(e instanceof Error?e.message:'처리 결과를 확인해 주세요.');}finally{lock.current=false;setPending(false);}}}><label>처리 상태<select className="form-input" value={decision} onChange={e=>setDecision(e.target.value)}>{Object.entries(labels).filter(([v])=>v!=='RECEIVED').map(([v,label])=><option key={v} value={v}>{label}</option>)}</select></label><label>처리 메모<textarea className="form-input" rows={4} required={decision!=='REVIEWING'} maxLength={1000} value={note} onChange={e=>setNote(e.target.value)}/></label>{error&&<p role="alert">{error}</p>}<Button type="submit" disabled={pending}>결과 저장</Button></form></Modal>}
 </section>;
}
