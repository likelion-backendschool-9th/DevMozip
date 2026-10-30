import { Link } from 'react-router-dom';
export function NotFoundPage() { return <div className="container planned"><section className="planned-card"><div className="eyebrow">404 · PAGE NOT FOUND</div><h1>페이지를 찾을 수 없어요.</h1><p>주소를 확인하거나 메인 화면으로 이동해 주세요.</p><Link className="button" to="/">메인으로</Link></section></div>; }
