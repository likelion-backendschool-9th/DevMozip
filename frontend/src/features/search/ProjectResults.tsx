import { Link } from 'react-router-dom';
import { ArrowUpRight, CalendarDays, Users } from 'lucide-react';
import { projectStatus } from '../../services/projectService';
import { BookmarkButton } from '../project/ProjectActions';
import { Card } from '../../components/common/Card';
import type { Project } from '../../types/home';
export function ProjectResults({items}:{items:Project[]}){return <div className="search-project-list">{items.map(p=><Card key={p.id} className="search-project-card"><span className={`search-project-mark ${p.color}`}>{p.mark}</span><div className="search-project-info"><div className="search-project-eyebrow"><span>{p.field}</span><span className="outline-badge">{projectStatus(p)}</span></div><Link to={`/projects/${p.id}`}><h3>{p.title}</h3></Link><p>{p.summary}</p><div className="tags">{p.stacks.map(s=><span key={s}>{s}</span>)}</div><div className="search-project-facts"><span>모집 직군 · {p.roles.join(' / ')}</span><span><Users size={12}/>{p.members}/{p.capacity}명</span><span><CalendarDays size={12}/>{p.duration}</span></div></div><BookmarkButton projectId={p.id} compact/><Link className="icon-button" to={`/projects/${p.id}`} aria-label={`${p.title} 상세 보기`}><ArrowUpRight size={19}/></Link></Card>)}</div>;}
