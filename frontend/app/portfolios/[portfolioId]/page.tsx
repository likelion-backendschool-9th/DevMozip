'use client';
import { useState } from 'react';
import { useParams } from 'next/navigation';
import Link from 'next/link';
import {
    ExternalLink,
    Printer,
    ArrowLeft,
    ArrowUpRight,
    Code2,
    CalendarDays,
    Users,
    BriefcaseBusiness,
} from 'lucide-react';
import { useAuth } from '@/lib/state/authStore';
import { useProfiles } from '@/lib/state/profileStore';
import {
    canOpenPortfolioDetail,
    canUsePortfolioFeedback,
    portfolioFeedback,
    safeExternalUrl,
} from '@/lib/profiles/profileService';
import { getPortfolioBlocks, portfolioBlockImages, portfolioFields } from '@/lib/profiles/portfolioBlocks';
import { PortfolioContent } from '@/app/portfolios/_components/PortfolioContent';
import { Button } from '@/components/common/Button';
import styles from './page.module.css';
export default function PortfolioDetailScreen() {
    const { portfolioId } = useParams<{ portfolioId: string }>();
    const { user, canViewProfile } = useAuth();
    const { state } = useProfiles();
    const [feedback, setFeedback] = useState(false);
    const item = state.portfolios.find(p => p.id === portfolioId);
    const available = item && canViewProfile(item.ownerId);
    const owner = item?.ownerId === user?.id;
    if (!available)
        return (
            <div className="container planned">
                <section className="planned-card">
                    <h1>포트폴리오를 볼 수 없어요.</h1>
                    <p>삭제되었거나 비공개인 포트폴리오입니다.</p>
                    <Link className="button secondary" href="/explore">
                        개발자 탐색
                    </Link>
                </section>
            </div>
        );
    if (!canOpenPortfolioDetail(user?.role, item.ownerId, user?.id))
        return (
            <div className="container planned">
                <section className="planned-card">
                    <h1>프리미엄 포트폴리오 상세 열람</h1>
                    <p>상세 내용을 보려면 프리미엄 데모 구독을 활성화해 주세요.</p>
                    <Link className="button" href="/subscription">
                        프리미엄 안내
                    </Link>
                    <Link className="text-link" href={`/users/${item.ownerId}?tab=portfolio`}>
                        포트폴리오 목록으로
                    </Link>
                </section>
            </div>
        );
    return (
        <div className={`container ${styles.page}`}>
            <div className={styles.topbar}>
                <Link href={`/users/${item.ownerId}?tab=portfolio`}>
                    <ArrowLeft size={16} />
                    포트폴리오 목록
                </Link>
                <div className={styles.actions}>
                    {owner && (
                        <Link className="button secondary" href={`/portfolios/${item.id}/edit`}>
                            포트폴리오 수정
                        </Link>
                    )}
                    <Button variant="secondary" onClick={() => window.print()}>
                        <Printer size={15} />
                        인쇄 / PDF
                    </Button>
                </div>
            </div>
            <article className={styles.article}>
                <header className={styles.hero}>
                    <div className={styles.heroText}>
                        <div className={styles.kicker}>
                            <span className={styles.dot} />
                            <span>DEVELOPER PORTFOLIO</span>
                            <span className={styles.field}>{portfolioFields(item).join(' · ') || '분야 미등록'}</span>
                        </div>
                        <h1>{item.title}</h1>
                        <p className={styles.lead}>{item.summary}</p>
                        <Link className={styles.author} href={`/users/${item.ownerId}`}>
                            <span className={styles.authorMark}>
                                {state.profiles[item.ownerId]?.nickname?.slice(0, 1) || 'D'}
                            </span>
                            <span>
                                <strong>{state.profiles[item.ownerId]?.nickname || '작성자'}</strong>
                                <small>{item.role}</small>
                            </span>
                            <ArrowUpRight size={18} />
                        </Link>
                        {!!item.links?.length && (
                            <div className={styles.heroLinks} role="region" aria-label="프로젝트 관련 링크">
                                {item.links.map((link, index) => {
                                    const href = safeExternalUrl(link.url);
                                    return href ? (
                                        <a
                                            key={index}
                                            className={index === 0 ? styles.primaryLink : styles.secondaryLink}
                                            href={href}
                                            target="_blank"
                                            rel="noopener noreferrer"
                                        >
                                            {index === 0 ? <Code2 size={18} /> : <ExternalLink size={16} />}
                                            <span>{link.label}</span>
                                            <ArrowUpRight size={16} />
                                        </a>
                                    ) : null;
                                })}
                            </div>
                        )}
                    </div>
                    <div className={styles.heroVisual}>
                        <div className={styles.visualLabel}>
                            <span>SELECTED WORK</span>
                            <span>{item.end?.slice(0, 4)}</span>
                        </div>
                        <div className={styles.window}>
                            <div className={styles.windowBar}>
                                <i />
                                <i />
                                <i />
                                <span>PROJECT PREVIEW</span>
                            </div>
                            <img
                                className={styles.cover}
                                src={item.coverImage || item.images?.[0]?.src || '/portfolio-cover.svg'}
                                alt={`${item.title} 대표 이미지`}
                            />
                        </div>
                        <div className={styles.visualFoot}>
                            <span>설계에서 구현까지, 프로젝트의 기록</span>
                            <ArrowUpRight size={20} />
                        </div>
                    </div>
                </header>
                <dl className={styles.facts} aria-label="프로젝트 개요">
                    <div>
                        <dt>
                            <CalendarDays size={16} />
                            진행 기간
                        </dt>
                        <dd>
                            {item.start} — {item.end}
                        </dd>
                    </div>
                    <div>
                        <dt>
                            <BriefcaseBusiness size={16} />
                            담당 역할
                        </dt>
                        <dd>{item.role}</dd>
                    </div>
                    <div>
                        <dt>
                            <Users size={16} />팀 구성
                        </dt>
                        <dd>{item.teamSize || '미등록'}</dd>
                    </div>
                    <div>
                        <dt>
                            <Code2 size={16} />
                            기술 스택
                        </dt>
                        <dd className={styles.stackList}>
                            {item.stacks.map(stack => (
                                <span key={stack}>{stack}</span>
                            ))}
                        </dd>
                    </div>
                </dl>
                <nav className={styles.caseNav} aria-label="포트폴리오 상세 목차">
                    {getPortfolioBlocks(item)
                        .filter(block => block.kind !== 'gallery' || !!portfolioBlockImages(item, block).length)
                        .map(block => (
                            <a key={block.id} href={`#case-${block.id}`}>
                                {block.title}
                            </a>
                        ))}
                </nav>
                <div className={styles.content}>
                    <PortfolioContent item={item} />
                    {owner && canUsePortfolioFeedback(item.ownerId, user?.id || null, user?.role) && (
                        <div className={styles.feedbackActions}>
                            <div>
                                <strong>다음 개선점을 찾아보세요.</strong>
                                <p>작성자 본인에게만 제공되는 Mock 포트폴리오 피드백입니다.</p>
                            </div>
                            <Button variant="secondary" onClick={() => setFeedback(!feedback)}>
                                AI 포트폴리오 피드백 데모
                            </Button>
                        </div>
                    )}
                    {owner && feedback && canUsePortfolioFeedback(item.ownerId, user?.id || null, user?.role) && (
                        <section className={styles.feedback} aria-label="나만 보는 AI 포트폴리오 피드백">
                            <h2>나만 보는 AI 피드백 · Mock</h2>
                            <p>입력한 내용에 따른 규칙 기반 데모이며 실제 AI 호출은 없습니다.</p>
                            <ul>
                                {portfolioFeedback(item).map(point => (
                                    <li key={point}>{point}</li>
                                ))}
                            </ul>
                        </section>
                    )}
                    <footer className={styles.caseFooter}>
                        <span>PROJECT / {item.title}</span>
                        <Link href={`/users/${item.ownerId}?tab=portfolio`}>
                            다른 포트폴리오 보기 <ArrowUpRight size={17} />
                        </Link>
                    </footer>
                </div>
            </article>
        </div>
    );
}
