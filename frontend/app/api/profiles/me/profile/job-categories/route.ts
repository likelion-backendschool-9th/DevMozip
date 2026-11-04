import { profileRequestGuard, proxyProfile } from '@/lib/profiles/profileProxy';
export async function GET() {
    return profileRequestGuard() ?? proxyProfile('/api/v1/users/me/profile/job-categories', {}, body => {
        if (!Array.isArray(body) || !body.every(job => typeof job === 'string')) throw new Error('직군 응답 오류');
        return body;
    });
}
