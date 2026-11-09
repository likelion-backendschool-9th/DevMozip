'use client';
import { useParams } from 'next/navigation';
import { ApiFollowsContent } from './_components/ApiFollowsContent';

// 경로 값은 Mock 프로필 id가 아니라 users.tag다(/users/[userId] 프로필 화면과 같다).
export default function FollowsPage() {
    const { userId = '' } = useParams<{ userId?: string }>();
    return <ApiFollowsContent key={userId} slug={userId} />;
}
