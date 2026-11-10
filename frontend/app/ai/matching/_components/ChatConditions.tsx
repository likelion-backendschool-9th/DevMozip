'use client';
import { useId, useRef, useState } from 'react';
import { ArrowRight, Check, SlidersHorizontal, Sparkles, X } from 'lucide-react';
import { chatCareerOptions, chatDurationOptions, chatInterestOptions, chatRequirementLabels, chatRoleOptions, emptyChatRequirements } from '@/mocks/matchingChat';
import { stackOptions } from '@/mocks/home';
import { useHome } from '@/lib/state/homeStore';
import { useAuth } from '@/lib/state/authStore';
import { addChatStack, missingChatRequirements } from '@/lib/matching/matchingChatService';
import styles from './ChatConditions.module.css';
export function ChatConditions({ onRecommend, onClose, busy = false, retry = false, signedIn = true }: {
    onRecommend: () => void; onClose?: () => void; busy?: boolean; retry?: boolean; signedIn?: boolean;
}) {
    const { conversation, editChat, recommendationUnchanged } = useHome();
    const { user } = useAuth();
    const r = conversation?.requirements || emptyChatRequirements;
    const [stack, setStack] = useState('');
    const [role, setRole] = useState('');
    const [error, setError] = useState('');
    const [showMissing, setShowMissing] = useState(false);
    const root = useRef<HTMLDivElement>(null);
    const errorId = useId();
    const missing = missingChatRequirements(r);
    const message = error || (showMissing && !retry && missing.length ? `${missing.map(k => chatRequirementLabels[k]).join('·')} 항목을 선택해 주세요.` : '');
    const limit = user?.role === 'premium' || user?.role === 'admin' ? 100 : 5;
    const tags = (key: 'interests' | 'stacks', options: string[]) => <div className={styles.chips}>{options.map(value => <button type="button" key={value} aria-pressed={r[key].includes(value)} onClick={() => {
        try { editChat({ [key]: r[key].includes(value) ? r[key].filter(v => v !== value) : key === 'stacks' ? addChatStack(r.stacks, value) : [...r[key], value] }); setError(''); }
        catch (e) { setError(e instanceof Error ? e.message : '조건을 확인해 주세요.'); }
    }}>{r[key].includes(value) && <Check size={11} />}{value}</button>)}</div>;
    const status = (values: string[]) => values.length ? values.join(', ') : '미설정';
    const recommend = () => {
        if (!signedIn) { onRecommend(); return; }
        if (recommendationUnchanged) return;
        setShowMissing(true); setError('');
        if (missing.length && !retry) {
            const target = root.current?.querySelector<HTMLElement>(`[data-condition="${missing[0]}"]`);
            target?.scrollIntoView({ block: 'nearest', behavior: 'smooth' });
            target?.querySelector<HTMLElement>('button,select')?.focus({ preventScroll: true });
            return;
        }
        onRecommend();
    };
    return <div className={styles.panel} ref={root}>
        <header className={styles.header}><div><SlidersHorizontal size={17} /><strong>추천 조건</strong></div>{onClose && <button type="button" aria-label="추천 조건 패널 접기" onClick={onClose}><X size={17} /></button>}</header>
        <div className={styles.intro}><span className={styles.badge}>{missing.length ? `${missing.length}개 항목 확인 필요` : '조건 준비 완료'}</span><p>아이디어에 맞게 조건을 정리해 보세요.<br />선택한 조건으로 동료와 프로젝트를 찾아요.</p></div>
        <fieldset disabled={busy || !signedIn} className={styles.body}>
            <div className={styles.group} data-condition="interests">
                <label>관심 분야 <span className={styles.required}>(필수)</span></label><small className={styles.value}>{status(r.interests)}</small>{tags('interests', chatInterestOptions)}
                <small className={styles.help}>관심 분야를 하나 이상 선택해 주세요.</small>
            </div>
            <details className={styles.group}>
                <summary>기술 스택 <span>{r.stacks.length || '선택'}</span></summary><small className={styles.value}>{status(r.stacks)}</small>{tags('stacks', [...new Set([...stackOptions, ...r.stacks])])}
                <div className={styles.custom}><input aria-label="기술 스택 직접 입력" placeholder="기술 직접 입력" maxLength={50} value={stack} onChange={e => setStack(e.target.value)} onKeyDown={e => { if (e.key === 'Enter' && !e.nativeEvent.isComposing) { e.preventDefault(); try { editChat({ stacks: addChatStack(r.stacks, stack) }); setStack(''); setError(''); } catch (e) { setError(e instanceof Error ? e.message : '입력을 확인해 주세요.'); } } }} /><button type="button" onClick={() => { try { editChat({ stacks: addChatStack(r.stacks, stack) }); setStack(''); setError(''); } catch (e) { setError(e instanceof Error ? e.message : '입력을 확인해 주세요.'); } }}>기술 추가</button></div>
            </details>
            <details className={styles.group}>
                <summary>모집 직군 <span>{r.roles.length || '선택'}</span></summary><small className={styles.value}>{status(r.roles.map(v => `${v.name} ${v.count}명`))}</small>
                <div className={styles.chips}>{chatRoleOptions.filter(v => v !== '기타').map(name => <button type="button" key={name} aria-pressed={r.roles.some(v => v.name === name)} onClick={() => {
                    if (!r.roles.some(v => v.name === name) && r.roles.length >= 12) { setError('모집 직군은 최대 12개입니다.'); return; }
                    editChat({ roles: r.roles.some(v => v.name === name) ? r.roles.filter(v => v.name !== name) : [...r.roles, { name, count: 1 }] });
                }}>{name}</button>)}</div>
                <div className={styles.custom}><input aria-label="기타 모집 직군" placeholder="기타 직군 직접 입력" maxLength={30} value={role} onChange={e => setRole(e.target.value)} /><button type="button" onClick={() => {
                    if (!role.trim() || r.roles.some(v => v.name === role.trim()) || r.roles.length >= 12) { setError('중복 없는 직군을 입력해 주세요. 최대 12개입니다.'); return; }
                    editChat({ roles: [...r.roles, { name: role.trim(), count: 1 }] }); setRole(''); setError('');
                }}>직군 추가</button></div>
                {r.roles.map(v => <div key={v.name} className={styles.count}><label>{v.name}<input type="number" aria-label={`${v.name} 모집 인원`} min={1} max={20} value={v.count} onChange={e => editChat({ roles: r.roles.map(x => x.name === v.name ? { ...x, count: Math.max(1, Math.min(20, Number(e.target.value) || 1)) } : x) })} />명</label><button type="button" aria-label={`${v.name} 직군 삭제`} onClick={() => editChat({ roles: r.roles.filter(x => x.name !== v.name) })}><X size={13} /></button></div>)}
            </details>
            <div className={styles.group} data-condition="career"><label htmlFor={`${errorId}-career`}>요구 경력 <span className={styles.required}>(필수)</span></label><select id={`${errorId}-career`} aria-label="요구 경력" aria-invalid={showMissing && missing.includes('career')} value={r.career} onChange={e => editChat({ career: e.target.value })}><option value="">미설정</option>{chatCareerOptions.map(v => <option key={v}>{v}</option>)}</select></div>
            <div className={styles.group} data-condition="duration"><label htmlFor={`${errorId}-duration`}>예상 프로젝트 기간 <span className={styles.required}>(필수)</span></label><select id={`${errorId}-duration`} aria-label="예상 프로젝트 기간" aria-invalid={showMissing && missing.includes('duration')} value={r.duration} onChange={e => editChat({ duration: e.target.value })}><option value="">미설정</option>{[...new Set([...chatDurationOptions, r.duration].filter(Boolean))].map(v => <option key={v}>{v}</option>)}</select></div>
            {message && <p id={errorId} role="alert" className={styles.error}>{message}</p>}
        </fieldset>
        <footer className={styles.footer}>
            <div className={styles.usage}><span>월간 추천</span><strong>— <small>/ {limit}회</small></strong></div><p>서버 사용량 집계 준비 중 · 현재 차감되지 않습니다.</p>
            <button type="button" className={styles.recommend} disabled={busy || recommendationUnchanged} onClick={recommend}><Sparkles size={16} />{busy ? '추천 처리 중…' : retry ? '이전 추천 확인/재시도' : '조건으로 추천 받기'}<ArrowRight size={15} /></button>
            <small>{recommendationUnchanged ? '추천 조건을 변경하면 다시 추천받을 수 있어요.' : signedIn ? '대화·조건 변경은 추천 횟수에 영향을 주지 않아요.' : '로그인 후 추천을 실행할 수 있어요.'}</small>
        </footer>
    </div>;
}
