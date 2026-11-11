import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';

const mockMode = vi.hoisted(() => ({ enabled: false }));
vi.mock('@/lib/state/mockVisibilityStore', () => ({ useMockVisibility: () => ({ enabled: mockMode.enabled, setEnabled: () => true }) }));
vi.mock('@/lib/state/messengerSocketStore', async () => (await import('@/tests/helpers/messengerSocketMock')).messengerSocketStoreMock());
vi.mock('@/lib/notifications/notificationApi', async importOriginal => ({
    ...await importOriginal<typeof import('@/lib/notifications/notificationApi')>(),
    fetchNotifications: vi.fn(),
    changeNotificationRead: vi.fn(),
    readAllNotifications: vi.fn(),
}));

import { fetchNotifications } from '@/lib/notifications/notificationApi';
import { resetLocalStores } from '@/lib/localStore';
import { startNotificationSync, type NotificationListState } from '@/lib/state/notificationStore';
import { NotificationListView, type NotificationListViewProps } from '@/components/notifications/NotificationList';
import { NotificationModal } from '@/components/notifications/NotificationModal';
import { AppLayout } from '@/app/_components/AppLayout';
import NotificationsPage from '@/app/notifications/page';
import { MemoryRouter, Route, Routes } from '@/tests/helpers/RouterFixture';
import { signIn } from '@/tests/helpers/session';
import { demoAccounts } from '@/mocks/auth';
import type { ApiNotification } from '@/types/notificationApi';

const member = demoAccounts[0];
const noop = () => {};
const notice = (id: number, extra: Partial<ApiNotification> = {}): ApiNotification => ({
    id, type: 'PROJECT_STATUS_CHANGED', title: '프로젝트 상태 변경', body: '알림 프로젝트: 모집 완료', actionUrl: `/projects/${id}`, projectId: id,
    read: false, resolved: false, review: null, createdAt: '2026-11-10T16:05:00.123456', ...extra,
});
const ready = (items: ApiNotification[], extra: Partial<NotificationListState> = {}): NotificationListState => ({
    tab: 'all', status: 'ready', items, page: 0, hasNext: false, more: 'idle', message: null, pending: [], actionError: null, ...extra,
});
const view = (list: NotificationListState, extra: Partial<NotificationListViewProps> = {}) => renderToStaticMarkup(
    <NotificationListView variant="page" list={list} unreadCount={list.items.filter(n => !n.read && !n.resolved).length}
        onTab={noop} onRetry={noop} onMore={noop} onToggleRead={noop} onReadAll={noop} onOpen={noop} {...extra} />);
const reviewNotice = (status: 'PENDING' | 'COMPLETED' | 'EXPIRED', extra: Partial<ApiNotification> = {}) => notice(9, {
    type: 'PROJECT_REVIEW_REQUESTED', title: '협업 리뷰 작성 요청', body: '알림 프로젝트: 2026-12-06 12:00까지 본인을 제외한 팀원을 평가해 주세요.',
    actionUrl: '/projects/9/reviews', review: { status, expiresAt: '2026-12-06T12:00:00' }, resolved: status !== 'PENDING', ...extra,
});
function layout(url = '/') {
    return renderToStaticMarkup(<MemoryRouter initialEntries={[url]}><Routes><Route element={<AppLayout />}>
        <Route path="/" element={<p>홈</p>} />
        <Route path="/notifications" element={<NotificationsPage />} />
    </Route></Routes></MemoryRouter>);
}

let stop: (() => void) | undefined;
beforeEach(() => {
    mockMode.enabled = false;
    const data = new Map<string, string>();
    vi.stubGlobal('localStorage', { getItem: (key: string) => data.get(key) ?? null, setItem: (key: string, value: string) => { data.set(key, value); } });
    vi.stubGlobal('sessionStorage', { getItem: () => null });
    vi.mocked(fetchNotifications).mockReset();
    resetLocalStores();
});
afterEach(() => {
    stop?.();
    stop = undefined;
    vi.unstubAllGlobals();
});

