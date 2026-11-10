'use client';
import {formatKickExpiry} from '@/lib/projects/kickVoteView';
import styles from './KickVotePanel.module.css';
import { useState } from 'react';
import { useAuth } from '@/lib/state/authStore';
import { useProfiles } from '@/lib/state/profileStore';
import { useProjects } from '@/lib/state/projectStore';
import { Modal } from '@/components/common/Modal';
import { Button } from '@/components/common/Button';
import { kickReasons, kickStatusLabels, kickVotePolicy } from '@/mocks/chatKick';
import { kickVoteCounts } from '@/lib/projects/chatKickService';
export function KickVoteDialog({ projectId, targetId, members, onClose }: {
    projectId: string;
    targetId: string;
    members: string[];
    onClose: () => void;
}) { const { user } = useAuth(); const { state: profiles } = useProfiles(); const { kick, projects } = useProjects(); const [target, setTarget] = useState(targetId); const [reason, setReason] = useState(kickReasons[0]); const [error, setError] = useState(''); const count = members.filter(id => id !== target).length; return <Modal title="강퇴 투표 요청" onClose={onClose}><form className={[styles.scope, "profile-form"].join(" ")} onSubmit={e => { e.preventDefault(); const result = kick({ type: 'start', projectId, targetId: target, reason }); if (result.ok)
    onClose();
else
    setError(result.error || '투표 내용을 확인해 주세요.'); }}><label>강퇴 대상자<select className={[styles.scope, "form-input"].join(" ")} value={target} onChange={e => setTarget(e.target.value)}>{members.filter(id => id !== user?.id && id !== projects.find(p=>p.id===projectId)?.hostId).map(id => <option key={id} value={id}>{profiles.profiles[id]?.nickname || id}</option>)}</select></label><label>강퇴 사유<select className={[styles.scope, "form-input"].join(" ")} value={reason} onChange={e => setReason(e.target.value)}>{kickReasons.map(r => <option key={r}>{r}</option>)}</select></label><p>대상자를 제외한 {count}명 중 {Math.floor(count / 2) + 1}명 이상의 찬성이 필요합니다. 요청자도 찬성/반대를 따로 선택해야 합니다.</p><p className={[styles.scope, "auth-hint"].join(" ")}>임시 정책: 주최자도 동일한 1표 · 기간 {kickVotePolicy.durationHours}시간 · 기권도 정족수 분모에 포함 · 주최자는 대상에서 제외 · 강퇴 시 프로젝트 참여와 채팅 접근이 종료되며 강퇴 이력은 유지합니다.</p>{error && <p className={[styles.scope, "form-error"].join(" ")} role="alert">{error}</p>}<div className={[styles.scope, "modal-actions"].join(" ")}><Button variant="secondary" type="button" onClick={onClose}>취소</Button><Button type="submit">강퇴 투표 시작</Button></div></form></Modal>; }
export function KickVotePanel({ projectId, members, canWrite }: {
    projectId: string;
    members: string[];
    canWrite: boolean;
}) { const { user } = useAuth(); const { state: profiles } = useProfiles(); const { state, now, kick } = useProjects(); const votes = state.kickVotes.filter(v => v.projectId === projectId); if (!votes.length) return null; return <section className={[styles.scope, "kick-vote-panel"].join(" ")} aria-label="강퇴 투표 현황"><header><h2>강퇴 투표 현황</h2><span className={[styles.scope, "demo-pill"].join(" ")}>임시 운영 정책</span></header><p className={[styles.scope, "auth-hint"].join(" ")}>대상자는 투표할 수 없습니다. 전체 투표 가능 인원의 절반을 초과해야 강퇴됩니다. 주최자도 동일한 1표입니다.</p>{votes.map(v => { const count = kickVoteCounts(v); const mine = user ? v.ballots[user.id] : undefined; const eligible = !!user && v.eligibleVoterIds.includes(user.id) && members.includes(user.id); const active = canWrite && v.status === 'active' && Date.parse(v.createdAt) <= now.getTime() && now.getTime() < Date.parse(v.expiresAt); return <article className={[styles.scope, "kick-vote-card"].join(" ")} key={v.id}><header><div><h3>{profiles.profiles[v.targetId]?.nickname || v.targetId} 님</h3><p>{v.reason}</p></div><strong className={[styles.scope, `vote-state ${v.status}`].join(" ")}>{kickStatusLabels[v.status]}</strong></header><div className={[styles.scope, "kick-vote-counts"].join(" ")}><span>찬성 <b>{count.yes}</b>명</span><span>반대 <b>{count.no}</b>명</span><span>전체 투표 가능 <b>{count.total}</b>명</span><span>가결 기준 <b>{count.required}</b>명</span></div><progress className={styles.progress} value={count.yes} max={count.total} aria-label="찬성 투표 현황"/><p className={[styles.scope, "kick-majority"].join(" ")}>{count.hasMajority ? '과반수 달성' : '과반수 미달'} · 미투표 {count.remaining}명</p><p className={[styles.scope, "auth-hint"].join(" ")}>만료 {formatKickExpiry(v.expiresAt)}</p>{v.resolutionReason && <p className={[styles.scope, "auth-hint"].join(" ")}>{v.resolutionReason}</p>}{v.status === 'active' && <div className={[styles.scope, "kick-ballots"].join(" ")}>{user?.id === v.targetId ? <p>강퇴 대상자는 투표할 수 없습니다.</p> : mine ? <p role="status">이미 {mine === 'yes' ? '찬성' : '반대'} 투표했습니다. 한 번만 투표할 수 있습니다.</p> : <p>아직 투표하지 않았습니다.</p>}<Button disabled={!active || !eligible || !!mine} onClick={() => kick({ type: 'vote', voteId: v.id, choice: 'yes' })}>찬성</Button><Button variant="secondary" disabled={!active || !eligible || !!mine} onClick={() => kick({ type: 'vote', voteId: v.id, choice: 'no' })}>반대</Button></div>}</article>; })}</section>; }
