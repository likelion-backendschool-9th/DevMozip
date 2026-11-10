import { describe, expect, it } from 'vitest';
import {
    DIRECT_PEER_MEMBER, directPeerName, directPeerView, lastPeerMessageId, mergeDirectMessages, mergeFirstChatPage, profileHref, toChatMessages,
} from './directChatView';
import { directChatListFixture, directChatMessageFixture, directChatPeerFixture } from '@/tests/helpers/directChatFixture';
import type { DirectChatMessage, DirectChatSummary } from '@/types/messaging';

const message = (id: number, mine = false, body = `메시지 ${id}`): DirectChatMessage => ({ ...directChatMessageFixture, id, mine, body });
const summary = (id: number, lastMessageId: number): DirectChatSummary => ({ ...directChatListFixture.items[0], id, lastMessage: message(lastMessageId) });

describe('메시지 병합', () => {
    it('폴링과 전송 응답이 겹쳐도 ID당 하나만 남기고 오름차순으로 둔다', () => {
        const merged = mergeDirectMessages([message(1), message(3)], [message(3, false, '새 내용'), message(2), message(4)]);
        expect(merged.map(item => item.id)).toEqual([1, 2, 3, 4]);
        expect(merged[2].body).toBe('새 내용');
    });
    it('받은 메시지가 없으면 같은 배열을 돌려준다', () => {
        const current = [message(1)];
        expect(mergeDirectMessages(current, [])).toBe(current);
    });
    it('상대의 마지막 메시지 ID를 찾는다', () => {
        expect(lastPeerMessageId([message(1), message(2, true)])).toBe(1);
        expect(lastPeerMessageId([message(2, true)])).toBeNull();
        expect(lastPeerMessageId([])).toBeNull();
    });
});

describe('목록 병합', () => {
    it('마지막 페이지면 새 목록으로 바꾼다', () => {
        const current = { items: [summary(1, 10), summary(2, 5)], nextCursor: 5 };
        const page = { items: [summary(1, 10)], nextCursor: null };
        expect(mergeFirstChatPage(current, page)).toEqual(page);
    });
    it('더 불러온 이전 대화를 유지하고 커서도 유지한다', () => {
        const current = { items: [summary(3, 30), summary(2, 20), summary(1, 10)], nextCursor: 10 };
        const merged = mergeFirstChatPage(current, { items: [summary(2, 40), summary(3, 30)], nextCursor: 30 });
        expect(merged.items.map(item => item.id)).toEqual([2, 3, 1]);
        expect(merged.nextCursor).toBe(10);
    });
    it('이어 붙일 대화가 없으면 새 페이지의 커서를 쓴다', () => {
        const current = { items: [summary(3, 30)], nextCursor: null };
        expect(mergeFirstChatPage(current, { items: [summary(3, 30)], nextCursor: 30 }).nextCursor).toBe(30);
    });
});

describe('화면 형태 변환', () => {
    it('내 메시지와 상대 메시지를 구분하고 상대의 읽음 위치까지만 읽음으로 둔다', () => {
        const [first, second, third] = toChatMessages([message(1, true), message(2, true), message(3)], 'me', 1);
        expect(first).toMatchObject({ id: '1', senderId: 'me', kind: 'text', readBy: [DIRECT_PEER_MEMBER] });
        expect(second).toMatchObject({ id: '2', senderId: 'me', readBy: [] });
        expect(third).toMatchObject({ id: '3', senderId: DIRECT_PEER_MEMBER });
    });
    it('상대가 읽지 않았으면 모두 안 읽음이다', () => {
        expect(toChatMessages([message(1, true)], 'me', null)[0].readBy).toEqual([]);
    });
    it('상대 정보를 프로필과 같은 문구로 만든다', () => {
        expect(directPeerView(directChatPeerFixture)).toEqual({
            name: '샘플개발자', href: '/users/sample_dev', subtitle: '백엔드 개발자 · 협업 가능', color: 'lavender', image: undefined,
        });
    });
    it('태그와 닉네임이 없는 상대도 표시한다', () => {
        const view = directPeerView({ tag: null, nickname: null, profileImage: 'javascript:alert(1)', jobCategories: [], collaborationStatus: null });
        expect(view).toEqual({ name: '사용자', href: null, subtitle: '직군 미입력 · 협업 상태 미등록', color: 'lavender', image: undefined });
        expect(directPeerName(null)).toBe('사용자');
        expect(profileHref(null)).toBeNull();
    });
});
