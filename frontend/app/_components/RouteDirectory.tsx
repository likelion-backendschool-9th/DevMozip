'use client';
import styles from './RouteDirectory.module.css';
import Link from 'next/link';
import { ArrowUpRight } from 'lucide-react';
import { routes } from '@/mocks/routes';
export function RouteDirectory() { return <div className={[styles.scope, "route-grid"].join(" ")}>{routes.filter(route => route.path !== '/').map(route => <Link key={route.path} href={route.path.replace(':userId', 'demo-user').replace(':projectId', 'demo-project').replace(':postId', 'demo-post')} className={[styles.scope, "route-card"].join(" ")}><div><span className={[styles.scope, "route-group"].join(" ")}>{route.group}</span><span className={[styles.scope, "stage-label"].join(" ")}>{route.stage}단계 예정</span></div><h3>{route.title}<ArrowUpRight size={17}/></h3><p>{route.description}</p></Link>)}</div>; }
