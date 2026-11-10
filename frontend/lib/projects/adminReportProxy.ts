import 'server-only';
import {getSession} from '@/lib/auth/session';
import {projectGuard,projectError} from './projectProxy';
export async function adminReportGuard(request?:Request){
 const blocked=await projectGuard(request);if(blocked)return blocked;
 try{return (await getSession())?.role==='ADMIN'?null:projectError(403,'관리자만 이용할 수 있습니다.');}
 catch{return projectError(502,'인증 상태를 확인하지 못했습니다.');}
}
