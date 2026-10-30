import {useState} from 'react';
import {PremiumProjectGate} from '../features/project/PremiumProjectGate';
import {Button} from '../components/common/Button';
import { Link } from 'react-router-dom';
import { LockKeyhole } from 'lucide-react';
import { useAuth } from '../app/providers/AuthProvider';
import { canCreateProject } from '../services/projectService';
import { DemoAccounts } from '../features/auth/DemoAccounts';
import { CreateProjectForm } from '../features/project/CreateProjectForm';
export function CreateProjectPage(){const {user}=useAuth();const [gate,setGate]=useState(false);return <div className="container create-project-page"><Link className="text-link" to="/projects">← 모집 목록으로</Link>{canCreateProject(user)?<section className="create-project-panel"><div className="eyebrow">FIND YOUR NEXT TEAM</div><h1>함께할 동료를 모집해 보세요.</h1><p>핵심 목표와 모집 조건을 명확하게 알려주세요.</p><CreateProjectForm/></section>:<section className="planned-card premium-project-gate"><LockKeyhole size={32}/><h1>모집 공고 작성은 프리미엄 전용입니다.</h1><p>무료 회원도 모든 프로젝트를 조회하고 지원할 수 있습니다.</p>{user?<Link className="button" to="/premium">프리미엄 안내</Link>:<Link className="button" to="/login">로그인하기</Link>}<Button variant="secondary" onClick={()=>setGate(true)}>프리미엄 권한 안내</Button><p className="auth-hint">아래 데모 계정으로 전환해 프리미엄 작성 권한을 확인할 수 있습니다.</p><DemoAccounts/></section>}{gate&&<PremiumProjectGate onClose={()=>setGate(false)}/>}</div>;}
