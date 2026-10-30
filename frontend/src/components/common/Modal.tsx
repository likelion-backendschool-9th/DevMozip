import { useEffect, useId, useRef, type ReactNode } from 'react';
import { X } from 'lucide-react';
export function Modal({ title, children, onClose, className = '' }: { className?: string; title: string; children: ReactNode; onClose: () => void }) {
 const titleId = useId();
 const ref = useRef<HTMLDialogElement>(null);
 useEffect(() => { const el = ref.current; el?.showModal(); return () => { el?.close(); }; }, []);
 return <dialog className={className} ref={ref} onCancel={onClose} onClick={e => { if (e.target === e.currentTarget) onClose(); }} aria-labelledby={titleId}><div className="modal-head"><h2 id={titleId}>{title}</h2><button className="icon-button" aria-label="닫기" onClick={onClose}><X size={20}/></button></div>{children}</dialog>;
}
