import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';

const mockMode = vi.hoisted(() => ({ enabled: false }));
vi.mock('@/lib/state/mockVisibilityStore', () => ({ useMockVisibility: () => ({ enabled: mockMode.enabled, setEnabled: () => true }) }));
vi.mock('@/lib/messaging/directChatApi', async importOriginal => ({
    ...await importOriginal<typeof import('@/lib/messaging/directChatApi')>(),
    fetchDirectChat: vi.fn(),
    fetchDirectChats: vi.fn(),
    fetchDirectUnreadCount: vi.fn(),
    markDirectChatRead: vi.fn(),
}));
vi.mock('@/lib/state/messengerSocketStore', async () => (await import('@/tests/helpers/messengerSocketMock')).messengerSocketStoreMock());

import { fetchDirectChat, fetchDirectChats, fetchDirectUnreadCount, markDirectChatRead } from '@/lib/messaging/directChatApi';
import { emitSignal, resetSocketMock } from '@/tests/helpers/messengerSocketMock';
import { resetLocalStores } from '@/lib/localStore';
import { openDirectChat, startDirectChatSync, watchDirectChatList } from '@/lib/state/directChatStore';
import { AppLayout } from '@/app/_components/AppLayout';
import MessagesPage from '@/app/messages/chats/page';
import { ChatsLayout } from '@/app/messages/chats/_components/ChatsLayout';
import { UserReportDialog } from '@/app/messages/_components/UserReportDialog';
import { ApiProfileHeader } from '@/app/(user)/users/[userId]/_components/ApiProfileHeader';
import ProjectDetailPage from '@/app/projects/[projectId]/page';
import { MemoryRouter, Route, Routes } from '@/tests/helpers/RouterFixture';
import { signIn } from '@/tests/helpers/session';
import { apiProfileFixture } from '@/tests/helpers/apiProfileFixture';
import { directChatDetailFixture, directChatListFixture, directChatMessageFixture, directChatPeerFixture } from '@/tests/helpers/directChatFixture';
import { demoAccounts } from '@/mocks/auth';
import type { DirectChatDetail } from '@/types/messaging';

const member = demoAccounts[0];
let cleanups: Array<() => void> = [];

function screen(url: string) {
    return renderToStaticMarkup(<MemoryRouter initialEntries={[url]}><Routes><Route element={<AppLayout />}><Route element={<ChatsLayout>{null}</ChatsLayout>}>
        <Route path="/messages/chats" element={<MessagesPage />} />
        <Route path="/messages/chats/:conversationId" element={<MessagesPage />} />
    </Route></Route></Routes></MemoryRouter>);
}

async function openedChat(detail: DirectChatDetail = directChatDetailFixture) {
    vi.mocked(fetchDirectChat).mockResolvedValue(detail);
    cleanups.push(openDirectChat(detail.id));
    await vi.advanceTimersByTimeAsync(0);
    return screen(`/messages/chats/${detail.id}`);
}

