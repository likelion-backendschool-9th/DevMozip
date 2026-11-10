import 'server-only';
import {NextResponse} from 'next/server';
import {backendFetch} from '@/lib/auth/backend';
import {getSession} from '@/lib/auth/session';
export const projectApiEnabled=()=>process.env.PROJECT_API_ENABLED!=='false';
export function projectError(status:number,message:string){return NextResponse.json({message},{status,headers:{'Cache-Control':'no-store'}});}
export async function projectGuard(request?:Request,premium=false){
 if(!projectApiEnabled())return projectError(503,'프로젝트 서버 연결이 비활성화되어 있습니다.');
 if(request){
  const url=new URL(request.url);let origin:URL;try{origin=new URL(request.headers.get('origin')??'');}catch{return projectError(403,'같은 사이트에서 요청해 주세요.');}
  // Next.js가 내부 URL의 호스트를 정규화해도 브라우저가 전송한 Host와 비교한다.
  if(origin.host!==(request.headers.get('host')??url.host)||origin.protocol!==url.protocol)return projectError(403,'같은 사이트에서 요청해 주세요.');
 }
 try{const session=await getSession();if(!session)return projectError(401,'로그인 후 이용해 주세요.');if(premium&&session.role!=='PREMIUM')return projectError(403,'프리미엄 회원만 공고를 생성할 수 있습니다.');}
 catch{return projectError(502,'인증 서버에 연결할 수 없습니다.');}
 return null;
}
export async function proxyProject(path:string,init:RequestInit={},parse?:(value:unknown)=>unknown){
 if(!projectApiEnabled())return projectError(503,'프로젝트 서버 연결이 비활성화되어 있습니다.');
 try{const response=await backendFetch(path,init);
  if(!response.ok){const messages:Record<number,string>={400:'입력값과 날짜를 확인해 주세요.',401:'로그인이 필요하거나 만료되었습니다.',403:'이 프로젝트의 처리 권한이 없습니다.',404:'프로젝트가 없거나 접근할 수 없습니다.',409:'현재 상태·팀원·지원서·기간 조건으로 처리할 수 없습니다.'};return projectError(response.status>=500?502:response.status,messages[response.status]||'요청을 처리하지 못했습니다.');}
  if(response.status===204)return new Response(null,{status:204,headers:{'Cache-Control':'no-store'}});
  const data=await response.json();return NextResponse.json(parse?parse(data):data,{status:response.status,headers:{'Cache-Control':'no-store'}});
 }catch{return projectError(502,init.method&&init.method!=='GET'?'서버 응답을 확인하지 못했습니다. 중복 요청 전에 저장 상태를 확인해 주세요.':'서버 응답을 확인하지 못했습니다. 다시 시도해 주세요.');}
}
export async function readProjectBody(request:Request){if(!request.headers.get('content-type')?.startsWith('application/json'))throw new Error('JSON 형식이 필요합니다.');const value=await request.json();if(!value||typeof value!=='object'||Array.isArray(value))throw new Error('입력 내용을 확인해 주세요.');return value as Record<string,unknown>;}
export function projectQuery(request:Request){const result=new URLSearchParams();for(const [key,value] of new URL(request.url).searchParams)if(['query','jobRole','status','interests','techStacks','sort','page','size','tab'].includes(key))result.append(key,value);return result.toString();}
