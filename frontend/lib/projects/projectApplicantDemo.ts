import {demoProjectApplications} from '@/mocks/projectApplicants';
import {allProjects,teamFor} from './projectSelectors';
import type {RecruitmentState} from '@/types/project';
import type {ProfileState} from '@/types/profile';
import type {AuthUser} from '@/types/auth';
export function addDemoApplicants(state:RecruitmentState,projectId:string,user:AuthUser|null,profiles:ProfileState):{state:RecruitmentState;message:string;projectId?:string}{
 const p=allProjects(state).find(p=>p.id===projectId);
 if(!user||!p||p.hostId!==user.id)throw new Error('프로젝트 주최자만 데모 지원자를 추가할 수 있습니다.');
 if(!['모집 중','모집 완료'].includes(p.status||'모집 중'))throw new Error('모집 단계의 프로젝트에서만 데모 지원자를 추가할 수 있습니다.');
 const next=structuredClone(state);const members=teamFor(state,projectId);
 const candidates=demoProjectApplications.filter(a=>a.projectId===projectId&&a.userId!==p.hostId&&!members.some(m=>m.userId===a.userId)&&!state.applications.some(existing=>existing.projectId===projectId&&existing.userId===a.userId)&&profiles.profiles[a.userId]&&profiles.portfolios.some(portfolio=>portfolio.id===a.portfolioId&&portfolio.ownerId===a.userId)&&p.slots.some(s=>s.role===a.role));
 next.applications.push(...structuredClone(candidates).map(a=>({...a,portfolioTitle:profiles.portfolios.find(p=>p.id===a.portfolioId)!.title})));
 return {state:next,message:candidates.length?`데모 지원자 ${candidates.length}명을 추가했습니다.`:'추가 가능한 데모 지원자가 없습니다. 기존 지원서는 유지됩니다.'};
}
