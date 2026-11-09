'use client';
import { useEffect, useState } from 'react';
import { ExternalLink, Pencil } from 'lucide-react';
import { Button } from '@/components/common/Button';
import { fetchProfileLinks } from '@/lib/profiles/profileLinksApi';
import type { ProfileLinksDetail } from '@/types/profileLinks';
import { ApiProfileLinksEditor } from './ApiProfileLinksEditor';
import styles from './AboutTab.module.css';

type Result = { key: string; status: 'loading' } | { key: string; status: 'ready'; value: ProfileLinksDetail } | { key: string; status: 'error'; message: string };
export function ApiProfileLinks({ slug, canEdit }: { slug: string; canEdit: boolean }) {
    const key = `${canEdit ? 'own' : 'public'}:${slug}`;
    const [result, setResult] = useState<Result>({ key, status: 'loading' });
    const [attempt, setAttempt] = useState(0);
    const [editing, setEditing] = useState(false);
    useEffect(() => {
        const controller = new AbortController();
        setEditing(false); setResult({ key, status: 'loading' });
        void fetchProfileLinks(slug, canEdit, controller.signal).then(value => {
            if (!controller.signal.aborted) setResult({ key, status: 'ready', value });
        }).catch(error => {
            if (!controller.signal.aborted) setResult({ key, status: 'error', message: error instanceof Error ? error.message : '외부 링크를 불러오지 못했어요.' });
        });
        return () => controller.abort();
    }, [key, slug, canEdit, attempt]);
    const visible = result.key === key ? result : { key, status: 'loading' as const };
    const link = (label: string, url: string, id: string) => url ? <a key={id} href={url} target="_blank" rel="noopener noreferrer"><span className={styles.linkLabel}>{label}</span><span className={styles.linkUrl}>{url}</span><ExternalLink size={14} /></a> : <p key={id} className={styles.empty}>{label} 링크 미등록</p>;
    return <section className={`${styles.group} ${styles.links} profile-links`} aria-label="외부 링크">
        <div className={styles.groupHeading}><h2>외부 링크</h2>
            {canEdit && visible.status === 'ready' && <button className={styles.editSection} onClick={() => setEditing(true)} aria-label="외부 링크 수정"><Pencil size={14} />수정</button>}
        </div>
        {visible.status === 'loading' ? <p role="status">외부 링크를 불러오는 중…</p> : visible.status === 'error'
            ? <div role="alert"><p>{visible.message}</p><Button variant="secondary" onClick={() => setAttempt(v => v + 1)}>다시 시도</Button></div>
            : <>{link('GitHub', visible.value.github, 'github')}{link('블로그', visible.value.blog, 'blog')}{visible.value.externalLinks.map((item, index) => link(item.label, item.url, `custom:${index}`))}</>}
        {editing && canEdit && visible.status === 'ready' && <ApiProfileLinksEditor key={key} initial={visible.value} onClose={() => setEditing(false)} onSaved={value => setResult({ key, status: 'ready', value })} />}
    </section>;
}
