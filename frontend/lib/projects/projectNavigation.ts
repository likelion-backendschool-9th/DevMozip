/** 목록/프로필 내부 경로만 복귀 대상으로 허용한다. 외부 URL이나 API 경로는 사용하지 않는다. */
export function projectReturnTarget(value: string | null | undefined, fallback = '/projects') {
    if (!value || !value.startsWith('/') || value.startsWith('//') || /[\\\p{Cc}]/u.test(value)) return fallback;
    try {
        const url = new URL(value, 'https://devmozip.local');
        if (url.origin !== 'https://devmozip.local' || !/^(\/projects|\/my-projects|\/explore|\/profile\/me|\/users\/[^/]+)$/.test(url.pathname)) return fallback;
        return url.pathname + url.search;
    } catch { return fallback; }
}
export function withProjectReturn(path: string, returnTo?: string | null) {
    if (!returnTo) return path;
    return `${path}?${new URLSearchParams({ returnTo: projectReturnTarget(returnTo) })}`;
}
export function projectDetailHref(id: string | number, returnTo?: string | null) {
    return withProjectReturn(`/projects/${encodeURIComponent(id)}`, returnTo);
}
