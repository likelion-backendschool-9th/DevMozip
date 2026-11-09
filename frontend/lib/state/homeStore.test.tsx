import { toAiConditions } from '@/lib/matching/aiApi';
import { matchingLoginPath, readLoginPrompt } from '@/lib/matching/loginPrompt';
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
        expect(home().sendChat('추천해 주세요.')).toEqual({ ok: true, conversationId: id });
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

describe('AI 서버 연동과 재시도', () => {
    function ready() {
        signIn('demo-user'); home().newChat();
        home().editChat({ interests: ['웹'], career: '경력 무관', duration: '3개월' });
        home().sendChat('함께 만들 프로젝트');
        return home().conversation!;
    }
    function remote(c: ReturnType<typeof ready>) {
        return { id: 71, clientKey: c.id, title: c.title, createdAt: '2026-11-07T10:00:00', updatedAt: '2026-11-07T10:00:00',
            currentConditions: { interests: ['웹'], career: '경력 무관', durationMonths: 3 },
            messages: c.messages.map((m, index) => ({ id: 81 + index, clientKey: m.id, role: 'USER', content: m.text, createdAt: '2026-11-07T10:00:00' })) };
    }
    it('추천 시에만 저장하고 응답 유실·재로딩 후 같은 실행 키를 사용한다', async () => {
        const c = ready();
        expect(fetch).not.toHaveBeenCalled();
        vi.mocked(fetch).mockResolvedValueOnce(Response.json(remote(c))).mockRejectedValueOnce(new Error('network'));
        await expect(home().recommendChat()).rejects.toThrow('network');
        const key = home().conversation!.pendingRecommendation!.key;
        expect(home().conversation!.serverId).toBe(71);
        expect(home().conversation!.messages[0].serverId).toBe(81);
        resetLocalStores(); signIn('demo-user');
        expect(home().conversation!.pendingRecommendation!.key).toBe(key);
        home().editChat({ duration: '' });
        vi.mocked(fetch).mockResolvedValueOnce(Response.json(remote(c))).mockResolvedValueOnce(Response.json({ status: 'SUCCESS', requestId: 91 }));
        await home().recommendChat();
        expect(vi.mocked(fetch).mock.calls[3][1]?.headers).toMatchObject({ 'Idempotency-Key': key });
        expect(home().conversation!.pendingRecommendation).toBeUndefined();
        expect(JSON.parse(String(vi.mocked(fetch).mock.calls[2][1]?.body)).messages).toEqual([]);
        expect(JSON.parse(String(vi.mocked(fetch).mock.calls[2][1]?.body)).conditions.durationMonths).toBe(3);
    });
    it('성공 조건이 같으면 재실행·선택 순서 변경·재로딩·메시지 추가로도 POST하지 않는다', async () => {
        ready();
        home().editChat({ interests: ['웹', '교육'], stacks: ['React', 'Java'], roles: [{name:'백엔드',count:1},{name:'프론트엔드',count:2}] });
        const c = home().conversation!;
        vi.mocked(fetch).mockResolvedValueOnce(Response.json(remote(c))).mockResolvedValueOnce(Response.json({status:'SUCCESS',requestId:91}));
        await home().recommendChat();
        expect(home().recommendationUnchanged).toBe(true);
        await expect(home().recommendChat()).rejects.toThrow('이미 추천한 조건');
        home().editChat({interests:['교육','웹'],stacks:['Java','React'],roles:[{name:'프론트엔드',count:2},{name:'백엔드',count:1}]});
        expect(home().recommendationUnchanged).toBe(true);
        resetLocalStores(); signIn('demo-user');
        home().sendChat('조건은 그대로인 추가 설명');
        await expect(home().recommendChat()).rejects.toThrow('이미 추천한 조건');
        expect(fetch).toHaveBeenCalledTimes(2);
        home().editChat({duration:'6개월'});
        expect(home().recommendationUnchanged).toBe(false);
        vi.mocked(fetch).mockResolvedValueOnce(Response.json(remote(home().conversation!))).mockResolvedValueOnce(Response.json({status:'SUCCESS',requestId:92}));
        await home().recommendChat();
        expect(fetch).toHaveBeenCalledTimes(4);
        expect(home().recommendationUnchanged).toBe(true);
    });
    it('저장 이력이 아직 화면에 복원되지 않았어도 같은 성공 조건이면 GET만 한다', async () => {
        const c = ready();
        const chat = structuredClone(home().state.chat);
        chat.conversations['demo-user'][0].serverId = 71;
        home().update({chat});
        vi.mocked(fetch).mockResolvedValueOnce(Response.json([{requestId:91,status:'SUCCESS'}]))
            .mockResolvedValueOnce(Response.json({requestId:91,status:'SUCCESS',conditions:toAiConditions(c.requirements)}));
        await expect(home().recommendChat()).rejects.toThrow('이미 추천한 조건');
        expect(home().recommendationUnchanged).toBe(true);
        expect(fetch).toHaveBeenCalledTimes(2);
        expect(vi.mocked(fetch).mock.calls.every(([,init]) => !init?.method)).toBe(true);
        expect(home().conversation!.pendingRecommendation).toBeUndefined();
    });
    it('실패한 추천은 같은 조건으로 다시 실행할 수 있다', async () => {
        const c = ready();
        vi.mocked(fetch).mockResolvedValueOnce(Response.json(remote(c))).mockResolvedValueOnce(Response.json({status:'FAILED',requestId:91},{status:500}));
        await home().recommendChat();
        expect(home().recommendationUnchanged).toBe(false);
        vi.mocked(fetch).mockResolvedValueOnce(Response.json([{requestId:91,status:'FAILED'}]))
            .mockResolvedValueOnce(Response.json(remote(c))).mockResolvedValueOnce(Response.json({status:'SUCCESS',requestId:92}));
        await home().recommendChat();
        expect(home().recommendationUnchanged).toBe(true);
        expect(vi.mocked(fetch).mock.calls.filter(([,init])=>init?.method==='POST')).toHaveLength(4);
    });
    it('여러 메시지의 마지막 ID를 범위로 보내고 재시도 중 추가한 메시지는 범위를 바꾸지 않는다', async () => {
        const c = ready();
        home().sendChat('두 번째 설명');
        const all = home().conversation!;
        vi.mocked(fetch).mockResolvedValueOnce(Response.json(remote(all))).mockRejectedValueOnce(new Error('network'));
        await expect(home().recommendChat()).rejects.toThrow('network');
        expect(JSON.parse(String(vi.mocked(fetch).mock.calls[1][1]?.body))).toEqual({ messageId: 82 });
        home().sendChat('요청 이후 설명');
        const updated = home().conversation!;
        vi.mocked(fetch).mockResolvedValueOnce(Response.json(remote(updated))).mockResolvedValueOnce(Response.json({ status: 'SUCCESS', requestId: 91 }));
        await home().recommendChat();
        expect(JSON.parse(String(vi.mocked(fetch).mock.calls[3][1]?.body))).toEqual({ messageId: 82 });
        expect(home().conversation!.id).toBe(c.id);
    });
    it('최초 동기화 응답을 잃어도 같은 대화·메시지 키로 재시도한다', async () => {
        const c = ready();
        vi.mocked(fetch).mockRejectedValueOnce(new Error('network'));
        await expect(home().recommendChat()).rejects.toThrow();
        vi.mocked(fetch).mockResolvedValueOnce(Response.json(remote(c))).mockResolvedValueOnce(Response.json({ status: 'SUCCESS' }));
        await home().recommendChat();
        expect(JSON.parse(String(vi.mocked(fetch).mock.calls[0][1]?.body))).toEqual(JSON.parse(String(vi.mocked(fetch).mock.calls[1][1]?.body)));
    });
    it('계정 전환 후 도착한 응답은 저장하거나 후속 추천을 실행하지 않는다', async () => {
        const c = ready();
        let resolve!: (response: Response) => void;
        vi.mocked(fetch).mockImplementationOnce(() => new Promise<Response>(done => { resolve = done; }));
        const running = home().recommendChat();
        signIn('demo-premium');
        resolve(Response.json(remote(c)));
        await expect(running).rejects.toThrow('계정');
        expect(home().conversations).toEqual([]);
        expect(fetch).toHaveBeenCalledTimes(1);
        signIn('demo-user');
        expect(home().conversation!.serverId).toBeUndefined();
    });
    it('삭제 실패 시 로컬 기록을 보존하고 성공한 뒤 제거한다', async () => {
        const c = ready();
        vi.mocked(fetch).mockResolvedValueOnce(Response.json(remote(c))).mockResolvedValueOnce(Response.json({ status: 'SUCCESS' }));
        await home().recommendChat();
        vi.mocked(fetch).mockResolvedValueOnce(Response.json({ message: '서버 연결 실패' }, { status: 502 }));
        await expect(home().deleteAiChat(c.id)).rejects.toThrow();
        expect(home().conversation!.id).toBe(c.id);
        vi.mocked(fetch).mockResolvedValueOnce(new Response(null, { status: 204 }));
        await home().deleteAiChat(c.id);
        expect(home().conversations).toEqual([]);
    });
    it('저장 공간 실패 시 서버 실행을 시작하지 않는다', async () => {
        ready();
        storage.setItem.mockImplementation(() => { throw new Error('quota'); });
        await expect(home().recommendChat()).rejects.toThrow('저장 공간');
        expect(fetch).not.toHaveBeenCalled();
    });
});

