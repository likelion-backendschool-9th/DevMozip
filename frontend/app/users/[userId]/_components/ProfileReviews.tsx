'use client';
import styles from './ProfileReviews.module.css';
import Link from 'next/link';
import { Star } from 'lucide-react';
import { useAuth } from '@/lib/state/AuthProvider';
import { useProfiles } from '@/lib/state/ProfileProvider';
import { useProjects } from '@/lib/state/ProjectProvider';
import { keywordStats, starColor, temperature } from '@/lib/profiles/profileService';
import { Card } from '@/components/common/Card';
export function ProfileReviews({ profileId }: {
    profileId: string;
}) {
    const { projects } = useProjects();
    const { administration } = useAuth();
    const { state } = useProfiles();
    const reviews = state.reviews.filter(r => r.targetId === profileId);
    const stats = keywordStats(state.reviews, profileId, administration.keywords);
    const temp = temperature(state.reviews, profileId);
    const average = reviews.length ? (reviews.reduce((sum, r) => sum + r.rating, 0) / reviews.length).toFixed(1) : '—';
    return <div className={[styles.scope, "reviews-tab"].join(" ")}><div className={[styles.scope, "tab-section-title"].join(" ")}><div><h2>함께한 동료들의 리뷰</h2><p>실제 Mock 협업 이력을 바탕으로 나누는 신뢰의 기록입니다.</p></div></div><div className={[styles.scope, "review-overview"].join(" ")}><Card className={[styles.scope, "profile-section trust-card"].join(" ")}><span>신뢰 온도</span><div style={{ color: starColor(temp) }}><Star size={30} fill="currentColor"/><strong>{temp.toFixed(1)}<small>°C</small></strong></div><p>초기 36.5°C · 별점에 따른 데모 계산</p><div className={[styles.scope, "review-metrics"].join(" ")}><span>평균 별점 <b>{average}</b></span><span>협업 리뷰 <b>{reviews.length}개</b></span></div></Card><Card className={[styles.scope, "profile-section keyword-chart"].join(" ")}><h3>동료들이 발견한 강점</h3><p>비율은 전체 선택 키워드 중 해당 키워드의 비중입니다.</p><div className={[styles.scope, "keyword-bubbles"].join(" ")} role="list" aria-label="리뷰 키워드 버블 차트">{stats.map((s, i) => <div role="listitem" className={[styles.scope, `keyword-bubble bubble-${i}`].join(" ")} key={s.keyword} style={{ width: 72 + Math.sqrt(s.percent / 100) * 66, height: 72 + Math.sqrt(s.percent / 100) * 66 }}><span>{s.keyword}</span><strong>{s.count}회</strong><small>{s.percent}%</small></div>)}</div>{stats.every(s => s.count === 0) && <p className={[styles.scope, "chart-empty"].join(" ")}>아직 선택된 키워드가 없습니다. 키워드 없이도 별점 리뷰를 작성할 수 있어요.</p>}</Card></div><p className={[styles.scope, "profile-policy-note"].join(" ")}>리뷰는 종료된 프로젝트의 리뷰 요청 알림에서만 작성할 수 있습니다. 이 탭은 받은 리뷰 조회 전용입니다.</p><div className={[styles.scope, "review-records"].join(" ")}>{reviews.map(r => <Card className={[styles.scope, "profile-section"].join(" ")} key={r.id}><div className={[styles.scope, "review-record-head"].join(" ")}><Link href={`/users/${r.authorId}`}>{state.profiles[r.authorId]?.nickname || '협업자'}</Link><span className={[styles.scope, "review-stars"].join(" ")} aria-label={`별점 ${r.rating}점`}>{'★'.repeat(r.rating)}{'☆'.repeat(5 - r.rating)}</span><small>{new Date(r.createdAt).toLocaleDateString('ko-KR', { timeZone: 'Asia/Seoul' })}</small></div><p>{projects.find(p => p.id === r.projectId)?.title}</p><div className={[styles.scope, "tags"].join(" ")}>{r.keywords.map(k => <span key={k}>{k}</span>)}{r.keywords.length === 0 && <span>별점만 작성한 리뷰</span>}</div></Card>)}</div><p className={[styles.scope, "profile-policy-note"].join(" ")}>임시 온도 규칙: 36.5 + Σ(별점 − 3) × 0.5, 0~100°C 범위. 별 색상: 36.5°C 미만 회색, 38°C 미만 시안, 40°C 미만 보라, 그 이상 골드. 실제 서비스 정책은 미확정입니다.</p></div>;
}
