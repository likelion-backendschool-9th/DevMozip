import type { ProjectStatusCode } from './projectApi';
export interface ProjectActivity {
    projectId: number; title: string; imageUrl: string | null; status: ProjectStatusCode;
    developmentStart: string; developmentEnd: string; completedAt: string | null;
    role: 'HOST' | 'PARTICIPANT'; jobRole: string | null; membershipStatus: 'ACTIVE' | 'LEFT' | 'KICKED';
}
export interface ProjectActivityPage {
    items: ProjectActivity[]; totalElements: number; page: number; size: number; totalPages: number;
}
