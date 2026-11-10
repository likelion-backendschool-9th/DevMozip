// 14.9 리뷰 키워드 관리의 응답 해석과 요청 확인 (docs/admin-plan.md 5.8).
import { fields, isRecord } from './adminApi';
import type { AdminReviewKeyword } from '@/types/adminApi';

/** 키워드 최대 길이. Mock 화면의 입력 제한과 같다. */
export const KEYWORD_MAX = 30;

export function parseAdminKeywords(value: unknown): AdminReviewKeyword[] {
    return fields(value, 'keywords').list('items').map(raw => {
        const f = fields(raw, 'keyword');
        return { id: f.num('id'), name: f.str('name'), active: f.bool('active') };
    });
}

/** 키워드 이름 하나를 확인한다. 앞뒤 공백을 빼고 1~30자여야 한다. 틀리면 null이다. */
export function keywordName(value: string): string | null {
    const name = value.trim();
    return name && name.length <= KEYWORD_MAX ? name : null;
}

/** PUT 본문 `{ keywords: string[] }`을 확인한다. 이름 규칙과 중복을 본다. 틀리면 안내 문구다. */
export function checkKeywordsBody(value: unknown): { keywords: string[] } | string {
    if (!isRecord(value) || Object.keys(value).length !== 1 || !Array.isArray(value.keywords)) return '키워드 목록을 확인해 주세요.';
    const names: string[] = [];
    for (const raw of value.keywords) {
        const name = typeof raw === 'string' ? keywordName(raw) : null;
        if (!name) return `키워드는 1~${KEYWORD_MAX}자로 입력해 주세요.`;
        if (names.includes(name)) return '같은 키워드를 두 번 넣을 수 없어요.';
        names.push(name);
    }
    return { keywords: names };
}

export const activeNames = (keywords: AdminReviewKeyword[]) => keywords.filter(k => k.active).map(k => k.name);
