import {projectGuard,projectError,proxyProject} from '@/lib/projects/projectProxy';
import {validProjectId} from '@/lib/projects/projectApiContract';
type Context={params:Promise<{projectId:string}>};
export async function POST(request:Request,{params}:Context){const blocked=await projectGuard(request);if(blocked)return blocked;const {projectId}=await params;return validProjectId(projectId)?proxyProject('/api/v1/projects/'+projectId+'/reviews/reminders',{method:'POST'}):projectError(400,'프로젝트 ID를 확인해 주세요.');}
