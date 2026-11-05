'use client';
import styles from './RecommendationPanel.module.css';
import { useEffect, useRef, useState } from 'react';
import Link from 'next/link';
import { ArrowRight, LockKeyhole, Sparkles, UserPlus } from 'lucide-react';
import { useAuth } from '@/lib/state/authStore';
import { useProfiles } from '@/lib/state/profileStore';
import { useHome } from '@/lib/state/homeStore';
import { jobOptions } from '@/mocks/auth';
import { stackOptions, interestOptions, interestFilterOptions } from '@/mocks/home';
import { matchingLatency, questionLabels } from '@/mocks/matching';
import { analyzeMatching } from '@/lib/matching/matchingService';
import { Button } from '@/components/common/Button';
import { Card } from '@/components/common/Card';
import { Textarea } from '@/components/common/Input';
import type { RecommendationInput } from '@/types/home';
export function RecommendationPanel() {
    const { user } = useAuth();
    const { state, update, usage, result: stored, run } = useHome();
    const { state: profiles, act } = useProfiles();
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState('');
    const [prompted, setPrompted] = useState(false);
    const timer = useRef<ReturnType<typeof setTimeout> | null>(null);
    useEffect(() => () => { if (timer.current)
        clearTimeout(timer.current); }, []);
    const analysis = analyzeMatching(state.input);
    const premium = user?.role === 'premium' || user?.role === 'admin';
    const result = loading ? null : stored?.result;
    const edit = (patch: Partial<RecommendationInput>) => { update({ input: { ...state.input, ...patch } }); setError(''); };
    const submit = (e: React.FormEvent) => { e.preventDefault(); setError(''); if (state.input.description.trim().length < 10) {
        setError('프로젝트를 10자 이상 설명해 주세요.');
        return;
    } if (!user) {
        setError('로그인 후 계정별 Mock 추천을 이용해 주세요.');
        return;
    } if (analysis.missing.length) {
        setPrompted(true);
        setError('추가 질문에 답하거나 아직 정하지 않았음을 선택해 주세요. 횟수는 차감하지 않았습니다.');
        return;
    } setLoading(true); const outcome = run(); if (!outcome.ok) {
        setLoading(false);
        setError(outcome.error || '추천하지 못했습니다.');
        return;
    } timer.current = setTimeout(() => setLoading(false), matchingLatency); };
    return <section className={[styles.scope, "matching-section"].join(" ")}><div className={[styles.scope, "matching-intro"].join(" ")}><div className={[styles.scope, "eyebrow"].join(" ")}><Sparkles size={15}/> YOUR NEXT TEAM STARTS HERE</div><h1>아이디어에 딱 맞는 동료,<br /><span>함께 찾을까요?</span></h1><p>프로젝트 설명에서 기술과 관심 분야를 찾아<br />함께할 개발자와 유사 프로젝트를 비교해 드려요.</p><div className={[styles.scope, "matching-footnote"].join(" ")}><span className={[styles.scope, "status-dot"].join(" ")}/>Gemini 연결 없는 Mock 규칙 기반 추천</div><div className={[styles.scope, "matching-usage"].join(" ")} aria-live="polite">{user ? <><span>{usage.month} · {premium ? '프리미엄 데모' : '무료 회원'}</span><strong>남은 추천 <b>{usage.remaining}</b> / {usage.limit}회</strong><p>{usage.used}회 사용 · {premium ? '월 100회는 임시 확대 정책입니다.' : '무료 회원 월 15회'}</p><progress value={Math.min(usage.used, usage.limit)} max={usage.limit} aria-label="이번 달 추천 사용량"/></> : <><strong>계정별 추천 사용량을 확인하세요.</strong><p>일반·프리미엄 데모 계정으로 권한 차이를 체험할 수 있어요.</p><Link href="/login">로그인하고 추천 받기 →</Link></>}</div>{user && usage.remaining === 0 && <div className={[styles.scope, "matching-limit"].join(" ")} role="status">이번 달 추천 횟수를 모두 사용했습니다. 한국 시각 다음 달에 새 한도가 적용됩니다.{!premium && <Link href="/subscription">프리미엄 안내 →</Link>}</div>}</div>
 <form className={[styles.scope, "matching-form"].join(" ")} onSubmit={submit}><div className={[styles.scope, "form-title"].join(" ")}><Sparkles size={18}/><strong>어떤 프로젝트를 만들고 싶으세요?</strong><span>MOCK</span></div><label className={[styles.scope, "sr-only"].join(" ")} htmlFor="project-description">프로젝트 설명</label><Textarea id="project-description" disabled={loading} value={state.input.description} onChange={e => edit({ description: e.target.value })} placeholder="예: React와 Spring Boot로 개발자 팀의 일정 관리 서비스를 만들고 싶어요. 백엔드 동료가 필요해요." maxLength={1500} rows={3} aria-invalid={!!error} aria-describedby={error ? 'recommend-error' : undefined}/>{prompted && analysis.missing.length > 0 && <div className={[styles.scope, "matching-questions"].join(" ")}><strong>조금만 더 알려주세요.</strong><ul>{analysis.missing.map(k => <li key={k}>{questionLabels[k]}</li>)}</ul><p>답을 정하지 않았다면 해당 항목의 ‘아직 정하지 않았어요’를 선택할 수 있어요.</p></div>}<div className={[styles.scope, "question-label"].join(" ")}>함께 쓸 기술 <small>설명에서 추출하거나 직접 선택</small></div><div className={[styles.scope, "chips"].join(" ")}>{stackOptions.map(stack => <button disabled={loading} type="button" className={[styles.scope, `chip ${state.input.stacks.includes(stack) ? 'selected' : ''}`].join(" ")} aria-pressed={state.input.stacks.includes(stack)} key={stack} onClick={() => edit({ stacks: state.input.stacks.includes(stack) ? state.input.stacks.filter(s => s !== stack) : [...state.input.stacks, stack] })}>{stack}</button>)}</div>{prompted && !analysis.stacks.length && <label className={[styles.scope, "checkbox-label matching-skip"].join(" ")}><input type="checkbox" checked={!!state.input.skipStacks} disabled={loading} onChange={e => edit({ skipStacks: e.target.checked })}/>기술은 아직 정하지 않았어요.</label>}<div className={[styles.scope, "matching-extra-fields"].join(" ")}><label>관심 분야<select disabled={loading} value={state.input.interest} onChange={e => edit({ interest: e.target.value })}>{interestFilterOptions.map(i => <option key={i}>{i}</option>)}</select></label><label>필요한 개발 직군<select disabled={loading} value={state.input.role || ''} onChange={e => edit({ role: e.target.value })}><option value="">설명에서 분석 / 선택</option>{jobOptions.map(j => <option key={j}>{j}</option>)}</select></label></div>{prompted && !analysis.interests.length && <label className={[styles.scope, "checkbox-label matching-skip"].join(" ")}><input type="checkbox" disabled={loading} checked={!!state.input.skipInterest} onChange={e => edit({ skipInterest: e.target.checked })}/>관심 분야는 아직 정하지 않았어요.</label>}{prompted && !analysis.role && <label className={[styles.scope, "checkbox-label matching-skip"].join(" ")}><input type="checkbox" disabled={loading} checked={!!state.input.skipRole} onChange={e => edit({ skipRole: e.target.checked })}/>모집 직군은 아직 정하지 않았어요.</label>}{(analysis.inferredStacks.length > 0 || analysis.inferredInterests.length > 0 || analysis.inferredRole) && <div className={[styles.scope, "matching-analysis"].join(" ")}><span>설명에서 찾은 조건 · Mock</span><p>{[...analysis.inferredStacks, ...analysis.inferredInterests, analysis.inferredRole].filter(Boolean).join(' · ')}</p></div>}<div className={[styles.scope, "matching-submit"].join(" ")}><small>성공한 추천 실행만 1회 사용합니다.</small><Button disabled={loading || !!user && usage.remaining === 0} type="submit"><Sparkles size={16}/>{loading ? '조건을 비교하고 있어요…' : '추천 받기'}<ArrowRight size={16}/></Button></div>{error && <p id="recommend-error" className={[styles.scope, "form-error"].join(" ")} role="alert">{error}{!user && <Link href="/login"> 로그인 →</Link>}</p>}</form>
 {loading && <div className={[styles.scope, "recommend-status"].join(" ")} role="status">Mock 조건으로 개발자와 프로젝트를 비교하고 있어요…</div>}
 {result && <div className={[styles.scope, "recommend-results"].join(" ")} aria-live="polite"><div className={[styles.scope, "section-heading"].join(" ")}><div><h2>이런 동료와 함께해 보세요</h2><p>추천 시점 기준 데모 추정 점수이며 실제 AI 분석이 아닙니다. {new Date(stored!.createdAt).toLocaleString('ko-KR', { timeZone: 'Asia/Seoul' })}</p></div><span className={[styles.scope, "outline-badge"].join(" ")}>Mock 추천 결과</span></div><div className={[styles.scope, "developer-grid"].join(" ")}>{result.developers.filter(d => !!profiles.profiles[d.id]).map(d => { const p = profiles.profiles[d.id]; const followed = !!user && !!profiles.following[user.id]?.includes(d.id); return <Card key={d.id} className={[styles.scope, "developer-card"].join(" ")}><div className={[styles.scope, "developer-top"].join(" ")}><Link href={`/users/${p.id}`} className={[styles.scope, `avatar ${p.color}`].join(" ")} aria-label={`${p.nickname} 프로필`}>{p.nickname.slice(1)}</Link><span className={[styles.scope, "match-score"].join(" ")}>{d.score}% <small>적합도</small></span></div><Link href={`/users/${p.id}`}><h3>{p.nickname} <small>{p.career}</small></h3></Link><p>{p.job} · <span className={[styles.scope, "available"].join(" ")}>{p.available ? '협업 가능' : '협업 쉬는 중'}</span></p><p className={[styles.scope, "developer-bio"].join(" ")}>{p.bio}</p><div className={[styles.scope, "tags"].join(" ")}>{p.stacks.map(s => <span key={s}>{s}</span>)}</div>{premium ? <ul className={[styles.scope, "matching-reasons"].join(" ")}>{d.reasons.map(reason => <li key={reason}>{reason}</li>)}</ul> : <Link className={[styles.scope, "locked-reason"].join(" ")} href="/subscription"><LockKeyhole size={13}/>상세 추천 이유는 프리미엄에서<ArrowRight size={13}/></Link>}{user && <Button variant="secondary" className={[styles.scope, "matching-follow"].join(" ")} onClick={() => act({ type: 'follow', targetId: p.id })}><UserPlus size={13}/>{followed ? '언팔로우' : '팔로우'}</Button>}</Card>; })}</div><div className={[styles.scope, "matching-project-results"].join(" ")}><strong>비슷한 아이디어의 프로젝트</strong><div>{result.projects.map(p => <article key={p.id}><Link href={`/projects/${p.id}`}><span className={[styles.scope, `project-mark ${p.color}`].join(" ")}>{p.mark}</span><span>{p.title}</span><b>{p.score}%</b><ArrowRight size={14}/></Link>{premium ? <p>{p.reasons.join(' · ')}</p> : <Link className={[styles.scope, "matching-project-lock"].join(" ")} href="/subscription"><LockKeyhole size={11}/>상세 비교 이유 잠금</Link>}</article>)}</div></div></div>}
 </section>;
}
