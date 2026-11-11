import { afterEach, describe, expect, it, vi } from 'vitest';
import {
    apiFilterChoices, applyFollowChange, explorePage, pageAfterEmpty, parseApiSearch, parseSearchResult, searchApiQuery, searchProxyQuery,
} from './searchApi';
import { changeSearchTarget, developerSearchParams, mockFilterChoices, parseSearch } from './searchService';
import { GET } from '@/app/api/search/route';
import { backendFetch } from '@/lib/auth/backend';
import type { DeveloperSearchItem, SearchPage } from '@/types/searchApi';

vi.mock('@/lib/auth/backend', () => ({ backendFetch: vi.fn() }));
afterEach(() => vi.mocked(backendFetch).mockReset());

const url = (query: string) => new URLSearchParams(query);
const developer = (tag: string, extra: Partial<DeveloperSearchItem> = {}): DeveloperSearchItem => ({
    tag, nickname: `닉네임-${tag}`, profileImage: null, jobCategories: ['백엔드 개발자'], career: '2년 차', summary: '소개',
    skills: ['Java'], interests: ['핀테크'], collaborationStatus: 'AVAILABLE', trustScore: 36.5, followed: false, ...extra,
});
const page = <T,>(items: T[], extra: Partial<SearchPage<T>> = {}): SearchPage<T> =>
    ({ items, page: 0, size: 20, totalCount: items.length, totalPages: items.length ? 1 : 0, ...extra });
const project = {
    id: 3, title: '프로젝트', summary: '한 줄', imageUrl: null, status: 'RECRUITMENT_CLOSED', jobRoles: ['BACKEND', '보안 엔지니어'],
    techStacks: ['React'], interests: ['핀테크'], totalCapacity: 4, memberCount: 2, developmentStart: '2026-12-01',
    developmentEnd: '2027-02-28', createdAt: '2026-11-09T10:00:00',
};

describe('화면 URL → 검색 API 쿼리 (6.10)', () => {
    it('개발자 필터를 API 이름으로 바꾸고 페이지를 붙인다', () => {
        const filters = parseApiSearch(url('type=developers&q=+자바+&job=백엔드 개발자&career=3년 차&interest=핀테크&interest=교육&stack=Java&stack=Spring Boot&available=no&tab=following&page=2'));
        expect([...searchApiQuery(filters, explorePage(url('page=2')))]).toEqual([
            ['type', 'developers'], ['q', '자바'], ['jobCategory', '백엔드 개발자'], ['career', '3년 차'], ['interests', '핀테크'], ['interests', '교육'],
            ['skills', 'Java'], ['skills', 'Spring Boot'], ['available', 'false'], ['following', 'true'], ['page', '2'], ['size', '20'],
        ]);
        expect(searchApiQuery(parseApiSearch(url('type=developers&available=yes')), 0).get('available')).toBe('true');
        expect(searchApiQuery(parseApiSearch(url('type=developers')), 0).toString()).toBe('type=developers&page=0&size=20');
    });
    it('프로젝트 모집 직군 라벨은 코드로, 모집 상태 라벨은 API 값으로 바꾼다', () => {
        const query = searchApiQuery(parseApiSearch(url('type=projects&q=핀테크&role=백엔드&status=모집 마감&field=핀테크&stack=React&job=무시')), 0);
        expect([...query]).toEqual([
            ['type', 'projects'], ['q', '핀테크'], ['jobRole', 'BACKEND'], ['status', 'RECRUITMENT_CLOSED'], ['interests', '핀테크'],
            ['techStacks', 'React'], ['page', '0'], ['size', '20'],
        ]);
        expect(searchApiQuery(parseApiSearch(url('type=projects&role=디자이너')), 0).get('jobRole')).toBe('DESIGNER');
        expect(searchApiQuery(parseApiSearch(url('type=projects&role=보안 엔지니어')), 0).get('jobRole')).toBe('보안 엔지니어');
        expect(searchApiQuery(parseApiSearch(url('type=projects&status=모집 중')), 0).get('status')).toBe('RECRUITING');
        expect(searchApiQuery(parseApiSearch(url('type=projects&status=없는 상태')), 0).has('status')).toBe(false);
    });
    it('Mock 끄기에서는 백엔드 기준 직군·모집 직군을 Mock 목록으로 거르지 않는다', () => {
        expect(parseApiSearch(url('type=developers&job=DevOps 엔지니어')).job).toBe('DevOps 엔지니어');
        expect(parseApiSearch(url(`type=developers&job=${'a'.repeat(31)}`)).job).toBe('');
        expect(parseApiSearch(url('type=projects&role=DevOps')).role).toBe('DevOps');
        expect(parseApiSearch(url('type=projects&role=BACKEND')).role).toBe('백엔드');
        // Mock 켜기의 해석은 그대로다.
        expect(parseSearch(url('type=projects&role=DevOps')).role).toBe('');
    });
    it('화면 URL의 page는 0부터이고 형식이 틀리면 첫 페이지다', () => {
        expect(explorePage(url('page=3'))).toBe(3);
        for (const value of ['-1', 'abc', '01', '1.5', '']) expect(explorePage(url(`page=${value}`))).toBe(0);
        expect(explorePage(url(''))).toBe(0);
    });
    it('검색어를 다시 검색하면 필터는 남기고 첫 페이지로 돌아간다', () => {
        const next = changeSearchTarget(url('type=developers&stack=Java&page=3'), 'developer', 'Kotlin');
        expect(next.getAll('stack')).toEqual(['Java']);
        expect(next.has('page')).toBe(false);
    });
});

