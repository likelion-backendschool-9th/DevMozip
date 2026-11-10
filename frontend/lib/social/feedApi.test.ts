import { describe, expect, it } from 'vitest';
import {
    appendFeedPosts, categoryCode, categoryLabel, FEED_VIEWS_KEY, feedAuthorHref, feedDraftForm, feedListQuery, feedListSearch,
    feedPostId, isFirstFeedView, parseFeedComment, parseFeedLike, parseFeedPage, parseFeedPost, readFeedViews, recordFeedView,
    validateFeedDraft,
} from './feedApi';
import type { FeedPostDraft } from '@/types/feedApi';
import { apiComment, apiPost } from '@/tests/helpers/feedApiFixture';

const memoryStorage = () => {
    const data = new Map<string, string>();
    return { data, getItem: (key: string) => data.get(key) ?? null, setItem: (key: string, value: string) => { data.set(key, value); } };
};

const draft = (extra: Partial<FeedPostDraft> = {}): FeedPostDraft => ({
    category: 'INFO', title: '제목', content: '본문', techStacks: [], interests: [], keepImageUrls: [], files: [], ...extra,
});

describe('피드 응답 해석', () => {
    it('게시글에서 필요한 값만 남기고 작성자의 알 수 없는 필드를 버린다', () => {
        const post = parseFeedPost(apiPost());
        expect(post.author).toEqual({ id: 1, nickname: '김하늘', tag: 'member', profileImage: null, jobCategories: ['프론트엔드 개발자'] });
        expect(post.imageUrls).toEqual(['/images/feed_7_20261110101500_1.png']);
    });
    it('서버 저장 경로가 아닌 이미지 주소나 잘못된 값은 거절한다', () => {
        for (const extra of [{ imageUrls: ['https://evil.example/a.png'] }, { imageUrls: ['/images/../secret.png'] }, { category: '정보' },
            { visibility: 'PUBLIC' }, { likeCount: -1 }, { createdAt: 'yesterday' }, { original: { id: 1, available: false } }]) {
            expect(() => parseFeedPost(apiPost(extra))).toThrow();
        }
    });
    it('퍼간 글은 원본 요약이 있어야 하고, 원본을 볼 수 없으면 id만 남긴다', () => {
        const repost = parseFeedPost(apiPost({ type: 'REPOST', title: null, imageUrls: [], original: { id: 3, available: false, title: 'x' } }));
        expect(repost.original).toEqual({ id: 3, available: false, category: null, title: null, author: null });
        expect(() => parseFeedPost(apiPost({ type: 'REPOST', original: null }))).toThrow();
        expect(() => parseFeedPost(apiPost({ type: 'REPOST', original: { id: 3, available: false }, myRepost: { id: 9, content: null } }))).toThrow();
    });
    it('목록 페이지와 중복 id를 확인한다', () => {
        expect(parseFeedPage({ items: [apiPost()], page: 0, size: 20, hasNext: false, totalCount: 1 }).items).toHaveLength(1);
        expect(() => parseFeedPage({ items: [apiPost(), apiPost()], page: 0, size: 20, hasNext: false, totalCount: 2 })).toThrow();
        expect(() => parseFeedPage({ items: [apiPost(), apiPost({ id: 8 })], page: 0, size: 1, hasNext: true, totalCount: 2 })).toThrow();
    });
    it('좋아요·댓글 응답을 확인한다', () => {
        expect(parseFeedLike({ postId: 7, liked: true, likeCount: 1 })).toEqual({ postId: 7, liked: true, likeCount: 1 });
        expect(() => parseFeedLike({ postId: 7, liked: true, likeCount: 0 })).toThrow();
        expect(parseFeedComment(apiComment({ mine: true })).mine).toBe(true);
        expect(parseFeedComment(apiComment()).author).not.toHaveProperty('email');
    });
});

