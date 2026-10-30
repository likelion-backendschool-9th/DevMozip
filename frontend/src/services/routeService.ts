import { matchPath } from 'react-router-dom';
import { routes } from '../mocks/routes';
export function routeTitle(pathname:string){const exact=routes.find(route=>route.path===pathname);const dynamic=routes.find(route=>matchPath({path:route.path,end:true},pathname));return `${(exact||dynamic)?.title||'페이지를 찾을 수 없습니다'} · DevMozip`;}