describe('BFF 쿼리 확인', () => {
    it('허용한 값만 남기고 사용자 ID 같은 다른 값은 넘기지 않는다', () => {
        expect(searchProxyQuery(url('type=developers&q=java&userId=3&followerId=3&skills=+Java+&skills=Java&available=true&page=1&size=20&jobRole=BACKEND'))?.toString())
            .toBe('type=developers&q=java&available=true&page=1&size=20&skills=Java');
        expect(searchProxyQuery(url('type=projects&status=RECRUITING&techStacks=React&skills=Java&following=true'))?.toString())
            .toBe('type=projects&status=RECRUITING&techStacks=React');
    });
    it('형식이 틀리면 거절한다', () => {
        const many = Array.from({ length: 31 }, (_, i) => `skills=v${i}`).join('&');
        for (const query of ['', 'type=users', 'type=developers&type=projects', `type=developers&q=${'가'.repeat(101)}`, 'type=developers&q=a&q=b',
            'type=developers&available=yes', 'type=developers&page=-1', 'type=developers&size=0', 'type=developers&size=101',
            `type=developers&jobCategory=${'a'.repeat(31)}`, `type=developers&${many}`, `type=projects&techStacks=${'a'.repeat(31)}`,
            'type=projects&status=CLOSED', 'type=projects&jobRole=%07'])
            expect(searchProxyQuery(url(query)), query).toBeNull();
        expect(searchProxyQuery(url(`type=developers&q=${'가'.repeat(100)}&size=100`))).not.toBeNull();
    });
});

describe('검색 응답 해석', () => {
    it('개발자 응답에서 알 수 없는 필드는 빼고 형식이 틀리면 거절한다', () => {
        const result = parseSearchResult('developers', page([{ ...developer('dev-one'), email: 'a@b.c', id: 7 }]));
        expect(result.type).toBe('developers');
        expect(result.page.items[0]).toEqual(developer('dev-one'));
        for (const value of [
            page([developer('bad tag')]), page([developer('dev-one'), developer('dev-one')], { totalCount: 2 }),
            page([developer('dev-one', { trustScore: 'hot' as never })]), page([developer('dev-one')], { totalPages: 2 }),
            page([developer('dev-one')], { totalCount: 0, totalPages: 0 }), { items: [], page: 0, size: 0, totalCount: 0, totalPages: 0 },
        ]) expect(() => parseSearchResult('developers', value)).toThrow();
    });
    it('프로젝트 응답은 모집 상태 5개만 받는다', () => {
        expect(parseSearchResult('projects', page([{ ...project, hostMemberId: 1, viewCount: 9 }])).page.items[0]).toEqual(project);
        expect(() => parseSearchResult('projects', page([{ ...project, status: 'CLOSED' }]))).toThrow();
        expect(() => parseSearchResult('projects', page([{ ...project, id: 0 }]))).toThrow();
    });
    it('전체 페이지보다 큰 페이지는 빈 목록과 실제 수로 받는다', () => {
        expect(parseSearchResult('developers', { items: [], page: 5, size: 20, totalCount: 21, totalPages: 2 }).page.totalPages).toBe(2);
    });
});

describe('팔로우 상태 반영 (6.5·6.6)', () => {
    it('전체 탭에서는 응답의 최종 상태로 버튼만 바꾼다', () => {
        const next = applyFollowChange(page([developer('a'), developer('b')]), 'b', true, false);
        expect(next.items.map(item => item.followed)).toEqual([false, true]);
        expect(next.totalCount).toBe(2);
    });
    it('팔로잉 탭에서 언팔로우하면 카드를 바로 빼고 결과 수를 줄인다', () => {
        const next = applyFollowChange(page([developer('a'), developer('b')], { size: 1, totalCount: 2, totalPages: 2, items: [developer('a')] }), 'a', false, true);
        expect(next.items).toEqual([]);
        expect(next.totalCount).toBe(1);
        expect(next.totalPages).toBe(1);
        expect(applyFollowChange(page([developer('a')]), 'x', false, true).totalCount).toBe(1);
    });
    it('다시 받은 페이지가 비어 있으면 앞 페이지로 간다', () => {
        expect(pageAfterEmpty({ items: [], page: 3, size: 20, totalCount: 40, totalPages: 2 })).toBe(1);
        expect(pageAfterEmpty({ items: [], page: 1, size: 20, totalCount: 20, totalPages: 1 })).toBe(0);
        expect(pageAfterEmpty({ items: [], page: 0, size: 20, totalCount: 0, totalPages: 0 })).toBeNull();
        expect(pageAfterEmpty(page([developer('a')], { page: 2 }))).toBeNull();
    });
});

