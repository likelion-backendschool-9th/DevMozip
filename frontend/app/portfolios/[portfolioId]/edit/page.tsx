import type { Metadata } from 'next';
import { PortfolioEditRoute } from '../../_components/ApiPortfolioComposer';
export const metadata: Metadata = { title: '포트폴리오 수정' };
export default function Page() {
    return <PortfolioEditRoute />;
}
