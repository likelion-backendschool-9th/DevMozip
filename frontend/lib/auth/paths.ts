const protectedPrefixes = ['/ai/matching', '/my-projects', '/onboarding', '/profile/me', '/bookmarks', '/settings', '/messages', '/chats', '/notifications', '/portfolios', '/admin'];
export const hasPrefix = (path: string, prefix: string) => path === prefix || path.startsWith(`${prefix}/`);
export function isProtectedPath(path: string) {
    return protectedPrefixes.some(prefix => hasPrefix(path, prefix))
        || /^\/projects\/(new|[^/]+\/(manage|edit|reviews(?:\/write)?))(?:\/|$)/.test(path)
        || /^\/(?:posts|feeds)\/(new|[^/]+\/edit)(?:\/|$)/.test(path);
}
export function safeNext(value: unknown) {
    if (typeof value !== 'string' || !value.startsWith('/') || value.startsWith('//') || /[\\\r\n\u0000-\u0020]/.test(value)) return '/';
    try {
        const url = new URL(value, 'https://internal.invalid');
        return url.origin === 'https://internal.invalid' ? `${url.pathname}${url.search}${url.hash}` : '/';
    } catch { return '/'; }
}
