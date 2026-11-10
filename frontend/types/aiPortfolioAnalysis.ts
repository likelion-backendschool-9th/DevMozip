export interface AiPortfolioAnalysis {
    recommendationId: number;
    status: 'READY' | 'NO_DATA' | 'NOT_GENERATED' | 'STALE' | 'PENDING' | 'FAILED';
    cached: boolean;
    generatedAt: string | null;
    portfolios: { id: number; title: string; selectionReason: string; truncated: boolean }[];
    assessments: { requirementId: string; requirement: string; status: 'MET' | 'PARTIAL' | 'MISMATCH' | 'UNKNOWN'; portfolioId: number | null; field: string | null; quote: string | null }[];
    questions: string[];
    notice: string;
}
