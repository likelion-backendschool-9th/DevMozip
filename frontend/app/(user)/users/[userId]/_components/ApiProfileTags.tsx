'use client';
import { useEffect, useState } from 'react';
import { Pencil } from 'lucide-react';
import { Button } from '@/components/common/Button';
import { fetchProfileTags } from '@/lib/profiles/profileTagsApi';
import type { ProfileTagsDetail } from '@/types/profileTags';
import { ApiProfileTagsEditor } from './ApiProfileTagsEditor';
import styles from './AboutTab.module.css';

type Result = { key: string; status: 'loading' } | { key: string; status: 'ready'; value: ProfileTagsDetail } | { key: string; status: 'error'; message: string };
export function ApiProfileTags({ slug, canEdit }: { slug: string; canEdit: boolean }) {
    const key = `${canEdit ? 'own' : 'public'}:${slug}`;
    const [result, setResult] = useState<Result>({ key, status: 'loading' });
    const [attempt, setAttempt] = useState(0);
    const [editing, setEditing] = useState(false);
    useEffect(() => {
        const controller = new AbortController();
        setEditing(false); setResult({ key, status: 'loading' });
        void fetchProfileTags(slug, canEdit, controller.signal).then(value => {
            if (!controller.signal.aborted) setResult({ key, status: 'ready', value });
        }).catch(error => {
            if (!controller.signal.aborted) setResult({ key, status: 'error', message: error instanceof Error ? error.message : '선택 정보를 불러오지 못했어요.' });
        });
        return () => controller.abort();
    }, [key, slug, canEdit, attempt]);
    const visible = result.key === key ? result : { key, status: 'loading' as const };
    return <>
        {visible.status === 'error' && <div role="alert"><p>{visible.message}</p><Button variant="secondary" onClick={() => setAttempt(v => v + 1)}>다시 시도</Button></div>}
        {(['skills', 'interests'] as const).map(field => <section key={field} className={styles.group} aria-label={field === 'skills' ? '기술 스택' : '관심 분야'}>
            <div className={styles.groupHeading}><h2>{field === 'skills' ? '기술 스택' : '관심 분야'}</h2>
                {canEdit && visible.status === 'ready' && <button className={styles.editSection} onClick={() => setEditing(true)} aria-label={`${field === 'skills' ? '기술 스택' : '관심 분야'} 수정`}><Pencil size={13} />수정</button>}
            </div>
            {visible.status === 'loading' ? <p role="status">불러오는 중…</p> : visible.status === 'ready'
                ? visible.value[field].length ? <div className={`profile-tags ${field === 'interests' ? 'cyan-tags' : ''}`}>{visible.value[field].map(item => <span key={item}>{item}</span>)}</div>
                    : <p className={styles.empty}>아직 등록된 {field === 'skills' ? '기술 스택이' : '관심 분야가'} 없어요.</p>
                : <p className={styles.empty}>정보를 불러오지 못했어요.</p>}
        </section>)}
        {editing && canEdit && visible.status === 'ready' && <ApiProfileTagsEditor key={key} initial={visible.value} onClose={() => setEditing(false)} onSaved={value => setResult({ key, status: 'ready', value })} />}
    </>;
}
