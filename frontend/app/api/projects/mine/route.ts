import {projectGuard,proxyProject,projectQuery} from '@/lib/projects/projectProxy';
import {parseProjectPage} from '@/lib/projects/projectApiContract';
export async function GET(request:Request){const blocked=await projectGuard();return blocked??proxyProject('/api/v1/me/projects?'+projectQuery(request),{},parseProjectPage);}
