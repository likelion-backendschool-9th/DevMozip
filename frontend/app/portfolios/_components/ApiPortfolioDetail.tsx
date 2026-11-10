'use client';
import { useEffect, useState } from 'react';
import Link from 'next/link';
import Image from 'next/image';
import { PortfolioDeleteButton } from './PortfolioDeleteButton';
import { PortfolioContent } from './PortfolioContent';
import { portfolioInputFromDetail } from '@/lib/portfolios/portfolioApiDraft';
import { ArrowLeft, ArrowUpRight, BriefcaseBusiness, CalendarDays, Code2, Printer } from 'lucide-react';
import { useAuth } from '@/lib/state/authStore';
import { fetchPortfolioDetail, portfolioDate, PortfolioRequestError } from '@/lib/portfolios/portfolioApi';
import type { PortfolioDetail } from '@/types/portfolioApi';
import { Button } from '@/components/common/Button';
import { ProfileAvatar } from '@/components/profile/ProfileAvatar';
import styles from '../[portfolioId]/page.module.css';
import content from './PortfolioContent.module.css';
export type PortfolioDetailState = { status: 'loading' } | { status: 'error'; code: number; message: string } | { status: 'ready'; value: PortfolioDetail };
export function PortfolioDetailResult({ result, onRetry }: { result: PortfolioDetailState; onRetry: () => void }) {
    if (result.status === 'loading') return <section className="container planned" role="status">포트폴리오를 불러오는 중…</section>;
    if (result.status === 'error') return <section className="container planned" role="alert"><h1>{result.code === 404 ? '포트폴리오를 볼 수 없어요.' : '포트폴리오를 불러오지 못했어요.'}</h1><p>{result.message}</p>
        {result.code === 401 ? <Link className="button" href="/login">로그인하기</Link> : result.code === 403 ? <Link className="button" href="/subscription">프리미엄 안내</Link> : <Button variant="secondary" onClick={onRetry}>다시 시도</Button>}
        <Link className="button secondary" href="/profile/me?tab=portfolio">내 포트폴리오</Link></section>;
    const { portfolio: item, author, description, attachments } = result.value;
    const profilePath = author.tag && author.tag.toLowerCase() !== 'me' ? `/users/${encodeURIComponent(author.tag)}` : null;
    const listPath = result.value.canEdit ? '/profile/me?tab=portfolio' : profilePath ? `${profilePath}?tab=portfolio` : '/explore';
    const authorLabel = <><ProfileAvatar name={author.nickname || '작성자'} color="lavender" small image={author.profileImage || undefined} /><span><strong>{author.nickname || '작성자'}</strong><small>{item.role || '역할 미입력'}</small></span></>;
    return <div className={`${styles.page} container`}>
        <div className={styles.topbar}><Link href={listPath}><ArrowLeft size={16} />포트폴리오 목록</Link><div className={styles.actions}>
            {result.value.canEdit && <><Link className="button secondary" href={`/portfolios/${item.id}/edit`}>수정</Link><PortfolioDeleteButton id={item.id} title={item.title || '포트폴리오'} /></>}
            <Button variant="secondary" onClick={() => window.print()}><Printer size={15} />인쇄 / PDF</Button>
        </div></div>
        <article className={styles.article}>
            <header className={styles.hero}><div className={styles.heroText}>
                <div className={styles.kicker}><span className={styles.dot} /><span>DEVELOPER PORTFOLIO</span><span className={styles.field}>{item.topic || '분야 미등록'}</span></div>
                <h1>{item.title || '제목 미입력'}</h1><p className={styles.lead}>{item.summary || '한 줄 소개 미입력'}</p>
                {profilePath ? <Link className={styles.author} href={profilePath}>{authorLabel}<ArrowUpRight size={18} /></Link> : <div className={styles.author}>{authorLabel}</div>}
            </div><div className={styles.heroVisual}><div className={styles.visualLabel}><span>SELECTED WORK</span><span>{item.visibility === 'PRIVATE' ? '비공개' : '공개'}</span></div>
                <div className={styles.window}><div className={styles.windowBar}><i /><i /><i /><span>PROJECT PREVIEW</span></div>
                    <Image unoptimized width={960} height={600} className={styles.cover} src={item.thumbnailUrl || '/portfolio-cover.svg'} alt={`${item.title || '포트폴리오'} 대표 이미지`} /></div>
                <div className={styles.visualFoot}><span>설계에서 구현까지, 프로젝트의 기록</span><ArrowUpRight size={20} /></div>
            </div></header>
            <dl className={styles.facts} aria-label="포트폴리오 개요">
                <div><dt><CalendarDays size={16} />{result.value.document ? '진행 기간' : '등록일'}</dt><dd>{result.value.document ? `${result.value.document.start} — ${result.value.document.end}` : item.createdAt ? <time dateTime={item.createdAt}>{portfolioDate(item.createdAt)}</time> : '미입력'}</dd></div>
                <div><dt><BriefcaseBusiness size={16} />담당 역할</dt><dd>{item.role || '미입력'}</dd></div>
                <div><dt><CalendarDays size={16} />{result.value.document ? '팀 구성' : '수정일'}</dt><dd>{result.value.document ? result.value.document.teamSize || '미입력' : item.updatedAt ? <time dateTime={item.updatedAt}>{portfolioDate(item.updatedAt)}</time> : '미입력'}</dd></div>
                <div><dt><Code2 size={16} />기술 스택</dt><dd className={styles.stackList}>{item.techStack.length ? item.techStack.map(stack => <span key={stack}>{stack}</span>) : '미입력'}</dd></div>
            </dl>
            <nav className={styles.caseNav} aria-label="포트폴리오 상세 목차">{result.value.document ? result.value.document.blocks.map(b => <a key={b.id} href={`#case-${b.id}`}>{b.title}</a>) : <a href="#portfolio-description">프로젝트 설명</a>}{!!attachments.length && <a href="#portfolio-attachments">첨부파일</a>}</nav>
            <div className={styles.content}>
                {result.value.document ? <PortfolioContent item={portfolioInputFromDetail(result.value)} /> : <section className={content.section} id="portfolio-description"><header><span className={content.number}>01</span><h2>프로젝트 설명</h2></header><div className={content.body}><p className={content.plain}>{description || '등록된 설명이 없습니다.'}</p></div></section>}
                {!!attachments.length && <section className={content.section} id="portfolio-attachments"><header><span className={content.number}>02</span><h2>첨부파일</h2></header><div className={`${content.body} ${content.links}`}>{attachments.map((file, index) => <a key={`${file.url}:${index}`} href={file.url} target="_blank" rel="noopener noreferrer">{file.name || `첨부파일 ${index + 1}`} ↗</a>)}</div></section>}
                <footer className={styles.caseFooter}><span>PROJECT / {item.title || '제목 미입력'}</span><Link href={listPath}>다른 포트폴리오 보기<ArrowUpRight size={17} /></Link></footer>
            </div>
        </article>
    </div>;
}
export function ApiPortfolioDetail({ id }: { id: string }) {
    const { user } = useAuth(); const [attempt, setAttempt] = useState(0);
    return <PortfolioDetailLoader key={`${user?.email || ''}:${id}:${attempt}`} id={id} retry={() => setAttempt(v => v + 1)} />;
}
function PortfolioDetailLoader({ id, retry }: { id: string; retry: () => void }) {
    const [result, setResult] = useState<PortfolioDetailState>({ status: 'loading' });
    useEffect(() => {
        const controller = new AbortController();
        void fetchPortfolioDetail(id, controller.signal).then(value => {
            if (!controller.signal.aborted) setResult({ status: 'ready', value });
        }).catch(error => {
            if (!controller.signal.aborted) setResult({ status: 'error', code: error instanceof PortfolioRequestError ? error.status : 502,
                message: error instanceof PortfolioRequestError ? error.message : '포트폴리오 응답을 확인할 수 없어요.' });
        });
        return () => controller.abort();
    }, [id]);
    return <PortfolioDetailResult result={result} onRetry={retry} />;
}
