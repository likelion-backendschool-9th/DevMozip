// 관리자 API 응답 해석·요청 조건 확인·화면 요청 (docs/admin-plan.md 6.1).
// 응답은 허용한 필드만 골라 새 객체로 만든다. 형식이 틀리면 예외를 던지고, BFF는 이를 502로 바꾼다.
import type { AdminPage } from '@/types/adminApi';

export const isRecord = (value: unknown): value is Record<string, unknown> =>
    !!value && typeof value === 'object' && !Array.isArray(value);

const invalid = (name: string): never => { throw new Error(`관리자 응답 형식 오류: ${name}`); };

/** 객체 필드를 형식대로 읽는다. 맞지 않으면 예외다. */
export function fields(value: unknown, name = 'item') {
    const raw = isRecord(value) ? value : invalid(name);
    return {
        str: (key: string) => typeof raw[key] === 'string' ? raw[key] as string : invalid(`${name}.${key}`),
        num: (key: string) => typeof raw[key] === 'number' && Number.isFinite(raw[key]) ? raw[key] as number : invalid(`${name}.${key}`),
        bool: (key: string) => typeof raw[key] === 'boolean' ? raw[key] as boolean : invalid(`${name}.${key}`),
        optStr: (key: string) => raw[key] === null || raw[key] === undefined ? null
            : typeof raw[key] === 'string' ? raw[key] as string : invalid(`${name}.${key}`),
        optNum: (key: string) => raw[key] === null || raw[key] === undefined ? null
            : typeof raw[key] === 'number' && Number.isFinite(raw[key]) ? raw[key] as number : invalid(`${name}.${key}`),
        oneOf: <T extends string>(key: string, values: readonly T[]) =>
            values.includes(raw[key] as T) ? raw[key] as T : invalid(`${name}.${key}`),
        optOneOf: <T extends string>(key: string, values: readonly T[]) => raw[key] === null || raw[key] === undefined ? null
            : values.includes(raw[key] as T) ? raw[key] as T : invalid(`${name}.${key}`),
        obj: (key: string) => isRecord(raw[key]) ? raw[key] : invalid(`${name}.${key}`),
        optObj: (key: string) => raw[key] === null || raw[key] === undefined ? null : isRecord(raw[key]) ? raw[key] : invalid(`${name}.${key}`),
        list: (key: string) => Array.isArray(raw[key]) ? raw[key] as unknown[] : invalid(`${name}.${key}`),
    };
}

/** 관리자 목록 응답 `{ items, totalElements, page, totalPages }` */
export function parseAdminPage<T>(value: unknown, item: (raw: unknown) => T): AdminPage<T> {
    const f = fields(value, 'page');
    return { items: f.list('items').map(item), totalElements: f.num('totalElements'), page: f.num('page'), totalPages: f.num('totalPages') };
}

export type QueryRule = 'page' | 'text' | 'bool' | 'month' | readonly string[];

/**
 * 목록 조회 조건을 확인해 허용한 키만 남긴다. 같은 키가 두 번 오거나 형식이 틀리면 null이다.
 * page·size는 0 이상의 정수만 받고 범위는 백엔드가 확인한다. 빈 값은 보내지 않는다.
 */
export function adminQuery(search: URLSearchParams, rules: Record<string, QueryRule>): URLSearchParams | null {
    const result = new URLSearchParams();
    for (const [key, rule] of Object.entries({ page: 'page', size: 'page', ...rules } as Record<string, QueryRule>)) {
        const values = search.getAll(key);
        if (values.length > 1) return null;
        const value = values[0];
        if (value === undefined || value === '') continue;
        if (rule === 'page' && !/^\d{1,9}$/.test(value)) return null;
        if (rule === 'bool' && value !== 'true' && value !== 'false') return null;
        if (rule === 'month' && !/^\d{4}-(0[1-9]|1[0-2])$/.test(value)) return null;
        if (Array.isArray(rule) && !rule.includes(value)) return null;
        result.set(key, rule === 'text' ? value.trim() : value);
        if (rule === 'text' && !result.get(key)) result.delete(key);
    }
    return result;
}

export const withQuery = (path: string, query: URLSearchParams) => query.size ? `${path}?${query}` : path;

/** 관리자 조치 본문 `{ action: "DELETE" }`만 받는다 (공고·게시글·댓글 삭제, D3). */
export function checkActionBody(value: unknown): { action: 'DELETE' } | null {
    return isRecord(value) && Object.keys(value).length === 1 && value.action === 'DELETE' ? { action: 'DELETE' } : null;
}

/** 숨김·복원 본문 `{ restricted: boolean }`만 받는다 (공고·게시글). */
export function checkRestrictBody(value: unknown): { restricted: boolean } | null {
    return isRecord(value) && Object.keys(value).length === 1 && typeof value.restricted === 'boolean' ? { restricted: value.restricted } : null;
}

/** 경로의 숫자 ID. 양의 정수가 아니면 null이다. */
export const positiveId = (value: string) => /^[1-9]\d{0,17}$/.test(value) ? value : null;

/** 회원 태그(slug). 백엔드 태그 규칙(영문·숫자·밑줄·하이픈)이 아니면 null이다. */
export const memberSlug = (value: string) => /^[A-Za-z0-9_-]{1,100}$/.test(value) ? value : null;

/** 화면에서 관리자 BFF를 부른다. 실패하면 BFF가 준 안내 문구로 예외를 던진다. */
export async function adminRequest<T>(path: string, init: RequestInit = {}, parse?: (value: unknown) => T): Promise<T> {
    const response = await fetch(`/api/admin${path}`, { ...init, cache: 'no-store' });
    if (!response.ok) {
        let message = '요청을 처리하지 못했어요. 잠시 후 다시 시도해 주세요.';
        try {
            const body: unknown = await response.json();
            if (isRecord(body) && typeof body.message === 'string' && body.message) message = body.message;
        } catch { /* 본문이 없으면 기본 문구를 쓴다. */ }
        throw new Error(message);
    }
    if (response.status === 204) return undefined as T;
    const body: unknown = await response.json();
    return parse ? parse(body) : body as T;
}

export const adminJson = (method: string, body: unknown): RequestInit =>
    ({ method, headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) });

/** 서버 시각(LocalDateTime 문자열)을 화면 표시용으로 바꾼다. */
export const adminTime = (value: string | null) => value ? value.replace('T', ' ').slice(0, 16) : '-';
