'use client';
import { useEffect, useRef, useState } from 'react';
import { useRouter } from 'next/navigation';
import { Modal } from '@/components/common/Modal';
import { Button } from '@/components/common/Button';
import { deletePortfolio } from '@/lib/portfolios/portfolioMutations';
export function PortfolioDeleteButton({ id, title, onDeleted }: { id: number; title: string; onDeleted?: () => void }) {
    const router = useRouter(); const [open, setOpen] = useState(false), [busy, setBusy] = useState(false), [error, setError] = useState('');
    const request = useRef<AbortController | null>(null);
    useEffect(() => () => request.current?.abort(), []);
    async function remove() {
        if (request.current) return;
        const controller = new AbortController(); request.current = controller; setBusy(true); setError('');
        try { await deletePortfolio(String(id), controller.signal); if (!controller.signal.aborted) { setOpen(false); if (onDeleted) onDeleted(); else router.push('/profile/me?tab=portfolio'); } }
        catch (e) { if (!controller.signal.aborted) setError(e instanceof Error ? e.message : '삭제하지 못했어요.'); }
        finally { request.current = null; if (!controller.signal.aborted) setBusy(false); }
    }
    return <><Button variant="secondary" onClick={() => { setError(''); setOpen(true); }}>삭제</Button>
        {open && <Modal title="포트폴리오를 삭제할까요?" onClose={() => { if (!busy) setOpen(false); }}>
            <p>{title}</p><p>삭제하면 복구할 수 없습니다. 지원서에 연결된 경우에는 정책 협의 전까지 삭제가 보류됩니다.</p>
            {error && <p role="alert">{error}</p>}<div className="modal-actions"><Button variant="secondary" disabled={busy} onClick={() => setOpen(false)}>취소</Button><Button disabled={busy} onClick={remove}>{busy ? '삭제 중…' : '삭제하기'}</Button></div>
        </Modal>}
    </>;
}
