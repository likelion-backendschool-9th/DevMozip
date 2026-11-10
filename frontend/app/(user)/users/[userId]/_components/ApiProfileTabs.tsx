'use client';
import { profileTabs } from '@/mocks/profile';
import { useQueryState } from '@/lib/navigation';
import { ApiProfileLinks } from './ApiProfileLinks';
import { ApiProfileOptions } from './ApiProfileOptions';
import { ApiProfileIntroduction } from './ApiProfileIntroduction';
import { ApiProfileReviews } from './ApiProfileReviews';
import { ApiProfilePosts } from './ApiProfilePosts';
import { ApiPortfolioTab } from './ApiPortfolioTab';
import { Card } from '@/components/common/Card';
import styles from '../page.module.css';
import aboutStyles from './AboutTab.module.css';

// 탭과 섹션 배치를 유지하되 아직 조회하지 않은 데이터를 빈 목록/실제 값으로 단정하지 않는다.
export function ApiProfileTabs({ nickname, slug, canEdit = false, trustScore = null }: { nickname: string; slug?: string; canEdit?: boolean; trustScore?: number | null }) {
    const [params, setParams] = useQueryState();
    const tab = profileTabs.find(item => item.id === params.get('tab'))?.id || 'about';
    function select(id: string) {
        const next = new URLSearchParams(params);
        next.set('tab', id);
        setParams(next, { replace: true });
    }
    const scope = (name: string) => `${styles.scope} ${name}`;
    return <>
        <div className={scope('profile-tabs')} role="tablist" aria-label="프로필 정보">
            {profileTabs.map((item, index) => <button key={item.id} role="tab" id={`profile-tab-${item.id}`}
                aria-controls={`profile-panel-${item.id}`} aria-selected={tab === item.id} tabIndex={tab === item.id ? 0 : -1}
                className={scope(tab === item.id ? 'active' : '')} onClick={() => select(item.id)}
                onKeyDown={event => {
                    let next = index;
                    if (event.key === 'ArrowRight') next = (index + 1) % profileTabs.length;
                    else if (event.key === 'ArrowLeft') next = (index + profileTabs.length - 1) % profileTabs.length;
                    else if (event.key === 'Home') next = 0;
                    else if (event.key === 'End') next = profileTabs.length - 1;
                    else return;
                    event.preventDefault(); select(profileTabs[next].id);
                    document.getElementById(`profile-tab-${profileTabs[next].id}`)?.focus();
                }}>{item.label}</button>)}
        </div>
        <section className={scope('profile-tab-panel')} role="tabpanel" id={`profile-panel-${tab}`} aria-labelledby={`profile-tab-${tab}`} tabIndex={0}>
            {tab !== 'about' && tab !== 'portfolio' && tab !== 'posts' && tab !== 'reviews' && <p className="text-muted">이 탭의 정보는 아직 조회 API에 연결되지 않았습니다.</p>}
            {tab === 'about' ? <div className={aboutStyles.wrapper}>
                <div className={`${aboutStyles.layout} about-grid`}>
                    <Card className={`${aboutStyles.introduction} profile-section about-intro`}>
                        {slug ? <ApiProfileIntroduction key={slug} nickname={nickname} slug={slug} canEdit={canEdit} /> : <><div className={`${aboutStyles.title} tab-section-title`}><div><span className={aboutStyles.eyebrow}>ABOUT ME</span><h2>반가워요, {nickname}입니다.</h2></div></div><p>자기소개 조회 연결 전</p></>}
                    </Card>
                    <Card className={`${aboutStyles.sidebar} profile-section`}>
                        {slug && <ApiProfileOptions key={`options:${slug}`} slug={slug} canEdit={canEdit} />}
                        {slug && <ApiProfileLinks key={`links:${slug}`} slug={slug} canEdit={canEdit} />}
                    </Card>
                </div>
            </div> : tab === 'portfolio' ? <ApiPortfolioTab key={canEdit ? 'me' : slug} slug={slug} own={canEdit} /> : tab === 'posts' ? <ApiProfilePosts key={canEdit ? 'me' : slug} slug={slug} own={canEdit} /> : tab === 'reviews' ? <ApiProfileReviews key={canEdit ? 'me' : slug} slug={slug} own={canEdit} trustScore={trustScore} /> : <Card className="profile-section">
                <div className="tab-section-title"><div><h2>{'함께 만들어온 프로젝트'}</h2></div></div>
                <div className="empty-state">{profileTabs.find(item => item.id === tab)?.label} 조회 연결 전</div>
            </Card>}
        </section>
    </>;
}
