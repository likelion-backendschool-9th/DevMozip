import type { Metadata } from 'next';
import styles from './page.module.css';
import Link from 'next/link';
import { ArrowRight, Sparkles } from 'lucide-react';
import { FeedPreview } from './_components/FeedPreview';
import { GuideOnly } from './_components/GuideOnly';
import { MatchingStart } from './_components/MatchingStart';
import { ProjectPreview } from './_components/ProjectPreview';

export const metadata: Metadata = { title: '홈' };

// 루트 레이아웃과 같은 폴더라 제목(metadata)을 page에서 내보내야 하므로 서버 컴포넌트로 둔다.
// 저장된 설정에 따라 바뀌는 안내 문구(GuideOnly)와 각 미리보기만 클라이언트 컴포넌트다.
export default function HomePage() {
    return (
        <div className={[styles.scope, 'container home-page'].join(' ')}>
            <GuideOnly>
                <div className={[styles.scope, 'home-demo-note'].join(' ')}>
                    <span className={[styles.scope, 'status-dot'].join(' ')} />
                    기획 발표용 Mock 프로토타입 · 추천 점수와 모든 콘텐츠는 데모 데이터입니다.
                </div>
            </GuideOnly>
            <MatchingStart />
            <ProjectPreview />
            <div className={[styles.scope, 'premium-banner'].join(' ')}>
                <div className={[styles.scope, 'premium-spark'].join(' ')}>
                    <Sparkles size={26} />
                </div>
                <div>
                    <strong>더 깊이 알아보고, 더 좋은 팀을 만나세요.</strong>
                    <p>상세 추천 이유와 포트폴리오로 협업의 가능성을 넓혀보세요.</p>
                </div>
                <Link href="/subscription">
                    DevMozip Premium <ArrowRight size={16} />
                </Link>
            </div>
            <FeedPreview />
        </div>
    );
}
