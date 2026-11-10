'use client';
import {SearchMultiSelect} from './SearchMultiSelect';
import styles from './SearchFilters.module.css';
import type { SearchFilters as FilterValues } from '@/types/search';
import type { SearchFilterChoices } from '@/types/searchApi';
import { availabilityOptions } from '@/mocks/search';
import { SlidersHorizontal, RotateCcw } from 'lucide-react';
import { activeFilterCount } from '@/lib/search/searchService';
// 선택지는 페이지가 넘긴다. Mock 켜기는 Mock 저장소(mockFilterChoices), Mock 끄기는 백엔드 enum(apiFilterChoices)에서 만든다.
export function SearchFilters({ filters, choices, onChange, onReset }: {
    filters: FilterValues;
    choices: SearchFilterChoices;
    onChange: (key: string, value: string | string[]) => void;
    onReset: () => void;
}) { const count = activeFilterCount(filters); const select = (key: keyof FilterValues, label: string, options: string[]) => { const value = String(filters[key]); const list = value && !options.includes(value) ? [...options, value] : options; return <label className={[styles.scope, "search-filter-field"].join(" ")}>{label}<select value={value} className={[styles.scope, "form-input"].join(" ")} onChange={e => onChange(key, e.target.value)}><option value="">전체</option>{list.map(v => <option key={v}>{v}</option>)}</select></label>; }; return <aside className={[styles.scope, "search-filter-panel"].join(" ")} aria-label="검색 필터"><div className={[styles.scope, "search-filter-heading"].join(" ")}><strong><SlidersHorizontal size={15}/>상세 필터 {count > 0 && <span>{count}</span>}</strong><button onClick={onReset}><RotateCcw size={12}/>초기화</button></div>{select('job', '개발 직군', choices.jobs)}{select('career', '경력', choices.careers)}<label className={[styles.scope, "search-filter-field"].join(" ")}>협업 가능 여부<select className={[styles.scope, "form-input"].join(" ")} value={filters.available} onChange={e => onChange('available', e.target.value)}>{availabilityOptions.map(o => <option key={o.value} value={o.value}>{o.label}</option>)}</select></label><SearchMultiSelect label="관심 분야" options={choices.fields} value={filters.interests||[]} onChange={values=>onChange('interest',values)}/><SearchMultiSelect label="기술 스택" options={choices.stacks} value={filters.stacks} onChange={values=>onChange('stack',values)}/><p className={[styles.scope, "search-filter-note"].join(" ")}>분야는 하나 이상 일치, 기술 스택은 모두 포함하는 결과를 보여드립니다.</p></aside>; }
