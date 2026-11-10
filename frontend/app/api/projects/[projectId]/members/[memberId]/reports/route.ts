import {projectGuard,projectError,proxyProject,readProjectBody} from '@/lib/projects/projectProxy';
import {validProjectId} from '@/lib/projects/projectApiContract';
import {userReportReasons} from '@/mocks/messaging';
export async function POST(request:Request,{params}:{params:Promise<{projectId:string;memberId:string}>}){
 const blocked=await projectGuard(request);if(blocked)return blocked;const {projectId,memberId}=await params;
 if(!validProjectId(projectId)||!validProjectId(memberId))return projectError(400,'프로젝트와 팀원을 확인해 주세요.');
 try{const b=await readProjectBody(request);if(typeof b.reason!=='string'||!userReportReasons.includes(b.reason)||b.detail!==undefined&&(typeof b.detail!=='string'||b.detail.length>1000))throw Error();
 const response=await proxyProject('/api/v1/projects/'+projectId+'/members/'+memberId+'/reports',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({reason:b.reason,detail:b.detail})});
 return response.status===409?projectError(409,'이 프로젝트에서 이미 신고한 팀원입니다.'):response;
 }catch{return projectError(400,'신고 사유와 내용을 확인해 주세요.');}
}
