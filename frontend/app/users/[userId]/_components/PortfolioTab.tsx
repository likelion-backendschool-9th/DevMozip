'use client';
import {portfolioFields} from '@/lib/profiles/portfolioBlocks';
import styles from './PortfolioTab.module.css';
import { useState } from 'react';
import Link from 'next/link';
import { LockKeyhole, Plus } from 'lucide-react';
import { useAuth } from '@/lib/state/AuthProvider';
import { useProfiles } from '@/lib/state/ProfileProvider';
import { canOpenPortfolioDetail } from '@/lib/profiles/profileService';
import { Card } from '@/components/common/Card';
import { Modal } from '@/components/common/Modal';
import { Button } from '@/components/common/Button';
import type { Portfolio } from '@/types/profile';
export function PortfolioTab({profileId}:{profileId:string}){
 const {user}=useAuth();const {state,act}=useProfiles();const [remove,setRemove]=useState<Portfolio|null>(null);const [locked,setLocked]=useState(false);const owner=user?.id===profileId;const allowed=canOpenPortfolioDetail(user?.role,profileId,user?.id);const items=state.portfolios.filter(p=>p.ownerId===profileId);
 return <div><div className="tab-section-title"><div><h2>경험으로 쌓아온 포트폴리오 <span>{items.length}</span></h2><p>이미지와 요약으로 살펴보고, 상세 페이지에서 더 알아보세요.</p></div>{owner&&<Link className="button" href="/portfolios/new"><Plus size={15}/>포트폴리오 등록</Link>}</div>{!items.length&&<div className="empty-state">아직 등록된 포트폴리오가 없습니다.</div>}<div className={styles.grid}>{items.map(item=><Card key={item.id} className={styles.summaryCard}>{allowed?<Link className={styles.openCard} href={`/portfolios/${item.id}`} aria-label={`${item.title} 상세 보기`}><PortfolioSummary item={item}/></Link>:<button type="button" className={styles.openCard} onClick={()=>setLocked(true)} aria-label={`${item.title} 상세 보기 · 프리미엄`}><PortfolioSummary item={item}/></button>}{!allowed&&<span className={styles.lockHint}><LockKeyhole size={14}/>상세 열람은 프리미엄</span>}{owner&&<div className={styles.ownerActions}><Link href={`/portfolios/${item.id}/edit`}>수정</Link><button type="button" onClick={()=>setRemove(item)}>삭제</button></div>}</Card>)}</div>{remove&&<Modal title="포트폴리오를 삭제할까요?" onClose={()=>setRemove(null)}><p>{remove.title}</p><div className="modal-actions"><Button variant="secondary" onClick={()=>setRemove(null)}>취소</Button><Button onClick={()=>{if(act({type:'portfolio-delete',id:remove.id}))setRemove(null);}}>삭제하기</Button></div></Modal>}{locked&&<Modal title="프리미엄 포트폴리오 상세 열람" onClose={()=>setLocked(false)}><p>포트폴리오의 상세 내용은 프리미엄 회원에게 제공됩니다.</p><div className="modal-actions"><Button variant="secondary" onClick={()=>setLocked(false)}>닫기</Button><Link className="button" href="/premium">프리미엄 안내</Link></div></Modal>}</div>;
}
function PortfolioSummary({item}:{item:Portfolio}){return <><img className={styles.cover} src={item.coverImage||'/portfolio-cover.svg'} alt={`${item.title} 대표 이미지`}/><div className={styles.summary}><span className={styles.field}>{portfolioFields(item).join(' · ')||'분야 미등록'}</span><h3>{item.title}</h3><p>{item.summary}</p><div className="profile-tags">{item.stacks.map(stack=><span key={stack}>{stack}</span>)}</div></div></>;}
