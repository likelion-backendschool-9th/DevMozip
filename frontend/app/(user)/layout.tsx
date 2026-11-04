import type { ReactNode } from 'react';
import { AccountLayout } from './_components/AccountLayout';

// 북마크 · 좋아요한 글 · 개인정보 설정 · 프로필을 묶는 route group(URL에는 (user)가 붙지 않는다).
// 각 page.tsx가 AccountLayout으로 감싸지 않아도 되도록 여기서 한 번 감싼다.
export default function Layout({ children }: { children: ReactNode }) {
    return <AccountLayout>{children}</AccountLayout>;
}
