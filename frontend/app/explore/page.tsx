'use client';
import {useMockVisibility} from '@/lib/state/mockVisibilityStore';
import styles from './page.module.css';
import { useEffect, useRef, useState, type ReactNode } from 'react';
import { AdvancedSearch } from '@/app/explore/_components/AdvancedSearch';
import { advancedSearch } from '@/lib/administration/advancedSearchService';
import { temperature } from '@/lib/profiles/profileService';
import Link from 'next/link';
import { useQueryState as useSearchParams } from '@/lib/navigation';
import { Search, X } from 'lucide-react';
import { useAuth } from '@/lib/state/authStore';
import { useProfiles } from '@/lib/state/profileStore';
import { usePrototype } from '@/lib/state/prototypeStore';
import { signupOptionsAction } from '@/lib/auth/actions';
import {
    parseSearch,
    searchDevelopers,
    activeFilterCount,
    developerSearchParams,
    mockFilterChoices,
} from '@/lib/search/searchService';
import { apiFilterChoices, applyFollowChange, explorePage, pageAfterEmpty, parseApiSearch, searchApiQuery } from '@/lib/search/searchApi';
import { useExploreSearch } from '@/lib/search/useExploreSearch';
import { useOwnProfile } from '@/app/(user)/_components/OwnProfileProvider';
import { SearchBar } from '@/app/explore/_components/SearchBar';
import { Button } from '@/components/common/Button';
import { SearchFilters } from '@/app/explore/_components/SearchFilters';
import { DeveloperResults } from '@/app/explore/_components/DeveloperResults';
import { ApiDeveloperResults } from '@/app/explore/_components/ApiDeveloperResults';
import { SearchPager } from '@/app/explore/_components/SearchPager';
import type { SignupOptions } from '@/types/auth';
import type { FollowResult } from '@/types/followApi';
// Mock 켜기는 Mock 저장소를 searchService로 거르고, Mock 끄기는 통합 검색 API(GET /api/v1/search)를 쓴다.
// 두 모드의 화면 구조와 디자인은 같다(docs/explore-plan.md 5절).
export default function SearchPage() {
    const {enabled}=useMockVisibility();
    const [params, setParams] = useSearchParams();
    const developerParams = developerSearchParams(params);
    const filters = enabled ? parseSearch(developerParams) : parseApiSearch(developerParams);
    const page = explorePage(params);
    const { user, canViewProfile } = useAuth();
    const own = useOwnProfile();
    const { notify } = usePrototype();
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
    const signedOutFollowing = filters.scope === 'following' && !user;
    // Mock 끄기: 비로그인 팔로잉 탭은 요청하지 않고 로그인 안내를 보여 준다.
    const search = useExploreSearch(enabled || signedOutFollowing ? null : searchApiQuery(filters, page).toString());
    const [signupOptions, setSignupOptions] = useState<SignupOptions | null>(null);
    useEffect(() => {
        if (enabled) return;
        let active = true;
        void signupOptionsAction().then(value => { if (active && !('error' in value)) setSignupOptions(value); });
        return () => { active = false; };
    }, [enabled]);
    const choices = enabled ? mockFilterChoices('developer', Object.values(state.profiles), []) : apiFilterChoices(signupOptions);
    const loading = search.state.status === 'loading';
    const ready = search.state.status === 'ready' ? search.state.result : null;
    const shown = ready ?? (search.state.status === 'loading' ? search.state.previous : null);
    const apiError = search.state.status === 'error' ? search.state.error : null;
    const count = enabled
        ? developers.length
        : shown?.page.totalCount ?? 0;
    // 로그인했지만 내 프로필을 아직 모르면 본인 카드인지 알 수 없으므로 버튼을 잠시 숨긴다.
    const ownTag = !user ? null : own.profile ? own.profile.tag : own.loading ? undefined : null;
    const update = (key: string, value: string | string[]) => {
        const next = developerSearchParams(params);
        next.delete(key);
        for (const v of Array.isArray(value) ? value : [value]) if (v) next.append(key, v);
        // 검색어·필터·탭이 바뀌면 첫 페이지로 돌아간다.
        if (key !== 'page') next.delete('page');
        setParams(next);
    };
    const goPage = (target: number) => update('page', target > 0 ? String(target) : '');
    const reset = (clearQuery = false) => {
        const next = developerSearchParams(new URLSearchParams());
        if (!clearQuery && filters.q) next.set('q', filters.q);
        if (!clearQuery && filters.scope === 'following') next.set('tab', 'following');
        setParams(next);
    };
    // 팔로잉 탭에서 언팔로우하면 카드를 바로 빼고 현재 페이지를 다시 받는다. 다시 받은 페이지가 비어 있으면 앞 페이지로 간다.
    const backOnEmpty = useRef(false);
    const changeFollow = (tag: string, result: FollowResult) => {
        const followingTab = filters.scope === 'following';
        search.update(current => current.type === 'developers'
            ? { ...current, page: applyFollowChange(current.page, tag, result.followed, followingTab) } : current);
        if (followingTab && !result.followed) {
            backOnEmpty.current = true;
            search.reload();
        }
    };
    useEffect(() => {
        if (!ready || !backOnEmpty.current) return;
        backOnEmpty.current = false;
        const target = pageAfterEmpty(ready.page);
        if (target === null) return;
        const next = new URLSearchParams(params);
        if (target > 0) next.set('page', String(target));
        else next.delete('page');
        setParams(next);
    }, [ready, params, setParams]);
    const selected = [
        ...filters.stacks.map(value => ({key: 'stack', value})),
        {key: 'job', value: filters.job}, {key: 'career', value: filters.career},
        ...(filters.interests || []).map(value => ({key: 'interest', value})),
        {key: 'available', value: filters.available === 'yes' ? '협업 가능' : filters.available === 'no' ? '협업 쉬는 중' : ''},
    ].filter(item => item.value);
    const emptyFilters = (
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
    );
    const notice = (title: string, body: string, action: ReactNode) => (
        <div className={[styles.scope, 'search-empty'].join(' ')} role="alert">
            <span>
                <Search size={30} />
            </span>
            <h2>{title}</h2>
            <p>{body}</p>
            {action}
        </div>
    );
    const apiResults = () => {
        if (signedOutFollowing) return emptyFilters;
        if (apiError)
            return notice(apiError.message, '잠시 후 다시 시도해 주세요.', (
                <Button variant="secondary" onClick={search.reload}>다시 시도</Button>
            ));
        if (!shown || shown.type !== 'developers') return null;
        if (!shown.page.items.length) {
            if (shown.page.totalCount === 0) return emptyFilters;
            const last = shown.page.totalPages - 1;
            return notice('이 페이지에는 결과가 없어요.', `검색 결과는 ${shown.page.totalPages}페이지까지 있어요.`, (
                <Button variant="secondary" disabled={loading} onClick={() => goPage(last)}>마지막 페이지로 이동</Button>
            ));
        }
        return (
            <>
                <ApiDeveloperResults
                        items={shown.page.items}
                        signedIn={!!user}
                        ownTag={ownTag}
                        onFollowChange={changeFollow}
                        onFollowError={message => notify(message)}
                />
                <SearchPager page={shown.page.page} totalPages={shown.page.totalPages} disabled={loading} onChange={goPage} />
            </>
        );
    };
    return (
        <div className={[styles.scope, 'container search-page'].join(' ')}>
            <header className={[styles.scope, 'search-page-heading'].join(' ')}>
                <div className={[styles.scope, 'eyebrow'].join(' ')}>DISCOVER YOUR NEXT CONNECTION</div>
                <h1>
                    좋은 연결의 시작, <span>개발자 탐색</span>
                </h1>
                <p>기술과 관심사가 맞는 개발자를 찾아보세요.</p>
                <SearchBar variant="page" />
            </header>
            <div className={[styles.scope, 'search-workspace'].join(' ')}>
                <div>
                    <SearchFilters
                        filters={filters}
                        choices={choices}
                        onChange={update}
                        onReset={() => {
                            reset();
                            setMinimum(0);
                            setTrustSort(false);
                        }}
                    />

                        <AdvancedSearch
                            minimum={minimum}
                            sort={trustSort}
                            onChange={(m, s) => {
                                setMinimum(m);
                                setTrustSort(s);
                            }}
                        />
                </div>
                <section className={[styles.scope, 'search-results'].join(' ')} aria-label="검색 결과">
                    <div className={[styles.scope, 'search-results-heading'].join(' ')}>
                        <div>

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

                        </div>
                        <span className={[styles.scope, 'search-count'].join(' ')} role="status">
                            {!enabled && loading ? '조회 중…' : <>{count} 명의 개발자</>}
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
                    {!enabled ? apiResults() : count > 0 ? (
                        <DeveloperResults items={developers} />
                    ) : emptyFilters}
                    {enabled && (
                        <p className={[styles.scope, 'search-data-note'].join(' ')}>
                            Mock 데이터 기반 검색 · 프로필 수정과 팔로우 상태가 공통으로 반영됩니다.
                        </p>
                    )}
                </section>
            </div>
        </div>
    );
}
