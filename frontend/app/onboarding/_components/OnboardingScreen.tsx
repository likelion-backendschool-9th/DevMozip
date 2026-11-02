'use client';
import styles from './OnboardingScreen.module.css';
import { useRef, useState } from 'react';
import Link from 'next/link';
import { useNavigation as useNavigate } from '@/lib/navigation';
import { useAuth } from '@/lib/state/AuthProvider';
import { useProfiles } from '@/lib/state/ProfileProvider';
import { usePrototype } from '@/lib/state/PrototypeProvider';
import { Button } from '@/components/common/Button';
import { Input, Textarea } from '@/components/common/Input';
import { TagChoices } from '@/app/onboarding/_components/TagChoices';
import { OnboardingPreview } from '@/app/onboarding/_components/OnboardingPreview';
import { ProfileAvatar } from '@/components/profile/ProfileAvatar';
import { onboardingSteps } from '@/mocks/account';
import { careerOptions, jobOptions } from '@/mocks/auth';
import { interestOptions, stackOptions } from '@/mocks/home';
import { emptyOnboarding, onboardingErrors, readAvatar } from '@/lib/account/onboardingService';
import type { AuthUser } from '@/types/auth';
import type { OnboardingDraft } from '@/types/account';
function OnboardingForm({ user }: {
    user: AuthUser;
}) { const { accountArea, accountAct, completeOnboarding, publicUsers } = useAuth(); const { state, act } = useProfiles(); const { notify } = usePrototype(); const navigate = useNavigate(); const stored = accountArea.accounts[user.id]; const [draft, setDraft] = useState(() => stored?.draft || emptyOnboarding(user)); const draftRef = useRef(draft); draftRef.current = draft; const [step, setStep] = useState(stored?.step || 0); const [errors, setErrors] = useState<string[]>([]); const [custom, setCustom] = useState(''); const [photoBusy, setPhotoBusy] = useState(false); const edit = (patch: Partial<OnboardingDraft>) => { const next = { ...draftRef.current, ...patch }; draftRef.current = next; setDraft(next); setErrors([]); accountAct({ type: 'onboarding-draft', draft: next, step }); }; const move = (next: number) => { setStep(next); setErrors([]); accountAct({ type: 'onboarding-draft', draft, step: next }); window.scrollTo({ top: 0, behavior: 'instant' }); }; const taken = [...Object.values(state.profiles), ...publicUsers].some(p => p.id !== user.id && p.nickname.toLowerCase() === draft.nickname.trim().toLowerCase()); const submit = () => { const errors = onboardingErrors(draft, step, taken); setErrors(errors); if (errors.length)
    return; if (step < 3) {
    move(step + 1);
    return;
} if (act({ type: 'onboarding', input: draft }) && completeOnboarding(draft)) {
    notify('온보딩을 완료했습니다. 나의 프로필을 확인해 보세요.');
    navigate(`/users/${user.id}`, { replace: true });
} }; return <div className={[styles.scope, "container onboarding-page"].join(" ")}><header className={[styles.scope, "onboarding-heading"].join(" ")}><span className={[styles.scope, "eyebrow"].join(" ")}>WELCOME TO YOUR NEXT TEAM</span><h1>동료에게 나를 소개해 주세요.</h1><p>4단계로 내 프로필을 완성해요. 실제 개인정보 대신 발표용 가상 정보를 입력해 주세요.</p></header><ol className={[styles.scope, "onboarding-steps"].join(" ")} aria-label="온보딩 진행 단계">{onboardingSteps.map((label, i) => <li key={label} aria-current={step === i ? 'step' : undefined} className={[styles.scope, step === i ? 'active' : step > i ? 'done' : ''].join(" ")}><span>{i + 1}</span><strong>{label}</strong></li>)}</ol><section className={[styles.scope, "onboarding-panel"].join(" ")}><div className={[styles.scope, "onboarding-step-title"].join(" ")}><span>STEP 0{step + 1} / 04</span><h2>{onboardingSteps[step]}</h2></div><form className={[styles.scope, "profile-form"].join(" ")} onSubmit={e => { e.preventDefault(); submit(); }}>{step === 0 && <><div className={[styles.scope, "onboarding-photo"].join(" ")}><ProfileAvatar name={draft.nickname || '새 동료'} color="lavender" image={draft.avatar}/><div><label>프로필 사진 (선택)<Input type="file" accept="image/png,image/jpeg,image/webp" disabled={photoBusy} onChange={async (e) => { const file = e.target.files?.[0]; e.target.value = ''; if (!file)
    return; setPhotoBusy(true); try {
    edit({ avatar: await readAvatar(file) });
}
catch (e) {
    setErrors([e instanceof Error ? e.message : '사진을 읽지 못했습니다.']);
}
finally {
    setPhotoBusy(false);
} }}/></label><p className={[styles.scope, "auth-hint"].join(" ")}>PNG/JPEG/WebP · 256KB 이하 · 선택하지 않으면 기본 이미지</p>{draft.avatar && <Button type="button" variant="secondary" onClick={() => edit({ avatar: '' })}>사진 삭제</Button>}</div></div><label>닉네임 <span className={[styles.scope, "required-label"].join(" ")}>필수</span><Input value={draft.nickname} maxLength={20} onChange={e => edit({ nickname: e.target.value })}/></label><label>한 줄 자기소개 <span className={[styles.scope, "required-label"].join(" ")}>필수</span><Textarea rows={3} value={draft.bio} maxLength={150} onChange={e => edit({ bio: e.target.value })}/></label></>}{step === 1 && <><TagChoices label="개발 직군 (복수 선택 · 필수)" options={jobOptions} values={draft.jobs} onChange={jobs => edit({ jobs })}/><label>경력 <select className={[styles.scope, "form-input"].join(" ")} value={draft.career} onChange={e => edit({ career: e.target.value })}><option value="">경력 선택</option>{careerOptions.map(c => <option key={c}>{c}</option>)}</select></label><TagChoices label="기술 스택 (복수 선택 · 필수)" options={stackOptions} values={draft.stacks} onChange={stacks => edit({ stacks })}/><TagChoices label="관심 개발 분야 (복수 선택)" options={interestOptions.slice(1)} values={draft.interests} onChange={interests => edit({ interests })}/><label>기타 분야 직접 입력<Input value={custom} maxLength={30} onChange={e => setCustom(e.target.value)} placeholder="예: 접근성, 게임 개발"/></label><Button type="button" variant="secondary" onClick={() => { const value = custom.trim(); if (!value || draft.customFields.length >= 5 || [...draft.customFields, ...draft.interests].includes(value)) {
    setErrors(['기타 분야는 중복 없이 최대 5개까지 추가해 주세요.']);
    return;
} edit({ customFields: [...draft.customFields, value] }); setCustom(''); }}>기타 분야 추가</Button><div className={[styles.scope, "selected-tags"].join(" ")}>{draft.customFields.map(field => <button key={field} type="button" aria-label={`기타 분야 ${field} 삭제`} onClick={() => edit({ customFields: draft.customFields.filter(v => v !== field) })}>{field} ×</button>)}</div></>}{step === 2 && <><fieldset><legend>협업 가능 여부</legend><label className={[styles.scope, "checkbox-label"].join(" ")}><input type="checkbox" checked={draft.available} onChange={e => edit({ available: e.target.checked })}/>새로운 프로젝트에 협업할 수 있어요.</label></fieldset><TagChoices label="관심 프로젝트 분야 (복수 선택 · 필수)" options={interestOptions.slice(1)} values={draft.projectInterests} onChange={projectInterests => edit({ projectInterests })}/><label>연락 가능 시간 <span className={[styles.scope, "required-label"].join(" ")}>필수</span><Input value={draft.contact} maxLength={100} onChange={e => edit({ contact: e.target.value })} placeholder="예: 평일 19:00–22:00"/></label><label>GitHub URL (선택)<Input value={draft.github} maxLength={500} onChange={e => edit({ github: e.target.value })} placeholder="https://github.com/..."/></label><label>블로그 URL (선택)<Input value={draft.blog} maxLength={500} onChange={e => edit({ blog: e.target.value })} placeholder="https://..."/></label></>}{step === 3 && <><p>입력한 정보는 내 공개 프로필에 반영됩니다. 수정이 필요하면 이전 단계로 돌아가세요.</p><OnboardingPreview draft={draft}/></>}{errors.length > 0 && <ul className={[styles.scope, "form-error"].join(" ")} role="alert">{errors.map(error => <li key={error}>{error}</li>)}</ul>}<div className={[styles.scope, "onboarding-actions"].join(" ")}><Button variant="secondary" type="button" disabled={step === 0 || photoBusy} onClick={() => move(step - 1)}>이전</Button><span>{step + 1} / 4</span><Button type="submit" disabled={photoBusy}>{step === 3 ? '완료' : '다음'}</Button></div></form></section></div>; }
export function OnboardingPage() { const { user, needsOnboarding } = useAuth(); if (!user)
    return <div className={[styles.scope, "container planned"].join(" ")}><section className={[styles.scope, "planned-card"].join(" ")}><h1>로그인 후 온보딩을 시작해 주세요.</h1><Link className={[styles.scope, "button"].join(" ")} href="/login">로그인하기</Link></section></div>; if (!needsOnboarding)
    return <div className={[styles.scope, "container planned"].join(" ")}><section className={[styles.scope, "planned-card"].join(" ")}><h1>이미 온보딩을 완료했어요.</h1><p>프로필에서 내 정보를 확인하거나 수정할 수 있습니다.</p><Link className={[styles.scope, "button"].join(" ")} href={`/users/${user.id}`}>내 프로필로 이동</Link></section></div>; return <OnboardingForm key={user.id} user={user}/>; }
