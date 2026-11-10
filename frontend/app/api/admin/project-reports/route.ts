import {adminReportGuard} from '@/lib/projects/adminReportProxy';
import {proxyProject,projectError} from '@/lib/projects/projectProxy';
export async function GET(request:Request){
 const blocked=await adminReportGuard();if(blocked)return blocked;const source=new URL(request.url).searchParams;const q=new URLSearchParams();
 for(const key of ['page','size']){const value=source.get(key);if(value!==null){if(!/^\d+$/.test(value))return projectError(400,'페이지를 확인해 주세요.');q.set(key,value);}}
 const status=source.get('status');if(status){if(!['RECEIVED','REVIEWING','RESOLVED','REJECTED'].includes(status))return projectError(400,'신고 상태를 확인해 주세요.');q.set('status',status);}
 return proxyProject('/api/v1/admin/project-reports'+(q.size?'?'+q:''));
}
