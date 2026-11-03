'use client';
import styles from './SearchBar.module.css';
import { useId, useState } from 'react';
import { useNavigation as useNavigate, useCurrentLocation as useLocation } from '@/lib/navigation';
import { Search } from 'lucide-react';
import { parseSearch, changeSearchTarget } from '@/lib/search/searchService';
import { searchTargetOptions } from '@/mocks/search';
import type { SearchTarget } from '@/types/search';
import { Input } from '@/components/common/Input';
export function SearchBar({ variant = 'header' }: {
    variant?: 'header' | 'page';
}) { const location = useLocation(); const navigate = useNavigate(); const parsed = parseSearch(new URLSearchParams(location.search)); const urlQuery = location.pathname === '/explore' ? parsed.q : ''; const urlTarget = location.pathname === '/explore' ? parsed.target : 'developer'; const [query, setQuery] = useState(urlQuery); const [target, setTarget] = useState<SearchTarget>(urlTarget); const id = useId(); const urlKey = JSON.stringify([urlQuery, urlTarget, location.pathname]); const [shownUrl, setShownUrl] = useState(urlKey); if (shownUrl !== urlKey) { setShownUrl(urlKey); setQuery(urlQuery); setTarget(urlTarget); }const submit = (nextTarget: SearchTarget = target) => navigate(`/explore?${changeSearchTarget(new URLSearchParams(location.pathname === '/explore' ? location.search : ''), nextTarget, query)}`); return <form className={[styles.scope, variant === 'header' ? 'header-search' : 'search-main-bar'].join(" ")} role="search" aria-label={variant === 'header' ? '헤더 통합 검색' : '검색 결과 검색'} onSubmit={e => { e.preventDefault(); submit(); }}><label className={[styles.scope, "sr-only"].join(" ")} htmlFor={`${id}-target`}>검색 대상</label><select id={`${id}-target`} value={target} onChange={e => { const next = e.target.value as SearchTarget; setTarget(next); if (location.pathname === '/explore')
    submit(next); }}>{searchTargetOptions.map(o => <option key={o.value} value={o.value}>{o.label}</option>)}</select><Input type="search" aria-label="통합 검색어" placeholder={variant === 'header' ? '새로운 연결을 찾아보세요' : target === 'developer' ? '닉네임, 직군, 기술 스택으로 동료를 찾아보세요' : '프로젝트명, 기술 스택으로 검색해 보세요'} value={query} onChange={e => setQuery(e.target.value)} maxLength={100}/><button className={[styles.scope, variant === 'header' ? 'icon-button' : 'button'].join(" ")} aria-label="검색" type="submit"><Search size={variant === 'header' ? 17 : 19}/>{variant === 'page' && '검색'}</button></form>; }
