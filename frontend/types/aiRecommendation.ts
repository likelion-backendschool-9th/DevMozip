export interface AiConditions {
    interests: string[];
    stacks?: string[];
    roles?: { name: string; count: number }[];
    career: string;
    durationMonths: number;
}
export interface AiConversationSummary { clientKey?: string | null; id: number; title: string | null; createdAt: string; updatedAt: string }
export interface AiMessage { clientKey?: string | null; id: number; role: 'USER' | 'ASSISTANT'; content: string; createdAt: string }
export interface AiAnalysisResponse { userMessage: AiMessage; assistantMessage: AiMessage; currentConditions: Partial<AiConditions>; analysis: unknown; readyForRecommendation: boolean; replayed: boolean }
export interface AiConversationDetail extends AiConversationSummary { currentConditions: Partial<AiConditions>; messages: AiMessage[] }
export interface AiRequestSummary { requestId: number; status: 'PENDING' | 'SUCCESS' | 'FAILED'; createdAt: string }
export interface AiPublicDeveloper { trustScore?: number | null; career?: string | null; collaborationStatus?: string | null; tag: string; nickname: string | null; profileImage: string | null; jobCategories: string[]; skills: string[] }
export interface AiRecommendationItem { developer?: AiPublicDeveloper | null; id: number; recommendedUserId: number | null; recommendedProjectId: number | null; matchScore: number; basicReason: string; rank: number; createdAt: string }
export interface AiRecommendation extends AiRequestSummary {
    conversationId: number;
    messageId: number | null;
    conditions: AiConditions;
    completedAt: string | null;
    developers: AiRecommendationItem[];
    projects: AiRecommendationItem[];
}

export interface AiPublicProject { id: number; title: string; summary: string; description: string; imageUrl: string | null; stacks: string[]; roles: string[] }
