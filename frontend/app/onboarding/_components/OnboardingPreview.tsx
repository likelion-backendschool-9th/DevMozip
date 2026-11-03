'use client';
import styles from './OnboardingPreview.module.css';
import type { OnboardingDraft } from '@/types/account';
import { ProfileAvatar } from '@/components/profile/ProfileAvatar';
export function OnboardingPreview({ draft }: {
    draft: OnboardingDraft;
}) { return <article className={[styles.scope, "onboarding-preview"].join(" ")}><div className={[styles.scope, "onboarding-preview-banner"].join(" ")}/><ProfileAvatar name={draft.nickname || '새 동료'} color="lavender" image={draft.avatar}/><h2>{draft.nickname || '새 동료'}</h2><p>{draft.bio || '한 줄 자기소개를 입력해 주세요.'}</p><div className={[styles.scope, "profile-tags"].join(" ")}>{draft.jobs.map(j => <span key={j}>{j}</span>)}</div><p>{draft.career} · {draft.available ? '협업 가능' : '지금은 협업을 쉬고 있어요'}</p><dl><div><dt>기술 스택</dt><dd>{draft.stacks.join(' · ') || '선택 전'}</dd></div><div><dt>관심 개발 분야</dt><dd>{[...draft.interests, ...draft.customFields].join(' · ') || '선택 전'}</dd></div><div><dt>관심 프로젝트</dt><dd>{draft.projectInterests.join(' · ') || '선택 전'}</dd></div><div><dt>연락 가능 시간</dt><dd>{draft.contact || '입력 전'}</dd></div><div><dt>GitHub / 블로그</dt><dd>{[draft.github, draft.blog].filter(Boolean).join(' / ') || '미등록'}</dd></div></dl></article>; }
