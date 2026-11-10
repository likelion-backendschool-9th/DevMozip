// 14.8 신고 내역 처리의 응답 해석과 요청 확인 (docs/admin-plan.md 5.7, D5).
import { fields, isRecord, type QueryRule } from './adminApi';
import type { AdminParty, AdminReport, AdminReportStatus, AdminReportType } from '@/types/adminApi';

export const reportTypes: readonly AdminReportType[] = ['PROJECT', 'MEMBER', 'USER', 'POST', 'COMMENT'];
export const reportStatuses: readonly AdminReportStatus[] = ['AWATING', 'REVIEWING', 'RESOLVED', 'REJECTED'];
export const reportTypeLabels: Record<AdminReportType, string> = { PROJECT: '공고', MEMBER: '프로젝트 팀원', USER: '사용자', POST: '게시글', COMMENT: '댓글' };
export const reportStatusLabels: Record<AdminReportStatus, string> = { AWATING: '접수', REVIEWING: '검토 중', RESOLVED: '처리 완료', REJECTED: '기각' };
/** 처리 결과 최대 길이. 기존 프로젝트 신고 처리와 같다. */
export const RESOLUTION_MAX = 1000;

export const reportQueryRules: Record<string, QueryRule> = { type: reportTypes, status: reportStatuses };

export function parseAdminParty(value: unknown): AdminParty {
    const f = fields(value, 'party');
    return { id: f.num('id'), tag: f.optStr('tag'), nickname: f.str('nickname'), withdrawn: f.bool('withdrawn') };
}

export function parseAdminReport(value: unknown): AdminReport {
    const f = fields(value, 'report');
    const t = fields(f.obj('target'), 'target');
    const user = t.optObj('user');
    return {
        id: f.num('id'), type: f.oneOf('type', reportTypes), reporter: parseAdminParty(f.obj('reporter')),
        target: {
            projectId: t.optNum('projectId'), projectTitle: t.optStr('projectTitle'), user: user ? parseAdminParty(user) : null,
            postId: t.optNum('postId'), commentId: t.optNum('commentId'), content: t.optStr('content'), deleted: t.bool('deleted'),
        },
        reason: f.str('reason'), status: f.oneOf('status', reportStatuses), resolution: f.optStr('resolution'),
        handledByNickname: f.optStr('handledByNickname'), handledAt: f.optStr('handledAt'), createdAt: f.str('createdAt'),
    };
}

export const reportFinal = (report: AdminReport) => report.status === 'RESOLVED' || report.status === 'REJECTED';

export interface HandleBody { status: Exclude<AdminReportStatus, 'AWATING'>; resolution?: string }

/** 처리 본문을 확인한다. 접수로 되돌릴 수 없고, 완료·기각은 결과가 필요하다. 틀리면 안내 문구다. */
export function checkHandleBody(value: unknown): HandleBody | string {
    if (!isRecord(value) || Object.keys(value).some(key => !['status', 'resolution'].includes(key))) return '요청 내용을 확인해 주세요.';
    if (!['REVIEWING', 'RESOLVED', 'REJECTED'].includes(value.status as string)) return '처리 상태를 확인해 주세요.';
    if (value.resolution !== undefined && value.resolution !== null && typeof value.resolution !== 'string') return '처리 결과를 확인해 주세요.';
    const status = value.status as HandleBody['status'];
    const resolution = typeof value.resolution === 'string' ? value.resolution.trim() : '';
    if (status !== 'REVIEWING' && !resolution) return '처리 결과를 입력해 주세요.';
    if (resolution.length > RESOLUTION_MAX) return '처리 결과는 1,000자 이하로 입력해 주세요.';
    return resolution ? { status, resolution } : { status };
}

/** 신고 대상의 공개 화면 주소. 대상이 지워졌거나 링크할 곳이 없으면 null이다. */
export function reportTargetHref(report: AdminReport): string | null {
    const { target } = report;
    if (target.deleted) return null;
    if ((report.type === 'POST' || report.type === 'COMMENT') && target.postId) return `/feeds/${target.postId}`;
    if ((report.type === 'PROJECT' || report.type === 'MEMBER') && target.projectId) return `/projects/${target.projectId}`;
    if (report.type === 'USER' && target.user?.tag) return `/users/${target.user.tag}`;
    return null;
}

/** 회원 이름 표시. 탈퇴 회원은 표시를 붙인다. */
export const partyName = (party: AdminParty | null) => !party ? '-' : party.withdrawn ? `${party.nickname} (탈퇴)` : party.nickname;
