import {Link} from 'react-router-dom';
import {useAuth} from '../app/providers/AuthProvider';
import {useProfiles} from '../app/providers/ProfileProvider';
import {AccountLayout} from '../components/layout/AccountLayout';
import {PostCard} from '../features/post/PostCard';
import {likedPosts} from '../services/personalLibraryService';
export function LikedPostsPage(){const {user,canViewProfile}=useAuth();const {state}=useProfiles();const items=user?likedPosts(state,user.id).filter(i=>canViewProfile(i.post.authorId)):[];return <AccountLayout><header className="personal-page-heading"><span className="eyebrow">MY FAVORITE STORIES</span><h1>좋아요한 글</h1><p>마음에 담아 둔 동료들의 이야기를 다시 만나보세요.</p></header><div className="personal-post-list">{items.map(({post,likedAt})=><section key={post.id}><p className="saved-date">좋아요한 날짜 · {likedAt?<time dateTime={likedAt}>{new Date(likedAt).toLocaleString('ko-KR',{timeZone:'Asia/Seoul'})}</time>:'이전 기록 · 날짜 정보 없음'}</p><PostCard post={post}/></section>)}{!items.length&&<div className="empty-state"><h2>아직 좋아요한 글이 없어요.</h2><p>SNS 피드에서 마음에 드는 글에 좋아요를 눌러보세요.</p><Link to="/feed" className="button secondary">SNS 둘러보기</Link></div>}</div></AccountLayout>;}
