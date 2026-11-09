import {projectGuard,projectError,proxyProject,readProjectBody,projectQuery} from '@/lib/projects/projectProxy';
import {pickProjectRequest,parseProjectPage} from '@/lib/projects/projectApiContract';
import {parseProjectCreationReceipt} from '@/lib/projects/projectCreationApi';
export async function GET(request:Request){return proxyProject('/api/v1/projects?'+projectQuery(request),{},parseProjectPage);}
export async function POST(request:Request){const blocked=await projectGuard(request,true);if(blocked)return blocked;let body;try{body=pickProjectRequest(await readProjectBody(request));}catch{return projectError(400,'JSON 입력 내용을 확인해 주세요.');}return proxyProject('/api/v1/projects',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(body)},parseProjectCreationReceipt);}
