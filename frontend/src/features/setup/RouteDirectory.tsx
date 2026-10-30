import { Link } from 'react-router-dom';
import { ArrowUpRight } from 'lucide-react';
import { routes } from '../../mocks/routes';
export function RouteDirectory() { return <div className="route-grid">{routes.filter(route => route.path !== '/').map(route => <Link key={route.path} to={route.path.replace(':userId', 'demo-user').replace(':projectId', 'demo-project').replace(':postId', 'demo-post')} className="route-card"><div><span className="route-group">{route.group}</span><span className="stage-label">{route.stage}단계 예정</span></div><h3>{route.title}<ArrowUpRight size={17}/></h3><p>{route.description}</p></Link>)}</div>; }
