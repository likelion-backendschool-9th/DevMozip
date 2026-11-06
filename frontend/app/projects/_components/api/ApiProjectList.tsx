'use client';
import {useEffect,useState} from 'react';
import Link from 'next/link';
import {useQueryState} from '@/lib/navigation';
import {useProjectResource} from '@/lib/projects/useProjectApi';
import {roleLabels,statusLabels} from '@/lib/projects/projectApiContract';
import {useAuth} from '@/lib/state/authStore';
import type {ProjectOptions,ProjectPageResult} from '@/types/projectApi';
import {Button} from '@/components/common/Button';
import {ApiError,TagsInput} from './ApiCommon';
import styles from './ApiCommon.module.css';
const tabs=[['ALL','전체'],['HOSTED','내가 모집한 프로젝트'],['PARTICIPATING','내가 참여한 프로젝트'],['APPLYING','지원 중인 프로젝트'],['COMPLETED','종료된 프로젝트']];
export function ApiProjectList({mine=false,compact=false,initialQuery=''}:{mine?:boolean;compact?:boolean;initialQuery?:string}){
 const {user}=useAuth();const options=useProjectResource<ProjectOptions>(user?'/options':null);
 const [params,setParams]=useQueryState();const term=params.get('query')??params.get('q')??initialQuery;const [q,setQ]=useState(term);useEffect(()=>setQ(term),[term]);
 const query=new URLSearchParams();for(const key of ['query','jobRole','status','interests','techStacks','sort','page','size'])for(const value of params.getAll(key))query.append(key,value);
 query.set('query',term);query.set('size',compact?'3':'12');const sort=(params.get('sort')??'LATEST').toUpperCase();query.set('sort',['LATEST','POPULAR','DEADLINE'].includes(sort)?sort:'LATEST');if(!query.has('jobRole')&&params.get('role')){const role=Object.entries(roleLabels).find(([,label])=>label===params.get('role'));if(role)query.set('jobRole',role[0]);}if(!query.has('interests')&&params.get('field'))query.set('interests',params.get('field')!);if(!query.has('techStacks')&&params.get('stack'))query.set('techStacks',params.get('stack')!);if(mine){const tab=params.get('tab')??'ALL';query.set('tab',({all:'ALL',hosted:'HOSTED',joined:'PARTICIPATING',applied:'APPLYING',ended:'COMPLETED'} as Record<string,string>)[tab]??tab.toUpperCase());}
 const resource=useProjectResource<ProjectPageResult>((mine?'/mine':'')+'?'+query.toString());
 const update=(key:string,value:string|string[])=>{const next=new URLSearchParams(params);next.delete('q');next.delete(key);for(const v of typeof value==='string'?[value]:value)if(v)next.append(key,v);if(key!=='page')next.delete('page');setParams(next);};
 return <section className={compact?styles.preview:styles.page}>
 <div className={styles.heading}><div><h1>{compact?'함께 만들 프로젝트':mine?'내 프로젝트':'프로젝트 탐색'}</h1><p className={styles.muted}>실제 등록된 프로젝트를 확인하세요.</p></div><div className={styles.actions}>{compact?<Link href="/projects">전체 프로젝트 →</Link>:<Link className="button" href="/projects/new">모집 공고 작성</Link>}</div></div>
 {mine&&<div className={styles.tabs}>{tabs.map(([id,label])=><button key={id} aria-pressed={query.get('tab')===id} onClick={()=>update('tab',id)}>{label}</button>)}</div>}
 {!compact&&<><form className={styles.actions} onSubmit={e=>{e.preventDefault();update('query',q.trim());}}><input aria-label="프로젝트 검색" placeholder="프로젝트명 또는 기술 스택" value={q} maxLength={255} onChange={e=>setQ(e.target.value)}/><Button type="submit">검색</Button></form><div className={styles.filters}>
 <label>모집 직군<select value={params.get('jobRole')??''} onChange={e=>update('jobRole',e.target.value)}><option value="">전체</option>{Object.entries(roleLabels).map(([id,label])=><option key={id} value={id}>{label}</option>)}</select></label>
 <label>모집 상태<select value={params.get('status')??''} onChange={e=>update('status',e.target.value)}><option value="">전체</option>{Object.entries(statusLabels).map(([id,label])=><option key={id} value={id}>{label}</option>)}</select></label>
 <TagsInput label="기술 스택 (모두 포함)" values={params.getAll('techStacks')} options={options.data?.techStacks??[...new Set(resource.data?.items.flatMap(p=>p.techStacks)??[])]} onChange={values=>update('techStacks',values)}/><TagsInput label="관심 분야 (하나 이상 포함)" values={params.getAll('interests')} options={options.data?.interests??[...new Set(resource.data?.items.flatMap(p=>p.interests)??[])]} onChange={values=>update('interests',values)}/></div></>}
 <div className={styles.heading}><span role="status">{resource.loading?'조회 중…':resource.data?`총 ${resource.data.totalElements}개`:''}</span><label>정렬 <select aria-label="프로젝트 정렬" value={query.get('sort')!} onChange={e=>update('sort',e.target.value)}><option value="LATEST">최신순</option><option value="POPULAR">인기순</option><option value="DEADLINE">마감 임박순</option></select></label></div>
 {resource.error&&<ApiError error={resource.error} retry={resource.reload}/>}
 {resource.data?.items.length===0&&<div className={styles.panel}>조건에 맞는 프로젝트가 없습니다.</div>}
 <div className={styles.grid}>{resource.data?.items.map(p=><article key={p.id} className={styles.card}>
 <img src={p.imageUrl} alt={p.title+' 대표 이미지'} onError={e=>{e.currentTarget.onerror=null;e.currentTarget.src='/portfolio-cover.svg';}}/>
 <p className={styles.status}>{statusLabels[p.status]}{p.recruitmentClosed&&p.status==='RECRUITING'?' · 모집 마감':''}{!p.isPublic?' · 팀원 공개':''}</p><Link href={'/projects/'+p.id}><h2>{p.title}</h2></Link><p>{p.summary}</p><div className={styles.tags}>{p.techStacks.map(t=><span key={t}>{t}</span>)}</div><p className={styles.muted}>{p.recruitmentSlots.map(s=>`${roleLabels[s.roleCode]??s.roleCode} ${s.capacity}명 · ${s.minimumYears}년 이상`).join(' / ')}</p><p>{p.memberCount}/{p.totalCapacity}명 · 조회 {p.viewCount}</p><p className={styles.muted}>모집 {p.recruitmentStart} ~ {p.recruitmentEnd}<br/>진행 {p.developmentStart} ~ {p.developmentEnd}</p><div className={styles.actions}><Link href={'/projects/'+p.id}>상세 보기</Link>{p.myRole&&<Link href={'/my-projects/'+p.id+'/manage'}>{p.myRole==='HOST'?'관리하기':'참여 정보'}</Link>}</div>
 </article>)}</div>
 {!compact&&resource.data&&resource.data.totalPages>1&&<div className={styles.pager}><Button variant="secondary" disabled={resource.data.page===0} onClick={()=>update('page',String(resource.data!.page-1))}>이전</Button><span>{resource.data.page+1}/{resource.data.totalPages}</span><Button variant="secondary" disabled={resource.data.page+1>=resource.data.totalPages} onClick={()=>update('page',String(resource.data!.page+1))}>다음</Button></div>}
 </section>;
}
