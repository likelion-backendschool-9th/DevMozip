'use client';
import styles from './ProjectsScreen.module.css';
import { ProjectCreateButton } from '@/components/projects/ProjectCreateButton';
import { useEffect, useState } from 'react';
import Link from 'next/link';
import { useQueryState as useSearchParams } from '@/lib/navigation';
import { Plus, Search, RotateCcw } from 'lucide-react';
import { useProjects } from '@/lib/state/ProjectProvider';
import { projectSortOptions, stackOptions } from '@/mocks/home';
import { projectFields, recruitmentRoles } from '@/mocks/project';
import { projectStatusOptions } from '@/mocks/search';
import { filterProjectList, parseProjectListFilters } from '@/lib/projects/projectService';
import { Input } from '@/components/common/Input';
import { Button } from '@/components/common/Button';
import { RecruitmentCard } from '@/components/projects/RecruitmentCard';
export function ProjectsPage() { const { projects, now } = useProjects(); const [params, setParams] = useSearchParams(); const filters = parseProjectListFilters(params); const [q, setQ] = useState(filters.q); useEffect(() => setQ(filters.q), [filters.q]); const items = filterProjectList(projects, filters, now); const update = (key: string, value: string) => { const next = new URLSearchParams(params); if (value)
    next.set(key, value);
else
    next.delete(key); setParams(next); }; return <div className={[styles.scope, "container recruitment-page"].join(" ")}><div className={[styles.scope, "section-heading"].join(" ")}><div><div className={[styles.scope, "eyebrow"].join(" ")}>BUILD SOMETHING GREAT TOGETHER</div><h1>당신의 다음 프로젝트를 만나보세요.</h1><p>함께 고민하고 성장할 팀이 기다리고 있어요.</p></div><ProjectCreateButton><Plus size={16}/>모집 공고 작성</ProjectCreateButton></div><form className={[styles.scope, "project-list-search"].join(" ")} onSubmit={e => { e.preventDefault(); update('q', q.trim()); }}><Search size={18}/><Input aria-label="프로젝트명 또는 기술 스택 검색" placeholder="프로젝트명 또는 기술 스택을 검색해 보세요" maxLength={100} value={q} onChange={e => setQ(e.target.value)}/><Button type="submit">검색</Button></form><div className={[styles.scope, "project-list-filters"].join(" ")}>{([{ key: 'role', label: '모집 직군', options: recruitmentRoles }, { key: 'field', label: '분야', options: projectFields }, { key: 'status', label: '모집 상태', options: projectStatusOptions }, { key: 'stack', label: '기술 스택', options: stackOptions }] as const).map(f => <label key={f.key}>{f.label}<select className={[styles.scope, "form-input"].join(" ")} value={filters[f.key]} onChange={e => update(f.key, e.target.value)}><option value="">전체</option>{f.options.map(o => <option key={o}>{o}</option>)}</select></label>)}<Button variant="secondary" onClick={() => { setQ(''); setParams({}); }}><RotateCcw size={12}/>초기화</Button></div><div className={[styles.scope, "recruitment-list-heading"].join(" ")}><span role="status">총 <b>{items.length}</b>개의 프로젝트</span><div className={[styles.scope, "tabs compact"].join(" ")}>{projectSortOptions.map(o => <button key={o.value} aria-pressed={filters.sort === o.value} className={[styles.scope, filters.sort === o.value ? 'active' : ''].join(" ")} onClick={() => update('sort', o.value)}>{o.label}</button>)}</div></div>{items.length ? <div className={[styles.scope, "recruitment-grid"].join(" ")}>{items.map(p => <RecruitmentCard key={p.id} project={p}/>)}</div> : <div className={[styles.scope, "empty-state"].join(" ")}><strong>조건에 맞는 프로젝트가 없습니다.</strong><p>검색어 또는 필터를 바꿔보세요.</p><Button variant="secondary" onClick={() => { setQ(''); setParams({}); }}>전체 조건 초기화</Button></div>}</div>; }
