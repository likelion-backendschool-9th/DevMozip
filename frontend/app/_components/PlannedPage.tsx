'use client';
import styles from './PlannedPage.module.css';
import Link from 'next/link';
import { useParams } from 'next/navigation';
import { ArrowLeft, ArrowRight, Construction } from 'lucide-react';
import type { RouteDefinition } from '@/types';
export function PlannedPage({ route }: {
    route: RouteDefinition;
}) {
    const params = useParams<{
        projectId: string;
        postId: string;
        userId: string;
    }>();
    return <div className={[styles.scope, "container planned"].join(" ")}><Link href="/" className={[styles.scope, "text-link"].join(" ")}><ArrowLeft size={16}/> 메인 페이지로</Link><section className={[styles.scope, "planned-card"].join(" ")}><div className={[styles.scope, "planned-icon"].join(" ")}><Construction size={32}/></div><span className={[styles.scope, "outline-badge"].join(" ")}>{route.stage}단계 구현 예정</span><h1>{route.title}</h1><p>{route.description}</p><div className={[styles.scope, "planned-detail"].join(" ")}><strong>페이지 경로가 준비되었습니다.</strong><p>현재는 공통 레이아웃과 메인 페이지를 구현한 상태입니다. 이 화면의 상세 기능은 추후 단계에서 구현합니다.</p><code>{route.path}</code>{Object.keys(params).length > 0 && <small>현재 경로 ID: {Object.values(params).join(' / ')}</small>}</div><Link className={[styles.scope, "button"].join(" ")} href="/">메인 페이지로 돌아가기 <ArrowRight size={16}/></Link></section></div>;
}
