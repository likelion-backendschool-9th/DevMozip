'use client';
import { useRef, useState } from 'react';
import { Button } from '@/components/common/Button';
import { uploadPortfolioFile } from '@/lib/portfolios/portfolioFiles';
import type { PortfolioInput } from '@/types/profile';

export function PortfolioFileAttachments({ files, onChange, onBusy }: {
    files: NonNullable<PortfolioInput['attachments']>;
    onChange: (files: NonNullable<PortfolioInput['attachments']>) => void;
    onBusy: (busy: boolean) => void;
}) {
    const input = useRef<HTMLInputElement>(null); const lock = useRef(false);
    const [busy, setBusy] = useState(false); const [error, setError] = useState('');
    async function add(file?: File) {
        if (!file || lock.current) return;
        lock.current = true; setBusy(true); onBusy(true); setError('');
        try {
            const result = await uploadPortfolioFile(file, 'pdf');
            onChange([...files, { name: result.name, url: result.url }]);
        } catch (e) { setError(e instanceof Error ? e.message : '첨부파일을 업로드하지 못했어요.'); }
        finally { lock.current = false; setBusy(false); onBusy(false); }
    }
    return <section aria-label="포트폴리오 첨부파일">
        <h2>첨부파일</h2><p>PDF · 파일당 10MB 이하 · 최대 10개. 파일을 선택하면 서버에 업로드됩니다.</p>
        <input ref={input} type="file" hidden accept="application/pdf,.pdf" aria-label="PDF 첨부파일" disabled={busy || files.length >= 10}
            onChange={e => { const file = e.target.files?.[0]; e.target.value = ''; void add(file); }} />
        <Button variant="secondary" disabled={busy || files.length >= 10} onClick={() => input.current?.click()}>{busy ? '업로드 중…' : 'PDF 첨부'}</Button>
        {error && <p role="alert">{error}</p>}
        <ul>{files.map((file, index) => <li key={`${file.url}:${index}`}>
            <a href={file.url} target="_blank" rel="noopener noreferrer">{file.name || `첨부파일 ${index + 1}`}</a>{' '}
            <Button variant="secondary" disabled={busy} aria-label={`첨부파일 ${index + 1} 연결 해제`} onClick={() => onChange(files.filter((_, i) => i !== index))}>연결 해제</Button>
        </li>)}</ul>
        <p className="auth-hint">연결을 해제해도 서버 파일은 삭제하지 않습니다. 프로젝트 제출 자료 보존을 위해 파일 삭제는 별도 정책으로 처리합니다.</p>
    </section>;
}
