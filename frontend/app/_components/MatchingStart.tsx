'use client';
import styles from './MatchingStart.module.css';
import Link from 'next/link';
import { useNavigation as useNavigate } from '@/lib/navigation';
import { Sparkles, ArrowRight } from 'lucide-react';
import { useHome } from '@/lib/state/HomeProvider';
import { useState } from 'react';
import { Button } from '@/components/common/Button';
import { Textarea } from '@/components/common/Input';
export function MatchingStart() { const { state, update, usage, newChat } = useHome(); const navigate = useNavigate(); const [error, setError] = useState(''); return <section className={[styles.scope, "matching-section matching-start"].join(" ")}><div className={[styles.scope, "matching-intro"].join(" ")}><div className={[styles.scope, "eyebrow"].join(" ")}><Sparkles size={15}/> YOUR NEXT TEAM STARTS HERE</div><h1>아이디어에 딱 맞는 동료,<br /><span>대화하며 찾아보세요.</span></h1><p>만들고 싶은 프로젝트를 알려주세요.<br />AI 추천 대화에서 조건을 정리하고 동료를 비교해요.</p><small>Mock 규칙 기반 · 실제 AI API 연결 없음</small>{usage.limit > 0 && <p>{usage.month} · 남은 추천 {usage.remaining} / {usage.limit}회 · {usage.used}회 사용</p>}</div><form className={[styles.scope, "matching-form"].join(" ")} onSubmit={e => { e.preventDefault(); if (state.input.description.trim().length < 10) {
    setError('프로젝트를 10자 이상 설명해 주세요.');
    return;
} newChat(state.input.description); navigate('/matching'); }}><label htmlFor="project-description"><strong>어떤 프로젝트를 만들고 싶으세요?</strong></label><Textarea aria-label="프로젝트 설명" id="project-description" rows={5} maxLength={1500} value={state.input.description} onChange={e => { update({ input: { ...state.input, description: e.target.value } }); setError(''); }} placeholder="예: React로 일정 관리 웹 서비스를 만들고 싶어요. 프론트엔드와 백엔드 동료가 필요해요."/><div className={[styles.scope, "matching-submit"].join(" ")}><small>추가 질문은 무료 · 결과 생성 시 1회</small><Button type="submit"><Sparkles size={16}/>추천 시작<ArrowRight size={16}/></Button></div>{error && <p role="alert" className={[styles.scope, "form-error"].join(" ")}>{error}</p>}<Link className={[styles.scope, "auth-hint"].join(" ")} href="/matching">이전 추천 대화 열기 →</Link></form></section>; }
