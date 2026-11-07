export interface AiConditions {
    interests: string[];
    stacks?: string[];
    roles?: { name: string; count: number }[];
    career: string;
    durationMonths: number;
}
export interface AiConversationSummary { clientKey?: string | null; id: number; title: string | null; createdAt: string; updatedAt: string }
export interface AiMessage { clientKey?: string | null; id: number; role: 'USER' | 'ASSISTANT'; content: string; createdAt: string }
export interface AiConversationDetail extends AiConversationSummary { currentConditions: Partial<AiConditions>; messages: AiMessage[] }
export interface AiRequestSummary { requestId: number; status: 'PENDING' | 'SUCCESS' | 'FAILED'; createdAt: string }
export interface AiRecommendationItem { id: number; recommendedUserId: number | null; recommendedProjectId: number | null; matchScore: number; basicReason: string; rank: number; createdAt: string }
export interface AiRecommendation extends AiRequestSummary {
    conversationId: number;
    messageId: number | null;
    conditions: AiConditions;
    completedAt: string | null;
    developers: AiRecommendationItem[];
    projects: AiRecommendationItem[];
}
