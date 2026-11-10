import { BACKEND_URL } from '@/lib/auth/env';

type Context = { params: Promise<{ filename: string }> };

// 업로드 이미지(/images/...)는 nginx가 제공한다. nginx 없이 3000번으로 바로 접속하면(compose 기본 실행, local 프로필)
// 이 경로가 백엔드의 같은 경로(IMAGE_STORAGE 폴더)를 대신 부른다 (docs/feed-plan.md 1절).
// next.config.ts의 rewrites는 주소가 빌드할 때 고정되어 실행 시 BACKEND_URL을 따르지 못하므로 Route Handler로 둔다.
const FILE_NAME = /^[a-z]+_[A-Za-z0-9_-]{1,200}\.(png|jpg|webp|gif)$/;
const TYPES: Record<string, string> = { png: 'image/png', jpg: 'image/jpeg', webp: 'image/webp', gif: 'image/gif' };

export async function GET(_request: Request, context: Context) {
    const { filename } = await context.params;
    const match = FILE_NAME.exec(filename);
    if (!match) return new Response(null, { status: 404 });
    let response: Response;
    try { response = await fetch(`${BACKEND_URL}/images/${filename}`, { cache: 'no-store', signal: AbortSignal.timeout(5000) }); }
    catch { return new Response(null, { status: 502 }); }
    if (!response.ok || !response.body) return new Response(null, { status: response.status === 404 ? 404 : 502 });
    // 형식은 백엔드 응답이 아니라 파일 이름의 확장자로 정한다.
    return new Response(response.body, { status: 200, headers: {
        'Content-Type': TYPES[match[1]], 'X-Content-Type-Options': 'nosniff', 'Cache-Control': 'public, max-age=86400',
    } });
}
