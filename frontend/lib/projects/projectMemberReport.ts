import {projectRequest} from './useProjectApi';
import {notify} from '@/lib/state/prototypeStore';
export async function submitProjectMemberReport(projectId:number,memberId:number,reason:string,detail?:string){
 try{await projectRequest('/'+projectId+'/members/'+memberId+'/reports','POST',{reason,detail});notify('프로젝트 팀원 신고를 접수했어요.');return {ok:true};}
 catch(error){return {ok:false,error:error instanceof Error?error.message:'신고 접수 상태를 확인해 주세요.'};}
}