beforeEach(() => {
    vi.useFakeTimers();
    mockMode.enabled = false;
    const data = new Map<string, string>();
    vi.stubGlobal('localStorage', { getItem: (key: string) => data.get(key) ?? null, setItem: (key: string, value: string) => { data.set(key, value); } });
    vi.stubGlobal('sessionStorage', { getItem: () => null });
    vi.mocked(fetchDirectChats).mockReset().mockResolvedValue(directChatListFixture);
    vi.mocked(fetchDirectChat).mockReset();
    vi.mocked(fetchDirectUnreadCount).mockReset().mockResolvedValue({ count: 7 });
    vi.mocked(markDirectChatRead).mockReset().mockResolvedValue(undefined);
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

describe('Mock 토글 OFF의 1:1 화면', () => {
    it('목록은 서버 데이터로 표시하고 Mock 대화를 섞지 않는다', async () => {
        cleanups.push(startDirectChatSync(), watchDirectChatList());
        await vi.advanceTimersByTimeAsync(0);
        const html = screen('/messages/chats');
        for (const text of ['1:1 메신저', '샘플개발자', '안녕하세요', '/messages/chats/12']) expect(html).toContain(text);
        expect(html).not.toContain('이서준');
    });

    it('목록을 불러오는 중과 실패를 안내한다', async () => {
        expect(screen('/messages/chats')).toContain('대화 목록을 불러오고 있어요…');
        vi.mocked(fetchDirectChats).mockRejectedValue(new Error('연결 실패'));
        cleanups.push(startDirectChatSync(), watchDirectChatList());
        await vi.advanceTimersByTimeAsync(0);
        expect(screen('/messages/chats')).toContain('대화 목록을 불러오지 못했어요.');
    });

    it('빈 목록과 더 보기를 표시한다', async () => {
        vi.mocked(fetchDirectChats).mockResolvedValueOnce({ items: [], nextCursor: null });
        cleanups.push(startDirectChatSync(), watchDirectChatList());
        await vi.advanceTimersByTimeAsync(0);
        expect(screen('/messages/chats')).toContain('표시할 대화가 없습니다.');
    });

    it('헤더 배지는 서버의 안 읽은 수를 "안 읽은 메시지"로 쓴다', async () => {
        cleanups.push(startDirectChatSync());
        await vi.advanceTimersByTimeAsync(0);
        const html = screen('/messages/chats');
        expect(html).toContain('안 읽은 메시지 7개');
        expect(html).not.toContain('안 읽은 쪽지');
    });

    it('대화 화면은 상대 프로필 링크와 서버 메시지를 표시하고 Mock 문구를 표시하지 않는다', async () => {
        const html = await openedChat();
        for (const text of ['href="/users/sample_dev"', '샘플개발자', '백엔드 개발자 · 협업 가능', '안녕하세요', '사용자 신고', '메시지 보내기', '2,000자까지 보낼 수 있어요.']) expect(html).toContain(text);
        expect(html).not.toContain('Mock 대화');
        expect(html).not.toContain('/users/direct-peer');
    });

    it('시간대와 무관하게 서울 시각으로 표시한다', async () => {
        expect(await openedChat()).toContain('10:15');
    });

    it('내 메시지에는 상대의 읽음 위치에 따라 읽음을 표시한다', async () => {
        const messages = [{ ...directChatMessageFixture, id: 1, mine: true, body: '읽은 메시지' }, { ...directChatMessageFixture, id: 2, mine: true, body: '안 읽은 메시지' }];
        const html = await openedChat({ ...directChatDetailFixture, messages, peerLastReadMessageId: 1 });
        expect(html.match(/>읽음</g)).toHaveLength(1);
        expect(html.match(/>안 읽음</g)).toHaveLength(1);
    });

    it('이전 메시지가 있을 때만 더 보기를 표시한다', async () => {
        expect(await openedChat({ ...directChatDetailFixture, nextBefore: 340 })).toContain('이전 메시지 더 보기');
        expect(await openedChat()).not.toContain('이전 메시지 더 보기');
    });

    it('상대가 입력 중이면 대화 기록 맨 아래에 점 세 개 말풍선을 띄우고, 안내 문구는 기록 밖의 상태 영역에만 둔다', async () => {
        cleanups.push(startDirectChatSync());
        const idle = await openedChat();
        expect(idle).not.toContain('typing-bubble');
        expect(idle).toMatch(/<\/div><p class="[^"]*typing-status[^"]*" role="status"><\/p><form/);
        emitSignal({ type: 'typing', roomType: 'direct', roomId: 12, nickname: null });
        const html = screen('/messages/chats/12');
        // 말풍선은 기록의 마지막 항목이고 화면 읽기 프로그램에서 숨긴다. 문구는 기록 밖의 상태 영역이 읽는다.
        expect(html).toMatch(new RegExp('<article class="[^"]*chat-message typing-message[^"]*" aria-hidden="true"><div class="[^"]*typing-bubble[^"]*">'
            + '<p class="[^"]*typing-dots[^"]*">(<span class="[^"]*typing-dot[^"]*"></span>){3}</p></div></article><div></div></div>'
            + '<p class="[^"]*typing-status[^"]*" role="status">샘플개발자님이 입력 중이에요…</p><form'));
        const log = html.slice(html.indexOf('role="log"'), html.indexOf('typing-status'));
        expect(log).not.toContain('입력 중이에요');
        expect(html).not.toContain('typing-indicator');
    });

    it('상대가 정지되면 작성창을 막는다', async () => {
        expect(await openedChat({ ...directChatDetailFixture, canSend: false })).toContain('메시지를 작성할 수 없는 채팅방입니다.');
    });

    it('태그가 없는 상대는 프로필 링크와 신고 버튼을 표시하지 않는다', async () => {
        const html = await openedChat({ ...directChatDetailFixture, peer: { ...directChatPeerFixture, tag: null } });
        expect(html).toContain('샘플개발자');
        expect(html).not.toContain('사용자 신고');
        expect(html).not.toContain('/users/');
    });

    it.each([
        ['없는 대화', async () => { vi.mocked(fetchDirectChat).mockRejectedValue(Object.assign(new (await import('@/lib/messaging/directChatApi')).DirectChatRequestError(404, '없음'))); cleanups.push(openDirectChat(12)); await vi.advanceTimersByTimeAsync(0); return screen('/messages/chats/12'); }, '대화를 열 수 없습니다.'],
        ['Mock 대화 ID', async () => screen('/messages/chats/direct-a-b'), '대화를 열 수 없습니다.'],
        ['Mock 회원 ID로 진입', async () => screen('/messages/chats?userId=demo-premium'), '대화를 열 수 없습니다.'],
        ['대화 미선택', async () => screen('/messages/chats'), '대화를 선택해 주세요.'],
        ['불러오는 중', async () => screen('/messages/chats/12'), '대화를 불러오고 있어요…'],
    ])('%s를 안내한다', async (_name, render, text) => {
        expect(await render()).toContain(text);
    });

    it('세션이 만료되면 로그인으로 안내한다', async () => {
        const { DirectChatRequestError } = await import('@/lib/messaging/directChatApi');
        vi.mocked(fetchDirectUnreadCount).mockRejectedValue(new DirectChatRequestError(401, '만료'));
        cleanups.push(startDirectChatSync());
        await vi.advanceTimersByTimeAsync(0);
        const html = screen('/messages/chats');
        expect(html).toContain('로그인이 만료됐어요. 다시 로그인해 주세요.');
        expect(html).toContain('/login?next=%2Fmessages%2Fchats');
    });
});

describe('Mock 토글 ON의 1:1 화면', () => {
    it('서버 데이터를 쓰지 않고 기존 Mock 화면과 문구를 유지한다', () => {
        mockMode.enabled = true;
        const html = screen('/messages/chats');
        for (const text of ['이서준', 'Mock 대화 · 실제 서버 전송 없음', '사용자 신고']) expect(html).toContain(text);
        expect(fetchDirectChats).not.toHaveBeenCalled();
        expect(fetchDirectUnreadCount).not.toHaveBeenCalled();
        expect(html).not.toContain('대화 목록을 불러오고 있어요…');
        expect(html).not.toContain('typing-indicator');
        expect(html).not.toContain('typing-status');
        expect(html).not.toContain('typing-bubble');
    });
});

describe('사용자 신고 창', () => {
    it('토글 OFF는 Mock 안내 없이 서버 제출 문구를 표시한다', () => {
        const html = renderToStaticMarkup(<UserReportDialog server={{ name: '샘플개발자', onSubmit: async () => ({ ok: true }) }} onClose={() => {}} />);
        for (const text of ['샘플개발자', '계정당 사용자별로 한 번 신고할 수 있습니다.', '신고하기', '사칭']) expect(html).toContain(text);
        expect(html).not.toContain('실제 관리자 서버에는 전송하지 않습니다');
    });
    it('토글 ON은 기존 Mock 안내를 유지한다', () => {
        const html = renderToStaticMarkup(<UserReportDialog targetId="demo-premium" onClose={() => {}} />);
        for (const text of ['실제 관리자 서버에는 전송하지 않습니다', '신고 기록']) expect(html).toContain(text);
    });
});

describe('프로젝트 상세의 주최자 연락', () => {
    const detail = (url = '/projects/pocket') => renderToStaticMarkup(<MemoryRouter initialEntries={[url]}><Routes><Route path="/projects/:projectId" element={<ProjectDetailPage />} /></Routes></MemoryRouter>);
    it('토글 OFF에서는 실제 프로젝트 조회로 전환하고 Mock 회원 ID를 전달하지 않는다', () => {
        const html = detail('/projects/123');
        expect(html).toContain('프로젝트를 불러오고 있습니다.');
        expect(html).not.toContain('/messages/chats?userId=');
        expect(html).not.toContain('주최자와 Mock 1:1 대화');
        expect(detail()).toContain('프로젝트 ID를 확인해 주세요.');
    });
    it('토글 ON에서는 기존 Mock 대화 시작을 유지한다', () => {
        mockMode.enabled = true;
        const html = detail();
        expect(html).toContain('주최자에게 쪽지');
        expect(html).toContain('/messages/chats?userId=');
        expect(html).toContain('주최자와 Mock 1:1 대화를 시작할 수 있습니다.');
    });
});

describe('프로필의 메시지 보내기', () => {
    const header = (own = false) => renderToStaticMarkup(<ApiProfileHeader profile={apiProfileFixture} onEdit={own ? () => {} : undefined} />);
    it('토글 OFF에서 다른 회원의 프로필에 표시한다', () => {
        expect(header()).toContain('메시지 보내기');
    });
    it('본인 프로필에는 표시하지 않는다', () => {
        expect(header(true)).not.toContain('메시지 보내기');
    });
    it('토글 ON에서는 표시하지 않는다', () => {
        mockMode.enabled = true;
        expect(header()).not.toContain('메시지 보내기');
    });
});
