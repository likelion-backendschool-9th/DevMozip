import { Link } from 'react-router-dom';
import { ArrowRight, Sparkles } from 'lucide-react';
import { MatchingStart } from '../features/matching/MatchingStart';
import { ProjectPreview } from '../features/project/ProjectPreview';
import { FeedPreview } from '../features/post/FeedPreview';
import { usePrototype } from '../app/providers/PrototypeProvider';
export function HomePage(){const {settings}=usePrototype();return <div className="container home-page">{settings.showGuide&&<div className="home-demo-note"><span className="status-dot"/>기획 발표용 Mock 프로토타입 · 추천 점수와 모든 콘텐츠는 데모 데이터입니다.</div>}<MatchingStart/><ProjectPreview/><div className="premium-banner"><div className="premium-spark"><Sparkles size={26}/></div><div><strong>더 깊이 알아보고, 더 좋은 팀을 만나세요.</strong><p>상세 추천 이유와 포트폴리오로 협업의 가능성을 넓혀보세요.</p></div><Link to="/premium">DevMozip Premium <ArrowRight size={16}/></Link></div><FeedPreview/></div>;}
