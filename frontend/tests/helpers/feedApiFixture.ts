// 피드 API 응답 예시. 백엔드 FeedPostResponse와 같은 모양이며, 작성자에는 브라우저로 넘기면 안 되는 필드(email)를 일부러 넣었다.
export const apiAuthor = (extra: Record<string, unknown> = {}) => ({
    id: 1, nickname: '김하늘', tag: 'member', profileImage: null, jobCategories: ['프론트엔드 개발자'], email: 'private@x', ...extra,
});

export const apiPost = (extra: Record<string, unknown> = {}) => ({
    id: 7, type: 'POST', category: 'INFO', title: '제목', content: '본문', techStacks: ['React'], interests: ['교육'],
    imageUrls: ['/images/feed_7_20261110101500_1.png'], viewCount: 3, likeCount: 2, commentCount: 1, visibility: 'VISIBLE',
    author: apiAuthor(), original: null, createdAt: '2026-11-10T10:15:00+09:00', updatedAt: '2026-11-10T10:15:00+09:00', edited: false,
    mine: false, liked: false, bookmarked: false, reported: false, myRepost: null, ...extra,
});

export const apiComment = (extra: Record<string, unknown> = {}) => ({
    id: 11, postId: 7, content: '댓글', author: apiAuthor(), createdAt: '2026-11-10T10:15:00+09:00',
    updatedAt: '2026-11-10T10:15:00+09:00', edited: false, mine: false, reported: false, ...extra,
});
