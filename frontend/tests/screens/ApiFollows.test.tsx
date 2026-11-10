import { afterEach, describe, expect, it } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import { FollowListResult, type FollowListState } from '@/app/(user)/users/[userId]/follows/_components/FollowListResult';
import FollowsPage from '@/app/(user)/users/[userId]/follows/page';
import { FollowsPrivateNotice } from '@/app/(user)/users/[userId]/follows/_components/ApiFollowsContent';
import { ProfileApiResult } from '@/app/(user)/users/[userId]/_components/ApiProfileContent';
import { ApiFollowSettings } from '@/app/(user)/users/[userId]/_components/ApiFollowSettings';
import { apiProfileFixture } from '@/tests/helpers/apiProfileFixture';
import { resetLocalStores } from '@/lib/localStore';
import { syncServerSession } from '@/lib/state/authStore';
import { MemoryRouter, Route, Routes } from '@/tests/helpers/RouterFixture';
import type { FollowUser } from '@/types/followApi';

const person = (tag: string | null, extra: Partial<FollowUser> = {}): FollowUser => ({
    tag, nickname: tag ? `닉네임-${tag}` : '태그 없는 회원', profileImage: null, jobCategories: ['백엔드 개발자'], career: '2년 차',
    skills: ['Java', 'Spring Boot'], collaborationStatus: 'AVAILABLE', followed: false, activityNotificationEnabled: false, ...extra,
});
const ready = (items: FollowUser[], extra: Partial<Extract<FollowListState, { status: 'ready' }>> = {}): FollowListState =>
    ({ status: 'ready', items, hasNext: false, totalCount: items.length, page: 0, more: 'idle', ...extra });
const noop = () => {};
const list = (state: FollowListState, options: { signedIn?: boolean; ownTag?: string | null; filtered?: boolean } = {}) => renderToStaticMarkup(
    <FollowListResult state={state} filtered={options.filtered ?? false} signedIn={options.signedIn ?? false} ownTag={'ownTag' in options ? options.ownTag : null}
        loginHref="/login?next=%2Fusers%2Fdev-one%2Ffollows" onRetry={noop} onMore={noop} onChange={noop} onError={noop} onSettings={noop} />);
afterEach(() => resetLocalStores());

