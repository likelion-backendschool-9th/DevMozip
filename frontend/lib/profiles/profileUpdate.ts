import { parseProfileDetail } from './profileDetail';
import type { ProfileDetail, ProfileUpdateInput, ProfileImageKind, ProfileImageResult } from '@/types/profileDetail';

export class ProfileSaveError extends Error {
    constructor(public status: number, message: string) { super(message); }
}
async function request(path: string, init: RequestInit = {}) {
    const response = await fetch(path, { ...init, cache: 'no-store' });
    if (!response.ok) {
        const body = await response.json().catch(() => null);
        throw new ProfileSaveError(response.status, typeof body?.message === 'string' ? body.message : '요청을 완료하지 못했어요.');
    }
    return response.json();
}
export async function fetchOwnProfile(signal?: AbortSignal): Promise<ProfileDetail> {
    return parseProfileDetail(await request('/api/profiles/me/profile', { signal }));
}
export async function saveOwnProfile(input: ProfileUpdateInput): Promise<ProfileDetail> {
    return parseProfileDetail(await request('/api/profiles/me/profile', {
        method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(input),
    }));
}
export async function fetchJobCategories(signal?: AbortSignal): Promise<{ id: number; name: string }[]> {
    const value = await request('/api/profiles/me/profile/job-categories', { signal });
    if (!Array.isArray(value) || !value.every(j => Number.isSafeInteger(j.id) && j.id > 0 && typeof j.name === 'string')) throw new Error('직군 목록을 확인할 수 없어요.');
    return value.map(j => ({ id: j.id, name: j.name }));
}
export async function changeOwnImage(kind: ProfileImageKind, file: File | null): Promise<ProfileImageResult> {
    const data = new FormData();
    if (file) data.append('file', file);
    const result = await request(`/api/profiles/me/images/${kind}`, { method: file ? 'PUT' : 'DELETE', ...(file ? { body: data } : {}) });
    if (!result || ![result.profileImage, result.bannerImage].every(v => v === null || typeof v === 'string')) throw new Error('이미지 응답을 확인할 수 없어요.');
    return { profileImage: result.profileImage, bannerImage: result.bannerImage };
}
