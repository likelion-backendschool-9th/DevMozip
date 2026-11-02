'use client';
import styles from './ApplicationModal.module.css';
import { useState } from 'react';
import Link from 'next/link';
import { useAuth } from '@/lib/state/AuthProvider';
import { useProfiles } from '@/lib/state/ProfileProvider';
import { useProjects } from '@/lib/state/ProjectProvider';
import { Modal } from '@/components/common/Modal';
import { Button } from '@/components/common/Button';
import { Textarea } from '@/components/common/Input';
import { cancellationPolicy } from '@/mocks/project';
import { isLateCancellation } from '@/lib/projects/projectService';
import type { RecruitmentProject } from '@/types/project';
export function ApplicationModal({ project, onClose }: {
    project: RecruitmentProject;
    onClose: () => void;
}) { const { user } = useAuth(); const { state } = useProfiles(); const { act } = useProjects(); const portfolios = state.portfolios.filter(p => p.ownerId === user?.id); const [portfolioId, setPortfolioId] = useState(portfolios[0]?.id || ''); const [role, setRole] = useState(project.slots.find(s => s.filled < s.count)?.role || ''); const [message, setMessage] = useState(''); const [error, setError] = useState(''); return <Modal className={[styles.scope, "profile-dialog"].join(" ")} title="프로젝트 지원" onClose={onClose}><p className={[styles.scope, "application-project-title"].join(" ")}>{project.title}</p>{portfolios.length === 0 ? <div className={[styles.scope, "empty-state"].join(" ")}><strong>먼저 내 포트폴리오를 등록해 주세요.</strong><p>지원서에는 본인의 포트폴리오가 필요합니다.</p><Link className={[styles.scope, "button"].join(" ")} href={`/users/${user?.id}?tab=portfolio`} onClick={onClose}>포트폴리오 등록하러 가기</Link></div> : <form className={[styles.scope, "profile-form"].join(" ")} onSubmit={e => { e.preventDefault(); const result = act({ type: 'apply', projectId: project.id, portfolioId, role, message }); if (result.ok)
    onClose();
else
    setError(result.error || '지원 내용을 확인해 주세요.'); }}><label>지원 직군<select className={[styles.scope, "form-input"].join(" ")} value={role} onChange={e => { setRole(e.target.value); setError(''); }}>{project.slots.filter(s => s.filled < s.count).map(s => <option key={s.role} value={s.role}>{s.role} · {s.count - s.filled}명 · {s.minYears ? `${s.minYears}년 이상` : '경력 무관'}</option>)}</select></label><label>내 포트폴리오<select className={[styles.scope, "form-input"].join(" ")} value={portfolioId} onChange={e => { setPortfolioId(e.target.value); setError(''); }}>{portfolios.map(p => <option key={p.id} value={p.id}>{p.title}</option>)}</select></label><label>지원 멘트<Textarea rows={5} value={message} maxLength={1500} onChange={e => { setMessage(e.target.value); setError(''); }} placeholder="함께하고 싶은 이유와 맡고 싶은 역할을 10자 이상 알려주세요."/></label><p className={[styles.scope, "auth-hint"].join(" ")}>지원은 무료 회원도 가능합니다. 제출 후 주최자의 검토를 기다리는 Mock 상태로 표시됩니다.</p>{error && <p role="alert" className={[styles.scope, "form-error"].join(" ")}>{error}</p>}<div className={[styles.scope, "modal-actions"].join(" ")}><Button variant="secondary" type="button" onClick={onClose}>취소</Button><Button type="submit">지원서 제출</Button></div></form>}</Modal>; }
export function CancelApplicationModal({ project, onClose }: {
    project: RecruitmentProject;
    onClose: () => void;
}) { const { act, now } = useProjects(); const late = isLateCancellation(project, now); const [ack, setAck] = useState(false); const [error, setError] = useState(''); return <Modal title="프로젝트 지원을 취소할까요?" onClose={onClose}><p>지원 취소 후 모집 중인 기간에는 다시 지원할 수 있습니다. 승인된 지원을 취소하면 팀원·채팅방 멤버에서도 제외됩니다.</p>{late && <div className={[styles.scope, "cancellation-warning"].join(" ")}><strong>마감 {cancellationPolicy.days}일 전 이후의 지원 취소입니다.</strong><p>신뢰 온도 패널티가 적용될 수 있습니다. 임시 예시 수치는 −{cancellationPolicy.examplePenalty.toFixed(1)}°C이며 실제 서비스 정책은 미확정입니다. 현재 데모에서는 안내만 표시하고 온도를 차감하지 않습니다.</p><label className={[styles.scope, "checkbox-label"].join(" ")}><input type="checkbox" checked={ack} onChange={e => setAck(e.target.checked)}/>임시 패널티 안내를 확인했습니다.</label></div>}{error && <p className={[styles.scope, "form-error"].join(" ")} role="alert">{error}</p>}<div className={[styles.scope, "modal-actions"].join(" ")}><Button variant="secondary" onClick={onClose}>지원 유지</Button><Button disabled={late && !ack} onClick={() => { const result = act({ type: 'cancel', projectId: project.id, acknowledgePenalty: ack }); if (result.ok)
    onClose();
else
    setError(result.error || '취소하지 못했습니다.'); }}>지원 취소</Button></div></Modal>; }
