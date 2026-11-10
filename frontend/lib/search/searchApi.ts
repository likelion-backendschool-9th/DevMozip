import { careerOptions } from '@/mocks/auth';
import { roleLabels } from '@/lib/projects/projectApiContract';
import { projectRoleCode, projectRoleLabel, validProjectChoice } from '@/lib/projects/projectChoices';
import { validProfileSlug } from '@/lib/profiles/profileDetail';
import { parseSearch } from './searchService';
import type { SignupOptions } from '@/types/auth';
import type { SearchFilters } from '@/types/search';
import type {
    DeveloperSearchItem, ProjectSearchItem, ProjectSearchStatus, SearchApiType, SearchFilterChoices, SearchPage, SearchResult,
} from '@/types/searchApi';

// 통합 검색 API 연결(Mock 끄기). 화면 URL → API 쿼리 변환, BFF 쿼리 확인, 응답 해석을 맡는다.
// 브라우저는 BFF(/api/search)만 부르고 사용자 ID를 보내지 않는다. 서버 결과는 localStorage에 저장하지 않는다.

export const SEARCH_PAGE_SIZE = 20;
export const SEARCH_QUERY_MAX = 100;
const VALUE_MAX = 30;
const VALUES_MAX = 30;
const MAX_SIZE = 100;

export const projectSearchStatusLabels: Record<ProjectSearchStatus, string> = {
    RECRUITING: '모집 중',
    RECRUITMENT_CLOSED: '모집 마감',
    RECRUITMENT_COMPLETED: '모집 완료',
    IN_PROGRESS: '프로젝트 진행 중',
    COMPLETED: '프로젝트 종료',
};
const statusByLabel = new Map(Object.entries(projectSearchStatusLabels).map(([code, label]) => [label, code]));
const isStatus = (value: unknown): value is ProjectSearchStatus => typeof value === 'string' && Object.hasOwn(projectSearchStatusLabels, value);

const pageNumber = /^(0|[1-9]\d{0,8})$/;
const controlCharacter = /\p{Cc}/u;

/** 화면 URL의 page(0부터). 형식이 틀리면 첫 페이지다. */
export function explorePage(params: URLSearchParams): number {
    const raw = params.get('page');
    return raw !== null && pageNumber.test(raw) ? Number(raw) : 0;
}

/**
 * Mock 끄기의 화면 URL 해석. 개발 직군은 백엔드 enum(가입 선택지)이 기준이고 늦게 불러오므로 Mock 목록으로 거르지 않고,
 * 모집 직군은 프로젝트 기능의 라벨(roleLabels)로 바꾼다. 나머지는 Mock 화면(parseSearch)과 같다.
 */
export function parseApiSearch(params: URLSearchParams): SearchFilters {
    const filters = parseSearch(params);
    if (filters.target === 'developer') {
        const job = (params.get('job') ?? '').trim();
        return { ...filters, job: job.length <= VALUE_MAX && !controlCharacter.test(job) ? job : '' };
    }
    const role = params.get('role');
    return { ...filters, role: role && validProjectChoice(role) ? projectRoleLabel(role) : '' };
}

/** 화면 필터 → API 쿼리(6.10). 화면 URL의 이름과 API 이름은 docs/explore-plan.md 5절 표를 따른다. */
export function searchApiQuery(filters: SearchFilters, page: number, size = SEARCH_PAGE_SIZE): URLSearchParams {
    const query = new URLSearchParams();
    const q = filters.q.trim();
    if (filters.target === 'developer') {
        query.set('type', 'developers');
        if (q) query.set('q', q);
        if (filters.job) query.set('jobCategory', filters.job);
        if (filters.career) query.set('career', filters.career);
        for (const value of filters.interests ?? []) query.append('interests', value);
        for (const value of filters.stacks) query.append('skills', value);
        if (filters.available) query.set('available', filters.available === 'yes' ? 'true' : 'false');
        if (filters.scope === 'following') query.set('following', 'true');
    } else {
        query.set('type', 'projects');
        if (q) query.set('q', q);
        if (filters.role) query.set('jobRole', projectRoleCode(filters.role));
        const status = statusByLabel.get(filters.status);
        if (status) query.set('status', status);
        for (const value of filters.fields ?? []) query.append('interests', value);
        for (const value of filters.stacks) query.append('techStacks', value);
    }
    query.set('page', String(page));
    query.set('size', String(size));
    return query;
}

