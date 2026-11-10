'use client';
import {useEffect,useState} from 'react';
import Link from 'next/link';
import {useQueryState} from '@/lib/navigation';
import {useProjectResource} from '@/lib/projects/useProjectApi';
import {projectRoleCode,projectRoleLabel} from '@/lib/projects/projectChoices';
import {signupOptionsAction} from '@/lib/auth/actions';
import type {SignupOptions} from '@/types/auth';
import {roleLabels,statusLabels} from '@/lib/projects/projectApiContract';
import {useAuth} from '@/lib/state/authStore';
import type {ProjectOptions,ProjectPageResult} from '@/types/projectApi';
import {Button} from '@/components/common/Button';
import {Input} from '@/components/common/Input';
import {ProjectSummaryCard} from '@/components/projects/ProjectSummaryCard';
import {Search,Plus,RotateCcw,ArrowUpRight} from 'lucide-react';
import recruitment from '../../page.module.css';
import workspace from '../../../my-projects/page.module.css';
import home from '../../../_components/ProjectPreview.module.css';
import {ApiError,TagsInput} from './ApiCommon';
import styles from './ApiCommon.module.css';
const tabs=[['ALL','전체'],['HOSTED','내가 모집한 프로젝트'],['PARTICIPATING','내가 참여한 프로젝트'],['APPLYING','지원 중인 프로젝트'],['COMPLETED','종료된 프로젝트']];
export function ApiProjectList({mine=false,compact=false,initialQuery=''}:{mine?:boolean;compact?:boolean;initialQuery?:string}){
 const {user}=useAuth();const [publicOptions,setPublicOptions]=useState<SignupOptions|null>(null);
 useEffect(()=>{let active=true;if(!user)void signupOptionsAction().then(value=>{if(active&&!('error' in value))setPublicOptions(value);});return()=>{active=false;};},[user?.email]);
 const options=useProjectResource<ProjectOptions>(user?'/options':null);
 const [params,setParams]=useQueryState();const term=params.get('query')??params.get('q')??initialQuery;const [q,setQ]=useState(term);useEffect(()=>setQ(term),[term]);
 const query=new URLSearchParams();for(const key of ['query','jobRole','status','interests','techStacks','sort','page','size'])for(const value of params.getAll(key))query.append(key,value);
 query.set('query',term);query.set('size',compact?'3':'12');const sort=(params.get('sort')??'LATEST').toUpperCase();query.set('sort',['LATEST','POPULAR','DEADLINE'].includes(sort)?sort:'LATEST');if(!query.has('jobRole')&&params.get('role'))query.set('jobRole',projectRoleCode(params.get('role')!));if(!query.has('interests')&&params.get('field'))query.set('interests',params.get('field')!);if(!query.has('techStacks')&&params.get('stack'))query.set('techStacks',params.get('stack')!);if(mine){const tab=params.get('tab')??'ALL';query.set('tab',({all:'ALL',hosted:'HOSTED',joined:'PARTICIPATING',applied:'APPLYING',ended:'COMPLETED'} as Record<string,string>)[tab]??tab.toUpperCase());}
 const resource=useProjectResource<ProjectPageResult>((mine?'/mine':'')+'?'+query.toString());
 const update=(key:string,value:string|string[])=>{const next=new URLSearchParams(params);next.delete('q');next.delete(key);if(key==='jobRole')next.delete('role');if(key==='interests')next.delete('field');if(key==='techStacks')next.delete('stack');for(const v of typeof value==='string'?[value]:value)if(v)next.append(key,v);if(key!=='page')next.delete('page');setParams(next);};
 const original=compact?home:mine?workspace:recruitment;
 const cls=(...names:string[])=>[original.scope,...names].join(' ');
 const sorting=<div className={cls('tabs compact')}>{[['LATEST','최신순'],['POPULAR','인기순'],['DEADLINE','마감 임박순']].map(([value,label])=><button key={value} aria-pressed={query.get('sort')===value} className={query.get('sort')===value?'active':''} onClick={()=>update('sort',value)}>{label}</button>)}</div>;
 return <section className={cls(compact?'home-section':mine?'container my-projects-page':'container recruitment-page')}>
 <div className={cls('section-heading')}><div><div className={cls('eyebrow')}>{compact?'FIND YOUR NEXT PROJECT':mine?'MY PROJECT WORKSPACE':'BUILD SOMETHING GREAT TOGETHER'}</div>{compact?<h2>함께 만들면 더 좋은 프로젝트</h2>:<h1>{mine?'함께 만드는 나의 프로젝트':'당신의 다음 프로젝트를 만나보세요.'}</h1>}<p>{compact?'당신의 경험이 필요한 팀을 만나보세요.':mine?'모집부터 참여, 종료와 동료 리뷰까지 한곳에서 관리하세요.':'함께 고민하고 성장할 팀이 기다리고 있어요.'}</p></div>{compact?<Link className={cls('text-link')} href="/projects">전체 프로젝트<ArrowUpRight size={16}/></Link>:<Link className="button" href="/projects/new"><Plus size={16}/>{mine?'새 프로젝트 모집':'모집 공고 작성'}</Link>}</div>
 {mine&&<div className={cls('tabs my-project-tabs')}>{tabs.map(([id,label])=><button key={id} aria-pressed={query.get('tab')===id} className={query.get('tab')===id?'active':''} onClick={()=>update('tab',id)}>{label}</button>)}</div>}
 {!compact&&<><form className={cls('project-list-search',styles.search)} onSubmit={e=>{e.preventDefault();update('query',q.trim());}}><Search size={18}/><Input aria-label="프로젝트 검색" placeholder="프로젝트명 또는 기술 스택을 검색해 보세요" value={q} maxLength={255} onChange={e=>setQ(e.target.value)}/><Button type="submit">검색</Button></form><div className={cls('project-list-filters',styles.filterBar)}>
 <TagsInput compact label="모집 직군" max={1} values={query.get('jobRole')?[projectRoleLabel(query.get('jobRole')!)]:[]} normalizeValue={projectRoleLabel} options={[...(options.data?.jobRoles.map(r=>r.label)??Object.values(roleLabels)),...(resource.data?.items.flatMap(p=>p.recruitmentSlots.map(s=>projectRoleLabel(s.roleCode)))??[])]} onChange={values=>update('jobRole',values[0]?projectRoleCode(values[0]):'')}/>
 <TagsInput compact label="분야" values={query.getAll('interests')} options={options.data?.interests??publicOptions?.interests??[...new Set(resource.data?.items.flatMap(p=>p.interests)??[])]} onChange={values=>update('interests',values)}/>
 <label>모집 상태<select className="form-input" value={params.get('status')??''} onChange={e=>update('status',e.target.value)}><option value="">전체</option>{Object.entries(statusLabels).map(([id,label])=><option key={id} value={id}>{label}</option>)}</select></label>
 <TagsInput compact label="기술 스택" values={query.getAll('techStacks')} options={options.data?.techStacks??publicOptions?.skills??[...new Set(resource.data?.items.flatMap(p=>p.techStacks)??[])]} onChange={values=>update('techStacks',values)}/>
 <Button variant="secondary" onClick={()=>{setQ('');setParams({});}}><RotateCcw size={12}/>초기화</Button></div></>}
 {compact?sorting:<div className={cls('recruitment-list-heading',styles.listHeading)}><span role="status">{resource.loading?'조회 중…':resource.data?<>총 <b>{resource.data.totalElements}</b>개의 프로젝트</>:''}</span>{sorting}</div>}
 {resource.error&&<ApiError error={resource.error} retry={resource.reload}/>}
 {resource.data?.items.length===0&&<div className="empty-state"><strong>조건에 맞는 프로젝트가 없습니다.</strong><p>검색어 또는 필터를 바꿔보세요.</p></div>}
 <div className={cls(compact?'project-grid':mine?'my-project-grid':'recruitment-grid')}>{resource.data?.items.map((p,index)=><ProjectSummaryCard key={p.id} project={p} index={index} compact={compact} returnTo={mine?'/my-projects'+(params.size?'?'+params.toString():''):undefined}/>)}</div>
 {!compact&&resource.data&&resource.data.totalPages>1&&<div className={styles.pager}><Button variant="secondary" disabled={resource.data.page===0} onClick={()=>update('page',String(resource.data!.page-1))}>이전</Button><span>{resource.data.page+1}/{resource.data.totalPages}</span><Button variant="secondary" disabled={resource.data.page+1>=resource.data.totalPages} onClick={()=>update('page',String(resource.data!.page+1))}>다음</Button></div>}
 </section>;
}
