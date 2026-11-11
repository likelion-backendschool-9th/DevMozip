export interface ProfilePostAuthor { id: number; nickname: string | null; tag: string | null; profileImage: string | null }
export interface ProfilePostItem {
    id: number; type: 'POST' | 'REPOST'; category: string | null; title: string | null; contentPreview: string | null;
    author: ProfilePostAuthor; createdAt: string | null;
    originalPost: { id: number; contentPreview: string | null; author: ProfilePostAuthor } | null;
}
export interface ProfilePostsPage { items: ProfilePostItem[]; page: number; size: number; totalElements: number; totalPages: number }
