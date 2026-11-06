'use client';
import styles from './CreateProjectForm.module.css';
import { useRef, useState } from 'react';
import { isAxiosError } from 'axios';
import { createProjectViaApi, toProjectCreationRequest } from '@/lib/projects/projectCreationApi';
import type { ProjectCreationReceipt } from '@/types/projectApi';
import { useNavigation as useNavigate } from '@/lib/navigation';
import { useProjects } from '@/lib/state/projectStore';
import { emptyProjectInput, projectFields, recruitmentRoles } from '@/mocks/project';
import { stackOptions } from '@/mocks/home';
import { validateProjectInput } from '@/lib/projects/projectService';
import { Input, Textarea } from '@/components/common/Input';
import { Button } from '@/components/common/Button';
import type { CreateProjectInput, RecruitmentProject } from '@/types/project';
export function CreateProjectForm({ project, onSaved, onCancel, apiEnabled = false }: {
    apiEnabled?: boolean;
    project?: RecruitmentProject;
    onSaved?: () => void;
    onCancel?: () => void;
} = {}) {
 const [imageUrl, setImageUrl] = useState('');
 const [isPublic, setIsPublic] = useState(true);
 const [pending, setPending] = useState(false);
 const inFlight = useRef(false);
 const [receipt, setReceipt] = useState<ProjectCreationReceipt | null>(null);
 const { act, now } = useProjects(); const navigate = useNavigate(); const [form, setForm] = useState<CreateProjectInput>(() => project ? { title: project.title, summary: project.summary, description: project.description, goal: project.goal, field: project.field, stacks: [...project.stacks], developmentStart: project.developmentStart, developmentEnd: project.developmentEnd, recruitmentStart: project.recruitmentStart, deadline: project.deadline, slots: project.slots.map(s => ({ role: s.role, count: s.count, minYears: s.minYears })) } : structuredClone(emptyProjectInput)); const [errors, setErrors] = useState<string[]>([]); const edit = <K extends keyof CreateProjectInput>(key: K, value: CreateProjectInput[K]) => { setForm(prev => ({ ...prev, [key]: value })); setErrors([]); }; if (receipt) return <section role="status"><h2>프로젝트를 등록했습니다.</h2><p>{receipt.title} · 프로젝트 번호 {receipt.id}</p><p>목록·상세 화면의 서버 연결은 다음 단계에서 제공됩니다.</p><Button type="button" variant="secondary" onClick={() => navigate('/projects')}>모집 목록으로</Button></section>;
 return <form className={[styles.scope, "profile-form create-project-form"].join(" ")} onSubmit={async e => {
 e.preventDefault();
 if (inFlight.current) return;
 const errors = validateProjectInput(form, project ? new Date('1970-01-01') : apiEnabled ? new Date() : now);
 setErrors(errors);
 if (errors.length) return;
 if (apiEnabled && !project) {
  inFlight.current = true; setPending(true);
  try { setReceipt(await createProjectViaApi(toProjectCreationRequest(form, imageUrl, isPublic))); }
  catch (error) {
   const message = isAxiosError(error) && typeof error.response?.data?.message === 'string' ? error.response.data.message
    : error instanceof Error && !isAxiosError(error) ? error.message : '등록 응답을 확인하지 못했습니다. 재등록 전에 저장 여부를 확인해 주세요.';
   setErrors([message]);
  } finally { inFlight.current = false; setPending(false); }
  return;
 }
 const result = act(project ? { type: 'edit', projectId: project.id, input: form } : { type: 'create', input: form });
 if (result.ok && project) { onSaved?.(); return; }
 if (result.ok && result.projectId) navigate(`/projects/${result.projectId}`);
 else setErrors([result.error || '등록하지 못했습니다.']);
 }}>{apiEnabled && !project && <><label>대표 이미지 URL<Input type="url" required maxLength={2048} value={imageUrl} onChange={e => setImageUrl(e.target.value)} placeholder="https://…" /></label><label>공개 범위<select className="form-input" value={isPublic ? 'public' : 'private'} onChange={e => setIsPublic(e.target.value === 'public')}><option value="public">전체 공개</option><option value="private">팀원만 공개</option></select></label></>}<label>프로젝트명<Input required value={form.title} maxLength={100} onChange={e => edit('title', e.target.value)}/></label><label>한 줄 소개<Input required value={form.summary} maxLength={200} onChange={e => edit('summary', e.target.value)}/></label><label>프로젝트 소개<Textarea required rows={5} maxLength={5000} value={form.description} onChange={e => edit('description', e.target.value)} placeholder="프로젝트를 10자 이상 소개해 주세요."/></label><label>프로젝트 목표<Textarea required rows={3} maxLength={1000} value={form.goal} onChange={e => edit('goal', e.target.value)}/></label><label>분야<select className={[styles.scope, "form-input"].join(" ")} value={form.field} onChange={e => edit('field', e.target.value)}>{projectFields.map(f => <option key={f}>{f}</option>)}</select></label><fieldset><legend>요구 기술 · 하나 이상 선택</legend><div className={[styles.scope, "chips"].join(" ")}>{stackOptions.map(s => <button type="button" key={s} aria-pressed={form.stacks.includes(s)} className={[styles.scope, `chip ${form.stacks.includes(s) ? 'selected' : ''}`].join(" ")} onClick={() => edit('stacks', form.stacks.includes(s) ? form.stacks.filter(x => x !== s) : [...form.stacks, s])}>{s}</button>)}</div></fieldset><div className={[styles.scope, "auth-two-columns"].join(" ")}>{([{ key: 'developmentStart', label: '개발 시작일' }, { key: 'developmentEnd', label: '개발 종료일' }, { key: 'recruitmentStart', label: '모집 시작일' }, { key: 'deadline', label: '모집 마감일' }] as const).map(f => <label key={f.key}>{f.label}<Input type="date" required value={form[f.key]} onChange={e => edit(f.key, e.target.value)}/></label>)}</div><fieldset><legend>모집 직군별 인원 및 최소 경력</legend>{form.slots.map((slot, i) => <div className={[styles.scope, "create-role-row"].join(" ")} key={i}><label>직군<select className={[styles.scope, "form-input"].join(" ")} value={slot.role} onChange={e => edit('slots', form.slots.map((s, n) => n === i ? { ...s, role: e.target.value } : s))}>{recruitmentRoles.map(r => <option key={r}>{r}</option>)}</select></label><label>모집 인원<Input type="number" min={1} max={10} value={slot.count} onChange={e => edit('slots', form.slots.map((s, n) => n === i ? { ...s, count: Number(e.target.value) } : s))}/></label><label>최소 경력<select className={[styles.scope, "form-input"].join(" ")} value={slot.minYears} onChange={e => edit('slots', form.slots.map((s, n) => n === i ? { ...s, minYears: Number(e.target.value) } : s))}>{[0, 1, 2, 3, 4, 5].map(n => <option key={n} value={n}>{n ? `${n}년 이상` : '경력 무관'}</option>)}</select></label><button className={[styles.scope, "text-button"].join(" ")} type="button" disabled={form.slots.length === 1} onClick={() => edit('slots', form.slots.filter((_, n) => n !== i))}>삭제</button></div>)}<Button type="button" variant="secondary" disabled={form.slots.length === recruitmentRoles.length} onClick={() => edit('slots', [...form.slots, { role: recruitmentRoles.find(r => !form.slots.some(s => s.role === r)) || recruitmentRoles[0], count: 1, minYears: 0 }])}>직군 추가</Button></fieldset><p className={[styles.scope, "auth-hint"].join(" ")}>{project ? '기존 등록 팀원과 지원자가 있는 직군·정원은 제한적으로 수정할 수 있습니다.' : '주최자 1명과 위 모집 인원으로 팀 정원이 구성됩니다.'} 주최자 관리 화면에서 지원자와 팀원을 관리할 수 있습니다.</p>{errors.length > 0 && <ul className={[styles.scope, "form-error"].join(" ")} role="alert">{errors.map(e => <li key={e}>{e}</li>)}</ul>}<div className={[styles.scope, "modal-actions"].join(" ")}><Button type="button" variant="secondary" disabled={pending} onClick={() => onCancel ? onCancel() : navigate('/projects')}>취소</Button><Button type="submit" disabled={pending}>{pending ? '등록 중…' : project ? '공고 수정 저장' : '모집 공고 등록'}</Button></div></form>; }
