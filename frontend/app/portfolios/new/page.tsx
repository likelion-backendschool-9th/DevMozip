import type { Metadata } from 'next';
import { ApiPortfolioComposer } from '../_components/ApiPortfolioComposer';
export const metadata: Metadata = { title: '포트폴리오 작성' };
export default function Page() {
    return <ApiPortfolioComposer />;
}
