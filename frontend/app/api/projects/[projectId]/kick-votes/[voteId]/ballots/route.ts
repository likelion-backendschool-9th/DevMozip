import {projectGuard,projectError,proxyProject,readProjectBody} from '@/lib/projects/projectProxy';
import {validProjectId} from '@/lib/projects/projectApiContract';
export async function POST(request:Request,{params}:{params:Promise<{projectId:string;voteId:string}>}){
 const blocked=await projectGuard(request);if(blocked)return blocked;const {projectId,voteId}=await params;
 if(!validProjectId(projectId)||!validProjectId(voteId))return projectError(400,'투표 ID를 확인해 주세요.');
 try{const b=await readProjectBody(request);if(typeof b.agree!=='boolean')throw Error();
 return proxyProject('/api/v1/projects/'+projectId+'/kick-votes/'+voteId+'/ballots',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({agree:b.agree})});
 }catch{return projectError(400,'찬성 또는 반대를 선택해 주세요.');}
}
