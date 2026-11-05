'use client';
import { useState } from 'react';
import { Modal } from '@/components/common/Modal';
import { Button } from '@/components/common/Button';
import { notify } from '@/lib/state/prototypeStore';
import { saveIntroduction } from '@/lib/profiles/profileIntroductionApi';
import type { ProfileIntroductionDetail } from '@/types/profileIntroduction';
import { IntroductionEditor } from './IntroductionEditor';
import styles from './ProfileEditor.module.css';

export function ApiIntroductionEditor({ initial, onClose, onSaved }: {
    initial: ProfileIntroductionDetail; onClose: () => void; onSaved: (value: ProfileIntroductionDetail) => void;
}) {
    const [value, setValue] = useState(initial.introduction || '');
    const [format, setFormat] = useState(initial.format);
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState('');
    return <Modal title="상세 소개 수정" className={`${styles.dialog} profile-dialog`} onClose={() => { if (!busy) onClose(); }}>
        <form className="profile-form" onSubmit={async event => {
            event.preventDefault();
            if (busy) return;
            setBusy(true); setError('');
            try {
                const saved = await saveIntroduction(value, format);
                onSaved(saved); onClose(); notify('상세 자기소개를 저장했어요.');
            } catch (error) { setError(error instanceof Error ? error.message : '저장하지 못했어요.'); }
            finally { setBusy(false); }
        }}>
            <fieldset disabled={busy} style={{ border: 0, padding: 0, margin: 0, minWidth: 0 }}>
                <IntroductionEditor value={value} format={format} onChange={(value, format) => { setValue(value); setFormat(format); setError(''); }} />
            </fieldset>
            <p className="auth-hint">내용을 비우고 저장하면 상세 자기소개가 삭제돼요. HTML은 허용된 서식만 저장됩니다.</p>
            {error && <p role="alert" className="form-error">{error}</p>}
            <div className="modal-actions"><Button type="button" variant="secondary" disabled={busy} onClick={onClose}>취소</Button><Button type="submit" disabled={busy}>{busy ? '저장 중…' : '저장하기'}</Button></div>
        </form>
    </Modal>;
}
