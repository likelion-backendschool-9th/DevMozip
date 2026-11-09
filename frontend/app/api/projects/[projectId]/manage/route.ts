import {projectGuard,projectError,proxyProject} from '@/lib/projects/projectProxy';
import {parseProjectDetail,validProjectId} from '@/lib/projects/projectApiContract';
export async function GET(_request:Request,{params}:{params:Promise<{projectId:string}>}){const blocked=await projectGuard();if(blocked)return blocked;const {projectId}=await params;return validProjectId(projectId)?proxyProject('/api/v1/projects/'+projectId+'/manage',{},parseProjectDetail):projectError(400,'프로젝트 ID를 확인해 주세요.');}
