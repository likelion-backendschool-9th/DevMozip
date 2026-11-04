import { demoAccounts, jobOptions, careerOptions } from '@/mocks/auth';
import { stackOptions, interestOptions } from '@/mocks/home';
import type { SignupErrors, SignupInput, StoredAccount } from '@/types/auth';
// 로그인은 백엔드가 맡는다. 여기에는 가입 입력 검증과 이전에 브라우저에 가입한 Mock 계정 읽기만 남긴다.
export const ACCOUNT_KEY='devmozip:accounts:v1';
export const normalizeEmail=(value:string)=>value.trim().toLowerCase();
export const validEmail=(value:string)=>/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value.trim())&&value.trim().length<=254;
export const validNickname=(value:string)=>/^[\p{L}\p{N}_ ]{2,20}$/u.test(value.trim());
export function emailAvailable(email:string,accounts:StoredAccount[]){return ![...demoAccounts,...accounts.map(a=>a.user)].some(u=>u.email===normalizeEmail(email));}
export function nicknameAvailable(nickname:string,accounts:StoredAccount[]){return ![...demoAccounts,...accounts.map(a=>a.user)].some(u=>u.nickname.toLowerCase()===nickname.trim().toLowerCase());}
export function validateSignup(input:SignupInput):SignupErrors{
 const errors:SignupErrors={};
 if(!validEmail(input.email))errors.email='올바른 이메일 주소를 입력해 주세요.';
 if(input.password.length<8||input.password.length>64||!/[A-Za-z]/.test(input.password)||!/[0-9]/.test(input.password))errors.password='영문과 숫자를 포함해 8~64자로 입력해 주세요.';
 if(!input.confirmPassword||input.password!==input.confirmPassword)errors.confirmPassword='비밀번호가 일치하지 않습니다.';
 if(!validNickname(input.nickname))errors.nickname='한글·영문·숫자·공백·밑줄로 2~20자를 입력해 주세요.';
 if(!jobOptions.includes(input.job))errors.job='개발 직군을 선택해 주세요.';
 if(!careerOptions.includes(input.career))errors.career='경력을 선택해 주세요.';
 if(input.stacks.length===0||input.stacks.some(s=>!stackOptions.includes(s)))errors.stacks='기술 스택을 하나 이상 선택해 주세요.';
 if(input.interests.length===0||input.interests.some(s=>!interestOptions.slice(1).includes(s)))errors.interests='관심 분야를 하나 이상 선택해 주세요.';
 return errors;
}
export function parseAccounts(raw:string|null):StoredAccount[]{
 try{const data=JSON.parse(raw||'null');if(data?.version!==1||!Array.isArray(data.accounts))return [];
 return data.accounts.filter((a:StoredAccount)=>a?.user&&typeof a.user.id==='string'&&typeof a.user.email==='string'&&validEmail(a.user.email)&&typeof a.user.nickname==='string'&&a.user.role==='free'&&(a.user.onboardingCompleted===undefined||typeof a.user.onboardingCompleted==='boolean')&&jobOptions.includes(a.user.job)&&careerOptions.includes(a.user.career)&&Array.isArray(a.user.stacks)&&a.user.stacks.every(s=>stackOptions.includes(s))&&Array.isArray(a.user.interests)&&a.user.interests.every(s=>interestOptions.slice(1).includes(s))&&typeof a.user.available==='boolean'&&typeof a.salt==='string'&&typeof a.passwordHash==='string'&&/^[a-f0-9]{64}$/.test(a.passwordHash));}catch{return [];}
}
export function loadAccounts(){try{return parseAccounts(localStorage.getItem(ACCOUNT_KEY));}catch{return [];}}
