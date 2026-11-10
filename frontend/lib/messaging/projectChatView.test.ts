import { describe, expect, it } from 'vitest';
import { currentMemberKeys, projectMemberKey, projectTypingText, toProjectChatMessages } from './projectChatView';
import { projectMemberFixture, projectMessageFixture } from '@/tests/helpers/projectChatFixture';

const me = 'demo-user';

describe('프로젝트 채팅 화면 변환', () => {
    it('나는 로그인 사용자 ID, 다른 팀원은 참여 행 ID로 구분하고 나간 팀원은 멤버에서 뺀다', () => {
        const members = [projectMemberFixture(1, { me: true }), projectMemberFixture(2), projectMemberFixture(3, { leftAt: '2026-11-06T11:00:00+09:00' })];
        expect(currentMemberKeys(members, me)).toEqual([me, projectMemberKey(2)]);
    });

    it('읽음 위치가 메시지 ID 이상인 지금 팀원을 readBy에 넣는다(태그가 없어도 구분한다)', () => {
        const members = [projectMemberFixture(1, { me: true, lastReadMessageId: 10 }), projectMemberFixture(2, { tag: null, lastReadMessageId: 11 }),
            projectMemberFixture(3, { tag: null, lastReadMessageId: 9 }),
            projectMemberFixture(4, { lastReadMessageId: 12, leftAt: '2026-11-06T11:00:00+09:00' })];
        const [message] = toProjectChatMessages(members, [projectMessageFixture(10, 1)], false, me);
        expect(message).toMatchObject({ id: '10', senderId: me, kind: 'text' });
        // 나간 팀원은 읽음 위치가 앞서 있어도 넣지 않는다.
        expect(message.readBy).toEqual([me, projectMemberKey(2)]);
        expect(toProjectChatMessages(members, [projectMessageFixture(11, 2)], false, me)[0].senderId).toBe(projectMemberKey(2));
    });

    it('방이 생긴 뒤 들어온 팀원과 나간 팀원만 문구로 넣고 시간순으로 둔다', () => {
        const members = [
            projectMemberFixture(1, { me: true }),
            projectMemberFixture(2, { nickname: '주최자' }),
            projectMemberFixture(3, { nickname: '새팀원', joinedAt: '2026-11-06T10:20:30+09:00', leftAt: '2026-11-06T10:40:00+09:00' }),
        ];
        const items = toProjectChatMessages(members, [projectMessageFixture(10, 2), projectMessageFixture(11, 3)], false, me);
        expect(items.map(item => item.body)).toEqual(['메시지 10', '새팀원님이 채팅방에 들어왔어요.', '메시지 11', '새팀원님이 채팅방을 나갔어요.']);
        expect(items.filter(item => item.kind === 'system').every(item => item.readBy.length === 0)).toBe(true);
    });

    it('이전 기록이 더 있으면 불러온 메시지보다 앞선 문구는 넣지 않는다', () => {
        const members = [projectMemberFixture(1, { me: true }), projectMemberFixture(2, { nickname: '새팀원', joinedAt: '2026-11-06T10:05:00+09:00' })];
        expect(toProjectChatMessages(members, [projectMessageFixture(10, 2)], true, me).map(item => item.body)).toEqual(['메시지 10']);
        expect(toProjectChatMessages(members, [projectMessageFixture(10, 2)], false, me)).toHaveLength(2);
    });

    it('내가 들어오거나 나간 문구는 나를 주어로 쓴다', () => {
        const members = [projectMemberFixture(1, { me: true, joinedAt: '2026-11-06T10:30:00+09:00' }), projectMemberFixture(2)];
        expect(toProjectChatMessages(members, [], false, me).map(item => item.body)).toEqual(['내가 채팅방에 들어왔어요.']);
    });

    it('입력 중 문구', () => {
        expect(projectTypingText([])).toBeNull();
        expect(projectTypingText(['가'])).toBe('가님이 입력 중이에요…');
        expect(projectTypingText(['가', '나'])).toBe('가님, 나님이 입력 중이에요…');
        expect(projectTypingText(['가', '나', '다'])).toBe('가님 외 2명이 입력 중이에요…');
    });
});
