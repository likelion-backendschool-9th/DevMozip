import {projectGuard,projectError,proxyProject} from '@/lib/projects/projectProxy';
import {validProjectId} from '@/lib/projects/projectApiContract';
type Context={params:Promise<{projectId:string}>};
export async function GET(request:Request,{params}:Context){const blocked=await projectGuard();if(blocked)return blocked;const {projectId}=await params;if(!validProjectId(projectId))return projectError(400,'프로젝트 ID를 확인해 주세요.');const query=new URL(request.url).searchParams.get('jobRole');return proxyProject('/api/v1/projects/'+projectId+'/application-options'+(query?'?jobRole='+encodeURIComponent(query):''));}
