import { Link, useParams } from 'react-router-dom';
import { ArrowLeft, ArrowRight, Construction } from 'lucide-react';
import type { RouteDefinition } from '../types';
export function PlannedPage({ route }: { route: RouteDefinition }) {
 const params = useParams();
 return <div className="container planned"><Link to="/" className="text-link"><ArrowLeft size={16}/> 메인 페이지로</Link><section className="planned-card"><div className="planned-icon"><Construction size={32}/></div><span className="outline-badge">{route.stage}단계 구현 예정</span><h1>{route.title}</h1><p>{route.description}</p><div className="planned-detail"><strong>페이지 경로가 준비되었습니다.</strong><p>현재는 공통 레이아웃과 메인 페이지를 구현한 상태입니다. 이 화면의 상세 기능은 추후 단계에서 구현합니다.</p><code>{route.path}</code>{Object.keys(params).length > 0 && <small>현재 경로 ID: {Object.values(params).join(' / ')}</small>}</div><Link className="button" to="/">메인 페이지로 돌아가기 <ArrowRight size={16}/></Link></section></div>;
}
