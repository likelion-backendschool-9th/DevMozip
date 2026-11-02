'use client';
import styles from './FollowNotices.module.css';
import Link from 'next/link';
import { useAuth } from '@/lib/state/AuthProvider';
import { useProfiles } from '@/lib/state/ProfileProvider';
export function FollowNotices() { const { user } = useAuth(); const { state } = useProfiles(); const items = state.followNotices.filter(n => n.recipientId === user?.id); return <section className={[styles.scope, "project-notices follow-notices"].join(" ")}><h2>팔로잉 개발자 소식 <span>{items.length}</span></h2><p className={[styles.scope, "auth-hint"].join(" ")}>설정한 이후의 새 글·새 프로젝트 모집 소식만 표시합니다.</p>{!items.length && <p className={[styles.scope, "empty-state"].join(" ")}>팔로잉 목록에서 알림을 켜고 새 소식을 기다려보세요.</p>}{items.map(n => <article key={n.id}><div><strong>{n.kind === 'activity' ? '새 게시글 활동' : '새 프로젝트 모집'}</strong><time>{new Date(n.createdAt).toLocaleString('ko-KR', { timeZone: 'Asia/Seoul' })}</time></div><p><Link href={`/users/${n.actorId}`}>{state.profiles[n.actorId]?.nickname || '개발자'}</Link> · {n.title}</p><Link href={n.kind === 'activity' ? `/posts/${n.recordId}` : `/projects/${n.recordId}`}>소식 확인 →</Link></article>)}</section>; }
