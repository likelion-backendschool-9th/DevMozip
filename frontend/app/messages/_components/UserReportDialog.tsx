'use client';
import { useState, type FormEvent } from 'react';
import { useProfiles } from '@/lib/state/profileStore';
import { useMessaging } from '@/lib/state/messagingStore';
import { userReportReasons } from '@/mocks/messaging';
import { Modal } from '@/components/common/Modal';
import { Button } from '@/components/common/Button';

/** 토글 OFF의 신고. 서버로 제출하고 결과를 돌려준다. */
export interface ServerUserReport {
    name: string;
    onSubmit: (reason: string) => Promise<{ ok: boolean; error?: string }>;
}

/** server를 넘기면 서버로 제출하고, 없으면 targetId로 Mock에 기록한다. */
export function UserReportDialog({ targetId, server, onClose }: { targetId?: string; server?: ServerUserReport; onClose: () => void }) {
    const { act } = useMessaging();
    const { state } = useProfiles();
    const [reason, setReason] = useState(userReportReasons[0]);
    const [error, setError] = useState('');
    const [pending, setPending] = useState(false);

    async function submit(event: FormEvent) {
        event.preventDefault();
        if (pending) return;
        setError('');
        setPending(true);
        const result = server ? await server.onSubmit(reason) : act({ type: 'report-user', targetId: targetId ?? '', reason });
        setPending(false);
        if (result.ok) onClose();
        else setError(result.error || '신고하지 못했습니다.');
    }

    return <Modal title="사용자 신고" onClose={onClose}>
        <form className="profile-form" onSubmit={submit}>
            <p>신고 대상: <strong>{server ? server.name : state.profiles[targetId ?? '']?.nickname || '사용자'}</strong></p>
            <label>신고 사유<select className="form-input" value={reason} onChange={e => setReason(e.target.value)}>{userReportReasons.map(r => <option key={r}>{r}</option>)}</select></label>
            <p className="auth-hint">{server ? '계정당 사용자별로 한 번 신고할 수 있습니다.' : '계정당 사용자별 1회 신고하는 임시 Mock 정책입니다. 실제 관리자 서버에는 전송하지 않습니다.'}</p>
            {error && <p role="alert" className="form-error">{error}</p>}
            <div className="modal-actions">
                <Button type="button" variant="secondary" onClick={onClose}>취소</Button>
                <Button type="submit" disabled={pending}>{server ? '신고하기' : '신고 기록'}</Button>
            </div>
        </form>
    </Modal>;
}