describe('로그인 프롬프트를 실제 계정 로컬 메시지로 인계', () => {
    function pending() {
        const data = new Map<string, string>();
        vi.stubGlobal('sessionStorage', { getItem: (k: string) => data.get(k) || null, setItem: (k: string,v: string) => data.set(k,v), removeItem: (k: string) => data.delete(k) });
        const next = new URL(matchingLoginPath('로그인 전에 작성한 프로젝트'), 'http://localhost').searchParams.get('next')!;
        return new URL(next, 'http://localhost').searchParams.get('resume')!;
    }
    it('로그인 후 한 번만 메시지를 만들고 서버 추천을 자동 실행하지 않는다', () => {
        const token = pending();
        expect(home().resumeLoginPrompt(token).ok).toBe(false);
        signIn('demo-user');
        expect(home().resumeLoginPrompt(token).ok).toBe(true);
        expect(home().resumeLoginPrompt(token).ok).toBe(false);
        expect(home().conversations).toHaveLength(1);
        expect(home().conversation!.messages[0].text).toBe('로그인 전에 작성한 프로젝트');
        expect(fetch).not.toHaveBeenCalled();
        signIn('demo-premium'); expect(home().conversations).toEqual([]);
    });
    it('계정 저장이 실패하면 일회성 입력을 소비하지 않는다', () => {
        const token = pending(); signIn('demo-user');
        storage.setItem.mockImplementationOnce(() => { throw new Error('quota'); });
        expect(home().resumeLoginPrompt(token).ok).toBe(false);
        expect(readLoginPrompt(token)).toBe('로그인 전에 작성한 프로젝트');
        expect(home().conversations).toHaveLength(0);
        expect(home().resumeLoginPrompt(token).ok).toBe(true);
    });
});


