'use client';
import {useEffect,useRef,useState,type ReactNode} from 'react';
import {Button} from '@/components/common/Button';
import {Modal} from '@/components/common/Modal';
import {projectRequest} from '@/lib/projects/useProjectApi';
import {onMessengerSignal} from '@/lib/state/messengerSocketStore';
import {kickReasons} from '@/mocks/chatKick';
import type {ChatHistoryItem} from '@/app/messages/_components/ChatWindow';
import type {MemberRemovalVote} from '@/types/memberRemoval';
import {formatKickExpiry} from '@/lib/projects/kickVoteView';
import styles from './KickVotePanel.module.css';
const labels={ACTIVE:'투표 진행 중',PASSED:'강퇴 완료',REJECTED:'부결',EXPIRED:'기간 만료',CANCELLED:'투표 취소'};
const policy='임시 정책: 주최자는 대상에서 제외 · 주최자도 1표 · 투표 기간 72시간 · 미투표자도 정족수에 포함 · 팀원 변경 시 취소';
export function ApiKickVotePanel({projectId,version=0,children}:{projectId:number;version?:number;children?:(items:ChatHistoryItem[],error:string)=>ReactNode}){
 const [votes,setVotes]=useState<MemberRemovalVote[]>([]);const [error,setError]=useState('');const [pending,setPending]=useState(false);const lock=useRef(false);const reload=useRef<()=>Promise<void>>(async()=>{});
 useEffect(()=>{let active=true;const abort=new AbortController();let loading=false;
  const load=async()=>{if(loading)return;loading=true;try{const rows=await projectRequest<MemberRemovalVote[]>('/'+projectId+'/kick-votes','GET',undefined,abort.signal);if(active){setVotes(rows);setError('');}}catch(e){if(active)setError(e instanceof Error?e.message:'투표를 불러오지 못했습니다.');}finally{loading=false;}};
  reload.current=load;void load();const timer=setInterval(()=>{if(!document.hidden)void load();},5000);
  const unsubscribe=onMessengerSignal(s=>{if(s.type==='project.members.changed'&&s.roomId===projectId)void load();});
  return()=>{active=false;abort.abort();clearInterval(timer);unsubscribe();};
 },[projectId,version]);
 async function vote(id:number,agree:boolean){if(lock.current)return;lock.current=true;setPending(true);try{await projectRequest('/'+projectId+'/kick-votes/'+id+'/ballots','POST',{agree});await reload.current();}catch(e){setError(e instanceof Error?e.message:'투표 상태를 확인해 주세요.');}finally{lock.current=false;setPending(false);}}
 if(!votes.length)return children?children([],error):error?<p role="alert">{error}</p>:null;
 const renderPanel=(selected:MemberRemovalVote[]) => <section className={[styles.scope,'kick-vote-panel'].join(' ')} aria-label="강퇴 투표 현황"><header><h2>강퇴 투표 현황</h2></header><details className={styles.policy}><summary>투표 기준 안내</summary><p>{policy}</p></details>{error&&<p role="alert">{error}</p>}{selected.map(v=><article key={v.id} className={[styles.scope,'kick-vote-card'].join(' ')}><header><div><h3>{v.targetNickname} 님</h3><p>{v.reason}</p></div><strong className={[styles.scope,'vote-state',v.status.toLowerCase()].join(' ')}>{labels[v.status]}</strong></header><div className={[styles.scope,'kick-vote-counts'].join(' ')}><span>찬성 <b>{v.yes}</b>명</span><span>반대 <b>{v.no}</b>명</span><span>투표 가능 <b>{v.eligibleVoterCount}</b>명</span><span>가결 기준 <b>{v.requiredCount}</b>명</span></div><progress className={styles.progress} value={v.yes} max={v.eligibleVoterCount} aria-label="찬성 투표 현황"/><div className={styles.voteMeta}><span>{v.status==='PASSED'?'과반수에 도달했어요':v.status!=='ACTIVE'?'투표가 종료됐어요':`가결까지 찬성 ${v.requiredCount-v.yes}표가 더 필요해요`}</span><time className={styles.expiry} dateTime={v.expiresAt}>만료 {formatKickExpiry(v.expiresAt)}</time></div>{v.status==='ACTIVE'&&<div className={[styles.scope,'kick-ballots'].join(' ')}><p>{v.myVote===null?(v.canVote?'아직 투표하지 않았습니다.':'강퇴 대상자는 투표할 수 없습니다.'):'이미 '+(v.myVote?'찬성':'반대')+' 투표했습니다.'}</p><Button disabled={!v.canVote||pending} onClick={()=>void vote(v.id,true)}>찬성</Button><Button variant="secondary" disabled={!v.canVote||pending} onClick={()=>void vote(v.id,false)}>반대</Button></div>}</article>)}</section>;
 return children?children(votes.map(v=>({id:String(v.id),createdAt:v.createdAt,content:renderPanel([v])})),error):renderPanel(votes);
}
export function ApiKickVoteDialog({projectId,targetMemberId,name,onClose,onCreated}:{projectId:number;targetMemberId:number;name:string;onClose:()=>void;onCreated:()=>void}){
 const [reason,setReason]=useState(kickReasons[0]);const [error,setError]=useState('');const [pending,setPending]=useState(false);const lock=useRef(false);
 return <Modal title="강퇴 투표 요청" onClose={onClose}><form className={[styles.scope,'profile-form'].join(' ')} onSubmit={async e=>{e.preventDefault();if(lock.current)return;lock.current=true;setPending(true);try{await projectRequest('/'+projectId+'/members','POST',{projectMemberId:targetMemberId,reason});onCreated();onClose();}catch(e){setError(e instanceof Error?e.message:'투표 상태를 확인해 주세요.');}finally{lock.current=false;setPending(false);}}}><strong>강퇴 대상 · {name}</strong><label>강퇴 사유<select value={reason} onChange={e=>setReason(e.target.value)} className="form-input">{kickReasons.map(r=><option key={r}>{r}</option>)}</select></label><p>전체 투표 가능 인원의 절반을 초과하는 찬성이 필요합니다. 가결되면 프로젝트 참여와 채팅 접근이 종료되고 강퇴 이력은 남습니다. 요청자도 별도로 투표해 주세요.</p><p className="auth-hint">{policy}</p>{error&&<p role="alert">{error}</p>}<Button disabled={pending} type="submit">강퇴 투표 시작</Button></form></Modal>;
}
