import { routes } from '@/mocks/routes';
export function routeTitle(pathname:string){const route=routes.find(r=>r.path===pathname)||routes.find(r=>new RegExp('^'+r.path.replace(/:[^/]+/g,'[^/]+')+'$').test(pathname));return `${route?.title||'페이지를 찾을 수 없습니다'} · DevMozip`;}