describe('피드 목록 조건', () => {
    it('허용한 조건만 받고 기본 정렬을 채운다', () => {
        const query = feedListQuery(new URLSearchParams('category=INFO&q=%20react%20&page=1&size=20&seed=42'), 'recommended');
        expect(query).toEqual({ category: 'INFO', q: 'react', sort: 'recommended', page: 1, size: 20, seed: 42 });
        expect(feedListSearch(query!)).toBe('category=INFO&q=react&sort=recommended&page=1&size=20&seed=42');
        expect(feedListQuery(new URLSearchParams(''), 'latest')).toEqual({ category: null, q: '', sort: 'latest', page: 0, size: 20, seed: 0 });
    });
    it('모르는 조건·잘못된 값·중복 값은 거절한다', () => {
        for (const search of ['userId=1', 'category=정보', 'sort=oldest', 'page=-1', 'size=0', 'size=101', 'seed=x', 'page=1&page=2',
            `q=${'a'.repeat(101)}`]) expect(feedListQuery(new URLSearchParams(search), 'latest')).toBeNull();
    });
    it('카테고리 문구와 코드를 바꾼다', () => {
        expect(categoryCode('피드백')).toBe('FEEDBACK');
        expect(categoryCode('전체')).toBeNull();
        expect(categoryLabel('COMMUNITY')).toBe('자유');
    });
    it('다음 페이지를 붙일 때 이미 받은 글은 다시 넣지 않는다', () => {
        const first = [parseFeedPost(apiPost({ id: 1 })), parseFeedPost(apiPost({ id: 2 }))];
        const next = [parseFeedPost(apiPost({ id: 2 })), parseFeedPost(apiPost({ id: 3 }))];
        expect(appendFeedPosts(first, next).map(item => item.id)).toEqual([1, 2, 3]);
    });
    it('경로 id와 작성자 프로필 주소', () => {
        expect(feedPostId('12')).toBe(12);
        for (const value of ['0', '01', 'abc', '', undefined, '1'.repeat(17)]) expect(feedPostId(value)).toBeNull();
        expect(feedAuthorHref({ tag: 'dev one' })).toBe('/users/dev%20one');
        expect(feedAuthorHref({ tag: null })).toBeNull();
    });
});

describe('조회수 기록', () => {
    it('처음 읽는 글만 집계 대상이고, 기록은 버전이 있는 키에 남는다', () => {
        const storage = memoryStorage();
        expect(isFirstFeedView(storage, 7)).toBe(true);
        recordFeedView(storage, 7);
        recordFeedView(storage, 7);
        expect(isFirstFeedView(storage, 7)).toBe(false);
        expect(isFirstFeedView(storage, 8)).toBe(true);
        expect(JSON.parse(storage.data.get(FEED_VIEWS_KEY)!)).toEqual([7]);
    });
    it('저장소가 없거나 값이 깨져 있으면 처음 읽는 글로 본다', () => {
        expect(isFirstFeedView(undefined, 7)).toBe(true);
        const broken = memoryStorage();
        broken.data.set(FEED_VIEWS_KEY, '{oops');
        expect(readFeedViews(broken).size).toBe(0);
        const throwing = { getItem: () => { throw new Error('blocked'); }, setItem: () => { throw new Error('blocked'); } };
        expect(isFirstFeedView(throwing, 7)).toBe(true);
        expect(() => recordFeedView(throwing, 7)).not.toThrow();
    });
});

describe('작성 입력', () => {
    it('서버와 같은 기준으로 확인한다', () => {
        expect(validateFeedDraft(draft())).toEqual([]);
        expect(validateFeedDraft(draft({ title: ' ', content: '' }))).toEqual(['제목을 1~150자로 입력해 주세요.', '본문을 1~10,000자로 입력해 주세요.']);
        expect(validateFeedDraft(draft({ techStacks: ['React', 'react '] }))).toHaveLength(1);
        expect(validateFeedDraft(draft({ interests: ['a'.repeat(31)] }))).toHaveLength(1);
        const image = new File([new Uint8Array(10)], 'a.png', { type: 'image/png' });
        expect(validateFeedDraft(draft({ files: [image], keepImageUrls: ['/images/a_1.png', '/images/a_2.png'] }))).toEqual([]);
        expect(validateFeedDraft(draft({ files: [image, image], keepImageUrls: ['/images/a_1.png', '/images/a_2.png'] }))).toHaveLength(1);
        expect(validateFeedDraft(draft({ files: [new File(['x'], 'a.svg', { type: 'image/svg+xml' })] }))).toHaveLength(1);
        expect(validateFeedDraft(draft({ files: [new File([new Uint8Array(512 * 1024 + 1)], 'big.png', { type: 'image/png' })] }))).toHaveLength(1);
    });
    it('글 내용은 JSON 파트, 이미지는 파일 파트로 만들고 수정할 때만 남길 이미지를 보낸다', async () => {
        const image = new File([new Uint8Array(4)], 'a.png', { type: 'image/png' });
        const form = feedDraftForm(draft({ title: ' 제목 ', techStacks: [' React '], keepImageUrls: ['/images/a_1.png'], files: [image] }), false);
        const post = form.get('post') as Blob;
        expect(post.type).toBe('application/json');
        expect(JSON.parse(await post.text())).toEqual({ category: 'INFO', title: '제목', content: '본문', techStacks: ['React'], interests: [] });
        expect(form.getAll('images')).toHaveLength(1);
        const editing = feedDraftForm(draft({ keepImageUrls: ['/images/a_1.png'] }), true);
        expect(JSON.parse(await (editing.get('post') as Blob).text()).keepImageUrls).toEqual(['/images/a_1.png']);
    });
});
