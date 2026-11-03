'use client';
import styles from './AuthShell.module.css';
import type { ReactNode } from 'react';
import Link from 'next/link';
import { Braces, Sparkles } from 'lucide-react';
export function AuthShell({ eyebrow, title, description, children, wide = false }: {
    eyebrow: string;
    title: string;
    description: string;
    children: ReactNode;
    wide?: boolean;
}) { return <div className={[styles.scope, `container auth-page ${wide ? 'auth-wide' : ''}`].join(" ")}><aside className={[styles.scope, "auth-story"].join(" ")}><div className={[styles.scope, "eyebrow"].join(" ")}><Sparkles size={15}/> BUILD YOUR NEXT CHAPTER</div><h1>좋은 동료와 함께,<br /><span>더 멋진 시작.</span></h1><p>아이디어를 나누고, 함께 만들고,<br />새로운 가능성을 발견하는 개발자의 공간.</p><div className={[styles.scope, "auth-code"].join(" ")}><Braces size={24}/><code>const next = you + together;</code><span>작은 연결에서 시작되는 큰 가능성</span></div><Link className={[styles.scope, "text-link"].join(" ")} href="/">← 메인 페이지 둘러보기</Link></aside><section className={[styles.scope, "auth-panel"].join(" ")}><div className={[styles.scope, "eyebrow"].join(" ")}>{eyebrow}</div><h2>{title}</h2><p className={[styles.scope, "auth-description"].join(" ")}>{description}</p>{children}<p className={[styles.scope, "auth-footnote"].join(" ")}>Mock 인증 프로토타입 · 실제 인증 서버와 연결하지 않습니다.</p></section></div>; }
