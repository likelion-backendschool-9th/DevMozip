import type { ProjectStatusCode } from './projectApi';
export interface ProjectActivity {
    projectId: number; title: string; status: ProjectStatusCode;
    developmentStart: string; developmentEnd: string; completedAt: string | null;
    role: 'HOST' | 'PARTICIPANT'; jobRole: string | null; membershipStatus: 'ACTIVE' | 'LEFT';
}
export interface ProjectActivityPage {
    items: ProjectActivity[]; totalElements: number; page: number; size: number; totalPages: number;
}
