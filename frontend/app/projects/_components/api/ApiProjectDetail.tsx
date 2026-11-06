'use client';
import {useEffect,useRef,useState} from 'react';
import {useParams,useRouter} from 'next/navigation';
import Link from 'next/link';
import {Button} from '@/components/common/Button';
import {Modal} from '@/components/common/Modal';
import {useProjectResource,projectRequest,ProjectApiError} from '@/lib/projects/useProjectApi';
import {statusLabels,roleLabels,validProjectId} from '@/lib/projects/projectApiContract';
import type {ProjectDetailResult,ProjectIdentity} from '@/types/projectApi';
import {ApiError} from './ApiCommon';
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
 return <section className={styles.page}><Link href={manage?'/my-projects':'/projects'}>← 목록으로</Link><div className={styles.heading}><div><p className={styles.status}>{statusLabels[p.status]}{resource.data.recruitmentClosed&&p.status==='RECRUITING'?' · 모집 마감':''}{!p.isPublic?' · 팀원 공개':''}</p><h1>{p.title}</h1><p>{p.summary}</p></div><div className={styles.actions}>{own&&!manage&&<Link className="button" href={'/projects/'+p.id+'/manage'}>프로젝트 관리</Link>}{own&&!completed&&<Link className="button secondary" href={'/projects/'+p.id+'/edit'}>공고 수정</Link>}</div></div>
 <img className={styles.cover} src={p.imageUrl} alt={p.title+' 대표 이미지'} onError={e=>{e.currentTarget.onerror=null;e.currentTarget.src='/portfolio-cover.svg';}}/>
 <div className={styles.panel}><h2>프로젝트 소개</h2><div className={styles.body}>{p.description}</div>{p.goal&&<><h3>프로젝트 목표</h3><p className={styles.body}>{p.goal}</p></>}<div className={styles.tags}>{p.techStacks.map(s=><span key={s}>{s}</span>)}</div><p>{p.interests.join(' · ')}</p></div>
 <div className={styles.grid}><div className={styles.panel}><h2>모집 정보</h2>{p.recruitmentSlots.map(s=><p key={s.roleCode}>{roleLabels[s.roleCode]??s.roleCode} {s.capacity}명 · 최소 {s.minimumYears}년</p>)}<p>현재 {resource.data.memberCount}/{p.totalCapacity}명 (주최자 포함)</p><p className={styles.muted}>지원·승인·지원 취소는 #48 연결 후 제공됩니다.</p></div><div className={styles.panel}><h2>기간과 주최자</h2><p>모집 {p.recruitmentStart} ~ {p.recruitmentEnd}</p><p>진행 {p.developmentStart} ~ {p.developmentEnd}</p><p>주최자 {host?.nickname??`회원 ${p.hostMemberId}`} {host?.tag&&<Link href={'/users/'+encodeURIComponent(host.tag)}>프로필 보기</Link>}</p><p>조회수 {p.viewCount}</p>{membership&&<p>내 역할: {membership.myRole==='HOST'?'주최자':'참여자'}</p>}</div></div>
 {note&&<p role="status">{note}</p>}{error&&<ApiError error={error}/>}
 {manage&&own&&<div className={styles.panel}><h2>프로젝트 관리</h2><div className={styles.actions}>
 {p.status==='RECRUITING'&&<Button disabled={pending} onClick={()=>run({action:'CHANGE_STATUS',status:'RECRUITMENT_COMPLETED'})}>모집 완료</Button>}
 {p.status==='RECRUITMENT_COMPLETED'&&<><Button disabled={pending} onClick={()=>run({action:'CHANGE_STATUS',status:'IN_PROGRESS'})}>프로젝트 시작</Button><Button disabled={pending} variant="secondary" onClick={()=>run({action:'CHANGE_STATUS',status:'RECRUITING'})}>모집 재개</Button></>}
 {p.status==='RECRUITING'&&<Button disabled={pending} variant="secondary" onClick={()=>setRemove(true)}>공고 삭제</Button>}
 </div>{!completed&&<form className={styles.form} onSubmit={e=>{e.preventDefault();run({action:'EXTEND_PERIOD',recruitmentEnd,developmentEnd});}}><h3>기간 연장</h3><div className={styles.row}><label>모집 종료일<input type="date" required min={p.recruitmentEnd} value={recruitmentEnd} onChange={e=>setRecruitmentEnd(e.target.value)}/></label><label>개발 종료일<input type="date" required min={p.developmentEnd} value={developmentEnd} onChange={e=>setDevelopmentEnd(e.target.value)}/></label></div><Button disabled={pending} type="submit">기간 연장 저장</Button></form>}<p className={styles.muted}>대기 지원서가 남아 있으면 시작할 수 없습니다. 지원자 관리·채팅·리뷰 발송은 아직 연결되지 않았습니다.</p></div>}
 {membership&&<div className={styles.panel}><h2>프로젝트 종료 동의</h2><p>{membership.endRequestCount}명 동의 · 과반수 기준 {membership.requiredEndRequestCount}명</p><p className={styles.muted}>진행 중 현재 팀원의 과반수가 동의하면 종료됩니다. 확정 전에는 본인 요청을 취소할 수 있습니다.</p>{p.status==='IN_PROGRESS'&&<Button disabled={pending} onClick={()=>run({action:membership.myEndRequested?'CANCEL_END_REQUEST':'REQUEST_END'})}>{membership.myEndRequested?'종료 요청 취소':'프로젝트 종료 요청'}</Button>}{completed&&<p>프로젝트가 종료되었습니다. 종료 요청을 취소할 수 없습니다.</p>}</div>}
 {remove&&<Modal title="모집 공고를 삭제할까요?" onClose={()=>{if(!pending)setRemove(false);}}><p>논리 삭제하며 지원·팀원 이력은 유지됩니다.</p><Button disabled={pending} onClick={()=>run(undefined,'DELETE')}>{pending?'처리 중…':'삭제 확인'}</Button></Modal>}
 </section>;
}
