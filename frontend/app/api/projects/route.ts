import { NextResponse } from 'next/server';
import { backendFetch } from '@/lib/auth/backend';
import { getSession } from '@/lib/auth/session';
import { parseProjectCreationReceipt } from '@/lib/projects/projectCreationApi';

function error(status: number, message: string) {
    return NextResponse.json({ message }, { status, headers: { 'Cache-Control': 'no-store' } });
}
export async function POST(request: Request) {
    if (process.env.PROJECT_CREATION_API_ENABLED !== 'true') return error(503, '프로젝트 등록 서버 연결을 준비 중입니다.');
    if (request.headers.get('origin') !== new URL(request.url).origin) return error(403, '같은 사이트에서 등록해 주세요.');
    if (!request.headers.get('content-type')?.startsWith('application/json')) return error(415, 'JSON 형식이 필요합니다.');
    try {
        const session = await getSession();
        if (!session) return error(401, '로그인 후 등록해 주세요.');
        if (session.role !== 'PREMIUM') return error(403, '모집 공고 작성은 프리미엄 회원만 가능합니다.');
        let input;
        try { input = await request.json(); } catch { return error(400, '입력 내용을 확인해 주세요.'); }
        if (!input || typeof input !== 'object' || Array.isArray(input)) return error(400, '입력 내용을 확인해 주세요.');
        // 주최자 ID·상태·권한·생성 시각은 요청에서 전달하지 않는다.
        const { title, summary, description, goal, imageUrl, isPublic, recruitmentSlots, techStacks, interests,
            recruitmentStart, recruitmentEnd, developmentStart, developmentEnd } = input;
        const response = await backendFetch('/api/v1/projects', {
            method: 'POST', headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ title, summary, description, goal, imageUrl, isPublic, recruitmentSlots, techStacks, interests,
                recruitmentStart, recruitmentEnd, developmentStart, developmentEnd }),
        });
        if (!response.ok) {
            const messages: Record<number, string> = { 400: '입력값과 모집·개발 기간을 확인해 주세요.', 401: '로그인이 만료되었습니다.', 403: '프로젝트 등록 권한이 없습니다.', 409: '현재 상태에서는 등록할 수 없습니다.' };
            return error(response.status >= 500 ? 502 : response.status, messages[response.status] || '프로젝트를 등록하지 못했습니다.');
        }
        if (response.status !== 201) return error(502, '등록 결과를 확인하지 못했습니다. 재등록 전에 저장 여부를 확인해 주세요.');
        const receipt = parseProjectCreationReceipt(await response.json());
        return NextResponse.json(receipt, { status: 201, headers: { 'Cache-Control': 'no-store' } });
    } catch {
        return error(502, '서버 응답을 확인하지 못했습니다. 중복 등록하지 말고 저장 여부를 먼저 확인해 주세요.');
    }
}
