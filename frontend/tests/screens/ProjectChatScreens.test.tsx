import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';

const mockMode = vi.hoisted(() => ({ enabled: false }));
vi.mock('@/lib/state/mockVisibilityStore', () => ({ useMockVisibility: () => ({ enabled: mockMode.enabled, setEnabled: () => true }) }));
vi.mock('@/lib/messaging/projectChatApi', async importOriginal => ({
    ...await importOriginal<typeof import('@/lib/messaging/projectChatApi')>(),
    fetchProjectChat: vi.fn(),
    fetchProjectChats: vi.fn(),
    fetchProjectUnreadCount: vi.fn(),
    markProjectChatRead: vi.fn(),
}));
vi.mock('@/lib/messaging/directChatApi', async importOriginal => ({
    ...await importOriginal<typeof import('@/lib/messaging/directChatApi')>(),
    fetchDirectChats: vi.fn(),
    fetchDirectUnreadCount: vi.fn(),
}));
vi.mock('@/lib/state/messengerSocketStore', async () => (await import('@/tests/helpers/messengerSocketMock')).messengerSocketStoreMock());

import { DirectChatRequestError, fetchDirectChats, fetchDirectUnreadCount } from '@/lib/messaging/directChatApi';
import { fetchProjectChat, fetchProjectChats, fetchProjectUnreadCount, markProjectChatRead } from '@/lib/messaging/projectChatApi';
import { resetLocalStores } from '@/lib/localStore';
import { startDirectChatSync } from '@/lib/state/directChatStore';
import { openProjectChat, startProjectChatSync, watchProjectChatList } from '@/lib/state/projectChatStore';
import { AppLayout } from '@/app/_components/AppLayout';
import ProjectChatPage from '@/app/messages/projects/[projectId]/page';
import { MemoryRouter, Route, Routes } from '@/tests/helpers/RouterFixture';
import { signIn } from '@/tests/helpers/session';
import { emitSignal, resetSocketMock } from '@/tests/helpers/messengerSocketMock';
import { projectChatDetailFixture, projectChatListFixture, projectMemberFixture, projectMessageFixture } from '@/tests/helpers/projectChatFixture';
import { demoAccounts } from '@/mocks/auth';
import type { ProjectChatDetail } from '@/types/messaging';

const member = demoAccounts[0];
let cleanups: Array<() => void> = [];

function screen(url = '/messages/projects/5') {
    return renderToStaticMarkup(<MemoryRouter initialEntries={[url]}><Routes><Route element={<AppLayout />}>
        <Route path="/messages/projects/:projectId" element={<ProjectChatPage />} />
    </Route></Routes></MemoryRouter>);
}

async function opened(detail: ProjectChatDetail = projectChatDetailFixture()) {
    vi.mocked(fetchProjectChat).mockResolvedValue(detail);
    cleanups.push(openProjectChat(detail.projectId));
    await vi.advanceTimersByTimeAsync(0);
    return screen(`/messages/projects/${detail.projectId}`);
}

beforeEach(() => {
    vi.useFakeTimers();
    mockMode.enabled = false;
    const data = new Map<string, string>();
    vi.stubGlobal('localStorage', { getItem: (key: string) => data.get(key) ?? null, setItem: (key: string, value: string) => { data.set(key, value); } });
    vi.stubGlobal('sessionStorage', { getItem: () => null });
    vi.mocked(fetchProjectChat).mockReset();
    vi.mocked(fetchProjectChats).mockReset().mockResolvedValue(projectChatListFixture);
    vi.mocked(fetchProjectUnreadCount).mockReset().mockResolvedValue({ count: 3 });
    vi.mocked(markProjectChatRead).mockReset().mockResolvedValue(undefined);
    vi.mocked(fetchDirectChats).mockReset().mockResolvedValue({ items: [], nextCursor: null });
    vi.mocked(fetchDirectUnreadCount).mockReset().mockResolvedValue({ count: 2 });
    resetLocalStores();
    resetSocketMock();
    signIn(member.id);
});
afterEach(() => {
    cleanups.reverse().forEach(cleanup => cleanup());
    cleanups = [];
    vi.useRealTimers();
    vi.unstubAllGlobals();
});

