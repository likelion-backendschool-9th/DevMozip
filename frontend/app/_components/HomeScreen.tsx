'use client';
import styles from './HomeScreen.module.css';
import Link from 'next/link';
import { ArrowRight, Sparkles } from 'lucide-react';
import { MatchingStart } from '@/app/_components/MatchingStart';
import { ProjectPreview } from '@/app/_components/ProjectPreview';
import { FeedPreview } from '@/app/_components/FeedPreview';
import { usePrototype } from '@/lib/state/PrototypeProvider';
export function HomePage() { const { settings } = usePrototype(); return <div className={[styles.scope, "container home-page"].join(" ")}>{settings.showGuide && <div className={[styles.scope, "home-demo-note"].join(" ")}><span className={[styles.scope, "status-dot"].join(" ")}/>기획 발표용 Mock 프로토타입 · 추천 점수와 모든 콘텐츠는 데모 데이터입니다.</div>}<MatchingStart /><ProjectPreview /><div className={[styles.scope, "premium-banner"].join(" ")}><div className={[styles.scope, "premium-spark"].join(" ")}><Sparkles size={26}/></div><div><strong>더 깊이 알아보고, 더 좋은 팀을 만나세요.</strong><p>상세 추천 이유와 포트폴리오로 협업의 가능성을 넓혀보세요.</p></div><Link href="/premium">DevMozip Premium <ArrowRight size={16}/></Link></div><FeedPreview /></div>; }
