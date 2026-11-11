// 14.4 프로젝트 공고 관리·14.5 표시 여부의 응답 해석 (docs/admin-plan.md 5.5, D2·D3).
import { fields, type QueryRule } from './adminApi';
import { statusLabels } from '@/lib/projects/projectApiContract';
import type { AdminProject } from '@/types/adminApi';
import type { ProjectStatusCode } from '@/types/projectApi';

export const projectStatuses: readonly ProjectStatusCode[] = ['RECRUITING', 'RECRUITMENT_COMPLETED', 'IN_PROGRESS', 'COMPLETED'];
export const projectStatusLabels = statusLabels;

export const projectQueryRules: Record<string, QueryRule> = { q: 'text', status: projectStatuses, restricted: 'bool' };

export function parseAdminProject(value: unknown): AdminProject {
    const f = fields(value, 'project');
    return {
        id: f.num('id'), title: f.str('title'), hostTag: f.optStr('hostTag'), hostNickname: f.optStr('hostNickname'),
        status: f.oneOf('status', projectStatuses), isPublic: f.bool('isPublic'), restricted: f.bool('restricted'),
        recruitmentEnd: f.str('recruitmentEnd'), developmentEnd: f.str('developmentEnd'), createdAt: f.str('createdAt'),
    };
}

/** 삭제는 주최자 삭제(7.5)와 같이 모집 중인 공고만 된다. 그 밖에는 숨김을 쓴다. */
export const projectDeletable = (project: AdminProject) => project.status === 'RECRUITING';
