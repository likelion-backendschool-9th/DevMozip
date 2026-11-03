'use client';
import styles from './AdvancedSearch.module.css';
import Link from 'next/link';
import { useAuth } from '@/lib/state/authStore';
export function AdvancedSearch({ minimum, sort, onChange }: {
    minimum: number;
    sort: boolean;
    onChange: (minimum: number, sort: boolean) => void;
}) { const { user } = useAuth(); const enabled = !!user && user.role !== 'free'; return <section className={[styles.scope, "advanced-search"].join(" ")}><strong>고급 검색 <span className={[styles.scope, "premium-label"].join(" ")}>PRO</span></strong>{enabled ? <><label>최소 신뢰 온도<select className={[styles.scope, "form-input"].join(" ")} value={minimum} onChange={e => onChange(Number(e.target.value), sort)}><option value={0}>전체</option><option value={36.5}>36.5°C 이상</option><option value={38}>38°C 이상</option><option value={40}>40°C 이상</option></select></label><label><input type="checkbox" checked={sort} onChange={e => onChange(minimum, e.target.checked)}/> 신뢰 온도 높은 순</label></> : <p>신뢰 온도 조건과 정렬은 프리미엄에서 제공합니다. <Link href="/subscription">요금제 보기 →</Link></p>}</section>; }