const scalarKeys: Record<SearchApiType, string[]> = {
    developers: ['q', 'jobCategory', 'career', 'available', 'following', 'page', 'size'],
    projects: ['q', 'jobRole', 'status', 'page', 'size'],
};
const listKeys: Record<SearchApiType, string[]> = {
    developers: ['interests', 'skills'],
    projects: ['interests', 'techStacks'],
};

function validScalar(key: string, value: string) {
    switch (key) {
        case 'q': return value.length <= SEARCH_QUERY_MAX;
        case 'available':
        case 'following': return value === 'true' || value === 'false';
        case 'status': return isStatus(value);
        case 'page': return pageNumber.test(value);
        case 'size': return pageNumber.test(value) && Number(value) >= 1 && Number(value) <= MAX_SIZE;
        default: return value.length <= VALUE_MAX && !controlCharacter.test(value);
    }
}

/** BFF가 받은 쿼리 중 허용한 값만 확인해 남긴다. 형식이 틀리면 null이다. 사용자 ID 같은 다른 값은 넘기지 않는다. */
export function searchProxyQuery(params: URLSearchParams): URLSearchParams | null {
    const types = params.getAll('type');
    const type = types.length === 1 ? types[0] : null;
    if (type !== 'developers' && type !== 'projects') return null;
    const query = new URLSearchParams({ type });
    for (const key of scalarKeys[type]) {
        const values = params.getAll(key);
        if (values.length > 1) return null;
        const value = values[0];
        if (value === undefined || value === '') continue;
        if (!validScalar(key, value)) return null;
        query.set(key, value);
    }
    for (const key of listKeys[type]) {
        const values = [...new Set(params.getAll(key).map(value => value.trim()).filter(Boolean))];
        if (values.length > VALUES_MAX || values.some(value => value.length > VALUE_MAX)) return null;
        for (const value of values) query.append(key, value);
    }
    return query;
}

const count = (v: unknown): v is number => typeof v === 'number' && Number.isSafeInteger(v) && v >= 0;
const text = (v: unknown): v is string | null => v === null || typeof v === 'string';
const strings = (v: unknown): v is string[] => Array.isArray(v) && v.every(item => typeof item === 'string');
const record = (v: unknown) => {
    if (!v || typeof v !== 'object' || Array.isArray(v)) throw new Error('검색 응답 형식 오류');
    return v as Record<string, unknown>;
};

function parsePage<T>(value: unknown, item: (value: unknown) => T): SearchPage<T> {
    const v = record(value);
    if (!count(v.page) || !count(v.size) || v.size < 1 || v.size > MAX_SIZE || !count(v.totalCount) || !count(v.totalPages)
        || v.totalPages !== Math.ceil(v.totalCount / v.size) || !Array.isArray(v.items)
        || v.items.length > v.size || v.items.length > v.totalCount) throw new Error('검색 응답 형식 오류');
    return { items: v.items.map(item), page: v.page, size: v.size, totalCount: v.totalCount, totalPages: v.totalPages };
}

function parseDeveloper(value: unknown): DeveloperSearchItem {
    const v = record(value);
    if (typeof v.tag !== 'string' || !validProfileSlug(v.tag) || !text(v.nickname) || !text(v.profileImage)
        || !strings(v.jobCategories) || !text(v.career) || !text(v.summary) || !strings(v.skills) || !strings(v.interests)
        || !text(v.collaborationStatus) || !(v.trustScore === null || typeof v.trustScore === 'number' && Number.isFinite(v.trustScore))
        || typeof v.followed !== 'boolean') throw new Error('개발자 검색 항목 오류');
    // 알 수 없는 추가 필드(이메일 등)는 브라우저 응답에 넣지 않는다.
    return { tag: v.tag, nickname: v.nickname, profileImage: v.profileImage, jobCategories: v.jobCategories, career: v.career,
        summary: v.summary, skills: v.skills, interests: v.interests, collaborationStatus: v.collaborationStatus,
        trustScore: v.trustScore as number | null, followed: v.followed };
}