describe('필터 선택지', () => {
    it('Mock 끄기는 백엔드 enum과 프로젝트 모집 직군 라벨을 쓰고 Mock 값을 섞지 않는다', () => {
        const choices = apiFilterChoices({ jobCategories: ['백엔드 개발자'], skills: ['Java'], interests: ['핀테크'] });
        expect(choices).toMatchObject({ jobs: ['백엔드 개발자'], stacks: ['Java'], fields: ['핀테크'] });
        expect(choices.roles).toContain('DevOps');
        expect(choices.statuses).toEqual(['모집 중', '모집 마감', '모집 완료', '프로젝트 진행 중', '프로젝트 종료']);
        expect(apiFilterChoices(null).jobs).toEqual([]);
    });
    it('Mock 켜기의 선택지는 Mock 기본 목록에 Mock 데이터의 값을 더한다', () => {
        const choices = mockFilterChoices('developer', [{ stacks: ['Rust'], interests: ['우주'] }] as never, []);
        expect(choices.stacks).toContain('Rust');
        expect(choices.stacks).toContain('React');
        expect(choices.fields).toContain('우주');
        expect(mockFilterChoices('project', [], [{ stacks: ['Go'], field: '헬스케어' }] as never).fields).toContain('헬스케어');
    });
});

describe('검색 BFF (/api/search)', () => {
    const request = (query: string) => GET(new Request(`http://localhost/api/search?${query}`));
    it('확인한 쿼리만 백엔드로 넘기고 응답을 해석해 돌려준다', async () => {
        vi.mocked(backendFetch).mockResolvedValue(Response.json(page([{ ...developer('dev-one'), email: 'x@y.z' }])));
        const response = await request('type=developers&q=java&userId=9&page=0&size=20');
        expect(response.status).toBe(200);
        expect(vi.mocked(backendFetch).mock.calls[0][0]).toBe('/api/v1/search?type=developers&q=java&page=0&size=20');
        const body = await response.json();
        expect(body.items[0].email).toBeUndefined();
        expect(response.headers.get('Cache-Control')).toBe('no-store');
    });
    it('형식 오류는 백엔드를 부르지 않고 400으로 답한다', async () => {
        const response = await request('type=users');
        expect(response.status).toBe(400);
        expect(backendFetch).not.toHaveBeenCalled();
    });
    it('백엔드 오류는 상태별 문구만 보내고, 응답 형식이 틀리면 502로 답한다', async () => {
        vi.mocked(backendFetch).mockResolvedValue(Response.json({ message: '내부 오류 상세' }, { status: 401 }));
        const unauthorized = await request('type=developers&following=true&page=0&size=20');
        expect(unauthorized.status).toBe(401);
        expect((await unauthorized.json()).message).toBe('로그인이 필요해요. 다시 로그인해 주세요.');
        vi.mocked(backendFetch).mockResolvedValue(Response.json({ message: '내부 오류 상세' }, { status: 500 }));
        const failed = await request('type=projects&page=0&size=20');
        expect(failed.status).toBe(502);
        expect((await failed.json()).message).not.toContain('내부');
        vi.mocked(backendFetch).mockResolvedValue(Response.json(page([project], { page: 1 })));
        expect((await request('type=projects&page=0&size=20')).status).toBe(502);
        vi.mocked(backendFetch).mockRejectedValue(new Error('down'));
        expect((await request('type=projects')).status).toBe(502);
    });
});


describe('개발자 탐색 전용 URL', () => {
    it('이전 프로젝트 조건을 제거하고 API에서도 개발자만 조회한다', () => {
        const original = url('type=projects&q=Java&role=BACKEND&field=웹&status=RECRUITING&stack=Java&interest=핀테크&tab=following&page=2');
        const normalized = developerSearchParams(original);
        const query = searchApiQuery(parseApiSearch(normalized), explorePage(normalized));
        expect(query.get('type')).toBe('developers');
        expect(query.get('q')).toBe('Java');
        expect(query.get('page')).toBe('2');
        expect(query.get('following')).toBe('true');
        for (const key of ['role', 'field', 'status']) expect(normalized.has(key)).toBe(false);
        expect(original.get('type')).toBe('projects');
    });
});
