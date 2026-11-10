import {adminReportGuard} from '@/lib/projects/adminReportProxy';
import {proxyProject,projectError,readProjectBody} from '@/lib/projects/projectProxy';
import {validProjectId} from '@/lib/projects/projectApiContract';
export async function PATCH(request:Request,{params}:{params:Promise<{id:string}>}){
 const blocked=await adminReportGuard(request);if(blocked)return blocked;const {id}=await params;if(!validProjectId(id))return projectError(400,'신고 ID를 확인해 주세요.');
 try{const b=await readProjectBody(request);if(typeof b.status!=='string'||!['REVIEWING','RESOLVED','REJECTED'].includes(b.status)||typeof b.resolution!=='string'||b.resolution.length>1000||b.status!=='REVIEWING'&&!b.resolution.trim())throw Error();
 return proxyProject('/api/v1/admin/project-reports/'+id,{method:'PATCH',headers:{'Content-Type':'application/json'},body:JSON.stringify({status:b.status,resolution:b.resolution})});
 }catch{return projectError(400,'처리 상태와 결과를 확인해 주세요.');}
}
