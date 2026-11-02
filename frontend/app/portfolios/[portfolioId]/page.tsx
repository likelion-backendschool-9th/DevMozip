import type {Metadata} from 'next';
import {PortfolioDetailScreen} from './_components/PortfolioDetailScreen';
export const metadata:Metadata={title:'포트폴리오 상세'};
export default function Page(){return <PortfolioDetailScreen/>;}
