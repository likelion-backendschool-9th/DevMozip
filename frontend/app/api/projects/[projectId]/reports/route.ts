import {projectGuard,projectError,proxyProject,readProjectBody} from '@/lib/projects/projectProxy';
import {validProjectId} from '@/lib/projects/projectApiContract';
import {projectReportError} from '@/lib/projects/projectReport';
export async function POST(request:Request,{params}:{params:Promise<{projectId:string}>}){
 const blocked=await projectGuard(request);if(blocked)return blocked;
 const {projectId}=await params;if(!validProjectId(projectId))return projectError(400,'프로젝트 ID를 확인해 주세요.');
 let body;try{body=await readProjectBody(request);}catch{return projectError(400,'신고 내용을 확인해 주세요.');}
 const detail=body.detail??'';const error=projectReportError(body.reason,detail);if(error)return projectError(400,error);
 const response=await proxyProject('/api/v1/projects/'+projectId+'/reports',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({reason:body.reason,detail})});
 return response.status===409?projectError(409,'이미 신고한 프로젝트입니다.'):response.status===400?projectError(400,'본인 프로젝트이거나 신고 내용이 올바르지 않습니다.'):response;
}
