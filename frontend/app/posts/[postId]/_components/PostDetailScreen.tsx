'use client';
import styles from './PostDetailScreen.module.css';
import { useEffect } from 'react';
import Link from 'next/link';
import { useParams } from 'next/navigation';
import { useCurrentLocation as useLocation } from '@/lib/navigation';
import { useAuth } from '@/lib/state/AuthProvider';
import { useProfiles } from '@/lib/state/ProfileProvider';
import { PostCard } from '@/components/posts/PostCard';
import { Comments } from '@/app/posts/[postId]/_components/Comments';
import { sessionVisitor } from '@/lib/social/socialService';
export function PostDetailPage() { const { postId = '' } = useParams<{
    projectId: string;
    postId: string;
    userId: string;
}>(); const location = useLocation(); const { user, canViewProfile } = useAuth(); const { state, act } = useProfiles(); const post = state.posts.find(p => p.id === postId); const readable = post && canViewProfile(post.authorId) && (!post.hidden || post.authorId === user?.id); useEffect(() => { if (readable)
    act({ type: 'social-view', id: postId, visitorId: sessionVisitor() }); }, [postId, !!readable]); useEffect(() => { if (location.hash !== '#comments')
    return; const id = requestAnimationFrame(() => document.getElementById('comments')?.scrollIntoView({ block: 'start' })); return () => cancelAnimationFrame(id); }, [location.hash, postId]); if (!readable)
    return <div className={[styles.scope, "container planned"].join(" ")}><section className={[styles.scope, "planned-card"].join(" ")}><h1>게시글을 찾을 수 없어요.</h1><p>삭제·숨김 처리되었거나 비공개 계정의 게시글입니다.</p><Link className={[styles.scope, "button"].join(" ")} href="/feed">전체 피드로</Link></section></div>; return <div className={[styles.scope, "container sns-detail-page"].join(" ")}><Link className={[styles.scope, "text-link"].join(" ")} href="/feed">← 전체 피드</Link><PostCard post={post} full/><Comments postId={postId}/></div>; }
