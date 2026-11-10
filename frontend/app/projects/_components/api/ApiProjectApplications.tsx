'use client';
import {useRef,useState} from 'react';
import Link from 'next/link';
import {Card} from '@/components/common/Card';
import {Button} from '@/components/common/Button';
import {Textarea} from '@/components/common/Input';
import {Modal} from '@/components/common/Modal';
import {useProjectResource,projectRequest,ProjectApiError} from '@/lib/projects/useProjectApi';
import {projectRoleLabel} from '@/lib/projects/projectChoices';
import type {ApiApplication,ApiApplicationOptions,ApiProjectMember,ApplicationStatus} from '@/types/projectApplicationApi';
import management from '../../[projectId]/manage/page.module.css';
import applicationStyle from '../../[projectId]/_components/ApplicationModal.module.css';
import {ApiError} from './ApiCommon';
const labels:Record<ApplicationStatus,string>={PENDING:'검토 대기',APPROVED:'승인',REJECTED:'거절',CANCELLED:'지원 취소'};
export function ApiProjectApplications({projectId,title,host,team,onChanged}:{projectId:number;title:string;host:boolean;team:boolean;onChanged:()=>void}){
 const base='/'+projectId;
 const options=useProjectResource<ApiApplicationOptions>(host?null:base+'/application-options');
 const applications=useProjectResource<ApiApplication[]>(host?base+'/applications':null);
 const members=useProjectResource<ApiProjectMember[]>(team?base+'/members':null);
 const [tab,setTab]=useState<'applications'|'members'>('applications');
 const [dialog,setDialog]=useState<'apply'|'cancel'|null>(null);const [reject,setReject]=useState<ApiApplication|null>(null);
 const [reason,setReason]=useState('');const [role,setRole]=useState('');const [portfolios,setPortfolios]=useState<number[]>([]);const [message,setMessage]=useState('');const [ack,setAck]=useState(false);
 const [pending,setPending]=useState(false);const lock=useRef(false);const [error,setError]=useState<ProjectApiError|null>(null);const [note,setNote]=useState('');
 const cls=(...names:string[])=>[management.scope,...names].join(' ');
 async function run(path:string,method:string,body:unknown){if(lock.current)return;lock.current=true;setPending(true);setError(null);setNote('');try{await projectRequest(path,method,body);setDialog(null);setReject(null);setReason('');setNote('처리되었습니다.');options.reload();applications.reload();members.reload();onChanged();}catch(e){setError(e instanceof ProjectApiError?e:new ProjectApiError('처리 결과를 확인하지 못했습니다. 새로고침 후 상태를 확인해 주세요.',502));}finally{lock.current=false;setPending(false);}}
 const current=options.data?.application;
 return <Card className={cls('profile-section')}>
  <h2>{host?'지원자 및 참여 팀원':'프로젝트 지원'}</h2>
  {note&&<p role="status">{note}</p>}{error&&<ApiError error={error}/>}
  {host?<><nav className={cls('manage-tabs tabs')} aria-label="프로젝트 관리"><button className={tab==='applications'?'active':''} onClick={()=>setTab('applications')}>지원자 {applications.data?.length??0}</button><button className={tab==='members'?'active':''} onClick={()=>setTab('members')}>참여 팀원 {members.data?.length??0}</button></nav>
   {tab==='applications'?<div className={cls('applicant-list manage-section')}>
    {applications.loading&&<p role="status">지원자를 불러오고 있습니다.</p>}{applications.error&&<ApiError error={applications.error} retry={applications.reload}/>}
    {applications.data?.length===0&&<p className="empty-state">아직 지원자가 없습니다.</p>}
    {applications.data?.map(a=><Card key={a.id} className={cls('profile-section applicant-card')}><div className={cls('applicant-head')}><div><strong>{a.tag?<Link href={'/users/'+encodeURIComponent(a.tag)}>{a.nickname}</Link>:a.nickname}</strong><small>{projectRoleLabel(a.jobRole)}</small></div><span className={cls('application-pill',a.status==='APPROVED'?'approved':'')}>{labels[a.status]}</span></div><p className="preserve-text">{a.introduction}</p><div className={cls('applicant-footer')}><SubmittedPortfolios application={a}/><span>{a.appliedAt.slice(0,10)}</span></div>{a.rejectionReason&&<p>거절 사유: {a.rejectionReason}</p>}{a.status==='PENDING'&&<div className={cls('applicant-decisions')}><Button variant="secondary" disabled={pending} onClick={()=>{setError(null);setReject(a);}}>거절</Button><Button disabled={pending} onClick={()=>run(base+'/applications/'+a.id,'PATCH',{status:'APPROVED'})}>지원 승인</Button></div>}</Card>)}
   </div>:<div className={cls('manage-team-grid manage-section')}>{members.error&&<ApiError error={members.error} retry={members.reload}/>}{members.loading&&<p role="status">팀원을 불러오고 있습니다.</p>}{members.data?.map(m=><Card key={m.id} className={cls('profile-section team-member-card')}><h3>{m.nickname}{m.role==='HOST'&&<small>주최자</small>}</h3><p>{m.jobRole?projectRoleLabel(m.jobRole):'주최자'}</p><span className="availability-badge">참여 중</span>{m.tag&&<Link className="text-link" href={'/users/'+encodeURIComponent(m.tag)}>프로필 보기</Link>}</Card>)}</div>}
  </>:<>{options.loading&&<p role="status">지원 정보를 불러오고 있습니다.</p>}{options.error&&<ApiError error={options.error} retry={options.reload}/>}{current?<><p>내 지원 상태: <strong>{labels[current.status]}</strong></p><p>지원 직군: {projectRoleLabel(current.jobRole)}</p><SubmittedPortfolios application={current}/>{current.rejectionReason&&<p>거절 사유: {current.rejectionReason}</p>}{['PENDING','APPROVED'].includes(current.status)&&<Button variant="secondary" disabled={pending} onClick={()=>{setAck(false);setError(null);setDialog('cancel');}}>지원 취소</Button>}</>:options.data&&<><p className="auth-hint">{options.data.unavailableReason??'내 포트폴리오와 지원 멘트를 제출해 주세요.'}</p><Button disabled={!options.data.canApply} onClick={()=>{setRole(options.data?.roles.find(r=>r.filled<r.capacity)?.roleCode??'');setPortfolios([]);setError(null);setDialog('apply');}}>프로젝트 지원</Button></>}</>}
  {dialog==='apply'&&options.data&&<Modal className={[applicationStyle.scope,'profile-dialog'].join(' ')} title="프로젝트 지원" onClose={()=>{if(!pending)setDialog(null);}}><p>{title}</p><form className="profile-form" onSubmit={e=>{e.preventDefault();run(base+'/applications','POST',{jobRole:role,portfolioIds:portfolios,introduction:message});}}><label>지원 직군<select className="form-input" required value={role} onChange={e=>setRole(e.target.value)}>{options.data.roles.filter(r=>r.filled<r.capacity).map(r=><option key={r.roleCode} value={r.roleCode}>{projectRoleLabel(r.roleCode)} · {r.capacity-r.filled}명 · {r.minimumYears?r.minimumYears+'년 이상':'경력 무관'}</option>)}</select></label><fieldset><legend>내 공개 포트폴리오 · 최대 3개</legend>{options.data.portfolios.map(p=><label className="checkbox-label" key={p.id}><input type="checkbox" checked={portfolios.includes(p.id)} disabled={!portfolios.includes(p.id)&&portfolios.length===3} onChange={e=>setPortfolios(ids=>e.target.checked?[...ids,p.id]:ids.filter(id=>id!==p.id))}/>{p.title}</label>)}</fieldset><label>지원 멘트<Textarea rows={5} required minLength={10} maxLength={1500} value={message} onChange={e=>setMessage(e.target.value)}/></label><p className="auth-hint">제출 당시 내용은 별도로 보관됩니다. 원본은 이후에도 수정·삭제할 수 있습니다. 지원 취소·거절 이후에는 재지원할 수 없습니다.</p>{error&&<ApiError error={error}/>}<div className="modal-actions"><Button type="button" variant="secondary" disabled={pending} onClick={()=>setDialog(null)}>취소</Button><Button type="submit" disabled={pending||portfolios.length===0}>지원서 제출</Button></div></form></Modal>}
  {dialog==='cancel'&&current&&<Modal title="프로젝트 지원을 취소할까요?" onClose={()=>{if(!pending)setDialog(null);}}><p>취소 후 재지원할 수 없습니다. 확정된 참여를 취소하면 팀원에서도 제외됩니다.</p><p>확정 참여 취소는 모집 마감일 기준 7일 전인 {current.penaltyFreeThrough}까지 패널티가 없습니다.</p>{current.cancellationPenaltyRequired&&<div className={[applicationStyle.scope,'cancellation-warning'].join(' ')}><p>패널티 면제 기간이 지났습니다. 신뢰 점수 계산식 연결 시 하락 대상이며, 현재는 처리 이벤트만 기록합니다.</p><label className="checkbox-label"><input type="checkbox" checked={ack} onChange={e=>setAck(e.target.checked)}/>패널티 안내를 확인했습니다.</label></div>}{error&&<ApiError error={error}/>}<div className="modal-actions"><Button disabled={pending} variant="secondary" onClick={()=>setDialog(null)}>지원 유지</Button><Button disabled={pending||(current.cancellationPenaltyRequired&&!ack)} onClick={()=>run(base+'/applications/'+current.id,'PATCH',{status:'CANCELLED',acknowledgePenalty:ack})}>지원 취소</Button></div></Modal>}
  {reject&&<Modal title="지원 거절 사유" onClose={()=>{if(!pending)setReject(null);}}><form className="profile-form" onSubmit={e=>{e.preventDefault();run(base+'/applications/'+reject.id,'PATCH',{status:'REJECTED',rejectionReason:reason.trim()});}}><label>지원자에게 전달할 한 줄 사유<Textarea required rows={2} maxLength={200} value={reason} onChange={e=>setReason(e.target.value.replace(/[\r\n]/g,' '))}/></label>{error&&<ApiError error={error}/>}<Button type="submit" disabled={pending||!reason.trim()}>거절 확인</Button></form></Modal>}
 </Card>;
}

export function SubmittedPortfolios({application}:{application:ApiApplication}){
 const copies=application.submittedPortfolios||[];
 return <div><span>제출 포트폴리오 {copies.length||application.portfolioIds.length}개</span>{copies.map(copy=><Link className="text-link" style={{display:'block'}} key={copy.id} href={`/projects/${application.projectId}/applications/${application.id}/portfolios/${copy.id}`}>{copy.title} · 제출본</Link>)}</div>;
}
