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

describe('로그인 필수 AI 로컬 대화', () => {
    it('비로그인 동작은 기존 저장 데이터를 읽어 노출하거나 변경하지 않는다', () => {
        const legacy = freshHomeState();
        const guest = newMatchingConversation();
        legacy.input.description = '이전 비로그인 메인 입력';
        legacy.chat = { conversations: { guest: [guest] }, active: { guest: guest.id } };
        data.set(HOME_KEY, JSON.stringify(legacy));
        resetLocalStores(); signIn();
        const before = data.get(HOME_KEY);
        expect(home().conversations).toEqual([]);
        expect(home().conversation).toBeNull();
        expect(home().state.input.description).toBe('');
        expect(home().state.chat.conversations).toEqual({});
        home().update({ input: { ...home().state.input, description: '비로그인 입력 저장 시도' } });
        home().newChat('새 대화'); home().editChat({ stacks: ['React'] });
        home().editChatMessage('입력'); home().selectChat(guest.id); home().deleteChat(guest.id); home().reset();
        expect(home().sendChat('메시지').ok).toBe(false);
        expect(home().startHomeChat().ok).toBe(false);
        expect(data.get(HOME_KEY)).toBe(before);
        expect(storage.setItem).not.toHaveBeenCalled();
        signIn('demo-user');
        expect(home().conversations).toEqual([]);
        expect(home().state.input.description).toBe('');
        expect(home().startHomeChat().ok).toBe(false);
        expect(fetch).not.toHaveBeenCalled();
    });

    it('로그인 메인 입력을 복원하고 제출 후 비우며 반복 제출을 막는다', () => {
        signIn('demo-user');
        const input = { ...home().state.input, description: '함께 만들 일정 관리 프로젝트' };
        home().update({ input });
        resetLocalStores(); signIn('demo-user');
        expect(home().state.input.description).toBe(input.description);
        const submit = home().startHomeChat;
        expect(submit()).toEqual({ ok: true });
        const id = home().conversation!.id;
        expect(submit().ok).toBe(false);
        expect(home().conversations).toHaveLength(1);
        expect(home().conversation!.messages[0].text).toBe(input.description);
        resetLocalStores(); signIn('demo-user');
        expect(home().conversation!.id).toBe(id);
        expect(home().state.input.description).toBe('');
        home().update({ input });
        expect(home().startHomeChat().ok).toBe(true);
        expect(home().conversations).toHaveLength(2);
    });

    it('대화 저장 실패 시 입력과 기존 기록을 보존하고 재시도한다', () => {
        signIn('demo-user'); home().newChat('기존 대화');
        const id = home().conversation!.id;
        home().update({ input: { ...home().state.input, description: '저장 실패 후 다시 보낼 프로젝트' } });
        storage.setItem.mockImplementationOnce(() => { throw new Error('quota'); });
        expect(home().startHomeChat().ok).toBe(false);
        expect(home().conversation!.id).toBe(id);
        expect(home().state.input.description).toBe('저장 실패 후 다시 보낼 프로젝트');
        expect(home().startHomeChat().ok).toBe(true);
        expect(home().conversations).toHaveLength(2);
    });

    it('로그인 메시지·조건·draft를 복원하고 자동 답변·추천·차감은 실행하지 않는다', () => {
        signIn('demo-user');
        const matching = structuredClone(home().state.matching);
        home().newChat();
        const id = home().conversation!.id;
        home().editChat({ stacks: ['React'], duration: '3개월' });
        home().editChatMessage('전송 전 입력문');
        resetLocalStores(); signIn('demo-user');
        expect(home().conversation).toMatchObject({ id, draft: '전송 전 입력문', requirements: { duration: '3개월' } });
        expect(home().sendChat('추천해 주세요.')).toEqual({ ok: true });
        expect(home().conversation!.messages.map(m => m.role)).toEqual(['user']);
        expect(home().conversation!.requirements.stacks).toEqual(['React']);
        expect(home().conversation!.draft).toBe('');
        expect(home().run().ok).toBe(false);
        expect(home().state.matching).toEqual(matching);
        expect(fetch).not.toHaveBeenCalled();
        home().deleteChat(id);
        expect(home().conversations).toEqual([]);
    });

    it('로그아웃 직전 확보한 함수도 비로그인 저장을 거절하고 계정별 대화를 보존한다', () => {
        signIn('demo-user'); home().newChat('계정 A 전용 대화');
        home().update({ input: { ...home().state.input, description: '계정 A 메인 프로젝트 설명' } });
        const id = home().conversation!.id;
        const previous = home();
        const before = data.get(HOME_KEY);
        signIn();
        previous.newChat(); previous.editChatMessage('로그아웃 후 입력'); previous.editChat({ duration: '6개월' });
        previous.selectChat(id); previous.deleteChat(id);
        expect(previous.sendChat('로그아웃 후 전송').ok).toBe(false);
        expect(previous.startHomeChat().ok).toBe(false);
        expect(data.get(HOME_KEY)).toBe(before);
        expect(home().conversations).toEqual([]);
        signIn('demo-premium');
        expect(home().state.input.description).toBe('');
        expect(home().conversations).toEqual([]);
        home().selectChat(id); home().deleteChat(id);
        signIn('demo-user');
        expect(home().conversation!.id).toBe(id);
        expect(home().state.input.description).toBe('계정 A 메인 프로젝트 설명');
    });

    it('같은 Mock ID를 사용하는 실제 계정도 이메일별로 분리한다', () => {
        syncServerSession({ identity: { username: 'one@example.com', role: 'USER' } });
        home().newChat('첫 계정 전용');
        const id = home().conversation!.id;
        syncServerSession({ identity: { username: 'two@example.com', role: 'USER' } });
        expect(home().conversations).toEqual([]);
        home().newChat('두 번째 계정 전용');
        syncServerSession({ identity: { username: 'one@example.com', role: 'USER' } });
        expect(home().conversation!.id).toBe(id);
    });

    it('로그인 입력 저장 실패 시 세션의 입력을 유지한다', () => {
        signIn('demo-user');
        storage.setItem.mockImplementation(() => { throw new Error('quota'); });
        home().editChatMessage('세션에 남겨둘 입력');
        expect(home().conversation!.draft).toBe('세션에 남겨둘 입력');
        expect(home().sendChat('   ').ok).toBe(false);
        expect(home().conversation!.draft).toBe('세션에 남겨둘 입력');
    });
});
