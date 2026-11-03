'use client';
import type { ReactNode } from 'react';
import Link from 'next/link';
import { MessengerLayout, messengerStyles as styles } from '@/app/messages/_components/MessengerLayout';
import { ConversationSidebar } from '@/app/messages/_components/ConversationSidebar';
import { useAuth } from '@/lib/state/authStore';
import { useActiveConversation } from '../_hooks/useActiveConversation';

/** 1:1 쪽지 공통 틀 — 대화를 바꿔도 사이드바(탭 · 검색어)가 유지되도록 layout에 둔다. 대화 영역은 page.tsx */
export function ChatsLayout({ children }: { children: ReactNode }) {
    const { user } = useAuth();
    const { active } = useActiveConversation();

    if (!user)
        return (
            <div className={[styles.scope, "container planned"].join(" ")}>
                <section className={[styles.scope, "planned-card"].join(" ")}>
                    <h1>로그인하고 대화를 시작하세요.</h1>
                    <Link className={[styles.scope, "button"].join(" ")} href="/login">로그인</Link>
                </section>
            </div>
        );

    return (
        <div className={[styles.scope, "container messenger-page"].join(" ")}>
            <div className={[styles.scope, "section-heading"].join(" ")}>
                <div>
                    <div className={[styles.scope, "eyebrow"].join(" ")}>CONNECT & COLLABORATE</div>
                    <h1>동료와 나누는 대화</h1>
                    <p>동료와 편하게 메시지를 나눠 보세요.</p>
                </div>
            </div>
            <MessengerLayout>
                <ConversationSidebar key={user.id} kind="direct" activeId={active?.id} />
                {children}
            </MessengerLayout>
        </div>
    );
}
