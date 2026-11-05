'use client';
import styles from './FeedPreview.module.css';
import { useAuth } from '@/lib/state/authStore';
import { useEffect, useRef, useState } from 'react';
import Link from 'next/link';
import { ArrowUpRight, ChevronLeft, ChevronRight, Heart, MessageCircle } from 'lucide-react';
import { categories } from '@/mocks/home';
import { postMetrics } from '@/lib/social/socialService';
import { filterPosts } from '@/lib/matching/homeService';
import { useProfiles } from '@/lib/state/profileStore';
import { useHome } from '@/lib/state/homeStore';
import { Card } from '@/components/common/Card';
export function FeedPreview() {
    const { canViewProfile } = useAuth();
    const { state } = useHome();
    const { state: profileState } = useProfiles();
    const [mode, setMode] = useState('recommended');
    const [category, setCategory] = useState<string>('전체');
    const track = useRef<HTMLDivElement>(null);
    const [edges, setEdges] = useState({ start: true, end: false });
    const items = filterPosts(mode, category, state.following, state.role !== 'guest', profileState.posts.filter(p => !p.hidden && canViewProfile(p.authorId)));
    const measure = () => { const el = track.current; if (el)
        setEdges({ start: el.scrollLeft <= 2, end: el.scrollLeft + el.clientWidth >= el.scrollWidth - 2 }); };
    useEffect(() => { track.current?.scrollTo({ left: 0 }); measure(); const observer = new ResizeObserver(measure); if (track.current)
        observer.observe(track.current); return () => observer.disconnect(); }, [mode, category, state.role, items.length]);
    const slide = (direction: number) => { const el = track.current; if (el) {
        const child = el.firstElementChild;
        el.scrollBy({ left: direction * ((child?.getBoundingClientRect().width || 320) + 18), behavior: window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'instant' : 'smooth' });
    } };
    return <section className={[styles.scope, "home-section feed-section"].join(" ")}><div className={[styles.scope, "section-heading"].join(" ")}><div><div className={[styles.scope, "eyebrow"].join(" ")}>DEV STORIES & INSIGHTS</div><h2>개발자의 오늘, 함께 나누는 이야기</h2><p>새로운 발견부터 작은 고민까지, 동료들의 이야기를 만나보세요.</p></div><Link className={[styles.scope, "text-link"].join(" ")} href="/feeds">전체 피드 더보기<ArrowUpRight size={16}/></Link></div><div className={[styles.scope, "feed-toolbar"].join(" ")}><div className={[styles.scope, "tabs compact"].join(" ")}>{[{ value: 'recommended', label: '추천' }, { value: 'following', label: '팔로잉' }].map(t => <button key={t.value} aria-pressed={mode === t.value} className={[styles.scope, mode === t.value ? 'active' : ''].join(" ")} onClick={() => setMode(t.value)}>{t.label}</button>)}</div><div className={[styles.scope, "chips feed-categories"].join(" ")}>{categories.map(c => <button key={c} className={[styles.scope, `chip ${category === c ? 'selected' : ''}`].join(" ")} aria-pressed={category === c} onClick={() => setCategory(c)}>{c}</button>)}</div><div className={[styles.scope, "slider-buttons"].join(" ")}><button className={[styles.scope, "icon-button"].join(" ")} aria-label="이전 게시글" disabled={edges.start || items.length === 0} onClick={() => slide(-1)}><ChevronLeft size={19}/></button><button className={[styles.scope, "icon-button"].join(" ")} aria-label="다음 게시글" disabled={edges.end || items.length === 0} onClick={() => slide(1)}><ChevronRight size={19}/></button></div></div><div className={[styles.scope, "feed-track"].join(" ")} ref={track} onScroll={measure} tabIndex={0} role="region" aria-label="SNS 게시글 가로 슬라이드">{items.map(p => { const metrics = postMetrics(profileState, p); const profile = profileState.profiles[p.authorId]; if (!profile)
        return null; const author = { id: profile.id, name: profile.nickname, role: profile.job, color: profile.color }; return <Card className={[styles.scope, "post-preview"].join(" ")} key={p.id}><div className={[styles.scope, `post-visual ${p.color}`].join(" ")}><span>{p.category}</span><strong>{p.tags[0] || p.category}</strong>{p.images?.[0] && <img className={[styles.scope, "main-post-image"].join(" ")} src={p.images[0].url} alt={p.images[0].name}/>}<span className={[styles.scope, "visual-code"].join(" ")} aria-hidden="true">{'</>'}</span></div><div className={[styles.scope, "post-content"].join(" ")}><Link href={`/users/${author.id}`} className={[styles.scope, "post-author"].join(" ")}><span className={[styles.scope, `small-avatar ${author.color}`].join(" ")}>{author.name.slice(1)}</span><strong>{author.name}</strong><span>{author.role}</span></Link><Link href={`/feeds/${p.id}`}><h3>{p.title}</h3></Link><p>{p.body}</p><div className={[styles.scope, "tags"].join(" ")}>{p.tags.map(t => <span key={t}>#{t}</span>)}</div><div className={[styles.scope, "post-bottom"].join(" ")}><span><Heart size={14}/>{metrics.likes}</span><span><MessageCircle size={14}/>{metrics.comments}</span><Link href={`/feeds/${p.id}`}>이야기 읽기 <ArrowUpRight size={14}/></Link></div></div></Card>; })}</div>{items.length === 0 && <div className={[styles.scope, "empty-state"].join(" ")}><strong>{mode === 'following' && state.role === 'guest' ? '로그인하고 동료들의 소식을 만나보세요.' : '아직 이 카테고리의 소식이 없어요.'}</strong><p>{mode === 'following' && state.role === 'guest' ? '로그인 후 팔로잉한 동료들의 소식을 확인할 수 있습니다.' : '다른 카테고리나 추천 피드를 선택해 보세요.'}</p></div>}</section>;
}
