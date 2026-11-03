import type { Metadata } from 'next';
import { PortfolioComposer } from '../_components/PortfolioComposer';
export const metadata: Metadata = { title: '포트폴리오 작성' };
export default function Page() {
    return <PortfolioComposer />;
}