describe('서버 알림 목록 (12.1·12.2)', () => {
    it('탭·안 읽음 수·항목별 읽음 전환·내용 확인 링크를 Mock 화면 형식으로 보여 준다', () => {
        const html = view(ready([notice(2), notice(1, { read: true })], { tab: 'unread' }));
        expect(html).toContain('새로운 소식을 확인하세요.');
        expect(html).toContain('안 읽은 알림 <b>1</b>개');
        expect(html).toMatch(/aria-pressed="true"[^>]*>안 읽음</);
        expect(html).toContain('href="/projects/2"');
        expect(html).toContain('내용 확인 →');
        expect(html).toContain('읽음 처리');
        expect(html).toContain('안 읽음으로 변경');
        expect(html.match(/aria-label="안 읽음"/g)).toHaveLength(1);
        expect(html).toContain('2026. 11. 10. 오후 4:05:00');
        expect(html).toContain('dateTime="2026-11-10T16:05:00.123456"');
        expect(html).toContain('실제 푸시·메일은 전송하지 않습니다.');
    });
    it('모두 읽음은 안 읽은 알림이 있을 때만 누를 수 있다', () => {
        expect(view(ready([notice(1)]))).not.toMatch(/disabled=""[^>]*>.*모두 읽음/s);
        expect(view(ready([notice(1, { read: true })]))).toMatch(/<button[^>]*disabled=""[^>]*>.*?모두 읽음/s);
    });
    it('변경 중인 알림의 버튼을 잠그고 실패 안내를 보여 준다', () => {
        const html = view(ready([notice(1)], { pending: [1], actionError: '알림을 찾을 수 없어요.' }));
        expect(html).toMatch(/<button[^>]*disabled=""[^>]*>읽음 처리/);
        expect(html).toContain('role="alert"');
        expect(html).toContain('알림을 찾을 수 없어요.');
    });
    it('로딩·오류·빈 목록을 구분한다', () => {
        expect(view({ ...ready([]), status: 'loading' })).toContain('알림을 불러오고 있어요.');
        const failed = view({ ...ready([]), status: 'error', message: '알림 서버에 연결하지 못했어요.' });
        expect(failed).toContain('알림 서버에 연결하지 못했어요.');
        expect(failed).toContain('다시 불러오기');
        expect(view(ready([]))).toContain('표시할 알림이 없습니다.');
    });
    it('다음 페이지가 있으면 더 보기를, 실패하면 재시도를 보여 준다', () => {
        expect(view(ready([notice(1)], { hasNext: true }))).toContain('더 보기');
        expect(view(ready([notice(1)], { hasNext: true, more: 'loading' }))).toContain('불러오는 중…');
        const failed = view(ready([notice(1)], { hasNext: true, more: 'error', message: '다음 알림을 불러오지 못했어요.' }));
        expect(failed).toContain('다음 알림을 불러오지 못했어요.');
        expect(failed).toContain('다시 불러오기');
        expect(view(ready([notice(1)]))).not.toContain('더 보기');
    });
    it('내부 경로가 아닌 링크는 이동 링크를 만들지 않는다', () => {
        const html = view(ready([notice(1, { actionUrl: null })]));
        expect(html).toContain('이동할 수 없는 알림입니다.');
        expect(html).not.toContain('href=');
    });
    it('모달에서는 큰 제목과 안내 문구를 숨긴다', () => {
        const html = view(ready([notice(1)]), { variant: 'modal' });
        expect(html).not.toContain('새로운 소식을 확인하세요.');
        expect(html).not.toContain('실제 푸시·메일은 전송하지 않습니다.');
        expect(html).toContain('안 읽은 알림 <b>1</b>개');
        expect(html).toContain('모두 읽음');
    });
});

