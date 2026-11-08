'use client';
import { useState } from 'react';
import { Plus, X } from 'lucide-react';
import { Modal } from '@/components/common/Modal';
import { Input } from '@/components/common/Input';
import { Button } from '@/components/common/Button';
import { notify } from '@/lib/state/prototypeStore';
import { saveProfileLinks } from '@/lib/profiles/profileLinksApi';
import type { ProfileLinksDetail } from '@/types/profileLinks';
import styles from './ProfileEditor.module.css';

export function ApiProfileLinksEditor({ initial, onClose, onSaved }: {
    initial: ProfileLinksDetail; onClose: () => void; onSaved: (value: ProfileLinksDetail) => void;
}) {
    const [value, setValue] = useState(() => ({ ...initial, externalLinks: initial.externalLinks.map(link => ({ ...link })) }));
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState('');
    function change(next: ProfileLinksDetail) { setValue(next); setError(''); }
    return <Modal title="외부 링크 수정" className={`${styles.dialog} profile-dialog`} onClose={() => { if (!busy) onClose(); }}>
        <form className="profile-form" onSubmit={async event => {
            event.preventDefault();
            if (busy) return;
            setBusy(true); setError('');
            try {
                const saved = await saveProfileLinks(value);
                onSaved(saved); onClose(); notify('외부 링크를 저장했어요.');
            } catch (error) { setError(error instanceof Error ? error.message : '저장하지 못했어요.'); }
            finally { setBusy(false); }
        }}>
            <fieldset disabled={busy} style={{ border: 0, padding: 0, margin: 0, minWidth: 0 }}>
                <label>GitHub URL<Input type="url" maxLength={500} value={value.github} onChange={e => change({ ...value, github: e.target.value })} placeholder="https://github.com/..." /></label>
                <label>블로그 URL<Input type="url" maxLength={500} value={value.blog} onChange={e => change({ ...value, blog: e.target.value })} placeholder="https://..." /></label>
                <fieldset className={styles.linkFieldset}><legend><span>추가 외부 링크</span><button type="button" className={styles.addButton} aria-label="외부 링크 추가" disabled={value.externalLinks.length >= 10} onClick={() => change({ ...value, externalLinks: [...value.externalLinks, { label: '', url: '' }] })}><Plus size={18} /></button></legend>
                    {value.externalLinks.map((link, index) => <div className={styles.linkRow} key={index}>
                        <label>링크 이름 {index + 1}<Input required maxLength={40} value={link.label} onChange={e => change({ ...value, externalLinks: value.externalLinks.map((item, i) => i === index ? { ...item, label: e.target.value } : item) })} /></label>
                        <label>링크 URL {index + 1}<Input required type="url" maxLength={500} value={link.url} placeholder="https://..." onChange={e => change({ ...value, externalLinks: value.externalLinks.map((item, i) => i === index ? { ...item, url: e.target.value } : item) })} /></label>
                        <button type="button" className={styles.removeButton} aria-label={`추가 링크 ${index + 1} 삭제`} onClick={() => change({ ...value, externalLinks: value.externalLinks.filter((_, i) => i !== index) })}><X size={17} /></button>
                    </div>)}
                </fieldset>
            </fieldset>
            <p className="auth-hint">http·https 주소만 등록할 수 있어요. 추가 링크는 최대 10개이며, GitHub·블로그는 비워서 저장하면 삭제됩니다.</p>
            {error && <p role="alert" className="form-error">{error}</p>}
            <div className="modal-actions"><Button type="button" variant="secondary" disabled={busy} onClick={onClose}>취소</Button><Button type="submit" disabled={busy}>{busy ? '저장 중…' : '저장하기'}</Button></div>
        </form>
    </Modal>;
}
