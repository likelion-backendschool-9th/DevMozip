import { beforeEach, afterEach, expect, it, vi } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import { useHome } from '@/lib/state/homeStore';
import { resetLocalStores } from '@/lib/localStore';
import { signIn } from '@/tests/helpers/session';
import { matchingLoginPath } from '@/lib/matching/loginPrompt';
import { useInitialChatAnalysis } from './useInitialChatAnalysis';
import type { MatchingConversation } from '@/types/matchingChat';

const lifecycle = vi.hoisted(() => ({ effect: () => {}, attempts: { current: new Set<string>() } }));
vi.mock('react', async importOriginal => ({
    ...await importOriginal<typeof import('react')>(),
    useEffect: (effect: () => void) => { lifecycle.effect = effect; },
    useRef: () => lifecycle.attempts,
}));

const capture = vi.fn<(value: ReturnType<typeof useHome>) => void>();
function Probe() { capture(useHome()); return null; }
function home() { renderToStaticMarkup(<Probe />); return capture.mock.lastCall![0]; }
const prompt = '데모 앱을 공유하고 서로 리뷰를 남기는 프로젝트를 만들고 싶어요.';

beforeEach(() => {
    const data = new Map<string, string>();
    const storage = {
        getItem: (key: string) => data.get(key) || null,
        setItem: (key: string, value: string) => { data.set(key, value); },
        removeItem: (key: string) => { data.delete(key); },
    };
    vi.stubGlobal('localStorage', storage);
    vi.stubGlobal('sessionStorage', storage);
    vi.stubGlobal('fetch', vi.fn());
    lifecycle.attempts.current.clear();
    resetLocalStores();
    signIn('demo-user');
});
afterEach(() => { vi.unstubAllGlobals(); });

function AnalysisProbe({ conversation, ready, analyze }: { conversation: MatchingConversation | null; ready: boolean; analyze: (id: string) => Promise<void> }) {
    useInitialChatAnalysis(conversation, ready, analyze);
    return null;
}
function enter(ready: boolean, analyze: (id: string) => Promise<void>, conversation = home().conversation) {
    renderToStaticMarkup(<AnalysisProbe conversation={conversation} ready={ready} analyze={analyze} />);
    lifecycle.effect();
}

it.each(['home', 'login'])('%s 첫 입력은 이력 로딩 후 한 번 분석하고 답변·조건을 반영한다', async source => {
    if (source === 'home') {
        home().update({ input: { ...home().state.input, description: prompt } });
        expect(home().startHomeChat().ok).toBe(true);
    } else {
        signIn();
        const path = new URL(matchingLoginPath(prompt), 'http://localhost');
        const token = new URL(path.searchParams.get('next')!, 'http://localhost').searchParams.get('resume')!;
        signIn('demo-user');
        expect(home().resumeLoginPrompt(token).ok).toBe(true);
    }
    const c = home().conversation!;
    const message = c.messages[0];
    const summary = { id: 81, title: c.title, createdAt: c.createdAt, updatedAt: c.updatedAt };
    const userMessage = { id: 82, clientKey: message.id, role: 'USER', content: message.text, createdAt: message.createdAt };
    const fetch = vi.mocked(globalThis.fetch);
    fetch.mockResolvedValueOnce(Response.json(summary))
        .mockResolvedValueOnce(Response.json({ ...summary, currentConditions: {}, messages: [userMessage] }))
        .mockResolvedValueOnce(Response.json({ userMessage,
            assistantMessage: { id: 83, role: 'ASSISTANT', content: '리뷰 공유 프로젝트군요. 어떤 동료가 필요하세요?', createdAt: message.createdAt },
            currentConditions: { interests: ['커뮤니티'], stacks: ['React'] }, analysis: {}, replayed: false, readyForRecommendation: false }));
    let pending: Promise<void> | undefined;
    const analyze = vi.fn(() => { pending = home().analyzeChat(); return pending; });
    enter(false, analyze);
    expect(analyze).not.toHaveBeenCalled();
    enter(true, analyze);
    lifecycle.effect(); // Strict Mode의 effect 재실행도 중복 요청을 만들지 않는다.
    expect(analyze).toHaveBeenCalledExactlyOnceWith(c.id);
    await pending;
    enter(true, analyze);
    expect(analyze).toHaveBeenCalledTimes(1);
    expect(home().conversation!.messages.map(m => m.role)).toEqual(['user', 'assistant']);
    expect(home().conversation!.requirements).toMatchObject({ interests: ['커뮤니티'], stacks: ['React'] });
    expect(fetch.mock.calls.map(([url]) => url)).toEqual(['/api/ai/conversations', '/api/ai/conversations/sync', '/api/ai/conversations/81/messages']);
    expect(JSON.parse(fetch.mock.calls[2][1]!.body as string)).toEqual({ content: prompt, clientKey: message.id, analyze: true });
});

it('실패한 첫 분석은 렌더링 때 반복하지 않고 수동 재시도에 맡긴다', async () => {
    home().newChat(prompt);
    vi.mocked(fetch).mockRejectedValueOnce(new Error('서버 연결 실패'));
    let pending: Promise<void> | undefined;
    const error = vi.fn();
    const analyze = vi.fn(() => { pending = home().analyzeChat().catch(error); return pending; });
    enter(true, analyze);
    await pending;
    enter(true, analyze);
    expect(analyze).toHaveBeenCalledTimes(1);
    expect(error).toHaveBeenCalledOnce();
    expect(home().conversation!.messages).toHaveLength(1);
});

it('빈 대화와 기존 서버 대화·미완료 분석은 자동 실행하지 않는다', () => {
    const analyze = vi.fn(async () => {});
    enter(true, analyze);
    home().newChat();
    enter(true, analyze);
    home().sendChat(prompt);
    const c = home().conversation!;
    enter(true, analyze, { ...c, serverId: 81 });
    enter(true, analyze, { ...c, pendingAnalysisKey: c.messages[0].id });
    expect(analyze).not.toHaveBeenCalled();
});
