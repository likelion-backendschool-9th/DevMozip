'use client';
import { useEffect } from 'react';
import { projectChatStatusLabel } from '@/lib/messaging/projectChatApi';
import { loadMoreProjectChats, setProjectChatQuery, useProjectChats, watchProjectChatList } from '@/lib/state/projectChatStore';
import { useMockVisibility } from '@/lib/state/mockVisibilityStore';

const SEARCH_DELAY = 300;

export interface ProjectConversationItem {
    id: string;
    href: string;
    title: string;
    mark: string;
    status: string;
    phase: string;
    memberCount: number;
    unread: number;
}

/**
 * 토글 OFF의 사이드바 프로젝트 탭 목록(GET /messages/chats?type=project). 탭이 보이는 동안만 조회하고,
 * 입력이 멈춘 뒤 프로젝트명으로 검색한다. 토글 ON이면 아무것도 조회하지 않는다.
 */
export function useProjectConversations(query: string, visible: boolean) {
    const { enabled } = useMockVisibility();
    const { list } = useProjectChats();
    const watching = !enabled && visible;

    useEffect(() => {
        if (watching) return watchProjectChatList();
    }, [watching]);

    useEffect(() => {
        if (!watching) return;
        const timer = setTimeout(() => setProjectChatQuery(query), SEARCH_DELAY);
        return () => clearTimeout(timer);
    }, [watching, query]);

    const items: ProjectConversationItem[] = list.items.map(item => ({
        id: String(item.id),
        href: `/messages/projects/${item.id}`,
        title: item.title,
        mark: item.title.trim().charAt(0) || '#',
        status: projectChatStatusLabel[item.status],
        phase: item.status === 'IN_PROGRESS' ? '진행 중' : item.status === 'COMPLETED' ? '종료된 프로젝트' : '시작 전',
        memberCount: item.memberCount,
        unread: item.unreadCount,
    }));
    return { server: !enabled, items, status: list.status, hasMore: list.nextCursor !== null, loadingMore: list.loadingMore, loadMore: () => void loadMoreProjectChats() };
}
