import type {AuthUser} from '../types/auth';
import type {ProjectApplication,RecruitmentProject} from '../types/project';
import type {Portfolio} from '../types/profile';
export function canCreateProject(user:AuthUser|null){return user?.role==='premium';}
export function canViewApplicantPortfolio(user:AuthUser|null,project:RecruitmentProject,application:ProjectApplication,portfolio?:Portfolio){return user?.role==='premium'&&user.id===project.hostId&&application.projectId===project.id&&!!portfolio&&portfolio.id===application.portfolioId&&portfolio.ownerId===application.userId;}