describe('리뷰 알림 표시 (12.5)', () => {
    it('작성 대기는 만료 시각과 팀원 리뷰 작성 버튼을 보여 준다', () => {
        const html = view(ready([reviewNotice('PENDING')]));
        expect(html).toContain('작성 대기');
        expect(html).toContain('작성 만료 2026. 12. 6. 오후 12:00:00');
        expect(html).toContain('팀원 리뷰 작성');
        expect(html.match(/href="\/projects\/9\/reviews"/g)).toHaveLength(2);
        expect(html).not.toContain('token');
        expect(html).toContain('aria-label="안 읽음"');
    });
    it.each([['COMPLETED', '작성 완료'], ['EXPIRED', '기간 만료']] as const)('%s는 처리 완료로 보이고 이동은 리뷰 화면에 맡긴다', (status, label) => {
        const html = view(ready([reviewNotice(status)]));
        expect(html).toContain('처리 완료');
        expect(html).toContain(`${label} · 리뷰 화면에서 상태를 확인할 수 있어요.`);
        expect(html).toContain('href="/projects/9/reviews"');
        expect(html).not.toContain('팀원 리뷰 작성');
        expect(html).not.toContain('읽음 처리');
        expect(html).not.toContain('aria-label="안 읽음"');
        expect(html).toContain('notification-item read');
    });
    it('읽은 작성 대기 알림도 리뷰 작성 버튼은 남는다', () => {
        const html = view(ready([reviewNotice('PENDING', { read: true })]));
        expect(html).toContain('팀원 리뷰 작성');
        expect(html).toContain('안 읽음으로 변경');
    });
});

describe('헤더 배지·공통 알림 모달·페이지', () => {
    it('토글 OFF는 서버 안 읽음 수를 배지에 보여 주고 종 버튼이 모달을 연다', async () => {
        signIn(member.id);
        vi.mocked(fetchNotifications).mockResolvedValue({ items: [], page: 0, size: 1, hasNext: false, unreadCount: 4 });
        stop = startNotificationSync();
        await new Promise(resolve => setTimeout(resolve, 0));
        const html = layout();
        expect(html).toContain('aria-label="안 읽은 알림 4개"');
        expect(html).toMatch(/<button[^>]*aria-label="알림"[^>]*aria-haspopup="dialog"/);
        expect(html).not.toContain('href="/notifications"');
    });
    it('서버 안 읽음 수가 0이면 배지를 숨긴다', () => {
        signIn(member.id);
        expect(layout()).not.toContain('안 읽은 알림');
    });
    it('토글 ON은 기존 Mock 알림 수를 배지에 쓴다', () => {
        mockMode.enabled = true;
        signIn(member.id);
        const html = layout();
        expect(html).toMatch(/<button[^>]*aria-label="알림"/);
        expect(html).not.toContain('aria-label="안 읽은 알림 4개"');
    });
    it('모달은 토글에 따라 서버 또는 Mock 목록과 전체 보기 링크를 보여 준다', () => {
        signIn(member.id);
        const server = renderToStaticMarkup(<NotificationModal onClose={noop} />);
        expect(server).toContain('<dialog');
        expect(server).toContain('notification-dialog');
        expect(server).toContain('href="/notifications"');
        expect(server).toContain('전체 보기 →');
        expect(server).toContain('알림을 불러오고 있어요.');
        expect(server).not.toContain('Mock 알림입니다');
        mockMode.enabled = true;
        const mock = renderToStaticMarkup(<NotificationModal onClose={noop} />);
        expect(mock).toContain('Mock 알림입니다');
        expect(mock).not.toContain('새로운 소식을 확인하세요.');
    });
    it('/notifications 페이지는 토글 OFF면 서버 목록, ON이면 기존 Mock 화면이다', () => {
        signIn(member.id);
        const server = layout('/notifications');
        expect(server).toContain('새로운 소식을 확인하세요.');
        expect(server).toContain('프로젝트 지원·상태·기간·종료와 협업 리뷰 요청 알림입니다.');
        mockMode.enabled = true;
        const mock = layout('/notifications');
        expect(mock).toContain('새로운 소식을 확인하세요.');
        expect(mock).toContain('팔로우·SNS 반응·프로젝트·선택한 팔로잉 소식을 통합한 Mock 알림입니다.');
    });
    it('비로그인은 로그인 안내를 보여 준다', () => {
        signIn();
        expect(layout('/notifications')).toContain('로그인 후 알림을 확인해 주세요.');
    });
});
