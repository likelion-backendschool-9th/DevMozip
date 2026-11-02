'use client';
import styles from './Toast.module.css';
import { useEffect } from 'react';
import { CheckCircle2, X } from 'lucide-react';
import { usePrototype } from '@/lib/state/PrototypeProvider';
export function Toast() {
 const { toast, notify } = usePrototype();
 useEffect(() => { if (!toast) return; const id = window.setTimeout(() => notify(''), 4500); return () => clearTimeout(id); }, [toast, notify]);
 return toast ? <div className={`${styles.toast} toast`} role="status"><CheckCircle2 size={18}/><span>{toast}</span><button className="icon-button" onClick={() => notify('')} aria-label="알림 닫기"><X size={16}/></button></div> : null;
}
