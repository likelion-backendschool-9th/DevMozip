// Explicit login handoff only. No guest conversation or server storage is created.
const KEY = 'devmozip:matching-login-prompt:v1';
const MAX_AGE = 10 * 60 * 1000;
export function matchingLoginPath(text: string, now = Date.now()): string {
    const content = text.trim();
    if (!content) return '/login?next=%2Fai%2Fmatching';
    if (content.length > 1500) throw new Error('메시지는 1,500자 이하로 입력해 주세요.');
    const id = crypto.randomUUID();
    try { sessionStorage.setItem(KEY, JSON.stringify({ version: 1, id, content, createdAt: now })); }
    catch { throw new Error('입력을 이어가려면 브라우저의 세션 저장소를 허용해 주세요. 입력은 그대로 유지됩니다.'); }
    return `/login?next=${encodeURIComponent(`/ai/matching?resume=${id}`)}`;
}
export function readLoginPrompt(id: string, now = Date.now()): string | null {
    try {
        const value = JSON.parse(sessionStorage.getItem(KEY) || 'null');
        if (value?.version !== 1 || value.id !== id || typeof value.content !== 'string' || !value.content.trim() || value.content.length > 1500
            || typeof value.createdAt !== 'number' || now < value.createdAt || now - value.createdAt > MAX_AGE) return null;
        return value.content;
    } catch { return null; }
}
export function clearLoginPrompt() { try { sessionStorage.removeItem(KEY); } catch { /* consumed only after account storage succeeds */ } }
