'use client';
import { useParams } from 'next/navigation';
import { ApiProfileContent } from './_components/ApiProfileContent';
export default function ProfilePage() {
    const { userId = '' } = useParams<{ userId?: string }>();
    return <ApiProfileContent key={userId} slug={userId} />;
}
