// 14.6 게시글·댓글 관리·14.7 표시 여부의 응답 해석과 요청 확인 (docs/admin-plan.md 5.6, D3·D4).
import { fields, type QueryRule } from './adminApi';
import type { AdminComment, AdminPost, AdminPostVisibility } from '@/types/adminApi';

export type AdminPostTarget = 'POST' | 'COMMENT';
export const postTargets: readonly AdminPostTarget[] = ['POST', 'COMMENT'];
export const postVisibilities: readonly AdminPostVisibility[] = ['VISIBLE', 'HIDDEN', 'RESTRICTED'];
export const postVisibilityLabels: Record<AdminPostVisibility, string> = { VISIBLE: '공개', HIDDEN: '작성자 숨김', RESTRICTED: '관리자 숨김' };

export const postQueryRules: Record<string, QueryRule> = { target: postTargets, q: 'text', visibility: postVisibilities };

export function parseAdminPost(value: unknown): AdminPost {
    const f = fields(value, 'post');
    return {
        id: f.num('id'), type: f.oneOf('type', ['POST', 'REPOST'] as const), title: f.optStr('title'), content: f.optStr('content'),
        authorTag: f.optStr('authorTag'), authorNickname: f.str('authorNickname'), visibility: f.oneOf('visibility', postVisibilities),
        createdAt: f.str('createdAt'),
    };
}

export function parseAdminComment(value: unknown): AdminComment {
    const f = fields(value, 'comment');
    return {
        id: f.num('id'), postId: f.num('postId'), postTitle: f.optStr('postTitle'), content: f.str('content'),
        authorTag: f.optStr('authorTag'), authorNickname: f.str('authorNickname'), createdAt: f.str('createdAt'),
    };
}

/** 조회 조건의 target. 없으면 POST다. */
export const postTarget = (search: URLSearchParams): AdminPostTarget => search.get('target') === 'COMMENT' ? 'COMMENT' : 'POST';

/** 관리자 숨김 버튼: 관리자 숨김이면 복원, 그 밖(공개·작성자 숨김)이면 숨김이다. 복원해도 작성자 숨김은 풀리지 않는다. */
export const restrictAction = (post: AdminPost) => post.visibility === 'RESTRICTED'
    ? { restricted: false, label: '공개 복원' } : { restricted: true, label: '관리자 숨김' };
