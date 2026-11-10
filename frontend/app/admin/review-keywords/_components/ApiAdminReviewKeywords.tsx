'use client';
import { useState, type FormEvent } from 'react';
import { AdminActionError, AdminLoadView, cls } from '../../_components/AdminParts';
import { Button } from '@/components/common/Button';
import { adminJson, adminRequest } from '@/lib/administration/adminApi';
import { activeNames, KEYWORD_MAX, keywordName, parseAdminKeywords } from '@/lib/administration/adminKeywords';
import { useAdminAction, useAdminResource } from '@/lib/administration/useAdminResource';
import type { AdminReviewKeyword } from '@/types/adminApi';

const parse = (raw: unknown) => parseAdminKeywords(raw);

/**
 * Mock 토글 OFF의 리뷰 키워드 관리 (WBS 14.9). 추가·삭제는 화면에서 모았다가 저장할 때 활성 목록 전체를 보낸다.
 * 삭제는 새 리뷰의 선택지에서만 빠지며, 같은 이름을 다시 추가하면 그 키워드가 다시 선택지가 된다.
 */
export function ApiAdminReviewKeywords() {
    const { load, reload } = useAdminResource('/review-keywords', parse);
    const [draft, setDraft] = useState<string[] | null>(null);
    const [input, setInput] = useState('');
    const [inputError, setInputError] = useState('');
    const action = useAdminAction();
    const saved = load.status === 'ready' ? activeNames(load.data) : [];
    const current = draft ?? saved;

    const add = (event: FormEvent) => {
        event.preventDefault();
        const name = keywordName(input);
        if (!name) { setInputError(`키워드는 1~${KEYWORD_MAX}자로 입력해 주세요.`); return; }
        if (current.includes(name)) { setInputError('이미 선택지에 있는 키워드예요.'); return; }
        setDraft([...current, name]);
        setInput('');
        setInputError('');
    };
    const save = async () => {
        const done = await action.run(() => adminRequest('/review-keywords', adminJson('PUT', { keywords: current })));
        if (done) { setDraft(null); reload(); }
    };

    return (
        <section className={cls('governance-panel admin-main')} aria-label="리뷰 키워드 관리">
            <h2>리뷰 키워드 관리</h2>
            <p>삭제하면 새 리뷰의 선택지에서 제외됩니다. 이미 작성된 리뷰와 버블 통계는 유지합니다. 바꾼 내용은 저장해야 반영됩니다.</p>
            <AdminLoadView load={load} onRetry={reload} loadingText="키워드를 불러오고 있어요.">
                {keywords => (
                    <>
                        <form className={cls('governance-actions')} onSubmit={add}>
                            <label>
                                새 키워드
                                <input className="form-input" maxLength={KEYWORD_MAX} value={input} onChange={e => setInput(e.target.value)} />
                            </label>
                            <Button type="submit" variant="secondary">추가</Button>
                        </form>
                        {inputError && <p role="alert">{inputError}</p>}
                        <KeywordListView keywords={keywords} names={current} onRemove={name => setDraft(current.filter(n => n !== name))} />
                        <AdminActionError message={action.error} />
                        <div className={cls('governance-actions')}>
                            <Button disabled={draft === null || action.busy} onClick={save}>{action.busy ? '저장 중…' : '변경 저장'}</Button>
                            <Button variant="secondary" disabled={draft === null || action.busy} onClick={() => { setDraft(null); action.clearError(); }}>되돌리기</Button>
                        </div>
                    </>
                )}
            </AdminLoadView>
        </section>
    );
}

/** 저장 전 선택지 목록. 서버에 없는 새 키워드와 비활성에서 되살린 키워드를 표시한다. */
export function KeywordListView({ keywords, names, onRemove }: { keywords: AdminReviewKeyword[]; names: string[]; onRemove: (name: string) => void }) {
    if (!names.length) return <p>선택지가 비어 있습니다. 키워드는 리뷰의 필수 항목이 아니라 비워 둘 수 있습니다.</p>;
    return (
        <>
            {names.map(name => {
                const saved = keywords.find(k => k.name === name);
                const note = !saved ? '새 키워드' : !saved.active ? '다시 추가' : '';
                return (
                    <div className={cls('admin-row')} key={name}>
                        <span>{name}{note && <small>{note} · 저장 전</small>}</span>
                        <Button variant="secondary" onClick={() => onRemove(name)}>선택지에서 삭제</Button>
                    </div>
                );
            })}
        </>
    );
}
