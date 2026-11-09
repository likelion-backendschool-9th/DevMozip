import type { PortfolioDocument } from '@/lib/portfolios/portfolioDocument';
export interface PortfolioSummary {
    id: number; userId: number; title: string | null; thumbnailUrl: string | null; topic: string | null;
    summary: string | null; role: string | null; techStack: string[]; visibility: 'PUBLIC' | 'PRIVATE';
    createdAt: string | null; updatedAt: string | null;
}
export interface PortfolioPage { items: PortfolioSummary[]; page: number; size: number; totalElements: number; totalPages: number }
export interface PortfolioDetail {
    portfolio: PortfolioSummary;
    author: { id: number; nickname: string | null; tag: string | null; profileImage: string | null };
    document?: PortfolioDocument | null; canEdit?: boolean; submitted?: boolean;
    description: string | null; attachments: { name: string | null; url: string }[];
}
