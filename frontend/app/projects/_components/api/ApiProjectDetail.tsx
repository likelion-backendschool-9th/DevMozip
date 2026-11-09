'use client';
import {useEffect,useRef,useState} from 'react';
import {useParams,useRouter} from 'next/navigation';
import Link from 'next/link';
import {Button} from '@/components/common/Button';
import {Card} from '@/components/common/Card';
import {Input} from '@/components/common/Input';
import {ArrowLeft,CalendarDays,Users} from 'lucide-react';
import original from '../../[projectId]/page.module.css';
import management from '../../[projectId]/manage/page.module.css';
import {Modal} from '@/components/common/Modal';
import {useProjectResource,projectRequest,ProjectApiError} from '@/lib/projects/useProjectApi';
import {projectRoleLabel} from '@/lib/projects/projectChoices';
import {statusLabels,roleLabels,validProjectId} from '@/lib/projects/projectApiContract';
import type {ProjectDetailResult,ProjectIdentity} from '@/types/projectApi';
import {ApiError} from './ApiCommon';
import {ProjectLikeButton} from '@/components/projects/ProjectLikeButton';
import {ProjectShareButton} from '@/components/projects/ProjectShareButton';
import {ProjectReportButton} from '@/components/projects/ProjectReportButton';
import {ProjectIconActions} from '@/components/projects/ProjectIconActions';
import {ProjectBookmarkButton} from '@/components/projects/ProjectBookmarkButton';
import styles from './ApiCommon.module.css';
export function ApiProjectDetail({manage=false}:{manage?:boolean}){
 const {projectId=''}=useParams<{projectId:string}>();const router=useRouter();
 const resource=useProjectResource<ProjectDetailResult>(validProjectId(projectId)?'/'+projectId+(manage?'/manage':''):null);
 const identity=useProjectResource<ProjectIdentity|null>('/identity');
 const [error,setError]=useState<ProjectApiError|null>(null);const [pending,setPending]=useState(false);const lock=useRef(false);const [remove,setRemove]=useState(false);const [note,setNote]=useState('');
 const [recruitmentEnd,setRecruitmentEnd]=useState('');const [developmentEnd,setDevelopmentEnd]=useState('');
 useEffect(()=>{if(resource.data){setRecruitmentEnd(resource.data.project.recruitmentEnd);setDevelopmentEnd(resource.data.project.developmentEnd);}},[resource.data]);
 const run=async(action:unknown,method='PATCH')=>{if(lock.current)return;lock.current=true;setPending(true);setError(null);setNote('');try{await projectRequest('/'+projectId+(method==='PATCH'?'/update':''),method,action);if(method==='DELETE'){router.push('/projects');return;}setNote('변경 사항을 저장했습니다.');resource.reload();}catch(e){setError(e instanceof ProjectApiError?e:new ProjectApiError('응답을 확인하지 못했습니다. 중복 요청 전에 저장 상태를 확인해 주세요.',502));}finally{lock.current=false;setPending(false);}};
 if(!validProjectId(projectId))return <section className={styles.page}><h1>프로젝트 ID를 확인해 주세요.</h1><Link href="/projects">프로젝트 목록</Link></section>;
 if(resource.loading)return <section className={styles.page}><p role="status">프로젝트를 불러오고 있습니다.</p></section>;
 if(resource.error)return <section className={styles.page}><ApiError error={resource.error} retry={resource.reload}/><Link href="/projects">목록으로</Link></section>;
 if(!resource.data)return null;
 const {project:p,membership,host}=resource.data;const own=membership?.myRole==='HOST'&&identity.data?.id===p.hostMemberId;const completed=p.status==='COMPLETED';
 const cls=(...names:string[])=>[original.scope,...names].join(' ');
 const managementCls=(...names:string[])=>[management.scope,...names].join(' ');
 const state=resource.data.recruitmentClosed&&p.status==='RECRUITING'?'모집 마감':statusLabels[p.status];
 return <section className={cls('container project-detail-page')}>
  <Link className="text-link" href={manage?'/my-projects':'/projects'}><ArrowLeft size={14}/>{manage?'내 프로젝트':'프로젝트 모집'}</Link>
  <header className={[cls('project-detail-heading'),original.heading].join(' ')}>
   <div className={cls('project-detail-labels')}><span className="outline-badge">{p.interests.join(' · ')||'프로젝트'}</span><span className="availability-badge">{state}</span>{!p.isPublic&&<span className="outline-badge">팀원 공개</span>}</div>
   <h1>{p.title}</h1><p>{p.summary}</p>
   <div className={original.headerActions}><ProjectIconActions><ProjectLikeButton projectId={p.id}/><ProjectShareButton projectId={p.id}/><ProjectBookmarkButton projectId={p.id}/>{!own&&<ProjectReportButton projectId={p.id}/>}</ProjectIconActions></div><div className={[styles.actions,original.headerMeta].join(' ')}>{own&&!manage&&<Link className="button" href={'/projects/'+p.id+'/manage'}>주최자 관리</Link>}{own&&!completed&&<Link className="button secondary" href={'/projects/'+p.id+'/edit'}>공고 수정</Link>}<span className={styles.muted}>조회 {p.viewCount}</span></div>
  </header>
  {manage&&own&&<div className={managementCls('manage-metrics')}>
   <Card><span>현재 참여 인원</span><strong>{resource.data.memberCount}<small>/ {p.totalCapacity}명</small></strong></Card>
   <Card><span>프로젝트 상태</span><strong className={managementCls('date-metric')}>{statusLabels[p.status]}</strong></Card>
   <Card><span>모집 마감</span><strong className={managementCls('date-metric')}>{p.recruitmentEnd}</strong></Card>
   <Card><span>진행 종료 예정</span><strong className={managementCls('date-metric')}>{p.developmentEnd}</strong></Card>
  </div>}
  {note&&<p className={styles.notice} role="status">{note}</p>}{error&&<ApiError error={error}/>}
  <div className={cls('project-detail-layout',manage?styles.manageLayout:'')}>
   <div className={cls('project-detail-main')}>
    <Card className={cls('profile-section')}>{p.imageUrl&&!p.imageUrl.includes('/portfolio-cover.svg')&&<img className={styles.detailImage} src={p.imageUrl} alt={p.title+' 대표 이미지'} onError={e=>{e.currentTarget.style.display='none';}}/>}<h2>프로젝트 소개</h2><p className={cls('preserve-text')}>{p.description}</p>{p.goal&&<><h2 className="sub-heading">함께 이루고 싶은 목표</h2><p className={cls('preserve-text')}>{p.goal}</p></>}</Card>
    <Card className={cls('profile-section')}><h2>기술 스택</h2><div className="profile-tags">{p.techStacks.map(s=><span key={s}>{s}</span>)}</div><h2 className="sub-heading">모집 직군과 인원</h2><div className={cls('role-requirements')}>
     <div className={cls('role-row role-table-heading')}><span>모집 직군</span><span>모집 인원</span><span>최소 경력</span></div>
     {p.recruitmentSlots.map(s=><div className={cls('role-row')} key={s.roleCode}><strong>{projectRoleLabel(s.roleCode)}</strong><span>{s.capacity}명</span><span>{s.minimumYears?`${s.minimumYears}년 이상`:'경력 무관'}</span></div>)}
    </div><p className="auth-hint">정원은 주최자를 포함합니다. 직군별 참여 인원은 현재 제공되는 정보에 포함되지 않습니다.</p></Card>
    <Card className={cls('profile-section')}><h2>진행 일정</h2><dl className={cls('project-schedule')}><div><dt>개발 기간</dt><dd>{p.developmentStart} — {p.developmentEnd}</dd></div><div><dt>모집 기간</dt><dd>{p.recruitmentStart} — {p.recruitmentEnd}</dd></div></dl></Card>
    {manage&&own&&<Card className={cls('profile-section')}><h2>프로젝트 관리</h2><div className={styles.actions}>
     {p.status==='RECRUITING'&&<Button disabled={pending} onClick={()=>run({action:'CHANGE_STATUS',status:'RECRUITMENT_COMPLETED'})}>모집 완료</Button>}
     {p.status==='RECRUITMENT_COMPLETED'&&<><Button disabled={pending} onClick={()=>run({action:'CHANGE_STATUS',status:'IN_PROGRESS'})}>프로젝트 시작</Button><Button disabled={pending} variant="secondary" onClick={()=>run({action:'CHANGE_STATUS',status:'RECRUITING'})}>모집 재개</Button></>}
     {p.status==='RECRUITING'&&<Button disabled={pending} variant="secondary" onClick={()=>setRemove(true)}>공고 삭제</Button>}
    </div>{!completed&&<form className={styles.form} onSubmit={e=>{e.preventDefault();run({action:'EXTEND_PERIOD',recruitmentEnd,developmentEnd});}}><h3>기간 연장</h3><div className="auth-two-columns"><label>모집 종료일<Input type="date" required min={p.recruitmentEnd} value={recruitmentEnd} onChange={e=>setRecruitmentEnd(e.target.value)}/></label><label>개발 종료일<Input type="date" required min={p.developmentEnd} value={developmentEnd} onChange={e=>setDevelopmentEnd(e.target.value)}/></label></div><Button disabled={pending} type="submit">기간 연장 저장</Button></form>}<p className="auth-hint">대기 지원서가 남아 있으면 시작할 수 없습니다. 지원자 관리·채팅·리뷰 발송은 아직 연결되지 않았습니다.</p></Card>}
    {membership&&<Card className={cls('profile-section')}><h2>프로젝트 종료 동의</h2><p>{membership.endRequestCount}명 동의 · 과반수 기준 {membership.requiredEndRequestCount}명</p><p className="auth-hint">진행 중 현재 팀원의 과반수가 동의하면 종료됩니다. 확정 전에는 본인 요청을 취소할 수 있습니다.</p>{p.status==='IN_PROGRESS'&&<Button disabled={pending} onClick={()=>run({action:membership.myEndRequested?'CANCEL_END_REQUEST':'REQUEST_END'})}>{membership.myEndRequested?'종료 요청 취소':'프로젝트 종료 요청'}</Button>}{completed&&<p>프로젝트가 종료되었습니다. 종료 요청을 취소할 수 없습니다.</p>}</Card>}
   </div>
   <aside className={cls('project-detail-sidebar')}>
    <Card className={cls('profile-section apply-summary')}><span className="eyebrow">YOUR NEXT CHALLENGE</span><h2>함께 만들어볼까요?</h2><p><Users size={14}/>현재 {resource.data.memberCount} / {p.totalCapacity}명</p><p><CalendarDays size={14}/>모집 마감 {p.recruitmentEnd}</p><p className="auth-hint">지원·승인·지원 취소 기능은 아직 연결되지 않았습니다.</p>{membership&&<p>내 역할: {membership.myRole==='HOST'?'주최자':'참여자'}</p>}</Card>
    <Card className={cls('profile-section project-host')}><h3>프로젝트 주최자</h3><div className={styles.host}><span className={styles.avatar} aria-hidden="true">{(host?.nickname??'회원').charAt(0)}</span><strong>{host?.nickname??`회원 ${p.hostMemberId}`}</strong></div>{host?.tag&&<Link className="button secondary" href={'/users/'+encodeURIComponent(host.tag)}>프로필 보기</Link>}</Card>
   </aside>
  </div>
  {remove&&<Modal title="모집 공고를 삭제할까요?" onClose={()=>{if(!pending)setRemove(false);}}><p>논리 삭제하며 지원·팀원 이력은 유지됩니다.</p><Button disabled={pending} onClick={()=>run(undefined,'DELETE')}>{pending?'처리 중…':'삭제 확인'}</Button></Modal>}
 </section>;
}
