import { afterEach, describe, expect, it, vi } from 'vitest';
import { AiApiError, aiConditionsKey, analyzeAiMessage, applyAiAnalysis, deleteAiConversation, executeAiRecommendation, fromAiConditions, getAiRecommendation, mergeAiConversation, syncAiDraft, toAiConditions } from './aiApi';
import { newMatchingConversation, sendMatchingMessage } from './matchingChatService';
import type { AiConversationDetail } from '@/types/aiRecommendation';
const remote: AiConversationDetail = { id: 7, clientKey: 'local-chat', title: '팀원', createdAt: '2026-11-07T10:00:00', updatedAt: '2026-11-07T10:00:00',
    currentConditions: { interests: ['웹'], career: '경력 무관', durationMonths: 3 }, messages: [{ id: 8, clientKey: 'local-message', role: 'USER', content: '첫 메시지', createdAt: '2026-11-07T10:00:00' }] };
afterEach(() => vi.unstubAllGlobals());
describe('AI API 및 초안 매핑', () => {
    it('동일 조건의 배열 순서·기술 표기는 무시하고 실제 인원·기간 변경은 구분한다', () => {
        const a = { interests:['웹','교육'],stacks:['React','Java'],roles:[{name:'백엔드',count:1},{name:'프론트엔드',count:2}],career:'경력 무관',durationMonths:3 };
        const b = {...a,interests:['교육','웹'],stacks:['java',' react '],roles:[...a.roles].reverse()};
        expect(aiConditionsKey(a)).toBe(aiConditionsKey(b));
        expect(aiConditionsKey(a)).not.toBe(aiConditionsKey({...a,durationMonths:6}));
        expect(aiConditionsKey(a)).not.toBe(aiConditionsKey({...a,roles:[{name:'백엔드',count:2}]}));
    });
    it('기간을 변환하고 설명·이전 fields를 서버 조건에 보내지 않는다', () => {
        const r = fromAiConditions(remote.currentConditions);
        expect(toAiConditions({ ...r, description: '로컬 설명', fields: ['웹'] })).toEqual({ interests: ['웹'], stacks: [], roles: [], career: '경력 무관', durationMonths: 3 });
    });
    it('서버 메시지와 로컬 키를 연결하고 미전송 입력·조건을 유지한다', () => {
        let c = newMatchingConversation();
        c.id = 'local-chat';
        c = sendMatchingMessage(c, '첫 메시지', c.requirements);
        c.messages[0].id = 'local-message';
        c = sendMatchingMessage(c, '아직 전송하지 않은 메시지', c.requirements);
        c.draft = '작성 중';
        c.requirements.interests = ['게임'];
        const merged = mergeAiConversation(remote, c);
        expect(merged.messages).toHaveLength(2);
        expect(merged.messages[0]).toMatchObject({ id: 'local-message', serverId: 8, createdAt: remote.messages[0].createdAt });
        expect(merged.requirements.interests).toEqual(['게임']);
        expect(merged.draft).toBe('작성 중');
        expect(mergeAiConversation(remote, merged).messages).toHaveLength(2);
    });
    it('분석 응답의 답변과 정형 조건을 반영하고 로컬 설명은 유지한다', () => {
        let c = newMatchingConversation();
        c = sendMatchingMessage(c, '백엔드 1명', c.requirements);
        c.messages[0].id = 'message-key';
        c.requirements.description = '로컬 설명';
        c.requirements.interests = ['게임'];
        const next = applyAiAnalysis(c, { replayed: false, readyForRecommendation: false, analysis: {}, currentConditions: { interests: ['웹'], career: '경력 무관', durationMonths: 6 },
            userMessage: { id: 3, clientKey: 'message-key', role: 'USER', content: '백엔드 1명', createdAt: '2026-11-09T01:00:00' },
            assistantMessage: { id: 4, clientKey: 'analysis:abc', role: 'ASSISTANT', content: '기간을 확인할게요.', createdAt: '2026-11-09T01:00:01' } });
        expect(next.messages.map(m => [m.role, m.serverId, m.text])).toEqual([['user', 3, '백엔드 1명'], ['assistant', 4, '기간을 확인할게요.']]);
        expect(next.requirements).toMatchObject({ interests: ['웹'], career: '경력 무관', duration: '6개월', description: '로컬 설명' });
        expect(applyAiAnalysis(next, { replayed: true, readyForRecommendation: false, analysis: {}, currentConditions: next.requirements.career ? { interests: ['웹'], career: '경력 무관', durationMonths: 6 } : {},
            userMessage: { id: 3, clientKey: 'message-key', role: 'USER', content: '백엔드 1명', createdAt: '2026-11-09T01:00:00' },
            assistantMessage: { id: 4, role: 'ASSISTANT', content: '기간을 확인할게요.', createdAt: '2026-11-09T01:00:01' } }).messages).toHaveLength(2);
    });
    it('마지막 메시지만 분석 모드로 전송한다', async () => {
        const fetch = vi.fn().mockResolvedValue(Response.json({})); vi.stubGlobal('fetch', fetch);
        await analyzeAiMessage(8, '기간은 6개월', 'message-key');
        expect(fetch.mock.calls[0][0]).toBe('/api/ai/conversations/8/messages');
        expect(JSON.parse(fetch.mock.calls[0][1].body)).toEqual({ content: '기간은 6개월', clientKey: 'message-key', analyze: true });
    });
    it('동기화 시 미저장 USER 메시지만 안정적인 키로 전송한다', async () => {
        const c = mergeAiConversation(remote);
        c.messages.push({ id: 'next-message', role: 'user', text: '다음', createdAt: new Date().toISOString() });
        const fetch = vi.fn().mockResolvedValue(Response.json(remote)); vi.stubGlobal('fetch', fetch);
        await syncAiDraft(c, toAiConditions(c.requirements));
        expect(JSON.parse(fetch.mock.calls[0][1].body)).toMatchObject({ conversationId: 7, messages: [{ clientKey: 'next-message', content: '다음' }] });
    });
    it('조회는 실행 API를 호출하지 않고 과거 요청 ID를 전달한다', async () => {
        const fetch = vi.fn().mockResolvedValue(Response.json({})); vi.stubGlobal('fetch', fetch);
        await getAiRecommendation(7, 2);
        expect(fetch.mock.calls[0][0]).toBe('/api/ai/conversations/7/recommendations?requestId=2');
    });
    it('FAILED 결과 DTO를 보존하고 인증 실패는 구분한다', async () => {
        const fetch = vi.fn().mockResolvedValueOnce(Response.json({ status: 'FAILED', requestId: 1 }, { status: 500 }))
            .mockResolvedValueOnce(Response.json({ message: '로그인 필요' }, { status: 401 })); vi.stubGlobal('fetch', fetch);
        expect(await executeAiRecommendation(7, 'same-key')).toMatchObject({ status: 'FAILED' });
        expect(fetch.mock.calls[0][1].headers['Idempotency-Key']).toBe('same-key');
        await expect(deleteAiConversation(7)).rejects.toBeInstanceOf(AiApiError);
    });
});


it('서버 CLEAR의 출처가 있는 조건만 비우고 미동기화 로컬 조건은 보존한다', () => {
    let c = newMatchingConversation();
    c = sendMatchingMessage(c, '기간 삭제', c.requirements);
    c.requirements.duration = '6개월'; c.requirements.interests = ['웹'];
    c.requirements.stacks = ['Java'];
    const result = applyAiAnalysis(c, { userMessage: { id: 31, clientKey: c.messages[0].id, role: 'USER', content: '기간 삭제', createdAt: c.createdAt },
        assistantMessage: { id: 32, role: 'ASSISTANT', content: '기간을 삭제했어요.', createdAt: c.createdAt },
        currentConditions: {}, analysis: { sources: { durationMonths: { origin: 'EXPLICIT' }, stacks: { origin: 'EXPLICIT' } } },
        readyForRecommendation: false, replayed: false });
    expect(result.requirements.duration).toBe('');
    expect(result.requirements.stacks).toEqual([]);
    expect(result.requirements.interests).toEqual(['웹']);
});
