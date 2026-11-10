import {NextResponse} from 'next/server';
import {backendFetch} from '@/lib/auth/backend';
import {getSession} from '@/lib/auth/session';
import {projectError} from '@/lib/projects/projectProxy';
export async function GET(){try{const session=await getSession();if(!session)return NextResponse.json(null,{headers:{'Cache-Control':'no-store'}});const response=await backendFetch('/api/v1/users/me/profile');if(!response.ok)return projectError(response.status,'회원 정보를 확인하지 못했습니다.');const data=await response.json();if(!Number.isSafeInteger(data.id)||data.id<=0)return projectError(502,'회원 ID를 확인하지 못했습니다.');return NextResponse.json({id:data.id,role:session.role},{headers:{'Cache-Control':'no-store'}});}catch{return projectError(502,'회원 정보를 확인하지 못했습니다.');}}
