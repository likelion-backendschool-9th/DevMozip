import { api } from '@/lib/shared/api';
import type { CreateProjectInput } from '@/types/project';
import type { CreateProjectRequest, ProjectCreationReceipt, ProjectRoleCode } from '@/types/projectApi';

const roleCodes: Record<string, ProjectRoleCode> = {
    '프론트엔드': 'FRONTEND', '백엔드': 'BACKEND', '풀스택': 'FULLSTACK', '디자이너': 'DESIGNER',
    'UI/UX': 'DESIGNER', 'DevOps': 'DEVOPS', '데이터 엔지니어': 'DATA_ENGINEER',
    'AI 엔지니어': 'AI_ENGINEER', '모바일': 'MOBILE', '게임': 'GAME', '기타': 'OTHER',
};
export function toProjectCreationRequest(input: CreateProjectInput, imageUrl: string, isPublic: boolean): CreateProjectRequest {
    let image: URL;
    try { image = new URL(imageUrl.trim()); } catch { throw new Error('대표 이미지의 HTTP(S) URL을 입력해 주세요.'); }
    if (!['http:', 'https:'].includes(image.protocol) || imageUrl.trim().length > 2048) throw new Error('대표 이미지의 HTTP(S) URL을 입력해 주세요.');
    if (input.stacks.length > 5) throw new Error('기술 스택은 최대 5개까지 선택해 주세요.');
    return {
        title: input.title.trim(), summary: input.summary.trim() || null, description: input.description.trim(), goal: input.goal.trim() || null,
        imageUrl: imageUrl.trim(), isPublic,
        recruitmentSlots: input.slots.map(slot => {
            const roleCode = roleCodes[slot.role];
            if (!roleCode) throw new Error('모집 직군을 확인해 주세요.');
            return { roleCode, capacity: slot.count, minimumYears: slot.minYears };
        }),
        techStacks: input.stacks, interests: [input.field], recruitmentStart: input.recruitmentStart,
        recruitmentEnd: input.deadline, developmentStart: input.developmentStart, developmentEnd: input.developmentEnd,
    };
}
export function parseProjectCreationReceipt(value: unknown): ProjectCreationReceipt {
    const data = value as Partial<ProjectCreationReceipt> | null;
    if (!data || !Number.isSafeInteger(data.id) || Number(data.id) <= 0 || typeof data.title !== 'string') throw new Error('등록 결과를 확인하지 못했습니다. 중복 등록하지 말고 저장 여부를 먼저 확인해 주세요.');
    return { id: data.id as number, title: data.title };
}
export async function createProjectViaApi(input: CreateProjectRequest): Promise<ProjectCreationReceipt> {
    const response = await api.post('/projects', input);
    return parseProjectCreationReceipt(response.data);
}
