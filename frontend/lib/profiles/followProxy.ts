import 'server-only';
import { NextResponse } from 'next/server';
import { backendFetch } from '@/lib/auth/backend';
import type { FollowListTab } from '@/types/followApi';
import { validProfileSlug } from './profileDetail';
import { followListQuery, followListSearch, parseFollowPage } from './followApi';
import { profileError } from './profileProxy';

const headers = { 'Cache-Control': 'no-store' };

// 백엔드 오류 본문은 그대로 넘기지 않고 상태별 문구만 보낸다. 프로필 수정용 문구(profileProxy)와 따로 둔다.
const messages: Record<number, string> = {
    400: '요청 내용을 확인해 주세요.',
    401: '로그인이 필요해요. 다시 로그인해 주세요.',
    403: '이 계정으로는 팔로우 기능을 이용할 수 없어요.',
    404: '사용자를 찾을 수 없어요.',
    409: '팔로우한 개발자의 알림만 설정할 수 있어요.',
};

// 쓰기 요청은 같은 사이트에서만 받는다. Docker 개발 서버(--hostname 0.0.0.0)처럼 Next 내부 URL의 호스트가
// 브라우저 주소와 다를 수 있어, 프로젝트 BFF(projectGuard)와 같이 브라우저가 보낸 Host와 비교한다.
export function followRequestGuard(request: Request) {
    const url = new URL(request.url);
    let origin: URL;
    try { origin = new URL(request.headers.get('origin') ?? ''); }
    catch { return profileError(403, '같은 사이트에서 요청해 주세요.'); }
    if (origin.host !== (request.headers.get('host') ?? url.host) || origin.protocol !== url.protocol)
        return profileError(403, '같은 사이트에서 요청해 주세요.');
    return null;
}

export function followSlugError(slug: string) {
    return validProfileSlug(slug) ? null : profileError(400, '올바른 사용자 태그가 필요합니다.');
}

export function followPath(slug: string, name: 'follow' | 'notify' | FollowListTab, search = '') {
    return `/api/v1/users/${encodeURIComponent(slug)}/${name}${search ? `?${search}` : ''}`;
}

export async function proxyFollow<T>(path: string, init: RequestInit, parse: (body: unknown) => T, check?: (value: T) => boolean,
    overrides: Record<number, string> = {}) {
    let response: Response;
    try { response = await backendFetch(path, init); }
    catch { return profileError(502, '팔로우 서버에 연결하지 못했어요. 잠시 후 다시 시도해 주세요.'); }
    if (!response.ok) {
        const message = overrides[response.status] ?? messages[response.status];
        return profileError(message ? response.status : 502, message ?? '팔로우 서버 요청을 완료하지 못했어요.');
    }
    try {
        const value = parse(await response.json());
        if (check && !check(value)) throw new Error('요청과 응답 불일치');
        return NextResponse.json(value, { headers });
    } catch { return profileError(502, '팔로우 서버의 응답 형식을 확인할 수 없어요.'); }
}

/** 팔로워·팔로잉 목록 BFF. 조건을 확인한 값만 백엔드로 보낸다. */
export async function proxyFollowList(request: Request, slug: string, tab: FollowListTab) {
    const invalid = followSlugError(slug);
    if (invalid) return invalid;
    const query = followListQuery(new URL(request.url).searchParams);
    if (!query) return profileError(400, '검색어와 페이지를 확인해 주세요.');
    return proxyFollow(followPath(slug, tab, followListSearch(query)), {}, parseFollowPage,
        page => page.page === query.page && page.size === query.size, { 400: '검색어와 페이지를 확인해 주세요.' });
}
