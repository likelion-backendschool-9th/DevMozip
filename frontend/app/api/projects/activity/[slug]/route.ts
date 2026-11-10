import { projectGuard, projectError, proxyProject } from '@/lib/projects/projectProxy';

export async function GET(request: Request, { params }: { params: Promise<{ slug: string }> }) {
    const { slug } = await params;
    if (!slug || slug.length > 100 || /[\/\\?#]/.test(slug) || slug === '.' || slug === '..') return projectError(400, '프로필 태그를 확인해 주세요.');
    if (slug === 'me') {
        const blocked = await projectGuard();
        if (blocked) return blocked;
    }
    const query = new URLSearchParams();
    for (const key of ['page', 'size']) {
        const value = new URL(request.url).searchParams.get(key);
        if (value !== null) query.set(key, value);
    }
    return proxyProject(`/api/v1/users/${encodeURIComponent(slug)}/projects?${query}`);
}
