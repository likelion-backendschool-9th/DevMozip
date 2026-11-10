'use client';
import {useEffect,useRef,useState} from 'react';
import {useRouter} from 'next/navigation';
import Link from 'next/link';
import {Button} from '@/components/common/Button';
import {Input,Textarea} from '@/components/common/Input';
import original from '../../new/page.module.css';
import formStyles from '../CreateProjectForm.module.css';
import {LockKeyhole} from 'lucide-react';
import {projectRequest,ProjectApiError,useProjectResource} from '@/lib/projects/useProjectApi';
import {projectRoleCode,projectRoleLabel,projectChoiceKey} from '@/lib/projects/projectChoices';
import {roleLabels} from '@/lib/projects/projectApiContract';
import type {CreateProjectRequest,ProjectIdentity,ProjectDetailResult,ProjectRoleCode} from '@/types/projectApi';
import {ApiError,TagsInput} from './ApiCommon';
import styles from './ApiCommon.module.css';
const empty:CreateProjectRequest={title:'',summary:'',description:'',goal:'',imageUrl:'',isPublic:true,recruitmentSlots:[{roleCode:'FRONTEND',capacity:1,minimumYears:0}],techStacks:[],interests:[],recruitmentStart:'',recruitmentEnd:'',developmentStart:'',developmentEnd:''};
export function ApiProjectForm({projectId}:{projectId?:string}){
 const router=useRouter();const identity=useProjectResource<ProjectIdentity|null>('/identity');const existing=useProjectResource<ProjectDetailResult>(projectId?'/'+projectId+'/manage':null);
 const options=useProjectResource<{jobRoles:{code:ProjectRoleCode;label:string}[];techStacks:string[];interests:string[]}>(identity.data?'/options':null);
 const [form,setForm]=useState<CreateProjectRequest>(()=>structuredClone(empty));const [error,setError]=useState<ProjectApiError|null>(null);const [pending,setPending]=useState(false);const lock=useRef(false);
 useEffect(()=>{if(existing.data){const p=existing.data.project;setForm({title:p.title,summary:p.summary,description:p.description,goal:p.goal,imageUrl:p.imageUrl,isPublic:p.isPublic,recruitmentSlots:p.recruitmentSlots,techStacks:p.techStacks,interests:p.interests,recruitmentStart:p.recruitmentStart,recruitmentEnd:p.recruitmentEnd,developmentStart:p.developmentStart,developmentEnd:p.developmentEnd});}},[existing.data]);
 const edit=<K extends keyof CreateProjectRequest>(key:K,value:CreateProjectRequest[K])=>setForm(prev=>({...prev,[key]:value}));
 if(identity.loading||existing.loading)return <section className={styles.page}><p role="status">작성 화면을 준비하고 있습니다.</p></section>;
 if(identity.error||existing.error)return <section className={styles.page}><ApiError error={(identity.error??existing.error)!} retry={()=>{identity.reload();existing.reload();}}/></section>;
 if(!identity.data)return <section className="container planned"><div className="planned-card"><h1>로그인 후 작성해 주세요.</h1><Link className="button" href="/login">로그인하기</Link></div></section>;
 if(!projectId&&identity.data.role!=='PREMIUM')return <section className={[original.scope,'container create-project-page'].join(' ')}><div className={[original.scope,'planned-card premium-project-gate'].join(' ')}><LockKeyhole size={32}/><h1>모집 공고 작성은 프리미엄 전용입니다.</h1><p>프리미엄 회원은 새로운 프로젝트를 모집할 수 있습니다.</p><Link className="button" href="/subscription">프리미엄 안내</Link><p className="auth-hint">데모 구독 변경으로 실제 계정 권한이 바뀌지는 않습니다.</p></div></section>;
 if(existing.data?.project.status==='COMPLETED')return <section className={styles.page}><h1>종료된 프로젝트는 수정할 수 없습니다.</h1><Link href={'/projects/'+projectId}>돌아가기</Link></section>;
 return <section className={[original.scope,'container create-project-page'].join(' ')}><Link className="text-link" href={projectId?'/projects/'+projectId+'/manage':'/projects'}>← 돌아가기</Link><div className={[original.scope,'create-project-panel'].join(' ')}><div className="eyebrow">FIND YOUR NEXT TEAM</div><h1>{projectId?'모집 공고 수정':'함께할 동료를 모집해 보세요.'}</h1><p>핵심 목표와 모집 조건을 명확하게 알려주세요.</p>
 <form className={[styles.form,formStyles.scope,'auth-form create-project-form'].join(' ')} onSubmit={async e=>{e.preventDefault();if(lock.current)return;lock.current=true;setPending(true);setError(null);try{
 if(!/^https?:\/\//i.test(form.imageUrl)||!form.title.trim()||!form.description.trim())throw new ProjectApiError('제목·소개·대표 이미지 HTTP(S) URL을 확인해 주세요.',400);
 if(form.recruitmentSlots.some(s=>!s.roleCode.trim())||new Set(form.recruitmentSlots.map(s=>projectChoiceKey(projectRoleCode(s.roleCode)))).size!==form.recruitmentSlots.length)throw new ProjectApiError('모집 직군을 중복 없이 선택해 주세요.',400);
 const body={...form,summary:form.summary?.trim()||null,goal:form.goal?.trim()||null};
 const result=await projectRequest<{id:number}>(projectId?'/'+projectId:'',projectId?'PUT':'POST',body);router.push('/projects/'+result.id);
 }catch(err){setError(err instanceof ProjectApiError?err:new ProjectApiError('등록 결과를 확인하지 못했습니다. 중복 요청 전에 저장 상태를 확인해 주세요.',502));}finally{lock.current=false;setPending(false);}}}>
 <fieldset disabled={pending} className={styles.form} style={{border:0,padding:0,minWidth:0}}>
 <label>대표 이미지 URL (필수)<Input type="url" required maxLength={2048} value={form.imageUrl} onChange={e=>edit('imageUrl',e.target.value)}/></label>{form.imageUrl&&/^https?:\/\//i.test(form.imageUrl)&&<img className={styles.cover} src={form.imageUrl} alt="대표 이미지 미리보기"/>}
 <label>프로젝트명<Input required maxLength={255} value={form.title} onChange={e=>edit('title',e.target.value)}/></label><label>한 줄 소개<Input maxLength={255} value={form.summary??''} onChange={e=>edit('summary',e.target.value)}/></label><label>프로젝트 소개<Textarea required value={form.description} onChange={e=>edit('description',e.target.value)}/></label><label>목표 (선택)<Textarea value={form.goal??''} onChange={e=>edit('goal',e.target.value)}/></label>
 <label>공개 범위<select className="form-input" value={form.isPublic?'public':'private'} onChange={e=>edit('isPublic',e.target.value==='public')}><option value="public">전체 공개</option><option value="private">현재 팀원만 공개</option></select></label>
 <TagsInput label="기술 스택" values={form.techStacks} options={options.data?.techStacks} onChange={v=>edit('techStacks',v)}/><TagsInput label="관심 분야" values={form.interests} options={options.data?.interests} onChange={v=>edit('interests',v)}/>
 <div className="auth-two-columns">{([['recruitmentStart','모집 시작일'],['recruitmentEnd','모집 마감일'],['developmentStart','개발 시작일'],['developmentEnd','개발 종료일']] as const).map(([key,label])=><label key={key}>{label}<Input required type="date" value={form[key]} onChange={e=>edit(key,e.target.value)}/></label>)}</div>
 <h2>모집 직군</h2>{form.recruitmentSlots.map((slot,index)=><div className={[formStyles.scope,'create-role-row'].join(' ')} key={index}><TagsInput compact label="직군" max={1} emptyLabel="직군 선택" normalizeValue={projectRoleLabel} values={slot.roleCode?[projectRoleLabel(slot.roleCode)]:[]} options={(options.data?.jobRoles??Object.entries(roleLabels).map(([code,label])=>({code,label}))).map(r=>r.label)} onChange={values=>edit('recruitmentSlots',form.recruitmentSlots.map((s,i)=>i===index?{...s,roleCode:values[0]?projectRoleCode(values[0]):''}:s))}/><label>인원<Input type="number" min={1} max={10} required value={slot.capacity} onChange={e=>edit('recruitmentSlots',form.recruitmentSlots.map((s,i)=>i===index?{...s,capacity:Number(e.target.value)}:s))}/></label><label>최소 경력 (년)<select className="form-input" value={slot.minimumYears} onChange={e=>edit('recruitmentSlots',form.recruitmentSlots.map((s,i)=>i===index?{...s,minimumYears:Number(e.target.value)}:s))}>{[0,1,2,3,4,5].map(n=><option key={n} value={n}>{n?`${n}년 이상`:'경력 무관'}</option>)}</select></label><Button type="button" variant="secondary" disabled={form.recruitmentSlots.length===1} onClick={()=>edit('recruitmentSlots',form.recruitmentSlots.filter((_,i)=>i!==index))}>삭제</Button></div>)}
 <Button type="button" variant="secondary" disabled={form.recruitmentSlots.length>=10} onClick={()=>edit('recruitmentSlots',[...form.recruitmentSlots,{roleCode:(Object.keys(roleLabels).find(c=>!form.recruitmentSlots.some(s=>s.roleCode===c))??'OTHER') as ProjectRoleCode,capacity:1,minimumYears:0}])}>직군 추가</Button>
 <p className={styles.muted}>총 정원은 주최자 1명과 모집 인원의 합입니다. 수정 시 지원자·팀원이 있는 직군과 정원은 보호됩니다.</p>{error&&<ApiError error={error}/>}<Button type="submit" disabled={pending}>{pending?'저장 중…':projectId?'수정 저장':'모집 공고 등록'}</Button>
 </fieldset></form></div></section>;
}
