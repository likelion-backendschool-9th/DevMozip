import { describe, expect, it } from 'vitest';
import { adminQuery, fields, memberSlug, parseAdminPage, positiveId, withQuery } from './adminApi';

describe('관리자 응답 해석', () => {
    it('목록 응답에서 허용한 필드만 남긴다', () => {
        const page = parseAdminPage({ items: [{ id: 1, name: 'a', secret: 'x' }], totalElements: 1, page: 0, totalPages: 1, extra: true },
            raw => { const f = fields(raw); return { id: f.num('id'), name: f.str('name') }; });
        expect(page).toEqual({ items: [{ id: 1, name: 'a' }], totalElements: 1, page: 0, totalPages: 1 });
    });
    it.each([null, [], { items: 'x', totalElements: 0, page: 0, totalPages: 0 }, { items: [], totalElements: '1', page: 0, totalPages: 0 }])(
        '형식이 틀린 목록 %j은 예외다', value => {
            expect(() => parseAdminPage(value, raw => raw)).toThrow();
        });
    it('선택 필드는 null로 읽고, 값이 있으면 형식을 확인한다', () => {
        const f = fields({ a: null, b: 'x', c: 3, d: 'ADMIN' });
        expect(f.optStr('a')).toBeNull();
        expect(f.optStr('missing')).toBeNull();
        expect(f.optStr('b')).toBe('x');
        expect(() => f.optStr('c')).toThrow();
        expect(f.oneOf('d', ['USER', 'ADMIN'] as const)).toBe('ADMIN');
        expect(() => f.oneOf('b', ['USER'] as const)).toThrow();
    });
});

describe('관리자 조회 조건', () => {
    const rules = { q: 'text', status: ['ACTIVE', 'WARNED'], restricted: 'bool', month: 'month' } as const;
    it('허용한 키만 남기고 검색어 앞뒤 공백을 뺀다', () => {
        const query = adminQuery(new URLSearchParams('page=1&size=20&q=%20kim%20&status=WARNED&userId=9'), rules);
        expect(query?.toString()).toBe('page=1&size=20&q=kim&status=WARNED');
        expect(adminQuery(new URLSearchParams('q=%20%20'), rules)?.toString()).toBe('');
    });
    it.each(['page=-1', 'size=1.5', 'page=a', 'status=SUSPENDED', 'restricted=yes', 'month=2026-13', 'status=ACTIVE&status=WARNED'])(
        '잘못된 조건 %s는 null이다', search => {
            expect(adminQuery(new URLSearchParams(search), rules)).toBeNull();
        });
    it('경로·ID·태그를 확인한다', () => {
        expect(withQuery('/api/v1/admin/users', new URLSearchParams())).toBe('/api/v1/admin/users');
        expect(withQuery('/api/v1/admin/users', new URLSearchParams('page=1'))).toBe('/api/v1/admin/users?page=1');
        expect(positiveId('12')).toBe('12');
        for (const id of ['0', '-1', '1.5', 'abc', '01']) expect(positiveId(id)).toBeNull();
        expect(memberSlug('dev_kim-1')).toBe('dev_kim-1');
        for (const slug of ['', '../x', 'a b', '김개발']) expect(memberSlug(slug)).toBeNull();
    });
});
