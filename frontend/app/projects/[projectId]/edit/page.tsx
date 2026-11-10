'use client';
import {useParams} from 'next/navigation';
import {useMockVisibility} from '@/lib/state/mockVisibilityStore';
import {ApiProjectForm} from '@/app/projects/_components/api/ApiProjectForm';
import {validProjectId} from '@/lib/projects/projectApiContract';
import Link from 'next/link';
export default function EditProjectPage(){const {projectId=''}=useParams<{projectId:string}>();const {enabled}=useMockVisibility();if(enabled)return <section className="container"><h1>시연 모드에서는 프로젝트 관리에서 수정할 수 있습니다.</h1><Link href={'/projects/'+projectId+'/manage'}>관리 화면으로</Link></section>;if(!validProjectId(projectId))return <p>프로젝트 ID를 확인해 주세요.</p>;return <ApiProjectForm projectId={projectId}/>;}
