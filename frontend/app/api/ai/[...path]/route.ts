import { NextResponse } from 'next/server';
import { backendFetch } from '@/lib/auth/backend';

type Context = { params: Promise<{ path: string[] }> };
const headers = { 'Cache-Control': 'no-store' };
const error = (status: number, message: string) => NextResponse.json({ message }, { status, headers });
async function handle(request: Request, context: Context) {
    const { path } = await context.params;
    const route = path.join('/');
    const id = '[1-9][0-9]*';
    // Presentation enrichment reuses the existing public project API; no backend contract changes.
    if (request.method === 'GET' && new RegExp(`^projects/${id}$`).test(route)) {
        if (new URL(request.url).search) return error(400, '조회 조건을 확인해 주세요.');
        try {
            const response = await backendFetch(`/api/v1/${route}`);
            if (!response.ok) return error([401, 403, 404].includes(response.status) ? response.status : 502, '프로젝트 정보를 불러올 수 없습니다.');
            const { project } = await response.json();
            if (!project || project.id !== Number(path[1]) || project.isPublic !== true || typeof project.title !== 'string'
                || !Array.isArray(project.techStacks) || !project.techStacks.every((v: unknown) => typeof v === 'string')
                || !Array.isArray(project.recruitmentSlots)) return error(502, '프로젝트 정보를 확인할 수 없습니다.');
            return NextResponse.json({ id: project.id, title: project.title, summary: typeof project.summary === 'string' ? project.summary : '',
                description: typeof project.description === 'string' ? project.description : '', imageUrl: typeof project.imageUrl === 'string' ? project.imageUrl : null,
                stacks: project.techStacks, roles: project.recruitmentSlots.filter((v: { role?: unknown }) => typeof v?.role === 'string').map((v: { role: string }) => v.role) }, { headers });
        } catch { return error(502, '프로젝트 정보를 불러올 수 없습니다.'); }
    }
    const allowed = request.method === 'GET'
        ? new RegExp(`^conversations(?:/${id}(?:/recommendations(?:/history)?)?)?$`).test(route)
        : request.method === 'DELETE' ? new RegExp(`^conversations/${id}$`).test(route)
        : request.method === 'POST' && (route === 'conversations/sync' || new RegExp(`^conversations/${id}/recommendations$`).test(route));
    if (!allowed) return error(404, '지원하지 않는 AI API 경로입니다.');
    const url = new URL(request.url);
    if ([...url.searchParams.keys()].some(key => key !== 'requestId') ||
        (url.search && (!route.endsWith('/recommendations') || request.method !== 'GET' ||
        url.searchParams.getAll('requestId').length !== 1 || !/^[1-9][0-9]*$/.test(url.searchParams.get('requestId') || '')))) {
        return error(400, '조회 조건을 확인해 주세요.');
    }
    const init: RequestInit = { method: request.method, signal: AbortSignal.timeout(60000) };
    if (request.method !== 'GET') {
        if (request.headers.get('origin') !== `${url.protocol}//${request.headers.get('host') || url.host}`) return error(403, '같은 사이트에서 요청해 주세요.');
        if (request.method === 'POST') {
            if (!request.headers.get('content-type')?.startsWith('application/json')) return error(415, 'JSON 형식이 필요합니다.');
            try { init.body = JSON.stringify(await request.json()); }
            catch { return error(400, 'JSON 내용을 확인해 주세요.'); }
            init.headers = { 'Content-Type': 'application/json' };
            if (route.endsWith('/recommendations')) {
                const key = request.headers.get('idempotency-key') || '';
                if (!/^[A-Za-z0-9._:-]{1,128}$/.test(key)) return error(400, '추천 요청 키를 확인해 주세요.');
                init.headers['Idempotency-Key'] = key;
            }
        }
    }
    try {
        const response = await backendFetch(`/api/v1/ai/${route}${url.search}`, init);
        if (response.status === 204) return new Response(null, { status: 204, headers });
        const body = await response.json().catch(() => null);
        // Recommendation FAILED responses contain the public result DTO; never forward server error details.
        if (response.ok || response.status === 500 && body?.status === 'FAILED' && Number.isSafeInteger(body?.requestId)) {
            return NextResponse.json(body, { status: response.status, headers });
        }
        const status = [400, 401, 403, 404, 409].includes(response.status) ? response.status : 502;
        return error(status, ({ 400: '입력 내용을 확인해 주세요.', 401: '로그인이 만료되었습니다. 다시 로그인해 주세요.',
            403: '이 계정으로 접근할 수 없습니다.', 404: '대화 또는 추천 기록을 찾을 수 없습니다.',
            409: '저장된 요청과 입력이 다릅니다. 대화를 다시 확인해 주세요.' } as Record<number, string>)[status] || 'AI 서버 요청을 완료하지 못했습니다. 잠시 후 다시 시도해 주세요.');
    } catch { return error(502, 'AI 서버에 연결하지 못했습니다. 초안은 유지됩니다. 다시 시도해 주세요.'); }
}
export const GET = handle;
export const POST = handle;
export const DELETE = handle;
