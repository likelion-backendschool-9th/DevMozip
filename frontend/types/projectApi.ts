export type ProjectRoleCode = 'FRONTEND' | 'BACKEND' | 'FULLSTACK' | 'DESIGNER' | 'DEVOPS' | 'DATA_ENGINEER' | 'AI_ENGINEER' | 'MOBILE' | 'GAME' | 'OTHER';
export interface CreateProjectRequest {
    title: string; summary: string | null; description: string; goal: string | null;
    imageUrl: string; isPublic: boolean;
    recruitmentSlots: { roleCode: ProjectRoleCode; capacity: number; minimumYears: number }[];
    techStacks: string[]; interests: string[];
    recruitmentStart: string; recruitmentEnd: string; developmentStart: string; developmentEnd: string;
}
export interface ProjectCreationReceipt { id: number; title: string }
