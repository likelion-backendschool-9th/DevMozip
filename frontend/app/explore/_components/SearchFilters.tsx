'use client';
import {SearchMultiSelect} from './SearchMultiSelect';
import {useProfiles} from '@/lib/state/profileStore';
import {useProjects} from '@/lib/state/projectStore';
import styles from './SearchFilters.module.css';
import type { SearchFilters as FilterValues } from '@/types/search';
import { careerOptions, jobOptions } from '@/mocks/auth';
import { interestOptions, stackOptions } from '@/mocks/home';
import { availabilityOptions, projectFieldOptions, projectRoleOptions, projectStatusOptions } from '@/mocks/search';
import { SlidersHorizontal, RotateCcw } from 'lucide-react';
import { activeFilterCount } from '@/lib/search/searchService';
export function SearchFilters({ filters, onChange, onReset }: {
    filters: FilterValues;
    onChange: (key: string, value: string | string[]) => void;
    onReset: () => void;
}) { const {state}=useProfiles();const {projects}=useProjects();const stacks=[...new Set([...stackOptions,...(filters.target==='developer'?Object.values(state.profiles).flatMap(p=>p.stacks):projects.flatMap(p=>p.stacks))])];const fields=[...new Set([...(filters.target==='developer'?interestOptions.slice(1):projectFieldOptions),...(filters.target==='developer'?Object.values(state.profiles).flatMap(p=>p.interests):projects.map(p=>p.field))])]; const count = activeFilterCount(filters); const select = (key: keyof FilterValues, label: string, options: string[]) => <label className={[styles.scope, "search-filter-field"].join(" ")}>{label}<select value={String(filters[key])} className={[styles.scope, "form-input"].join(" ")} onChange={e => onChange(key, e.target.value)}><option value="">전체</option>{options.map(v => <option key={v}>{v}</option>)}</select></label>; return <aside className={[styles.scope, "search-filter-panel"].join(" ")} aria-label="검색 필터"><div className={[styles.scope, "search-filter-heading"].join(" ")}><strong><SlidersHorizontal size={15}/>상세 필터 {count > 0 && <span>{count}</span>}</strong><button onClick={onReset}><RotateCcw size={12}/>초기화</button></div>{filters.target === 'developer' ? <>{select('job', '개발 직군', jobOptions)}{select('career', '경력', careerOptions)}<label className={[styles.scope, "search-filter-field"].join(" ")}>협업 가능 여부<select className={[styles.scope, "form-input"].join(" ")} value={filters.available} onChange={e => onChange('available', e.target.value)}>{availabilityOptions.map(o => <option key={o.value} value={o.value}>{o.label}</option>)}</select></label></> : <>{select('role', '모집 직군', projectRoleOptions)}{select('status', '모집 상태', projectStatusOptions)}</>}<SearchMultiSelect key={`${filters.target}-fields`} label={filters.target==='developer'?'관심 분야':'프로젝트 분야'} options={fields} value={filters.target==='developer'?(filters.interests||[]):(filters.fields||[])} onChange={values=>onChange(filters.target==='developer'?'interest':'field',values)}/><SearchMultiSelect key={`${filters.target}-stacks`} label="기술 스택" options={stacks} value={filters.stacks} onChange={values=>onChange('stack',values)}/><p className={[styles.scope, "search-filter-note"].join(" ")}>분야는 하나 이상 일치, 기술 스택은 모두 포함하는 결과를 보여드립니다.</p></aside>; }
