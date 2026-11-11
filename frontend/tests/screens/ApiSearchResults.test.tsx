import { afterEach, describe, expect, it } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import { ApiDeveloperResults } from '@/app/explore/_components/ApiDeveloperResults';
import { ApiProjectResults } from '@/app/explore/_components/ApiProjectResults';
import { SearchPager } from '@/app/explore/_components/SearchPager';
import { resetLocalStores } from '@/lib/localStore';
import type { DeveloperSearchItem, ProjectSearchItem } from '@/types/searchApi';

const person = (tag: string, extra: Partial<DeveloperSearchItem> = {}): DeveloperSearchItem => ({
    tag, nickname: `닉네임-${tag}`, profileImage: null, jobCategories: ['백엔드 개발자', 'DevOps 엔지니어'], career: '3년 차',
    summary: '한 줄 소개입니다', skills: ['Java', 'Spring Boot'], interests: ['핀테크'], collaborationStatus: 'AVAILABLE',
    trustScore: 37.25, followed: false, ...extra,
});
const project: ProjectSearchItem = {
    id: 3, title: '핀테크 정산', summary: '정산 자동화', imageUrl: null, status: 'RECRUITMENT_CLOSED', jobRoles: ['BACKEND', 'DESIGNER', '보안 엔지니어'],
    techStacks: ['Spring Boot', 'React'], interests: ['핀테크', '생산성'], totalCapacity: 4, memberCount: 2,
    developmentStart: '2026-12-01', developmentEnd: '2027-02-28', createdAt: '2026-11-09T10:00:00',
};
const noop = () => {};
const developers = (items: DeveloperSearchItem[], signedIn: boolean, ownTag: string | null | undefined) => renderToStaticMarkup(
    <ApiDeveloperResults items={items} signedIn={signedIn} ownTag={ownTag} onFollowChange={noop} onFollowError={noop} />);
afterEach(() => resetLocalStores());

describe('Mock 끄기 개발자 탐색 카드 (6.4·6.6·6.9)', () => {
    it('Mock 카드와 같은 배치로 서버 항목을 보여 주고 프로필로 이동한다', () => {
        const html = developers([person('dev-one')], false, null);
        for (const text of ['닉네임-dev-one', '백엔드 개발자, DevOps 엔지니어 · 3년 차', '한 줄 소개입니다', 'Spring Boot', '#핀테크', '협업 가능', '37.3°C', '신뢰 온도'])
            expect(html).toContain(text);
        expect(html).toContain('search-developer-card');
        expect(html).toContain('href="/users/dev-one"');
        // 비로그인의 팔로우는 로그인 화면으로 보낸다.
        expect(html).toContain('href="/login"');
    });
    it('협업 상태와 비어 있는 값은 프로필 상단과 같은 문구로 보여 준다', () => {
        const html = developers([person('dev-two', { collaborationStatus: 'BUSY', career: null, summary: null, trustScore: null, jobCategories: [] })], false, null);
        for (const text of ['현재 협업 어려움', '직군 미입력 · 경력 미입력', '아직 작성한 소개가 없습니다.', '—'])
            expect(html).toContain(text);
        expect(html).toContain('unavailable');
    });
    it('로그인하면 응답의 팔로우 상태로 버튼을 보여 주고, 본인 카드에는 내 프로필 보기만 둔다', () => {
        const html = developers([person('me-tag'), person('dev-one', { followed: true }), person('dev-two')], true, 'me-tag');
        expect(html).toContain('내 프로필 보기');
        expect(html).not.toContain('닉네임-me-tag 팔로우');
        expect(html).toContain('aria-label="닉네임-dev-one 언팔로우"');
        expect(html).toContain('aria-pressed="true"');
        expect(html).toContain('aria-label="닉네임-dev-two 팔로우"');
    });
    it('내 프로필을 아직 모르면 버튼을 숨긴다', () => {
        const html = developers([person('dev-one')], true, undefined);
        expect(html).not.toContain('팔로우');
        expect(html).not.toContain('내 프로필 보기');
    });
});

describe('Mock 끄기 프로젝트 탐색 카드 (6.3·6.8·6.9)', () => {
    it('모집 상태·모집 직군 라벨·인원·기간을 보여 주고 상세로 이동한다', () => {
        const html = renderToStaticMarkup(<ApiProjectResults items={[project]} />);
        for (const text of ['핀테크 · 생산성', '모집 마감', '핀테크 정산', '정산 자동화', 'React', '모집 직군 · 백엔드 / UI/UX / 보안 엔지니어', '2/4명', '2026-12-01 — 2027-02-28'])
            expect(html).toContain(text);
        expect(html).toContain('search-project-card');
        expect(html).toContain('>핀</span>');
        expect(html.match(/href="\/projects\/3"/g)).toHaveLength(2);
        // 비로그인의 북마크는 프로젝트 북마크(7.16) 버튼처럼 로그인 화면으로 보낸다.
        expect(html).toContain('aria-label="로그인 후 북마크"');
    });
});

describe('탐색 결과 페이지 이동', () => {
    it('한 페이지 이하이면 숨기고, 첫·마지막 페이지에서는 이전·다음을 막는다', () => {
        expect(renderToStaticMarkup(<SearchPager page={0} totalPages={1} onChange={noop} />)).toBe('');
        const first = renderToStaticMarkup(<SearchPager page={0} totalPages={3} onChange={noop} />);
        expect(first).toContain('<b>1</b> / 3');
        expect(first.match(/disabled=""/g)).toHaveLength(1);
        const last = renderToStaticMarkup(<SearchPager page={2} totalPages={3} onChange={noop} />);
        expect(last).toContain('<b>3</b> / 3');
        expect(last.match(/disabled=""/g)).toHaveLength(1);
        expect(renderToStaticMarkup(<SearchPager page={1} totalPages={3} disabled onChange={noop} />).match(/disabled=""/g)).toHaveLength(2);
    });
});
