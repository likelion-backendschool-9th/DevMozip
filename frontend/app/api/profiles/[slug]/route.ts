import { NextResponse } from 'next/server';
import { backendFetch } from '@/lib/auth/backend';
import { parseProfileDetail, validProfileSlug } from '@/lib/profiles/profileDetail';

const headers = { 'Cache-Control': 'no-store' };
const error = (status: number, message: string) => NextResponse.json({ message }, { status, headers });

export async function GET(_request: Request, context: { params: Promise<{ slug: string }> }) {
    const { slug } = await context.params;
    if (!validProfileSlug(slug)) return error(400, '올바른 사용자 태그가 필요합니다.');
    try {
        // JWT·백엔드 주소는 서버에만 두고 기존 BFF 통신 경계를 재사용한다.
        const response = await backendFetch(`/api/v1/users/${encodeURIComponent(slug)}`);
        if (response.status === 404) return error(404, '프로필을 찾을 수 없습니다.');
        if (response.status === 401 || response.status === 403) return error(response.status, '프로필을 조회할 권한이 없습니다.');
        if (!response.ok) return error(502, '프로필 정보를 불러오지 못했습니다.');
        const profile = parseProfileDetail(await response.json());
        if (profile.tag !== slug) return error(502, '프로필 정보를 확인할 수 없습니다.');
        return NextResponse.json(profile, { headers });
    } catch {
        return error(502, '프로필 서버에 연결하지 못했습니다. 잠시 후 다시 시도해 주세요.');
    }
}
