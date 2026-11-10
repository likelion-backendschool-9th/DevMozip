'use client';
import { useParams } from 'next/navigation';
import { PrototypePortfolioDetail } from '../_components/PrototypePortfolioDetail';
import { ApiPortfolioDetail } from '../_components/ApiPortfolioDetail';
export default function PortfolioDetailScreen() {
    const { portfolioId } = useParams<{ portfolioId: string }>();
    // 기존 문자열 ID의 프로토타입은 유지한다. 실제 숫자 ID는 API 실패 시 Mock으로 대체하지 않는다.
    return /^\d+$/.test(portfolioId) ? <ApiPortfolioDetail key={portfolioId} id={portfolioId} /> : <PrototypePortfolioDetail />;
}
