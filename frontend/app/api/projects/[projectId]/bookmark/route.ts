import {projectGuard,projectError,proxyProject} from '@/lib/projects/projectProxy';
import {validProjectId} from '@/lib/projects/projectApiContract';
type Context={params:Promise<{projectId:string}>};
async function mutate(request:Request,{params}:Context,method:string){const blocked=await projectGuard(request);if(blocked)return blocked;const {projectId}=await params;return validProjectId(projectId)?proxyProject('/api/v1/projects/'+projectId+'/bookmark',{method}):projectError(400,'프로젝트 ID를 확인해 주세요.');}
export const POST=(request:Request,context:Context)=>mutate(request,context,'POST');
export const DELETE=(request:Request,context:Context)=>mutate(request,context,'DELETE');
