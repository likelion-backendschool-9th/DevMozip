import type { ChatMessage, ProjectChatMember, ProjectChatMessage } from '@/types/messaging';

// 프로젝트 채팅 서버 응답을 ChatWindow가 Mock과 같은 형태로 그릴 수 있게 바꾼다.
// 회원 ID 대신 참여 행 ID(members[].id, sender.memberId)로 팀원을 구분해 태그가 없는 회원도 읽음 수에 넣는다.

/** 화면 안에서 팀원을 가리키는 값. 나는 ChatWindow가 내 메시지를 알아보도록 로그인 사용자 ID를 쓴다. */
export const projectMemberKey = (memberId: number) => `project-member-${memberId}`;
export const projectMemberName = (member: { nickname: string | null }) => member.nickname || '팀원';

const keyOf = (member: ProjectChatMember, myId: string) => (member.me ? myId : projectMemberKey(member.id));
const time = (value: string) => Date.parse(value);

/** 지금 방에 있는 팀원의 화면 값. ChatWindow의 members로 넘겨 읽음 n/m의 m을 정한다. */
export function currentMemberKeys(members: ProjectChatMember[], myId: string) {
    return members.filter(member => member.leftAt === null).map(member => keyOf(member, myId));
}

/**
 * 메시지와 초대·퇴장 문구를 시간순으로 합친다. 문구는 저장된 메시지가 아니라 joinedAt·leftAt으로 만든다.
 * 방이 만들어질 때 함께 들어온 팀원(가장 이른 joinedAt)은 참여 문구를 붙이지 않는다.
 * 이전 기록이 더 있으면 지금 불러온 가장 오래된 메시지보다 앞선 문구는 보이지 않는다.
 */
export function toProjectChatMessages(members: ProjectChatMember[], messages: ProjectChatMessage[], hasOlder: boolean, myId: string): ChatMessage[] {
    const current = members.filter(member => member.leftAt === null);
    const items: ChatMessage[] = messages.map(message => ({
        id: String(message.id),
        senderId: message.mine ? myId : projectMemberKey(message.sender.memberId),
        body: message.body,
        kind: 'text',
        createdAt: message.createdAt,
        readBy: current.filter(member => (member.lastReadMessageId ?? 0) >= message.id).map(member => keyOf(member, myId)),
    }));
    const founded = Math.min(...members.map(member => time(member.joinedAt)));
    const floor = hasOlder && messages.length ? time(messages[0].createdAt) : -Infinity;
    const notices: ChatMessage[] = [];
    for (const member of members) {
        const name = member.me ? '내가' : `${projectMemberName(member)}님이`;
        if (time(member.joinedAt) > founded && time(member.joinedAt) >= floor) {
            notices.push({ id: `joined-${member.id}`, senderId: 'system', body: `${name} 채팅방에 들어왔어요.`, kind: 'system', createdAt: member.joinedAt, readBy: [] });
        }
        if (member.leftAt !== null && time(member.leftAt) >= floor) {
            notices.push({ id: `left-${member.id}`, senderId: 'system', body: `${name} 채팅방을 나갔어요.`, kind: 'system', createdAt: member.leftAt, readBy: [] });
        }
    }
    // 같은 시각이면 메시지를 먼저 둔다. 정렬은 안정적이라 같은 종류끼리는 원래 순서를 지킨다.
    return [...items, ...notices].sort((a, b) => time(a.createdAt) - time(b.createdAt) || (a.kind === 'system' ? 1 : 0) - (b.kind === 'system' ? 1 : 0));
}

/** 입력 중인 팀원 안내. 없으면 null이다. */
export function projectTypingText(names: string[]) {
    if (!names.length) return null;
    if (names.length === 1) return `${names[0]}님이 입력 중이에요…`;
    if (names.length === 2) return `${names[0]}님, ${names[1]}님이 입력 중이에요…`;
    return `${names[0]}님 외 ${names.length - 1}명이 입력 중이에요…`;
}
