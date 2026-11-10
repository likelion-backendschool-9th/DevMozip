import {projectGuard,projectError,proxyProject,readProjectBody} from '@/lib/projects/projectProxy';
import {validProjectId} from '@/lib/projects/projectApiContract';
type Context={params:Promise<{projectId:string}>};
export async function GET(request:Request,{params}:Context){
 const blocked=await projectGuard();if(blocked)return blocked;
 const {projectId}=await params;if(!validProjectId(projectId))return projectError(400,'프로젝트 ID를 확인해 주세요.');
 const source=new URL(request.url).searchParams;const query=new URLSearchParams();
 if(source.get('jobRole'))query.set('jobRole',source.get('jobRole')!);
 if(source.get('includeInactive')==='true')query.set('includeInactive','true');
 return proxyProject('/api/v1/projects/'+projectId+'/members'+(query.size?'?'+query:''));
}
export async function POST(request:Request,{params}:Context){
 const blocked=await projectGuard(request);if(blocked)return blocked;
 const {projectId}=await params;if(!validProjectId(projectId))return projectError(400,'프로젝트 ID를 확인해 주세요.');
 try{const b=await readProjectBody(request);if(!Number.isSafeInteger(b.projectMemberId)||Number(b.projectMemberId)<=0||typeof b.reason!=='string'||!b.reason.trim()||b.reason.length>200)throw Error();
 return proxyProject('/api/v1/projects/'+projectId+'/members',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({projectMemberId:b.projectMemberId,reason:b.reason})});
 }catch{return projectError(400,'대상 팀원과 강퇴 사유를 확인해 주세요.');}
}
