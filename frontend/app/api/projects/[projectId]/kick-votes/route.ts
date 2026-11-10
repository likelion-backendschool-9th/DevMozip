import {projectGuard,projectError,proxyProject} from '@/lib/projects/projectProxy';
import {validProjectId} from '@/lib/projects/projectApiContract';
export async function GET(_request:Request,{params}:{params:Promise<{projectId:string}>}){
 const blocked=await projectGuard();if(blocked)return blocked;const {projectId}=await params;
 if(!validProjectId(projectId))return projectError(400,'프로젝트 ID를 확인해 주세요.');
 return proxyProject('/api/v1/projects/'+projectId+'/kick-votes');
}
