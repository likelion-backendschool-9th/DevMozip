export interface LikedPostAuthor { id: number; nickname: string | null; tag: string | null; profileImage: string | null }
export interface LikedPostItem {
    id: number; type: 'POST' | 'REPOST'; category: string | null; contentPreview: string | null;
    author: LikedPostAuthor; createdAt: string | null; likedAt: string | null;
    originalPost: { id: number; contentPreview: string | null; author: LikedPostAuthor } | null;
}
export interface LikedPostsPage { items: LikedPostItem[]; page: number; size: number; totalElements: number; totalPages: number }
