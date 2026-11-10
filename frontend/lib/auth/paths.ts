const protectedPrefixes = ['/my-projects', '/onboarding', '/profile/me', '/bookmarks', '/settings', '/messages', '/chats', '/notifications', '/portfolios', '/admin'];
export const hasPrefix = (path: string, prefix: string) => path === prefix || path.startsWith(`${prefix}/`);
export function isProtectedPath(path: string) {
    return protectedPrefixes.some(prefix => hasPrefix(path, prefix))
        || /^\/projects\/(new|[^/]+\/(manage|edit|reviews(?:\/write)?))(?:\/|$)/.test(path)
        || /^\/(?:posts|feeds)\/(new|[^/]+\/edit)(?:\/|$)/.test(path)
        // 상품 안내(/subscription)는 공개하고 결제·관리·내역만 로그인이 필요하다.
        || /^\/subscription\/(checkout|manage|history)(?:\/|$)/.test(path);
}
export function safeNext(value: unknown) {
    if (typeof value !== 'string' || !value.startsWith('/') || value.startsWith('//') || /[\\\r\n\u0000-\u0020]/.test(value)) return '/';
    try {
        const url = new URL(value, 'https://internal.invalid');
        return url.origin === 'https://internal.invalid' ? `${url.pathname}${url.search}${url.hash}` : '/';
    } catch { return '/'; }
}

// 예전 데모 계정의 내 프로필 복귀 주소만 실제 인증 기반 경로로 교정한다.
// 공개 프로필 탐색이나 메시지의 사용자 식별자는 변경하지 않는다.
export function loginNext(value: unknown) {
    const target = safeNext(value);
    const url = new URL(target, 'https://internal.invalid');
    if (url.pathname === '/login') return '/';
    if (/^\/users\/(demo-user|demo-premium|demo-admin)\/?$/.test(url.pathname)) {
        return `/profile/me${url.search}${url.hash}`;
    }
    return target;
}
