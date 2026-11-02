'use client';
import styles from './ChatResults.module.css';
import { useState } from 'react';
import Link from 'next/link';
import { useNavigation as useNavigate } from '@/lib/navigation';
import { LockKeyhole } from 'lucide-react';
import { useAuth } from '@/lib/state/AuthProvider';
import { useProfiles } from '@/lib/state/ProfileProvider';
import { useProjects } from '@/lib/state/ProjectProvider';
import { useMessaging } from '@/lib/state/MessagingProvider';
import { Card } from '@/components/common/Card';
import { Modal } from '@/components/common/Modal';
import { Button } from '@/components/common/Button';
import { Input, Textarea } from '@/components/common/Input';
import type { MatchingResult } from '@/types/matching';
export function ChatResults({ result, title }: {
    result: MatchingResult;
    title: string;
}) { const { user, canViewProfile } = useAuth(); const { state } = useProfiles(); const { projects } = useProjects(); const { act } = useMessaging(); const navigate = useNavigate(); const [target, setTarget] = useState<string | null>(null); const [projectTitle, setProjectTitle] = useState(title.slice(0, 100)); const [body, setBody] = useState(''); const [error, setError] = useState(''); const premium = user?.role === 'premium' || user?.role === 'admin'; const developers = result.developers.filter(d => state.profiles[d.id] && canViewProfile(d.id)); return <div className={[styles.scope, "chat-results"].join(" ")}><h3>이런 동료와 함께해 보세요</h3><p className={[styles.scope, "auth-hint"].join(" ")}>추천 시점의 Mock 규칙 계산 점수입니다. 실제 AI 평가·채용 적합도를 보장하지 않습니다.</p><div className={[styles.scope, "chat-result-grid"].join(" ")}>{developers.map(d => <Card className={[styles.scope, "chat-developer"].join(" ")} key={d.id}><div className={[styles.scope, "chat-developer-heading"].join(" ")}><Link href={`/users/${d.id}`}><strong>{state.profiles[d.id].nickname}</strong></Link><b>{d.score}%</b></div><p>{d.role} · {d.career}</p><p>신뢰 온도 {d.temperature.toFixed(1)}°C · {d.available ? '협업 가능' : '협업 쉬는 중'}</p><div className={[styles.scope, "tags"].join(" ")}>{d.stacks.map(s => <span key={s}>{s}</span>)}</div>{premium ? <ul className={[styles.scope, "matching-reasons"].join(" ")}>{d.reasons.map(reason => <li key={reason}>{reason}</li>)}</ul> : <Link className={[styles.scope, "locked-reason"].join(" ")} href="/premium"><LockKeyhole size={13}/>상세 추천 이유는 프리미엄에서</Link>}<div className={[styles.scope, "chat-card-actions"].join(" ")}><Link className={[styles.scope, "button secondary"].join(" ")} href={`/users/${d.id}`}>프로필 보기</Link><Button variant="secondary" onClick={() => { if (!user) {
    navigate('/login');
    return;
} setTarget(d.id); setBody(''); setError(''); }}>협업 제안</Button></div></Card>)}</div>{!developers.length && <p>현재 공개 가능한 개발자 후보가 없습니다. 조건을 조정해 주세요.</p>}<h3>유사 프로젝트</h3><div className={[styles.scope, "chat-project-results"].join(" ")}>{result.projects.filter(p => projects.some(v => v.id === p.id)).map(p => <Card key={p.id}><Link href={`/projects/${p.id}`}><strong>{p.title}</strong><b>{p.score}%</b></Link><p>{p.field} · {p.stacks.join(' · ')}</p>{premium ? <p>{p.reasons.join(' / ')}</p> : <Link href="/premium" className={[styles.scope, "locked-reason"].join(" ")}><LockKeyhole size={12}/>상세 비교 이유 잠금</Link>}</Card>)}</div>{target && <Modal title="협업 제안" onClose={() => setTarget(null)}><form onSubmit={e => { e.preventDefault(); const outcome = act({ type: 'send-direct', targetId: target, body, kind: 'proposal', proposalTitle: projectTitle }); if (outcome.ok) {
    setTarget(null);
    navigate(`/messages?userId=${encodeURIComponent(target)}`);
}
else
    setError(outcome.error || '전송하지 못했습니다.'); }}><label>프로젝트명<Input value={projectTitle} maxLength={100} onChange={e => setProjectTitle(e.target.value)} required/></label><label>제안 메시지<Textarea rows={4} value={body} minLength={10} maxLength={2000} onChange={e => setBody(e.target.value)} required/></label>{error && <p role="alert" className={[styles.scope, "form-error"].join(" ")}>{error}</p>}<div className={[styles.scope, "modal-actions"].join(" ")}><Button type="submit">협업 제안 보내기</Button></div></form></Modal>}</div>; }
