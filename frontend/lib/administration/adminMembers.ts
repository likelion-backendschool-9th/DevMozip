// 14.2 회원 조회·14.3 회원 제재의 응답 해석과 요청 확인 (docs/admin-plan.md 5.3·5.4).
import { fields, isRecord, positiveId, type QueryRule } from './adminApi';
import type { AdminMember, AdminMemberRole, AdminMemberStatus, AdminSanction } from '@/types/adminApi';

export const memberRoles: readonly AdminMemberRole[] = ['USER', 'PREMIUM', 'OWNER', 'ADMIN'];
export const memberStatuses: readonly AdminMemberStatus[] = ['ACTIVE', 'WARNED', 'SUSPENDED'];
const sanctionTypes = ['WARNED', 'SUSPENDED'] as const;

export const memberRoleLabels: Record<AdminMemberRole, string> = { USER: '일반', PREMIUM: '프리미엄', OWNER: '소유자', ADMIN: '관리자' };
export const memberStatusLabels: Record<AdminMemberStatus, string> = { ACTIVE: '정상', WARNED: '경고', SUSPENDED: '정지' };
export const sanctionActionLabels: Record<AdminMemberStatus, string> = { WARNED: '경고', SUSPENDED: '정지', ACTIVE: '해제' };

/** 사유 최대 길이. Mock 화면의 입력 제한과 같다. */
export const SANCTION_REASON_MAX = 300;

export const memberQueryRules: Record<string, QueryRule> = { q: 'text', status: memberStatuses, role: memberRoles };

export function parseAdminSanction(value: unknown): AdminSanction {
    const f = fields(value, 'sanction');
    return {
        type: f.oneOf('type', sanctionTypes), reason: f.optStr('reason'), adminNickname: f.str('adminNickname'),
        startsAt: f.str('startsAt'), endsAt: f.optStr('endsAt'), revokedAt: f.optStr('revokedAt'),
    };
}

export function parseAdminMember(value: unknown): AdminMember {
    const f = fields(value, 'member');
    const sanction = f.optObj('latestSanction');
    return {
        id: f.num('id'), tag: f.optStr('tag'), nickname: f.str('nickname'), email: f.optStr('email'),
        role: f.optOneOf('role', memberRoles), status: f.optOneOf('status', memberStatuses),
        latestSanction: sanction ? parseAdminSanction(sanction) : null,
    };
}

export interface BanBody { status: AdminMemberStatus; reason?: string; reportId?: number }

/** 제재 요청 본문을 확인한다. 허용한 키만 받고, 경고·정지는 사유가 있어야 한다. 틀리면 안내 문구를 돌려준다. */
export function checkBanBody(value: unknown): BanBody | string {
    if (!isRecord(value) || Object.keys(value).some(key => !['status', 'reason', 'reportId'].includes(key))) return '요청 내용을 확인해 주세요.';
    if (!memberStatuses.includes(value.status as AdminMemberStatus)) return '변경할 상태를 확인해 주세요.';
    const status = value.status as AdminMemberStatus;
    if (value.reason !== undefined && value.reason !== null && typeof value.reason !== 'string') return '사유를 확인해 주세요.';
    const reason = typeof value.reason === 'string' ? value.reason.trim() : '';
    if (status !== 'ACTIVE' && !reason) return '경고·정지 사유를 입력해 주세요.';
    // 해제는 유효한 제재를 철회만 한다. ERD에 해제 사유·근거 칼럼이 없어 받지 않는다.
    if (status === 'ACTIVE' && (reason || (value.reportId !== undefined && value.reportId !== null))) return '해제에는 사유와 근거 신고를 함께 보내지 않아요.';
    if (reason.length > SANCTION_REASON_MAX) return `사유는 ${SANCTION_REASON_MAX}자 이하로 입력해 주세요.`;
    const body: BanBody = { status };
    if (reason) body.reason = reason;
    if (value.reportId !== undefined && value.reportId !== null) {
        if (typeof value.reportId !== 'number' || !positiveId(String(value.reportId))) return '근거 신고를 확인해 주세요.';
        body.reportId = value.reportId;
    }
    return body;
}

/** 제재 버튼: 관리자 계정·태그 없는 회원은 버튼이 없고, 지금 상태와 같은 조치는 보여 주지 않는다. */
export function sanctionActions(member: AdminMember): AdminMemberStatus[] {
    if (member.role === 'ADMIN' || !member.tag || !member.status) return [];
    return (['WARNED', 'SUSPENDED', 'ACTIVE'] as const).filter(status => status !== member.status);
}
