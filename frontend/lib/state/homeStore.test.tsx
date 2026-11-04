import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import { resetLocalStores } from '@/lib/localStore';
import { useHome } from '@/lib/state/homeStore';
import { syncServerSession } from '@/lib/state/authStore';
import { HOME_KEY, loadHomeState, freshHomeState } from '@/lib/matching/homeService';
import { newMatchingConversation } from '@/lib/matching/matchingChatService';
import { signIn } from '@/tests/helpers/session';

let data: Map<string, string>;
let storage: { getItem: ReturnType<typeof vi.fn>; setItem: ReturnType<typeof vi.fn> };
let snapshot: ReturnType<typeof useHome>;
function Probe() { snapshot = useHome(); return null; }
function home() { renderToStaticMarkup(<Probe />); return snapshot; }

beforeEach(() => {
    data = new Map();
    storage = { getItem: vi.fn((key: string) => data.get(key) || null), setItem: vi.fn((key: string, value: string) => { data.set(key, value); }) };
    vi.stubGlobal('localStorage', storage);
    vi.stubGlobal('fetch', vi.fn());
    resetLocalStores();
    signIn();
});
afterEach(() => { vi.unstubAllGlobals(); });

describe('AI 로컬 초안과 계정 인계', () => {
    it.each([undefined, 'demo-user'])('메인 초안을 저장 후 비우고 반복 제출을 막는다 (%s)', account => {
        signIn(account);
        const input = { ...home().state.input, description: '함께 만들 일정 관리 프로젝트' };
        home().update({ input });
        resetLocalStores(); signIn(account);
        expect(home().state.input.description).toBe(input.description);
        const submit = home().startHomeChat;
        expect(submit()).toEqual({ ok: true });
        const id = home().conversation!.id;
        expect(submit().ok).toBe(false);
        expect(home().conversations).toHaveLength(1);
        expect(home().conversation!.messages[0].text).toBe(input.description);
        expect(loadHomeState().input.description).toBe('');
        resetLocalStores(); signIn(account);
        expect(home().conversation!.id).toBe(id);
        expect(home().state.input.description).toBe('');
        // 같은 문장을 새로 작성하는 것은 허용하며 기존 기록은 보존한다.
        home().update({ input });
        expect(home().startHomeChat().ok).toBe(true);
        expect(home().conversations).toHaveLength(2);
    });

    it('메인 대화 저장 실패 시 입력과 기존 기록을 보존하고 재시도한다', () => {
        home().newChat('기존 대화');
        const existing = home().conversation!.id;
        home().update({ input: { ...home().state.input, description: '저장 실패 후 다시 보낼 프로젝트' } });
        storage.setItem.mockImplementationOnce(() => { throw new Error('quota'); });
        expect(home().startHomeChat().ok).toBe(false);
        expect(home().conversation!.id).toBe(existing);
        expect(home().conversations).toHaveLength(1);
        expect(home().state.input.description).toBe('저장 실패 후 다시 보낼 프로젝트');
        expect(loadHomeState().input.description).toBe('저장 실패 후 다시 보낼 프로젝트');
        expect(home().startHomeChat().ok).toBe(true);
        expect(home().conversations).toHaveLength(2);
        expect(home().state.input.description).toBe('');
    });

    it('비로그인 생성·조건 선택·입력문·메시지·삭제를 저장하고 네트워크/사용량을 바꾸지 않는다', () => {
        const matching = structuredClone(home().state.matching);
        home().newChat();
        const id = home().conversation!.id;
        home().editChat({ stacks: ['React'], roles: [{ name: '백엔드', count: 2 }] });
        home().editChatMessage('전송 전 입력문');
        expect(loadHomeState().chat.conversations.guest[0].draft).toBe('전송 전 입력문');
        expect(home().sendChat('추천해 주세요.')).toEqual({ ok: true });
        expect(home().conversation!.messages.map(m => m.role)).toEqual(['user']);
        expect(home().conversation!.requirements.stacks).toEqual(['React']);
        expect(home().conversation!.draft).toBe('');
        expect(home().run().ok).toBe(false);
        expect(home().state.matching).toEqual(matching);
        expect(fetch).not.toHaveBeenCalled();
        home().deleteChat(id);
        expect(loadHomeState().chat.conversations.guest).toEqual([]);
    });

    it('로그인 취소·실패처럼 비로그인을 유지하거나 새로고침해도 입력문·조건이 남는다', () => {
        home().editChat({ duration: '3개월' });
        home().editChatMessage('아직 보내지 않은 문장');
        const id = home().conversation!.id;
        signIn();
        resetLocalStores();
        signIn();
        expect(home().conversation).toMatchObject({ id, draft: '아직 보내지 않은 문장', requirements: { duration: '3개월' } });
    });

    it('로그인만으로 자동 이동하지 않고 확인한 활성 초안 하나를 계정에 한 번만 옮긴다', () => {
        home().newChat('첫 비로그인 대화');
        const first = home().conversation!.id;
        home().newChat('이어받을 대화');
        const active = home().conversation!.id;
        home().editChat({ fields: ['웹'] });
        home().editChatMessage('이어 쓸 문장');
        signIn('demo-user');
        expect(home().conversation).toBeNull();
        expect(home().guestDraft!.id).toBe(active);
        expect(home().resumeGuestChat()).toEqual({ ok: true });
        expect(home().conversation).toMatchObject({ id: active, draft: '이어 쓸 문장', requirements: { fields: ['웹'] } });
        expect(loadHomeState().chat.conversations.guest.map(c => c.id)).toEqual([first]);
        // 같은 원본을 두 번 옮기지 않는다. 남은 guest 초안은 별도로 확인해 이어받는다.
        expect(home().conversations.filter(c => c.id === active)).toHaveLength(1);
        resetLocalStores(); signIn('demo-user');
        expect(home().conversation!.id).toBe(active);
        expect(fetch).not.toHaveBeenCalled();
    });

    it('계정의 기존 대화는 유지하고 로그아웃·다른 계정에서 접근/삭제되지 않는다', () => {
        signIn('demo-user'); home().newChat('계정 A 기존 대화');
        const existing = home().conversation!.id;
        signIn(); home().newChat('이동할 초안');
        signIn('demo-user'); home().resumeGuestChat();
        const moved = home().conversation!.id;
        expect(home().conversations.map(c => c.id)).toContain(existing);
        signIn('demo-premium');
        expect(home().conversations).toEqual([]);
        home().selectChat(moved); home().deleteChat(moved);
        expect(home().conversation).toBeNull();
        signIn(); expect(home().conversations).toEqual([]);
        signIn('demo-user'); expect(home().conversation!.id).toBe(moved);
    });

    it('공통 Mock ID가 같아도 실제 로그인 이메일이 다르면 로컬 대화를 분리한다', () => {
        syncServerSession({ identity: { username: 'one@example.com', role: 'USER' } });
        home().newChat('첫 계정 전용');
        const first = home().conversation!.id;
        syncServerSession({ identity: { username: 'two@example.com', role: 'USER' } });
        expect(home().conversations).toEqual([]);
        home().newChat('두 번째 계정 전용');
        syncServerSession({ identity: { username: 'one@example.com', role: 'USER' } });
        expect(home().conversation!.id).toBe(first);
    });

    it('저장 실패 시 인계 원본과 기존 계정 기록을 모두 유지하고 재시도한다', () => {
        home().newChat('잃으면 안 되는 초안');
        const id = home().conversation!.id;
        signIn('demo-user');
        storage.setItem.mockImplementationOnce(() => { throw new Error('quota'); });
        expect(home().resumeGuestChat()).toMatchObject({ ok: false });
        expect(home().guestDraft!.id).toBe(id);
        expect(home().conversations).toEqual([]);
        expect(loadHomeState().chat.conversations.guest[0].id).toBe(id);
        expect(home().resumeGuestChat()).toEqual({ ok: true });
        expect(home().conversation!.id).toBe(id);
        expect(home().guestDraft).toBeNull();
        expect(home().resumeGuestChat().ok).toBe(false);
    });

    it('입력 저장 실패는 현재 세션의 입력을 유지한다', () => {
        storage.setItem.mockImplementation(() => { throw new Error('quota'); });
        home().editChatMessage('세션에 남겨둘 입력');
        expect(home().conversation!.draft).toBe('세션에 남겨둘 입력');
        expect(home().prepareChatLogin()).toMatchObject({ ok: false });
        expect(home().sendChat('   ').ok).toBe(false);
        expect(home().conversation!.draft).toBe('세션에 남겨둘 입력');
    });

    it('이전 계정의 대화 ID와 충돌해도 기존 대화를 덮어쓰지 않는다', () => {
        const state = freshHomeState();
        const guest = newMatchingConversation();
        state.chat = { conversations: { guest: [{ ...guest, title: 'guest' }], 'demo-user': [{ ...guest, title: 'existing' }] }, active: { guest: guest.id, 'demo-user': guest.id } };
        data.set(HOME_KEY, JSON.stringify(state)); resetLocalStores(); signIn('demo-user');
        home().resumeGuestChat();
        expect(home().conversations).toHaveLength(2);
        expect(home().conversation!.id).not.toBe(guest.id);
        expect(home().conversations.find(c => c.id === guest.id)!.title).toBe('existing');
    });
});
