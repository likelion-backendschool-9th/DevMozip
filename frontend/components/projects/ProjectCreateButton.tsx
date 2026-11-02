'use client';
import {useState,type ReactNode} from 'react';
import Link from 'next/link';
import {useNavigation as useNavigate} from '@/lib/navigation';
import {useAuth} from '@/lib/state/AuthProvider';
import {canCreateProject} from '@/lib/projects/projectPermissionsService';
import {Button} from '@/components/common/Button';
import {PremiumProjectGate} from '@/components/projects/PremiumProjectGate';
export function ProjectCreateButton({children='모집 공고 작성'}:{children?:ReactNode}){const {user}=useAuth();const navigate=useNavigate();const [open,setOpen]=useState(false);return canCreateProject(user)?<Link className="button" href="/projects/new">{children}</Link>:<><Button onClick={()=>setOpen(true)}>{children}</Button>{open&&<PremiumProjectGate onClose={()=>setOpen(false)} onActivated={()=>navigate('/projects/new')}/>}</>;}
