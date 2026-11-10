'use client';
import {useRef,useState,type ReactNode} from 'react';
import Link from 'next/link';
import {useParams} from 'next/navigation';
import {ArrowLeft,CheckCircle2,Flag} from 'lucide-react';
import {Button} from '@/components/common/Button';
import {Textarea} from '@/components/common/Input';
import {useAuth} from '@/lib/state/authStore';
import {useMockVisibility} from '@/lib/state/mockVisibilityStore';
import {useProjects} from '@/lib/state/projectStore';
import {useProjectResource,projectRequest} from '@/lib/projects/useProjectApi';
import {validProjectId} from '@/lib/projects/projectApiContract';
import {projectReportError,PROJECT_REPORT_DETAIL_LIMIT} from '@/lib/projects/projectReport';
import {reportReasons} from '@/mocks/project';
import type {ProjectDetailResult} from '@/types/projectApi';
import styles from './ProjectReportPage.module.css';

function Notice({projectId,title,children,success=false}:{projectId:string;title:string;children?:ReactNode;success?:boolean}){return <div className={styles.panel}><div className={styles.success}>{success?<CheckCircle2 size={38}/>:<Flag size={38}/>}<h2>{title}</h2>{children}<Link className="button secondary" href={'/projects/'+projectId}>프로젝트로 돌아가기</Link></div></div>;}
function Form({projectId,title,summary,submit}:{projectId:string;title:string;summary:string;submit:(reason:string,detail:string)=>void|Promise<void>}){
 const [reason,setReason]=useState('');const [detail,setDetail]=useState('');const [error,setError]=useState('');const [pending,setPending]=useState(false);const [done,setDone]=useState(false);const lock=useRef(false);
 if(done)return <Notice success projectId={projectId} title="신고가 접수되었습니다"><p>운영 정책에 따라 내용을 검토하겠습니다.</p></Notice>;
 return <div className={styles.panel}><div className={styles.project}><h2>{title}</h2><p>{summary}</p></div><form className={styles.form} onSubmit={async e=>{e.preventDefault();if(lock.current)return;const invalid=projectReportError(reason,detail);if(invalid){setError(invalid);return;}lock.current=true;setPending(true);setError('');try{await submit(reason,detail);setDone(true);}catch(e){setError(e instanceof Error?e.message:'신고를 접수하지 못했습니다.');}finally{lock.current=false;setPending(false);}}}>
  <fieldset disabled={pending}><legend>신고 사유 <span aria-label="필수">*</span></legend><div className={styles.options}>{reportReasons.map(value=><label className={styles.option} key={value}><input type="radio" name="reason" value={value} checked={reason===value} required onChange={()=>setReason(value)}/>{value}</label>)}</div></fieldset>
  <label className={styles.detail}>추가 설명 (선택)<Textarea aria-label="추가 설명" value={detail} onChange={e=>setDetail(e.target.value)} disabled={pending} maxLength={PROJECT_REPORT_DETAIL_LIMIT} placeholder="신고 사유를 확인할 수 있는 내용을 적어 주세요."/><span className={styles.counter}>{detail.length.toLocaleString()} / 1,000</span></label>
  <p className={styles.hint}>프로젝트 모집 내용과 운영 정책 위반 사항을 확인한 후 신고해 주세요. 같은 프로젝트는 계정당 한 번 신고할 수 있습니다.</p>
  {error&&<p className={styles.error} role="alert">{error}</p>}
  <div className={styles.actions}><Link className="button secondary" href={'/projects/'+projectId}>취소</Link><Button type="submit" disabled={pending}><Flag size={16}/>{pending?'접수 중…':'신고 접수'}</Button></div>
 </form></div>;
}
function MockReport({projectId}:{projectId:string}){
 const {user}=useAuth();const {projects,state,act}=useProjects();const project=projects.find(p=>p.id===projectId);
 const initiallyReported=useRef(state.reports.some(r=>r.userId===user?.id&&r.projectId===projectId)).current;
 if(!project)return <Notice projectId={projectId} title="프로젝트를 찾을 수 없습니다"/>;
 if(project.hostId===user?.id)return <Notice projectId={projectId} title="본인의 프로젝트는 신고할 수 없습니다"/>;
 if(initiallyReported)return <Notice projectId={projectId} title="이미 신고한 프로젝트입니다"/>;
 return <Form projectId={projectId} title={project.title} summary={project.summary} submit={(reason,detail)=>{const result=act({type:'report',projectId,reason,detail});if(!result.ok)throw new Error(result.error||'신고를 접수하지 못했습니다.');}}/>;
}
function ApiReport({projectId}:{projectId:string}){
 const resource=useProjectResource<ProjectDetailResult>(validProjectId(projectId)?'/'+projectId:null);
 if(!validProjectId(projectId))return <Notice projectId={projectId} title="프로젝트 ID를 확인해 주세요"/>;
 if(resource.loading)return <p role="status">프로젝트를 불러오고 있습니다.</p>;
 if(resource.error)return <div className={styles.panel}><p role="alert">{resource.error.message}</p><Button variant="secondary" onClick={resource.reload}>다시 시도</Button></div>;
 if(!resource.data)return null;
 if(resource.data.membership?.myRole==='HOST')return <Notice projectId={projectId} title="본인의 프로젝트는 신고할 수 없습니다"/>;
 return <Form projectId={projectId} title={resource.data.project.title} summary={resource.data.project.summary??''} submit={async(reason,detail)=>{await projectRequest('/'+projectId+'/reports','POST',{reason,detail});}}/>;
}
export function ProjectReportPage(){
 const {projectId=''}=useParams<{projectId:string}>();const {enabled}=useMockVisibility();const {user}=useAuth();
 return <main className={styles.page}><Link className="text-link" href={'/projects/'+projectId}><ArrowLeft size={15}/>프로젝트로 돌아가기</Link><header className={styles.header}><span className="eyebrow">PROJECT REPORT</span><h1>프로젝트 신고</h1><p>운영 정책을 위반한 프로젝트를 알려 주세요.</p></header>
  {!user?<div className={styles.panel}><p>로그인 후 프로젝트를 신고할 수 있습니다.</p><Link className="button" href={'/login?next='+encodeURIComponent('/projects/'+projectId+'/report')}>로그인하기</Link></div>:enabled?<MockReport key={projectId+user.email} projectId={projectId}/>:<ApiReport key={projectId+user.email} projectId={projectId}/>}
 </main>;
}
