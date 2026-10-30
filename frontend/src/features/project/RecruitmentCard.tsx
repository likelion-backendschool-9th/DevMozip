import { Link } from 'react-router-dom';
import { ArrowUpRight, CalendarDays } from 'lucide-react';
import { useAuth } from '../../app/providers/AuthProvider';
import { useProjects } from '../../app/providers/ProjectProvider';
import { applicationFor, projectStatus } from '../../services/projectService';
import { Card } from '../../components/common/Card';
import { BookmarkButton } from './ProjectActions';
import type { RecruitmentProject } from '../../types/project';
export function RecruitmentCard({project:p}:{project:RecruitmentProject}){const {user}=useAuth();const {state,now}=useProjects();const application=applicationFor(state,p.id,user?.id);return <Card className="recruitment-card"><div className={`project-cover ${p.color}`}><span>{p.field}</span><div className="cover-glyph">{p.mark}<span>↗</span></div><span className="recruit-badge">{projectStatus(p,now)}</span></div><div className="project-content"><div className="recruitment-card-title"><Link to={`/projects/${p.id}`}><h3>{p.title}</h3></Link><BookmarkButton projectId={p.id} compact/></div><p>{p.summary}</p><div className="tags">{p.stacks.map(s=><span key={s}>{s}</span>)}</div><div className="recruitment-slots">{p.slots.map(s=><div key={s.role}><strong>{s.role}</strong><span>{s.filled}/{s.count}명</span><small>{s.minYears?`${s.minYears}년 이상`:'경력 무관'}</small></div>)}</div><div className="recruitment-card-dates"><CalendarDays size={12}/>{p.developmentStart} — {p.developmentEnd}</div><div className="project-bottom"><span>모집 마감 {p.deadline}</span><Link to={`/projects/${p.id}`}>{application?application.status:'상세 보기'}<ArrowUpRight size={14}/></Link></div></div></Card>;}