describe('AI 메시지 분석 재시도', () => {
    it('503 뒤 새로고침·서버 이력 복원 후에도 동일 메시지 키로 분석하며 추천하지 않는다', async () => {
        signIn('demo-user');
        const sent = home().sendChat('개발 초보인데 함께 배우고 싶어요.');
        expect(sent.conversationId).toBe(home().conversation!.id);
        const message = home().conversation!.messages[0];
        const summary = { id: 71, title: '대화', createdAt: message.createdAt, updatedAt: message.createdAt };
        const userMessage = { id: 72, role: 'USER', clientKey: message.id, content: message.text, createdAt: message.createdAt };
        const fetch = vi.mocked(globalThis.fetch);
        fetch.mockResolvedValueOnce(Response.json(summary))
            .mockResolvedValueOnce(Response.json({ message: '자연어 분석이 설정되지 않았습니다.' }, { status: 503 }));
        await expect(home().analyzeChat()).rejects.toThrow('자연어 분석이 설정되지 않았습니다.');
        expect(home().conversation!.pendingAnalysisKey).toBe(message.id);
        expect(home().sendChat('다음 질문').ok).toBe(false);
        resetLocalStores(); signIn('demo-user');
        fetch.mockResolvedValueOnce(Response.json([summary]))
            .mockResolvedValueOnce(Response.json({ ...summary, currentConditions: {}, messages: [userMessage] }));
        await home().loadAiChats();
        expect(home().conversation!.messages[0].serverId).toBe(72);
        fetch.mockResolvedValueOnce(Response.json({ userMessage,
            assistantMessage: { id: 73, role: 'ASSISTANT', content: '어떤 경험을 쌓고 싶으세요?', createdAt: message.createdAt },
            currentConditions: {}, analysis: {}, readyForRecommendation: false, replayed: false }));
        await home().analyzeChat();
        expect(home().conversation!.pendingAnalysisKey).toBeUndefined();
        expect(home().conversation!.messages).toHaveLength(2);
        const posts = fetch.mock.calls.filter(([, init]) => init?.method === 'POST');
        expect(posts.map(([url]) => url)).toEqual(['/api/ai/conversations', '/api/ai/conversations/71/messages', '/api/ai/conversations/71/messages']);
        expect(JSON.parse(posts[1][1]!.body as string)).toEqual(JSON.parse(posts[2][1]!.body as string));
    });
});


it('완성된 UI 조건은 분석 전에 기존 sync로 서버에 전달한다', async () => {
    signIn('demo-user');
    home().newChat();
    home().editChat({ interests: ['웹'], career: '경력 무관', duration: '3개월' });
    home().sendChat('개발 초보라 조언이 필요해요.');
    const c = home().conversation!;
    const message = c.messages[0];
    const summary = { id: 81, title: c.title, createdAt: c.createdAt, updatedAt: c.updatedAt };
    const userMessage = { id: 82, clientKey: message.id, role: 'USER', content: message.text, createdAt: message.createdAt };
    const fetch = vi.mocked(globalThis.fetch);
    fetch.mockResolvedValueOnce(Response.json(summary))
        .mockResolvedValueOnce(Response.json({ ...summary, currentConditions: toAiConditions(c.requirements), messages: [userMessage] }))
        .mockResolvedValueOnce(Response.json({ userMessage,
            assistantMessage: { id: 83, role: 'ASSISTANT', content: '어떤 것을 배우고 싶으세요?', createdAt: message.createdAt },
            currentConditions: toAiConditions(c.requirements), analysis: {}, replayed: false, readyForRecommendation: true }));
    await home().analyzeChat();
    expect(fetch.mock.calls.map(([url]) => url)).toEqual(['/api/ai/conversations', '/api/ai/conversations/sync', '/api/ai/conversations/81/messages']);
    expect(JSON.parse(fetch.mock.calls[1][1]!.body as string).conditions).toEqual(toAiConditions(c.requirements));
});
