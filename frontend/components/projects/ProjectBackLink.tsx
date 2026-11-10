'use client';
import Link from 'next/link';
import { ArrowLeft } from 'lucide-react';
import { usePathname, useSearchParams } from 'next/navigation';
import { projectReturnTarget } from '@/lib/projects/projectNavigation';

export function ProjectBackLink({ className, fallback }: { className?: string; fallback?: string }) {
    const pathname = usePathname();
    const params = useSearchParams();
    const href = projectReturnTarget(params.get('returnTo'), fallback ?? (pathname.startsWith('/my-projects/') ? '/my-projects' : '/projects'));
    const label = href.startsWith('/my-projects') ? '내 프로젝트' : href.startsWith('/users/') || href.startsWith('/profile/me') ? '프로필 활동' : href.startsWith('/explore') ? '프로젝트 탐색' : '프로젝트 모집';
    return <Link className={className ?? 'text-link'} href={href}><ArrowLeft size={14} />{label}</Link>;
}