describe('Mock 토글 OFF의 프로젝트 채팅', () => {
    it('입장할 수 없는 방(삭제된 방·팀원이 아님·모집 전)은 입장 거부를 보여 준다', async () => {
        vi.mocked(fetchProjectChat).mockRejectedValue(new DirectChatRequestError(404, '없음'));
        cleanups.push(openProjectChat(5));
        await vi.advanceTimersByTimeAsync(0);
        const html = screen();
        expect(html).toContain('채팅방에 입장할 수 없어요.');
        expect(html).not.toContain('메시지 입력');
    });

    it('숫자가 아닌 주소는 서버를 부르지 않고 입장 거부를 보여 준다', () => {
        expect(screen('/messages/projects/pocket')).toContain('채팅방에 입장할 수 없어요.');
        expect(fetchProjectChat).not.toHaveBeenCalled();
    });

    it('불러오는 중과 실패를 안내한다', async () => {
        expect(screen()).toContain('프로젝트 채팅을 불러오고 있어요…');
        vi.mocked(fetchProjectChat).mockRejectedValue(new Error('연결 실패'));
        cleanups.push(openProjectChat(5));
        await vi.advanceTimersByTimeAsync(0);
        expect(screen()).toContain('프로젝트 채팅을 불러오지 못했어요.');
    });

    it('서버 멤버와 메시지를 보여 주고 Mock 전용 강퇴 투표와 기한 시뮬레이터는 숨긴다', async () => {
        const html = await opened(projectChatDetailFixture({ messages: [projectMessageFixture(10, 2)] }));
        for (const text of ['샘플 프로젝트', '프로젝트 진행 중', '멤버 3명', '메시지 10', '주최자', 'href="/users/member_2"', '2,000자까지 보낼 수 있어요.']) expect(html).toContain(text);
        for (const text of ['투표 기한 시뮬레이션', '강퇴', 'Mock 단체 채팅', 'Mock 대화']) expect(html).not.toContain(text);
    });

    it('내 메시지에 나를 뺀 지금 팀원 중 읽은 수를 n/m으로 표시한다', async () => {
        const members = [
            projectMemberFixture(1, { me: true, lastReadMessageId: 11 }),
            projectMemberFixture(2, { lastReadMessageId: 11 }),
            projectMemberFixture(3, { lastReadMessageId: 10, tag: null }),
        ];
        const html = await opened(projectChatDetailFixture({ members, messages: [projectMessageFixture(10, 1), projectMessageFixture(11, 1)] }));
        expect(html.match(/>읽음 2\/2</g)).toHaveLength(1);
        expect(html.match(/>읽음 1\/2</g)).toHaveLength(1);
    });

    it('나중에 들어온 팀원과 나간 팀원을 문구로 보여 주고, 나간 팀원은 멤버 수에서 뺀다', async () => {
        const members = [
            projectMemberFixture(1, { me: true }),
            projectMemberFixture(2, { nickname: '주최자' }),
            projectMemberFixture(3, { nickname: '새팀원', joinedAt: '2026-11-06T10:30:00+09:00' }),
            projectMemberFixture(4, { nickname: '떠난팀원', leftAt: '2026-11-06T10:40:00+09:00' }),
        ];
        const html = await opened(projectChatDetailFixture({ members, messages: [projectMessageFixture(10, 2)] }));
        expect(html).toContain('새팀원님이 채팅방에 들어왔어요.');
        expect(html).toContain('떠난팀원님이 채팅방을 나갔어요.');
        expect(html).not.toContain('주최자님이 채팅방에 들어왔어요.');
        expect(html).toContain('멤버 3명');
    });

    it('종료된 프로젝트는 읽기 전용으로 보여 준다', async () => {
        const html = await opened(projectChatDetailFixture({ status: 'COMPLETED', canSend: false }));
        expect(html).toContain('종료된 프로젝트라 대화는 읽기만 할 수 있어요.');
        expect(html).toContain('메시지를 작성할 수 없는 채팅방입니다.');
        expect(html).toContain('프로젝트 종료');
    });

    it('입력 중인 팀원을 대화 기록 밖에 보여 준다', async () => {
        cleanups.push(startProjectChatSync());
        await opened();
        emitSignal({ type: 'typing', roomType: 'project', roomId: 5, nickname: '주최자' });
        emitSignal({ type: 'typing', roomType: 'project', roomId: 5, nickname: null });
        const html = screen();
        expect(html).toMatch(/<\/div><p class="[^"]*typing-indicator[^"]*">주최자님, 팀원님이 입력 중이에요…<\/p><form/);
        // 1:1의 입력 중 말풍선은 프로젝트 채팅에 쓰지 않는다.
        expect(html).not.toContain('typing-bubble');
        expect(html).not.toContain('typing-status');
    });

    it('사이드바 프로젝트 탭은 서버 목록을 보여 준다', async () => {
        // 정적 렌더링은 효과를 실행하지 않으므로 사이드바가 하는 목록 구독을 직접 연다.
        cleanups.push(startProjectChatSync(), watchProjectChatList());
        const html = await opened();
        expect(html).toContain('href="/messages/projects/5"');
        expect(html).toContain('진행 중 · 3명');
        expect(fetchProjectChats).toHaveBeenCalled();
    });

    it('헤더 배지는 1:1과 프로젝트 채팅의 안 읽은 수를 합산한다', async () => {
        cleanups.push(startDirectChatSync(), startProjectChatSync());
        await vi.advanceTimersByTimeAsync(0);
        expect(screen('/messages/projects/pocket')).toContain('안 읽은 메시지 5개');
    });

    it('세션이 만료되면 로그인으로 안내한다', async () => {
        vi.mocked(fetchProjectUnreadCount).mockRejectedValue(new DirectChatRequestError(401, '만료'));
        cleanups.push(startProjectChatSync());
        await vi.advanceTimersByTimeAsync(0);
        const html = screen();
        expect(html).toContain('로그인이 만료됐어요. 다시 로그인해 주세요.');
        expect(html).toContain('/login?next=%2Fmessages%2Fprojects%2F5');
    });
});

describe('Mock 토글 ON의 프로젝트 채팅', () => {
    it('서버를 부르지 않고 기존 Mock 화면을 유지하며, 배지는 쪽지 문구를 쓴다', () => {
        mockMode.enabled = true;
        const html = screen('/messages/projects/pocket');
        expect(fetchProjectChat).not.toHaveBeenCalled();
        expect(fetchProjectUnreadCount).not.toHaveBeenCalled();
        expect(html).not.toContain('안 읽은 메시지');
        expect(html).not.toContain('프로젝트 채팅을 불러오고 있어요…');
    });
});
