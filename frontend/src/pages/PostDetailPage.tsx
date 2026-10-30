import { useEffect } from 'react';
import { Link, useLocation, useParams } from 'react-router-dom';
import { useAuth } from '../app/providers/AuthProvider';
import { useProfiles } from '../app/providers/ProfileProvider';
import { PostCard } from '../features/post/PostCard';
import { Comments } from '../features/post/Comments';
import { sessionVisitor } from '../services/socialService';
export function PostDetailPage(){const {postId=''}=useParams();const location=useLocation();const {user,canViewProfile}=useAuth();const {state,act}=useProfiles();const post=state.posts.find(p=>p.id===postId);const readable=post&&canViewProfile(post.authorId)&&(!post.hidden||post.authorId===user?.id);useEffect(()=>{if(readable)act({type:'social-view',id:postId,visitorId:sessionVisitor()});},[postId,!!readable]);useEffect(()=>{if(location.hash!=='#comments')return;const id=requestAnimationFrame(()=>document.getElementById('comments')?.scrollIntoView({block:'start'}));return()=>cancelAnimationFrame(id);},[location.hash,postId]);if(!readable)return <div className="container planned"><section className="planned-card"><h1>게시글을 찾을 수 없어요.</h1><p>삭제·숨김 처리되었거나 비공개 계정의 게시글입니다.</p><Link className="button" to="/feed">전체 피드로</Link></section></div>;return <div className="container sns-detail-page"><Link className="text-link" to="/feed">← 전체 피드</Link><PostCard post={post} full/><Comments postId={postId}/></div>;}
