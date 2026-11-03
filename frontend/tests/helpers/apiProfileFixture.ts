import type { ProfileDetail } from '@/types/profileDetail';
export const apiProfileFixture: ProfileDetail = {
    id: 25, tag: 'api-developer', nickname: 'API 개발자',
    jobCategory: { id: 1, name: '백엔드 개발자' }, summary: '서버에서 가져온 소개', career: 18,
    profileImage: null, bannerImage: null, collaborationStatus: 'AVAILABLE', trustScore: 38.5,
};
