'use client';
import styles from './page.module.css';
import { useState } from 'react';
import { AdvancedSearch } from '@/app/explore/_components/AdvancedSearch';
import { advancedSearch } from '@/lib/administration/advancedSearchService';
import { temperature } from '@/lib/profiles/profileService';
import Link from 'next/link';
import { useQueryState as useSearchParams } from '@/lib/navigation';
import { Search, X } from 'lucide-react';
import { useAuth } from '@/lib/state/authStore';
import { useProfiles } from '@/lib/state/profileStore';
import { useProjects } from '@/lib/state/projectStore';
import {
    parseSearch,
    searchDevelopers,
    searchProjects,
    activeFilterCount,
    searchType,
} from '@/lib/search/searchService';
import { SearchBar } from '@/app/explore/_components/SearchBar';
import { Button } from '@/components/common/Button';
import { SearchFilters } from '@/app/explore/_components/SearchFilters';
import { DeveloperResults } from '@/app/explore/_components/DeveloperResults';
import { ProjectResults } from '@/app/explore/_components/ProjectResults';
export default function SearchPage() {
    const { projects } = useProjects();
    const [params, setParams] = useSearchParams();
    const filters = parseSearch(params);
    const { user, canViewProfile } = useAuth();
    const { state } = useProfiles();
    const following = user ? state.following[user.id] || [] : [];
    const [minimum, setMinimum] = useState(0);
    const [trustSort, setTrustSort] = useState(false);
    const developers = advancedSearch(
        searchDevelopers(
            Object.values(state.profiles).filter(p => canViewProfile(p.id)),
            filters,
            following,
            !!user,
        ),
        user,
        Object.fromEntries(Object.keys(state.profiles).map(id => [id, temperature(state.reviews, id)])),
        minimum,
        trustSort,
    );
    const foundProjects = searchProjects(projects, filters);
    const count = filters.target === 'developer' ? developers.length : foundProjects.length;
    const signedOutFollowing = filters.target === 'developer' && filters.scope === 'following' && !user;
    const update = (key: string, value: string | string[]) => {
        const next = new URLSearchParams(params);
        next.set('type', searchType(filters.target));
        next.delete(key);
        for (const v of Array.isArray(value) ? value : [value]) if (v) next.append(key, v);
        setParams(next);
    };
    const reset = (clearQuery = false) => {
        const next = new URLSearchParams();
        next.set('type', searchType(filters.target));
        if (!clearQuery && filters.q) next.set('q', filters.q);
        if (!clearQuery && filters.scope === 'following') next.set('tab', 'following');
        setParams(next);
    };
    const selected = [
        ...filters.stacks.map(s => ({ key: 'stack', value: s })),
        ...(filters.target === 'developer'
            ? [
                  { key: 'job', value: filters.job },
                  { key: 'career', value: filters.career },
                  ...(filters.interests || []).map(value => ({ key: 'interest', value })),
                  {
                      key: 'available',
                      value:
                          filters.available === 'yes' ? '협업 가능' : filters.available === 'no' ? '협업 쉬는 중' : '',
                  },
              ]
            : [
                  { key: 'role', value: filters.role },
                  ...(filters.fields || []).map(value => ({ key: 'field', value })),
                  { key: 'status', value: filters.status },
              ]),
    ].filter(v => v.value);
    return (
        <div className={[styles.scope, 'container search-page'].join(' ')}>
            <header className={[styles.scope, 'search-page-heading'].join(' ')}>
                <div className={[styles.scope, 'eyebrow'].join(' ')}>DISCOVER YOUR NEXT CONNECTION</div>
                <h1>
                    좋은 연결의 시작, <span>DevMozip 탐색</span>
                </h1>
                <p>같은 관심사를 가진 개발자부터 새로운 도전이 될 프로젝트까지.</p>
                <SearchBar variant="page" />
            </header>
            <div className={[styles.scope, 'search-workspace'].join(' ')}>
                <div>
                    <SearchFilters
                        filters={filters}
                        onChange={update}
                        onReset={() => {
                            reset();
                            setMinimum(0);
                            setTrustSort(false);
                        }}
                    />
                    {filters.target === 'developer' && (
                        <AdvancedSearch
                            minimum={minimum}
                            sort={trustSort}
                            onChange={(m, s) => {
                                setMinimum(m);
                                setTrustSort(s);
                            }}
                        />
                    )}
                </div>
                <section className={[styles.scope, 'search-results'].join(' ')} aria-label="검색 결과">
                    <div className={[styles.scope, 'search-results-heading'].join(' ')}>
                        <div>
                            {filters.target === 'developer' ? (
                                <div className={[styles.scope, 'tabs compact'].join(' ')} aria-label="개발자 검색 범위">
                                    <button
                                        className={[styles.scope, filters.scope === 'all' ? 'active' : ''].join(' ')}
                                        aria-pressed={filters.scope === 'all'}
                                        onClick={() => update('tab', 'all')}
                                    >
                                        전체
                                    </button>
                                    <button
                                        className={[styles.scope, filters.scope === 'following' ? 'active' : ''].join(
                                            ' ',
                                        )}
                                        aria-pressed={filters.scope === 'following'}
                                        onClick={() => update('tab', 'following')}
                                    >
                                        팔로잉
                                    </button>
                                </div>
                            ) : (
                                <h2>프로젝트 탐색</h2>
                            )}
                        </div>
                        <span className={[styles.scope, 'search-count'].join(' ')} role="status">
                            {count} {filters.target === 'developer' ? '명의 개발자' : '개의 프로젝트'}
                        </span>
                    </div>
                    {filters.q && (
                        <p className={[styles.scope, 'search-query-caption'].join(' ')}>“{filters.q}” 검색 결과</p>
                    )}
                    {activeFilterCount(filters) > 0 && (
                        <div className={[styles.scope, 'active-search-filters'].join(' ')}>
                            {selected.map(f => (
                                <button
                                    key={`${f.key}-${f.value}`}
                                    onClick={() =>
                                        update(
                                            f.key,
                                            f.key === 'stack'
                                                ? filters.stacks.filter(s => s !== f.value)
                                                : f.key === 'interest'
                                                  ? (filters.interests || []).filter(v => v !== f.value)
                                                  : f.key === 'field'
                                                    ? (filters.fields || []).filter(v => v !== f.value)
                                                    : '',
                                        )
                                    }
                                    aria-label={`${f.value} 필터 제거`}
                                >
                                    {f.value}
                                    <X size={11} />
                                </button>
                            ))}
                        </div>
                    )}
                    {count > 0 ? (
                        filters.target === 'developer' ? (
                            <DeveloperResults items={developers} />
                        ) : (
                            <ProjectResults items={foundProjects} />
                        )
                    ) : (
                        <div className={[styles.scope, 'search-empty'].join(' ')}>
                            <span>
                                <Search size={30} />
                            </span>
                            <h2>
                                {signedOutFollowing
                                    ? '로그인하고 동료들의 소식을 만나보세요.'
                                    : '조건에 맞는 결과가 없어요.'}
                            </h2>
                            <p>
                                {signedOutFollowing
                                    ? '팔로잉 목록은 계정별로 관리됩니다. 로그인해 주세요.'
                                    : '검색어를 바꾸거나 선택한 필터를 줄여 다시 찾아보세요.'}
                            </p>
                            {signedOutFollowing ? (
                                <Link href="/login" className={[styles.scope, 'button'].join(' ')}>
                                    로그인하기
                                </Link>
                            ) : (
                                <Button variant="secondary" onClick={() => reset(true)}>
                                    검색 조건 전체 초기화
                                </Button>
                            )}
                        </div>
                    )}
                    <p className={[styles.scope, 'search-data-note'].join(' ')}>
                        Mock 데이터 기반 검색 · 프로필 수정과 팔로우 상태가 공통으로 반영됩니다.
                    </p>
                </section>
            </div>
        </div>
    );
}
