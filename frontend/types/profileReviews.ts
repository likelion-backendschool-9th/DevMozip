export interface ProfileReviewItem {
    id: number; projectId: number | null; projectTitle: string; authorUserId: number; authorNickname: string;
    authorTag: string | null; rating: number; keywords: string[]; submittedAt: string | null;
}
export interface ProfileReviewsPage {
    userId: number; count: number; average: number; distribution: number[];
    keywords: { name: string; count: number }[]; items: ProfileReviewItem[]; page: number; size: number; totalPages: number;
}
