import { afterEach, describe, expect, it } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import { AdminUsersView, SanctionForm } from '@/app/admin/users/_components/ApiAdminUsers';
import { sanctionActions } from '@/lib/administration/adminMembers';
import { resetLocalStores } from '@/lib/localStore';
import { syncServerSession, useServerWarning } from '@/lib/state/authStore';
import type { AdminMember } from '@/types/adminApi';

const noop = () => {};
const base: AdminMember = { id: 3, tag: 'kim', nickname: '김개발', email: 'kim@test.local', role: 'USER', status: 'ACTIVE', latestSanction: null };
const view = (load: Parameters<typeof AdminUsersView>[0]['load']) =>
    renderToStaticMarkup(<AdminUsersView load={load} onRetry={noop} onPage={noop} onSanction={noop} />);
const page = (items: AdminMember[], extra = {}) => ({ status: 'ready' as const, data: { items, totalElements: items.length, page: 0, totalPages: 1, ...extra } });
afterEach(() => resetLocalStores());

describe('서버 회원 관리 화면 (14.2·14.3)', () => {
    it('불러오는 중과 오류·다시 시도를 보여 준다', () => {
        expect(view({ status: 'loading' })).toContain('목록을 불러오고 있어요.');
        const error = view({ status: 'error', message: '관리자만 이용할 수 있어요.' });
        expect(error).toContain('role="alert"');
        expect(error).toContain('관리자만 이용할 수 있어요.');
        expect(error).toContain('다시 시도');
    });
    it('빈 목록을 안내한다', () => {
        expect(view(page([]))).toContain('조건에 맞는 회원이 없습니다.');
    });
    it('회원 상태·역할·최근 조치와 제재 버튼을 보여 준다', () => {
        const warned: AdminMember = { ...base, status: 'WARNED', latestSanction: { type: 'WARNED', reason: '운영 정책 위반', adminNickname: '운영자',
            startsAt: '2026-11-10T10:00:00', endsAt: null, revokedAt: null } };
        const html = view(page([warned]));
        expect(html).not.toContain('해제 2026');
        expect(html).toContain('href="/users/kim"');
        expect(html).toContain('일반');
        expect(html).toContain('kim@test.local · 경고');
        expect(html).toContain('최근 조치: 경고 · 2026-11-10 10:00 · 운영자');
        expect(html).toContain('사유: 운영 정책 위반');
        expect(html).toContain('>정지<');
        expect(html).toContain('>해제<');
        expect(html).not.toContain('>경고<');
    });
    it('해제된 제재는 해제 시각을 함께 보여 준다', () => {
        const released: AdminMember = { ...base, latestSanction: { type: 'SUSPENDED', reason: '스팸', adminNickname: '운영자',
            startsAt: '2026-11-10T10:00:00', endsAt: null, revokedAt: '2026-11-11T09:30:00' } };
        expect(view(page([released]))).toContain('최근 조치: 정지 · 2026-11-10 10:00 · 운영자 · 사유: 스팸 · 해제 2026-11-11 09:30');
    });
    it('관리자와 태그 없는 회원에게는 제재 버튼이 없다', () => {
        expect(sanctionActions({ ...base, role: 'ADMIN' })).toEqual([]);
        expect(sanctionActions({ ...base, tag: null })).toEqual([]);
        expect(sanctionActions(base)).toEqual(['WARNED', 'SUSPENDED']);
        const html = view(page([{ ...base, tag: null, nickname: '태그없음' }]));
        expect(html).toContain('<b>태그없음</b>');
        expect(html).toContain('태그 없음');
        expect(html).not.toContain('<button');
    });
    it('여러 페이지면 페이지 이동을 보여 준다', () => {
        const html = view(page([base], { totalPages: 3, totalElements: 41 }));
        expect(html).toContain('1 / 3');
        expect(html).toContain('회원 목록 <small>41명</small>');
    });
    it('경고·정지는 사유가 있어야 보내고, 요청 중에는 버튼을 막는다', () => {
        const form = (props: Partial<Parameters<typeof SanctionForm>[0]>) => renderToStaticMarkup(
            <SanctionForm target={{ member: base, status: 'SUSPENDED' }} reason="" busy={false} error="" onReason={noop} onSubmit={noop} {...props} />);
        expect(form({})).toMatch(/<button[^>]*disabled[^>]*>변경 확인/);
        expect(form({})).toContain('로그인과 모든 기능 사용이 바로 막힙니다');
        expect(form({ reason: '스팸' })).not.toMatch(/<button[^>]*disabled/);
        expect(form({ reason: '스팸', busy: true })).toMatch(/<button[^>]*disabled[^>]*>변경 중/);
        // 해제는 사유를 받지 않고 유효한 제재를 철회한다고 안내한다.
        const release = form({ target: { member: base, status: 'ACTIVE' } });
        expect(release).not.toContain('<textarea');
        expect(release).toContain('유효한 경고·정지가 철회됩니다');
        expect(release).not.toMatch(/<button[^>]*disabled/);
        expect(form({ reason: '스팸', error: '이미 같은 상태인 회원입니다.' })).toContain('이미 같은 상태인 회원입니다.');
    });
});

describe('서버 경고 안내 (D8)', () => {
    function Warning() { const warning = useServerWarning(); return <p>{warning === null ? '없음' : `경고:${warning}`}</p>; }
    it('서버 세션이 경고 회원일 때만 사유를 준다', () => {
        syncServerSession({ identity: { username: 'kim@test.local', role: 'USER', status: 'WARNED', warningReason: '운영 정책 위반' } });
        expect(renderToStaticMarkup(<Warning />)).toContain('경고:운영 정책 위반');
        syncServerSession({ identity: { username: 'kim@test.local', role: 'USER', status: 'ACTIVE', warningReason: null } });
        expect(renderToStaticMarkup(<Warning />)).toContain('없음');
        syncServerSession({ identity: { username: 'kim@test.local', role: 'USER' } });
        expect(renderToStaticMarkup(<Warning />)).toContain('없음');
        syncServerSession({ identity: null });
        expect(renderToStaticMarkup(<Warning />)).toContain('없음');
    });
});
