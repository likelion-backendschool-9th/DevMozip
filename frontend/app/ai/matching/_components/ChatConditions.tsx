'use client';
import styles from './ChatConditions.module.css';
import { useState } from 'react';
import { chatCareerOptions, chatDurationOptions, chatFieldOptions, chatRoleOptions } from '@/mocks/matchingChat';
import { interestOptions, stackOptions } from '@/mocks/home';
import { useHome } from '@/lib/state/homeStore';
import { emptyChatRequirements } from '@/mocks/matchingChat';
import { missingChatRequirements } from '@/lib/matching/matchingChatService';
import { Input, Textarea } from '@/components/common/Input';
import { Button } from '@/components/common/Button';
export function ChatConditions({ onRecommend }: { onRecommend: () => void }) {
    const { conversation, editChat } = useHome();
    const r = conversation?.requirements || emptyChatRequirements;
    const [field, setField] = useState('');
    const [role, setRole] = useState('');
    const [otherField, setOtherField] = useState(false);
    const [otherRole, setOtherRole] = useState(false);
    const [error, setError] = useState('');
    const missing = missingChatRequirements(r);
    const tags = (key: 'fields' | 'interests' | 'stacks', options: string[]) => <div className={[styles.scope, "chips"].join(" ")}>{options.map(value => <button type="button" className={[styles.scope, `chip ${r[key].includes(value) ? 'selected' : ''}`].join(" ")} aria-pressed={r[key].includes(value)} key={value} onClick={() => editChat({ [key]: r[key].includes(value) ? r[key].filter(v => v !== value) : [...r[key], value] })}>{value}</button>)}</div>;
    return <details className={[styles.scope, "chat-conditions"].join(" ")}><summary>프로젝트 조건 설정 <span>{missing.length ? `${missing.length}개 항목 확인 필요` : '조건 준비 완료'}</span></summary><div className={[styles.scope, "chat-condition-body"].join(" ")}><p>여러 분야와 직군을 선택할 수 있어요. 선택/삭제만으로는 횟수가 차감되지 않습니다.</p><label>프로젝트 설명<Textarea rows={2} maxLength={1500} value={r.description} onChange={e => editChat({ description: e.target.value })}/></label><fieldset><legend>프로젝트 분야 · 복수 선택</legend>{tags('fields', chatFieldOptions.filter(v => v !== '기타'))}<button className={[styles.scope, "chat-other"].join(" ")} type="button" onClick={() => setOtherField(v => !v)}>기타 분야 직접 입력</button>{otherField && <div className={[styles.scope, "chat-custom"].join(" ")}><Input aria-label="기타 프로젝트 분야" maxLength={30} value={field} onChange={e => setField(e.target.value)}/><Button variant="secondary" onClick={() => { if (!field.trim() || r.fields.includes(field.trim()) || r.fields.length >= 12) {
        setError('중복 없는 분야를 입력해 주세요. 최대 12개입니다.');
        return;
    } editChat({ fields: [...r.fields, field.trim()] }); setField(''); setError(''); }}>분야 추가</Button></div>}<div className={[styles.scope, "chat-selected-tags"].join(" ")}>{r.fields.map(v => <button type="button" aria-label={`${v} 분야 삭제`} key={v} onClick={() => editChat({ fields: r.fields.filter(x => x !== v) })}>{v} ×</button>)}</div></fieldset><fieldset><legend>관심 분야 · 복수 선택</legend>{tags('interests', interestOptions)}</fieldset><fieldset><legend>기술 스택 · 복수 선택</legend>{tags('stacks', stackOptions)}</fieldset><fieldset><legend>모집 개발 직군 · 복수 선택</legend><div className={[styles.scope, "chips"].join(" ")}>{chatRoleOptions.filter(v => v !== '기타').map(name => <button type="button" key={name} className={[styles.scope, `chip ${r.roles.some(v => v.name === name) ? 'selected' : ''}`].join(" ")} aria-pressed={r.roles.some(v => v.name === name)} onClick={() => editChat({ roles: r.roles.some(v => v.name === name) ? r.roles.filter(v => v.name !== name) : [...r.roles, { name, count: 1 }] })}>{name}</button>)}</div><button className={[styles.scope, "chat-other"].join(" ")} type="button" onClick={() => setOtherRole(v => !v)}>기타 직군 직접 입력</button>{otherRole && <div className={[styles.scope, "chat-custom"].join(" ")}><Input aria-label="기타 모집 직군" value={role} maxLength={30} onChange={e => setRole(e.target.value)}/><Button variant="secondary" onClick={() => { if (!role.trim() || r.roles.some(v => v.name === role.trim()) || r.roles.length >= 12) {
        setError('중복 없는 직군을 입력해 주세요. 최대 12개입니다.');
        return;
    } editChat({ roles: [...r.roles, { name: role.trim(), count: 1 }] }); setRole(''); setError(''); }}>직군 추가</Button></div>}<div className={[styles.scope, "chat-role-counts"].join(" ")}>{r.roles.map(v => <div key={v.name}><label>{v.name}<Input type="number" aria-label={`${v.name} 모집 인원`} min={1} max={20} value={v.count || ''} onChange={e => editChat({ roles: r.roles.map(x => x.name === v.name ? { ...x, count: Math.max(1, Math.min(20, Number(e.target.value) || 1)) } : x) })}/>명</label><button type="button" aria-label={`${v.name} 직군 삭제`} onClick={() => editChat({ roles: r.roles.filter(x => x.name !== v.name) })}>×</button></div>)}</div></fieldset><div className={[styles.scope, "chat-condition-selects"].join(" ")}><label>요구 경력<select aria-label="요구 경력" value={r.career} onChange={e => editChat({ career: e.target.value })}><option value="">선택해 주세요</option>{chatCareerOptions.map(v => <option key={v}>{v}</option>)}</select></label><label>예상 프로젝트 기간<select aria-label="예상 프로젝트 기간" value={r.duration} onChange={e => editChat({ duration: e.target.value })}><option value="">선택해 주세요</option>{[...new Set([...chatDurationOptions, r.duration].filter(Boolean))].map(v => <option key={v}>{v}</option>)}</select></label></div>{error && <p role="alert" className={[styles.scope, "form-error"].join(" ")}>{error}</p>}<Button onClick={onRecommend}>조건으로 추천 받기</Button></div></details>;
}
