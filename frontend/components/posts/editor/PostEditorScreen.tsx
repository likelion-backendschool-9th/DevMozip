'use client';
import styles from './PostEditorScreen.module.css';
import Link from 'next/link';
import { useParams } from 'next/navigation';
import { useAuth } from '@/lib/state/AuthProvider';
import { useProfiles } from '@/lib/state/ProfileProvider';
import { PostForm } from '@/components/posts/PostForm';
export function PostEditorPage() { const { postId } = useParams<{
    projectId: string;
    postId: string;
    userId: string;
}>(); const { user } = useAuth(); const { state } = useProfiles(); const post = state.posts.find(p => p.id === postId); if (!user)
    return <div className={[styles.scope, "container planned"].join(" ")}><section className={[styles.scope, "planned-card"].join(" ")}><h1>로그인하고 이야기를 나눠보세요.</h1><Link className={[styles.scope, "button"].join(" ")} href="/login">로그인</Link></section></div>; if (postId && (!post || post.authorId !== user.id))
    return <div className={[styles.scope, "container planned"].join(" ")}><section className={[styles.scope, "planned-card"].join(" ")}><h1>본인 게시글만 수정할 수 있습니다.</h1><Link className={[styles.scope, "button"].join(" ")} href="/feed">피드로</Link></section></div>; return <div className={[styles.scope, "container sns-editor-page"].join(" ")}><Link className={[styles.scope, "text-link"].join(" ")} href="/feed">← 전체 피드</Link><section><div className={[styles.scope, "eyebrow"].join(" ")}>SHARE YOUR STORY</div><h1>{post ? '이야기 수정하기' : '새로운 이야기 나누기'}</h1><p>작은 경험이 누군가의 다음 시도에 도움이 될 수 있어요.</p><PostForm key={postId || 'new'} post={post}/></section></div>; }
