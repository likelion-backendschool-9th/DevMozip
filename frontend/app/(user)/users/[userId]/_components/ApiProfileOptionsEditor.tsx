'use client';
import { useEffect, useState } from 'react';
import { Modal } from '@/components/common/Modal';
import { Button } from '@/components/common/Button';
import { ProfileTagEditor } from '@/components/profile/ProfileTagEditor';
import { signupOptionsAction } from '@/lib/auth/actions';
import { notify } from '@/lib/state/prototypeStore';
import { parseProfileOptions, saveProfileOptions } from '@/lib/profiles/profileOptionsApi';
import type { ProfileOptionsDetail } from '@/types/profileOptions';
import styles from './ProfileEditor.module.css';

type Options = { skills: string[]; interests: string[] };
export function ApiProfileOptionsEditor({ initial, onClose, onSaved }: {
    initial: ProfileOptionsDetail; onClose: () => void; onSaved: (value: ProfileOptionsDetail) => void;
}) {
    const [value, setValue] = useState(initial);
    const [options, setOptions] = useState<Options | null>(null);
    const [optionsError, setOptionsError] = useState('');
    const [attempt, setAttempt] = useState(0);
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState('');
    useEffect(() => {
        let active = true;
        setOptions(null); setOptionsError('');
        void signupOptionsAction().then(result => {
            if (!active) return;
            if ('error' in result) setOptionsError(result.error);
            else setOptions(parseProfileOptions(result));
        }).catch(() => { if (active) setOptionsError('선택 목록을 불러오지 못했어요.'); });
        return () => { active = false; };
    }, [attempt]);
    return <Modal title="기술 스택·관심 분야 수정" className={`${styles.dialog} profile-dialog`} onClose={() => { if (!busy) onClose(); }}>
        <form className="profile-form" onSubmit={async event => {
            event.preventDefault();
            if (busy || !options) return;
            setBusy(true); setError('');
            try {
                const saved = await saveProfileOptions(value);
                onSaved(saved); onClose(); notify('기술 스택과 관심 분야를 저장했어요.');
            } catch (error) { setError(error instanceof Error ? error.message : '저장하지 못했어요.'); }
            finally { setBusy(false); }
        }}>
            {optionsError ? <div role="alert"><p>{optionsError}</p><Button type="button" variant="secondary" onClick={() => setAttempt(v => v + 1)}>다시 시도</Button></div>
                : !options ? <p role="status">선택 목록을 불러오는 중…</p>
                    : <fieldset disabled={busy} style={{ border: 0, padding: 0, margin: 0, minWidth: 0 }}>
                        <ProfileTagEditor label="기술 스택" options={options.skills} value={value.skills} allowCustom={false} onChange={skills => { setValue(v => ({ ...v, skills })); setError(''); }} />
                        <ProfileTagEditor label="관심 분야" options={options.interests} value={value.interests} allowCustom={false} onChange={interests => { setValue(v => ({ ...v, interests })); setError(''); }} />
                    </fieldset>}
            <p className="auth-hint">회원가입과 같은 선택 목록을 사용해요. 모두 해제하고 저장하면 해당 정보가 삭제됩니다.</p>
            {error && <p role="alert" className="form-error">{error}</p>}
            <div className="modal-actions"><Button type="button" variant="secondary" disabled={busy} onClick={onClose}>취소</Button><Button type="submit" disabled={busy || !options}>{busy ? '저장 중…' : '저장하기'}</Button></div>
        </form>
    </Modal>;
}
