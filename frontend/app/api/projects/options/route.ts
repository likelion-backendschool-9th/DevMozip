import {projectGuard,proxyProject} from '@/lib/projects/projectProxy';
export async function GET(){return (await projectGuard())??proxyProject('/api/v1/projects/options');}
