import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import { FeedPreview } from './FeedPreview';
import { fetchHomeFeed } from '@/lib/social/homeFeedService';
import { parseFeedPost } from '@/lib/social/feedApi';
import { apiPost } from '@/tests/helpers/feedApiFixture';

// DOM 없는 테스트 환경에서 상태·effect를 진행해 API 응답 뒤 실제 카드/안내 마크업을 검사한다.
const harness = vi.hoisted(() => ({
    state: [] as unknown[], cursor: 0, effects: [] as (() => void | (() => void))[],
    setters: [] as ((value: unknown) => void)[], user: null as { email: string } | null,
}));
vi.mock('react', async original => ({
    ...await original<typeof import('react')>(),
    useState: (initial: unknown) => {
        const index = harness.cursor++;
        if (!(index in harness.state)) harness.state[index] = typeof initial === 'function' ? initial() : initial;
        const set = (value: unknown) => { harness.state[index] = typeof value === 'function' ? value(harness.state[index]) : value; };
        harness.setters[index] = set;
        return [harness.state[index], set];
    },
    useEffect: (effect: () => void | (() => void)) => { harness.effects.push(effect); },
}));
vi.mock('@/lib/state/authStore', () => ({ useAuth: () => ({ user: harness.user }) }));
vi.mock('@/lib/social/socialService', () => ({ recommendationSeed: () => 77 }));
vi.mock('@/lib/social/homeFeedService', async original => ({
    ...await original<typeof import('@/lib/social/homeFeedService')>(), fetchHomeFeed: vi.fn(),
}));
function render() {
    harness.cursor = 0;
    harness.effects = [];
    return renderToStaticMarkup(<FeedPreview />);
}
async function load() {
    render();
    harness.effects[0]();
    await vi.waitFor(() => expect(harness.state[4]).toHaveProperty('key', expect.not.stringMatching(/^$/)));
    return render();
}
beforeEach(() => {
    harness.state = []; harness.setters = []; harness.user = null;
    vi.resetAllMocks();
    vi.mocked(fetchHomeFeed).mockResolvedValue({ items: [], followingCount: null });
});
afterEach(() => vi.unstubAllGlobals());

it('첫 화면은 로컬 글 대신 API 로딩 안내를 표시한다', () => {
    const html = render();
    expect(html).toContain('피드를 불러오는 중이에요.');
    expect(html).not.toContain('/feeds/react-patterns');
});
it('비로그인 팔로잉은 요청 없이 로그인 안내와 작은 이동 링크를 표시한다', () => {
    render(); harness.setters[0]('following');
    const html = render(); harness.effects[0]();
    expect(fetchHomeFeed).not.toHaveBeenCalled();
    expect(html).toContain('팔로우한 개발자의 소식을 보려면 로그인해 주세요.');
    expect(html).toContain('/login?next=');
    expect(html).not.toContain('SNS 게시글 가로 슬라이드');
});
it.each([0, 2])('빈 팔로잉의 팔로우 수 %s에 맞는 안내를 표시한다', async followingCount => {
    harness.user = { email: 'me@example.com' };
    render(); harness.setters[0]('following');
    vi.mocked(fetchHomeFeed).mockResolvedValue({ items: [], followingCount });
    const html = await load();
    expect(html).toContain(followingCount === 0 ? '아직 팔로우한 개발자가 없어요.' : '팔로우한 개발자의 게시글이 아직 없어요.');
    expect(html.includes('href="/explore"')).toBe(followingCount === 0);
});
it('서버 카드의 제목·작성자·숫자 ID·반응 수와 조건을 유지하는 더보기를 표시한다', async () => {
    vi.mocked(fetchHomeFeed).mockResolvedValue({ items: [parseFeedPost(apiPost({ title: 'API 게시글', likeCount: 8 }))], followingCount: null });
    render(); harness.setters[1]('INFO');
    const html = await load();
    for (const text of ['API 게시글', '/feeds/7', '/users/member', '좋아요 8개', '댓글 1개', 'SNS 게시글 가로 슬라이드', '이 조건의 피드 더 보기', 'category=%EC%A0%95%EB%B3%B4']) expect(html).toContain(text);
});
it('실패하면 빈 목록 대신 오류와 재시도를 표시한다', async () => {
    vi.mocked(fetchHomeFeed).mockRejectedValue(new Error('연결 실패'));
    const html = await load();
    expect(html).toContain('role="alert"'); expect(html).toContain('연결 실패'); expect(html).toContain('다시 시도');
    expect(html).not.toContain('아직 이 카테고리의 소식이 없어요.');
});
it('로그아웃 즉시 이전 사용자의 팔로잉 글을 숨긴다', async () => {
    harness.user = { email: 'me@example.com' };
    render(); harness.setters[0]('following');
    vi.mocked(fetchHomeFeed).mockResolvedValue({ items: [parseFeedPost(apiPost({ title: '내 팔로잉 글' }))], followingCount: null });
    expect(await load()).toContain('내 팔로잉 글');
    harness.user = null;
    const html = render();
    expect(html).not.toContain('내 팔로잉 글'); expect(html).toContain('로그인해 주세요.');
});
it('조건 변경으로 취소된 응답은 새 목록을 덮어쓰지 않는다', async () => {
    let complete: ((value: Awaited<ReturnType<typeof fetchHomeFeed>>) => void) | undefined;
    vi.mocked(fetchHomeFeed).mockImplementation(() => new Promise(resolve => { complete = resolve; }));
    render();
    const cleanup = harness.effects[0]();
    if (typeof cleanup === 'function') cleanup();
    complete?.({ items: [parseFeedPost(apiPost())], followingCount: null });
    await Promise.resolve();
    expect(harness.state[4]).toHaveProperty('key', '');
});
