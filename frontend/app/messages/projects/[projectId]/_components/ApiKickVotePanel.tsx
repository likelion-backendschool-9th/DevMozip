'use client';
import {useEffect,useRef,useState} from 'react';
import {Button} from '@/components/common/Button';
import {Modal} from '@/components/common/Modal';
import {projectRequest} from '@/lib/projects/useProjectApi';
import {onMessengerSignal} from '@/lib/state/messengerSocketStore';
import {kickReasons} from '@/mocks/chatKick';
import type {MemberRemovalVote} from '@/types/memberRemoval';
import styles from './KickVotePanel.module.css';
const labels={ACTIVE:'투표 진행 중',PASSED:'강퇴 완료',REJECTED:'부결',EXPIRED:'기간 만료',CANCELLED:'투표 취소'};
const policy='임시 정책: 주최자는 대상에서 제외 · 주최자도 1표 · 투표 기간 72시간 · 미투표자도 정족수에 포함 · 팀원 변경 시 취소';
export function ApiKickVotePanel({projectId,version=0}:{projectId:number;version?:number}){
 const [votes,setVotes]=useState<MemberRemovalVote[]>([]);const [error,setError]=useState('');const [pending,setPending]=useState(false);const lock=useRef(false);const reload=useRef<()=>Promise<void>>(async()=>{});
 useEffect(()=>{let active=true;const abort=new AbortController();let loading=false;
  const load=async()=>{if(loading)return;loading=true;try{const rows=await projectRequest<MemberRemovalVote[]>('/'+projectId+'/kick-votes','GET',undefined,abort.signal);if(active){setVotes(rows);setError('');}}catch(e){if(active)setError(e instanceof Error?e.message:'투표를 불러오지 못했습니다.');}finally{loading=false;}};
  reload.current=load;void load();const timer=setInterval(()=>{if(!document.hidden)void load();},5000);
  const unsubscribe=onMessengerSignal(s=>{if(s.type==='project.members.changed'&&s.roomId===projectId)void load();});
  return()=>{active=false;abort.abort();clearInterval(timer);unsubscribe();};
 },[projectId,version]);
 async function vote(id:number,agree:boolean){if(lock.current)return;lock.current=true;setPending(true);try{await projectRequest('/'+projectId+'/kick-votes/'+id+'/ballots','POST',{agree});await reload.current();}catch(e){setError(e instanceof Error?e.message:'투표 상태를 확인해 주세요.');}finally{lock.current=false;setPending(false);}}
 if(!votes.length)return error?<p role="alert">{error}</p>:null;
 return <section className={[styles.scope,'kick-vote-panel'].join(' ')} aria-label="강퇴 투표 현황"><header><h2>강퇴 투표 현황</h2></header><p className="auth-hint">{policy}</p>{error&&<p role="alert">{error}</p>}{votes.map(v=><article key={v.id} className={[styles.scope,'kick-vote-card'].join(' ')}><header><div><h3>대상 · {v.targetNickname}</h3><p>사유 · {v.reason}</p></div><strong>{labels[v.status]}</strong></header><div className={[styles.scope,'kick-vote-counts'].join(' ')}><span>찬성 <b>{v.yes}</b>명</span><span>반대 <b>{v.no}</b>명</span><span>투표 가능 <b>{v.eligibleVoterCount}</b>명</span><span>가결 기준 <b>{v.requiredCount}</b>명</span></div><p>{v.yes>=v.requiredCount?'과반수 달성':'과반수 미달'} · 만료 {v.expiresAt.replace('T',' ')} (한국 시간)</p>{v.status==='ACTIVE'&&<div className={[styles.scope,'kick-ballots'].join(' ')}><p>{v.myVote===null?(v.canVote?'아직 투표하지 않았습니다.':'강퇴 대상자는 투표할 수 없습니다.'):'이미 '+(v.myVote?'찬성':'반대')+' 투표했습니다.'}</p><Button disabled={!v.canVote||pending} onClick={()=>void vote(v.id,true)}>찬성</Button><Button variant="secondary" disabled={!v.canVote||pending} onClick={()=>void vote(v.id,false)}>반대</Button></div>}</article>)}</section>;
}
export function ApiKickVoteDialog({projectId,targetMemberId,name,onClose,onCreated}:{projectId:number;targetMemberId:number;name:string;onClose:()=>void;onCreated:()=>void}){
 const [reason,setReason]=useState(kickReasons[0]);const [error,setError]=useState('');const [pending,setPending]=useState(false);const lock=useRef(false);
 return <Modal title="강퇴 투표 요청" onClose={onClose}><form className={[styles.scope,'profile-form'].join(' ')} onSubmit={async e=>{e.preventDefault();if(lock.current)return;lock.current=true;setPending(true);try{await projectRequest('/'+projectId+'/members','POST',{projectMemberId:targetMemberId,reason});onCreated();onClose();}catch(e){setError(e instanceof Error?e.message:'투표 상태를 확인해 주세요.');}finally{lock.current=false;setPending(false);}}}><strong>강퇴 대상 · {name}</strong><label>강퇴 사유<select value={reason} onChange={e=>setReason(e.target.value)} className="form-input">{kickReasons.map(r=><option key={r}>{r}</option>)}</select></label><p>전체 투표 가능 인원의 절반을 초과하는 찬성이 필요합니다. 가결되면 프로젝트 참여와 채팅 접근이 종료되고 강퇴 이력은 남습니다. 요청자도 별도로 투표해 주세요.</p><p className="auth-hint">{policy}</p>{error&&<p role="alert">{error}</p>}<Button disabled={pending} type="submit">강퇴 투표 시작</Button></form></Modal>;
}