describe('API 팔로워·팔로잉 목록', () => {
    it('서버 항목을 기존 카드 배치로 보여 주고 비로그인에는 로그인 버튼을 둔다', () => {
        const html = list(ready([person('dev-one', { collaborationStatus: 'BUSY', career: null })]));
        for (const text of ['닉네임-dev-one', '@dev-one', '백엔드 개발자', '경력 미입력', 'Spring Boot', '협업 쉬는 중', '로그인하고 팔로우'])
            expect(html).toContain(text);
        expect(html).toContain('href="/users/dev-one"');
        expect(html).toContain('href="/login?next=%2Fusers%2Fdev-one%2Ffollows"');
        expect(html).not.toContain('활동 알림');
        expect(html).not.toContain('>팔로우<');
    });
    it('로그인하면 팔로우 상태와 알림 요약·설정 버튼을 보여 준다', () => {
        const html = list(ready([person('dev-one', { followed: true, activityNotificationEnabled: true }), person('dev-two')]), { signedIn: true, ownTag: 'me-tag' });
        expect(html).toContain('활동 알림 켜짐');
        expect(html).toContain('aria-label="닉네임-dev-one 언팔로우"');
        expect(html).toContain('aria-pressed="true"');
        expect(html).toContain('닉네임-dev-one 알림 설정');
        expect(html).toContain('aria-label="닉네임-dev-two 팔로우"');
        expect(html).not.toContain('닉네임-dev-two 알림 설정');
        expect(html).not.toContain('새 프로젝트 모집 알림');
    });
    it('내 항목은 내 프로필로, 태그 없는 회원은 링크·버튼 없이 표시한다', () => {
        const html = list(ready([person('me-tag'), person(null)]), { signedIn: true, ownTag: 'me-tag' });
        expect(html).toContain('내 프로필');
        expect(html).not.toContain('닉네임-me-tag 팔로우');
        expect(html).toContain('태그 없는 회원');
        expect(html).toContain('사용자 아이디 미설정');
        expect(html).not.toContain('태그 없는 회원 팔로우');
        expect(html.match(/href="\/users\//g)).toHaveLength(3);
    });
    it('내 프로필을 아직 모르면 버튼을 숨긴다', () => {
        const html = list(ready([person('dev-one')]), { signedIn: true, ownTag: undefined });
        expect(html).not.toContain('팔로우');
        expect(html).toContain('닉네임-dev-one');
    });
    it('다음 페이지 버튼과 오류 재시도를 보여 준다', () => {
        expect(list(ready([person('dev-one')], { hasNext: true, totalCount: 30 }))).toContain('더 보기');
        expect(list(ready([person('dev-one')], { hasNext: true, more: 'loading' }))).toContain('불러오는 중…');
        const failed = list(ready([person('dev-one')], { hasNext: true, more: 'error', moreMessage: '다음 목록을 불러오지 못했어요.' }));
        expect(failed).toContain('다음 목록을 불러오지 못했어요.');
        expect(failed).toContain('다시 불러오기');
        expect(list(ready([person('dev-one')]))).not.toContain('더 보기');
    });
    it('로딩·빈 목록·오류를 구분한다', () => {
        expect(list({ status: 'loading' })).toContain('role="status"');
        expect(list(ready([]))).toContain('새로운 개발자를 찾아 연결해 보세요.');
        expect(list(ready([]), { filtered: true })).toContain('검색어나 필터를 바꿔 보세요.');
        const error = list({ status: 'error', message: '사용자를 찾을 수 없어요.' });
        expect(error).toContain('role="alert"');
        expect(error).toContain('다시 시도');
    });
    it('경로 값을 태그로 읽고 Mock 프로필 대신 서버 응답을 기다린다', () => {
        syncServerSession({ identity: null });
        const html = renderToStaticMarkup(<MemoryRouter initialEntries={['/users/api-developer/follows?tab=followers&q=react&available=yes']}>
            <Routes><Route path="/users/:userId/follows" element={<FollowsPage />} /></Routes></MemoryRouter>);
        expect(html).toContain('개발자 연결');
        expect(html).toContain('목록을 불러오는 중…');
        expect(html).toContain('value="react"');
        expect(html).toContain('checked=""');
        expect(html).toContain('href="/users/api-developer"');
        expect(html).not.toContain('이유나');
    });
    it('태그 형식이 아닌 경로는 바로 찾을 수 없다고 안내한다', () => {
        const html = renderToStaticMarkup(<MemoryRouter initialEntries={['/users/bad.tag/follows']}>
            <Routes><Route path="/users/:userId/follows" element={<FollowsPage />} /></Routes></MemoryRouter>);
        expect(html).toContain('사용자를 찾을 수 없어요.');
    });
    it('비공개 계정의 목록은 Mock 목록 화면과 같은 안내를 보여 준다', () => {
        const html = renderToStaticMarkup(<FollowsPrivateNotice />);
        expect(html).toContain('<h1>비공개 계정입니다.</h1>');
        expect(html).toContain('이 사용자의 팔로워와 팔로잉 목록은 공개되지 않습니다.');
        expect(html).toContain('href="/explore"');
    });
    it('알림 설정 창은 체크박스 하나로 활동 알림을 고른다', () => {
        const html = renderToStaticMarkup(<ApiFollowSettings slug="dev-one" name="개발자" enabled onSaved={noop} onClose={noop} />);
        expect(html).toContain('활동 알림 (새 게시글·새 프로젝트 모집)');
        expect(html.match(/type="checkbox"/g)).toHaveLength(1);
        expect(html).toContain('checked=""');
    });
});

describe('API 프로필 상단의 팔로우 영역', () => {
    it('수를 누르면 태그 기준 목록으로 이동한다', () => {
        const html = renderToStaticMarkup(<ProfileApiResult result={{ status: 'ready', profile: apiProfileFixture }} onRetry={noop} />);
        expect(html).toContain('href="/users/api-developer/follows?tab=followers"');
        expect(html).toContain('href="/users/api-developer/follows?tab=following"');
        expect(html).not.toContain('팔로워 조회 연결 전');
    });
    it('태그가 없는 내 프로필은 목록 버튼을 막고 태그 설정을 안내한다', () => {
        const html = renderToStaticMarkup(<ProfileApiResult result={{ status: 'ready', profile: { ...apiProfileFixture, tag: null } }} onRetry={noop} onEdit={noop} />);
        expect(html).toContain('사용자 아이디(태그)를 정하면 팔로워·팔로잉을 볼 수 있어요.');
        expect(html).not.toContain('/follows?tab=');
    });
});
