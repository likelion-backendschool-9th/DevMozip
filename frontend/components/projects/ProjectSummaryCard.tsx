'use client';
import {useState} from 'react';
import Link from 'next/link';
import {ArrowUpRight,CalendarDays,Users} from 'lucide-react';
import {Card} from '@/components/common/Card';
import type {ProjectListRecord} from '@/types/projectApi';
import {projectRoleLabel} from '@/lib/projects/projectChoices';
import {roleLabels,statusLabels} from '@/lib/projects/projectApiContract';
import recruitment from './RecruitmentCard.module.css';
import home from '@/app/_components/ProjectPreview.module.css';
import styles from './ProjectSummaryCard.module.css';

/** API 데이터를 기존 모집 카드의 시각 구조로 표시한다. 참여 인원을 추정하지 않는다. */
export function ProjectSummaryCard({project:p,index=0,compact=false}:{project:ProjectListRecord;index?:number;compact?:boolean}){
 const [failedImage,setFailedImage]=useState('');
 const original=compact?home:recruitment;
 const cls=(...names:string[])=>[original.scope,...names].join(' ');
 const color=['peach','lavender','mint','blue'][index%4];
 const showImage=!!p.imageUrl&&!p.imageUrl.includes('/portfolio-cover.svg')&&failedImage!==p.imageUrl;
 const state=p.recruitmentClosed&&p.status==='RECRUITING'?'모집 마감':statusLabels[p.status];
 return <Card className={cls(compact?'project-preview':'recruitment-card')}>
  <div className={cls('project-cover',color)}>
   {showImage&&<img className={styles.image} src={p.imageUrl} alt={p.title+' 대표 이미지'} onError={()=>setFailedImage(p.imageUrl)}/>}
   <span className={showImage?styles.imageLabel:''}>{p.interests.join(' · ')}</span>
   {!showImage&&<div className={cls('cover-glyph')} aria-hidden="true">{p.title.trim().charAt(0)}<span>↗</span></div>}
   <span className={cls('recruit-badge')}>{state}</span>
  </div>
  <div className={cls('project-content')}>
   {compact&&<div className={cls('project-meta')}>{p.recruitmentSlots.map(s=>projectRoleLabel(s.roleCode)).join(' · ')} · {state}</div>}
   <div className={cls('recruitment-card-title')}><Link href={'/projects/'+p.id}><h3>{p.title}</h3></Link></div>
   <p>{p.summary}</p>
   <div className="tags">{p.techStacks.map(s=><span key={s}>{s}</span>)}</div>
   {compact?<div className={cls('project-facts')}><span><Users size={13}/>{p.memberCount}/{p.totalCapacity}명</span><span><CalendarDays size={13}/>{p.developmentStart} — {p.developmentEnd}</span></div>:<>
    <div className={cls('recruitment-slots')}>{p.recruitmentSlots.map(s=><div key={s.roleCode}><strong>{projectRoleLabel(s.roleCode)}</strong><span>{s.capacity}명 모집</span><small>{s.minimumYears?`${s.minimumYears}년 이상`:'경력 무관'}</small></div>)}</div>
    <div className={cls('recruitment-card-dates')}><CalendarDays size={12}/>{p.developmentStart} — {p.developmentEnd}</div>
   </>}
   <div className={cls('project-bottom')}><span>모집 마감 {p.recruitmentEnd}</span><Link href={'/projects/'+p.id}>상세 보기<ArrowUpRight size={14}/></Link></div>
   {!compact&&<div className={styles.meta}><span>현재 {p.memberCount}/{p.totalCapacity}명 · 조회 {p.viewCount}</span>{p.myRole&&<Link href={'/my-projects/'+p.id+'/manage'}>{p.myRole==='HOST'?'관리하기':'참여 정보'}</Link>}</div>}
  </div>
 </Card>;
}
