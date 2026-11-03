'use client';
import styles from './page.module.css';
import Link from 'next/link';
import { useQueryState as useSearchParams } from '@/lib/navigation';
import { useAuth } from '@/lib/state/authStore';
import { useProfiles } from '@/lib/state/profileStore';
import { useProjects } from '@/lib/state/projectStore';
import { PostCard } from '@/components/posts/PostCard';
import { RecruitmentCard } from '@/components/projects/RecruitmentCard';
import { bookmarkedPosts, bookmarkedProjects } from '@/lib/social/personalLibraryService';
export default function BookmarksPage() {
    const { user, canViewProfile } = useAuth();
    const { state } = useProfiles();
    const { state: recruitment, projects } = useProjects();
    const [params, setParams] = useSearchParams();
    const tab = params.get('tab') === 'projects' ? 'projects' : 'posts';
    const posts = user ? bookmarkedPosts(state, user.id).filter(p => canViewProfile(p.authorId)) : [];
    const savedProjects = user ? bookmarkedProjects(projects, recruitment, user.id) : [];
    const empty = tab === 'posts' ? !posts.length : !savedProjects.length;
    return (
        <>
            <header className={[styles.scope, 'personal-page-heading'].join(' ')}>
                <span className={[styles.scope, 'eyebrow'].join(' ')}>SAVED FOR YOUR NEXT STEP</span>
                <h1>내 북마크함</h1>
                <p>다시 읽을 이야기와 함께하고 싶은 프로젝트를 모았어요.</p>
            </header>
            <div className={[styles.scope, 'tabs'].join(' ')} role="tablist" aria-label="북마크 유형">
                <button
                    role="tab"
                    aria-selected={tab === 'posts'}
                    className={[styles.scope, tab === 'posts' ? 'active' : ''].join(' ')}
                    onClick={() => setParams({ tab: 'posts' })}
                >
                    게시글 {posts.length}
                </button>
                <button
                    role="tab"
                    aria-selected={tab === 'projects'}
                    className={[styles.scope, tab === 'projects' ? 'active' : ''].join(' ')}
                    onClick={() => setParams({ tab: 'projects' })}
                >
                    프로젝트 {savedProjects.length}
                </button>
            </div>
            {tab === 'posts' ? (
                <div className={[styles.scope, 'personal-post-list'].join(' ')}>
                    {posts.map(p => (
                        <PostCard key={p.id} post={p} />
                    ))}
                </div>
            ) : (
                <div className={[styles.scope, 'personal-project-grid'].join(' ')}>
                    {savedProjects.map(p => (
                        <RecruitmentCard key={p.id} project={p} />
                    ))}
                </div>
            )}
            {empty && (
                <div className={[styles.scope, 'empty-state'].join(' ')}>
                    <h2>아직 북마크한 {tab === 'posts' ? '게시글이' : '프로젝트가'} 없어요.</h2>
                    <p>원본 화면의 북마크 버튼으로 저장할 수 있습니다.</p>
                    <Link
                        href={tab === 'posts' ? '/feeds' : '/projects'}
                        className={[styles.scope, 'button secondary'].join(' ')}
                    >
                        둘러보기
                    </Link>
                </div>
            )}
        </>
    );
}
