'use client';
import { useState } from 'react';
import { Modal } from '@/components/common/Modal';
import { Button } from '@/components/common/Button';
import { FEED_REPORT_REASONS, FeedRequestError, reportFeedComment, reportFeedPost } from '@/lib/social/feedApi';

/** 게시글(8.15)·댓글(8.16) 신고 창. 이미 신고했다는 응답(409)도 신고된 상태로 맞춘다. */
export function FeedReportDialog({ targetType, targetId, onClose, onReported }: {
    targetType: 'post' | 'comment';
    targetId: number;
    onClose: () => void;
    onReported: () => void;
}) {
    const [reason, setReason] = useState<string>(FEED_REPORT_REASONS[0]);
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState('');
    async function submit() {
        if (busy) return;
        setBusy(true);
        setError('');
        try {
            await (targetType === 'post' ? reportFeedPost(targetId, reason) : reportFeedComment(targetId, reason));
            onReported();
            onClose();
        } catch (failure) {
            if (failure instanceof FeedRequestError && failure.status === 409) onReported();
            setError(failure instanceof Error ? failure.message : '신고하지 못했어요.');
        } finally {
            setBusy(false);
        }
    }
    return <Modal title={targetType === 'post' ? '게시글 신고' : '댓글 신고'} onClose={onClose}>
        <form className="profile-form" onSubmit={event => { event.preventDefault(); void submit(); }}>
            <label>신고 사유<select className="form-input" value={reason} disabled={busy} onChange={event => setReason(event.target.value)}>
                {FEED_REPORT_REASONS.map(item => <option key={item}>{item}</option>)}
            </select></label>
            <p className="auth-hint">같은 계정은 같은 내용을 한 번만 신고할 수 있습니다. 접수된 신고는 관리자가 확인합니다.</p>
            {error && <p className="form-error" role="alert">{error}</p>}
            <div className="modal-actions">
                <Button variant="secondary" type="button" disabled={busy} onClick={onClose}>취소</Button>
                <Button type="submit" disabled={busy}>{busy ? '신고하는 중…' : '신고하기'}</Button>
            </div>
        </form>
    </Modal>;
}
