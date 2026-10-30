import {useState,type ReactNode} from 'react';
import {Link,useNavigate} from 'react-router-dom';
import {useAuth} from '../../app/providers/AuthProvider';
import {canCreateProject} from '../../services/projectPermissionsService';
import {Button} from '../../components/common/Button';
import {PremiumProjectGate} from './PremiumProjectGate';
export function ProjectCreateButton({children='모집 공고 작성'}:{children?:ReactNode}){const {user}=useAuth();const navigate=useNavigate();const [open,setOpen]=useState(false);return canCreateProject(user)?<Link className="button" to="/projects/new">{children}</Link>:<><Button onClick={()=>setOpen(true)}>{children}</Button>{open&&<PremiumProjectGate onClose={()=>setOpen(false)} onActivated={()=>navigate('/projects/new')}/>}</>;}
