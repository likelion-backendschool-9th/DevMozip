'use client';
import { useEffect, useState } from 'react';
import { Pencil } from 'lucide-react';
import { Button } from '@/components/common/Button';
import { fetchIntroduction } from '@/lib/profiles/profileIntroductionApi';
import type { ProfileIntroductionDetail } from '@/types/profileIntroduction';
import { ProfileIntroduction } from './ProfileIntroduction';
import { ApiIntroductionEditor } from './ApiIntroductionEditor';
import styles from './AboutTab.module.css';
type Result = { key: string; status: 'loading' } | { key: string; status: 'ready'; value: ProfileIntroductionDetail } | { key: string; status: 'error'; message: string };

export function ApiProfileIntroduction({ nickname, slug, canEdit }: { nickname: string; slug: string; canEdit: boolean }) {
    const key = `${canEdit ? 'own' : 'public'}:${slug}`;
    const [result, setResult] = useState<Result>({ key, status: 'loading' });
    const [attempt, setAttempt] = useState(0);
    const [editing, setEditing] = useState(false);
    useEffect(() => {
        const controller = new AbortController();
        setEditing(false); setResult({ key, status: 'loading' });
        void fetchIntroduction(slug, canEdit, controller.signal).then(value => {
            if (!controller.signal.aborted) setResult({ key, status: 'ready', value });
        }).catch(error => {
            if (!controller.signal.aborted) setResult({ key, status: 'error', message: error instanceof Error ? error.message : '자기소개를 불러오지 못했어요.' });
        });
        return () => controller.abort();
    }, [key, slug, canEdit, attempt]);
    const visible = result.key === key ? result : { key, status: 'loading' as const };
    return <>
        <div className={`${styles.title} tab-section-title`}><div><span className={styles.eyebrow}>ABOUT ME</span><h2>반가워요, {nickname}입니다.</h2></div>
            {canEdit && visible.status === 'ready' && <Button variant="secondary" onClick={() => setEditing(true)}><Pencil size={13} />수정</Button>}
        </div>
        {visible.status === 'loading' ? <p role="status">자기소개를 불러오고 있어요…</p>
            : visible.status === 'error' ? <div role="alert"><p>{visible.message}</p><Button variant="secondary" onClick={() => setAttempt(v => v + 1)}>다시 시도</Button></div>
                : <ProfileIntroduction value={visible.value.introduction || ''} format={visible.value.format} />}
        {editing && canEdit && visible.status === 'ready' && <ApiIntroductionEditor initial={visible.value} onClose={() => setEditing(false)} onSaved={value => setResult({ key, status: 'ready', value })} />}
    </>;
}