function parseProject(value: unknown): ProjectSearchItem {
    const v = record(value);
    if (!count(v.id) || v.id < 1 || typeof v.title !== 'string' || !text(v.summary) || !text(v.imageUrl) || !isStatus(v.status)
        || !strings(v.jobRoles) || !strings(v.techStacks) || !strings(v.interests) || !count(v.totalCapacity) || !count(v.memberCount)
        || typeof v.developmentStart !== 'string' || typeof v.developmentEnd !== 'string' || typeof v.createdAt !== 'string')
        throw new Error('프로젝트 검색 항목 오류');
    return { id: v.id, title: v.title, summary: v.summary, imageUrl: v.imageUrl, status: v.status, jobRoles: v.jobRoles,
        techStacks: v.techStacks, interests: v.interests, totalCapacity: v.totalCapacity, memberCount: v.memberCount,
        developmentStart: v.developmentStart, developmentEnd: v.developmentEnd, createdAt: v.createdAt };
}

function unique<T>(page: SearchPage<T>, key: (item: T) => string | number) {
    if (new Set(page.items.map(key)).size !== page.items.length) throw new Error('중복된 검색 결과');
    return page;
}

export function parseSearchResult(type: SearchApiType, value: unknown): SearchResult {
    return type === 'developers'
        ? { type, page: unique(parsePage(value, parseDeveloper), item => item.tag) }
        : { type, page: unique(parsePage(value, parseProject), item => item.id) };
}

export class SearchRequestError extends Error {
    constructor(public readonly status: number, message: string) { super(message); }
}

/** BFF로 한 페이지를 받는다. query는 searchApiQuery로 만든 값이다. */
export async function fetchSearch(query: URLSearchParams, signal?: AbortSignal): Promise<SearchResult> {
    const type: SearchApiType = query.get('type') === 'projects' ? 'projects' : 'developers';
    const response = await fetch(`/api/search?${query}`, { cache: 'no-store', signal });
    if (!response.ok) {
        const body = await response.json().catch(() => null);
        throw new SearchRequestError(response.status, typeof body?.message === 'string' ? body.message : '검색 결과를 불러오지 못했어요.');
    }
    let result: SearchResult;
    try { result = parseSearchResult(type, await response.json()); }
    catch { throw new SearchRequestError(502, '검색 결과의 형식을 확인할 수 없어요.'); }
    if (result.page.page !== Number(query.get('page')) || result.page.size !== Number(query.get('size')))
        throw new SearchRequestError(502, '요청한 페이지와 응답이 일치하지 않아요.');
    return result;
}

/** Mock 끄기의 필터 선택지. 직군·기술 스택·관심 분야는 백엔드 enum(가입 선택지), 모집 직군은 프로젝트 기능의 라벨이다. */
export function apiFilterChoices(options: SignupOptions | null): SearchFilterChoices {
    return {
        jobs: options?.jobCategories ?? [],
        careers: careerOptions,
        roles: Object.values(roleLabels),
        statuses: Object.values(projectSearchStatusLabels),
        fields: options?.interests ?? [],
        stacks: options?.skills ?? [],
    };
}

/** 카드의 팔로우 결과를 목록에 반영한다. 팔로잉 탭에서 언팔로우하면 카드를 바로 빼고 결과 수를 줄인다. */
export function applyFollowChange(page: SearchPage<DeveloperSearchItem>, tag: string, followed: boolean, followingTab: boolean) {
    if (followingTab && !followed) {
        if (!page.items.some(item => item.tag === tag)) return page;
        const totalCount = Math.max(0, page.totalCount - 1);
        return { ...page, items: page.items.filter(item => item.tag !== tag), totalCount, totalPages: Math.ceil(totalCount / page.size) };
    }
    return { ...page, items: page.items.map(item => item.tag === tag ? { ...item, followed } : item) };
}

/** 다시 받은 페이지가 비어 있을 때 옮겨 갈 페이지. 옮길 필요가 없으면 null이다. */
export function pageAfterEmpty(page: SearchPage<unknown>): number | null {
    if (page.items.length > 0 || page.page === 0) return null;
    return Math.max(0, Math.min(page.page - 1, page.totalPages - 1));
}

export function projectRoleLabels(codes: string[]) {
    return codes.map(projectRoleLabel);
}
