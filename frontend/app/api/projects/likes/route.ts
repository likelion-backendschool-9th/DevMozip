import {projectGuard,proxyProject} from '@/lib/projects/projectProxy';
export async function GET(){const blocked=await projectGuard();return blocked??proxyProject('/api/v1/projects/likes');}
