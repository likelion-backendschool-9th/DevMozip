import type { ProjectChatDetail, ProjectChatList, ProjectChatMember, ProjectChatMessage } from '@/types/messaging';

// 서울 2026-11-06 10:00에 방이 생겼다. 참여 행 ID 1은 나, 2는 주최자, 3은 팀원이다.
export const projectMemberFixture = (id: number, extra: Partial<ProjectChatMember> = {}): ProjectChatMember => ({
    id, me: false, tag: `member_${id}`, nickname: `팀원${id}`, profileImage: null, role: 'PARTICIPANT',
    lastReadMessageId: null, joinedAt: '2026-11-06T10:00:00+09:00', leftAt: null, ...extra,
});

export const projectMessageFixture = (id: number, senderId: number, extra: Partial<ProjectChatMessage> = {}): ProjectChatMessage => ({
    id, body: `메시지 ${id}`, mine: senderId === 1, sender: { memberId: senderId, tag: `member_${senderId}`, nickname: `팀원${senderId}` },
    createdAt: `2026-11-06T10:${String(10 + id).padStart(2, '0')}:00+09:00`, ...extra,
});

export const projectChatDetailFixture = (extra: Partial<ProjectChatDetail> = {}): ProjectChatDetail => ({
    projectId: 5, title: '샘플 프로젝트', status: 'IN_PROGRESS', canSend: true, isHost: false,
    members: [projectMemberFixture(1, { me: true, nickname: '나' }), projectMemberFixture(2, { role: 'HOST', nickname: '주최자' }), projectMemberFixture(3)],
    messages: [], nextBefore: null, ...extra,
});

export const projectChatListFixture: ProjectChatList = {
    items: [{ id: 5, title: '샘플 프로젝트', status: 'IN_PROGRESS', memberCount: 3, unreadCount: 2 }],
    nextCursor: null,
};
