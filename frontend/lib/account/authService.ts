import { demoAccounts, demoPassword, jobOptions, careerOptions } from '@/mocks/auth';
import { stackOptions, interestOptions } from '@/mocks/home';
import type { AuthUser, SignupErrors, SignupInput, StoredAccount } from '@/types/auth';
export const ACCOUNT_KEY='devmozip:accounts:v1';
export const SESSION_KEY='devmozip:auth:v1';
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
async function hashPassword(password:string,salt:string){
 if(!globalThis.crypto?.subtle)throw new Error('회원가입 데모는 localhost 또는 HTTPS 주소에서 이용해 주세요.');
 const buffer=await crypto.subtle.digest('SHA-256',new TextEncoder().encode(`${salt}:${password}`));
 return Array.from(new Uint8Array(buffer),b=>b.toString(16).padStart(2,'0')).join('');
}
export async function createAccount(input:SignupInput,accounts:StoredAccount[]):Promise<StoredAccount>{
 if(Object.keys(validateSignup(input)).length)throw new Error('회원가입 입력 항목을 확인해 주세요.');
 if(!emailAvailable(input.email,accounts))throw new Error('이미 사용 중인 이메일입니다.');
 if(!nicknameAvailable(input.nickname,accounts))throw new Error('이미 사용 중인 닉네임입니다.');
 if(!globalThis.crypto?.subtle)throw new Error('회원가입 데모는 localhost 또는 HTTPS 주소에서 이용해 주세요.');
 const salt=crypto.randomUUID();
 return {user:{id:crypto.randomUUID(),email:normalizeEmail(input.email),nickname:input.nickname.trim(),role:'free',onboardingCompleted:false,job:input.job,career:input.career,stacks:[...input.stacks],interests:[...input.interests],available:input.available},salt,passwordHash:await hashPassword(input.password,salt)};
}
export async function authenticate(email:string,password:string,accounts:StoredAccount[]):Promise<AuthUser>{
 const demo=demoAccounts.find(u=>u.email===normalizeEmail(email));
 if(demo&&password===demoPassword)return {...demo,stacks:[...demo.stacks],interests:[...demo.interests]};
 const account=accounts.find(a=>a.user.email===normalizeEmail(email));
 if(account&&await hashPassword(password,account.salt)===account.passwordHash)return account.user;
 throw new Error('이메일 또는 비밀번호가 올바르지 않습니다.');
}
export function parseAccounts(raw:string|null):StoredAccount[]{
 try{const data=JSON.parse(raw||'null');if(data?.version!==1||!Array.isArray(data.accounts))return [];
 return data.accounts.filter((a:StoredAccount)=>a?.user&&typeof a.user.id==='string'&&typeof a.user.email==='string'&&validEmail(a.user.email)&&typeof a.user.nickname==='string'&&a.user.role==='free'&&(a.user.onboardingCompleted===undefined||typeof a.user.onboardingCompleted==='boolean')&&jobOptions.includes(a.user.job)&&careerOptions.includes(a.user.career)&&Array.isArray(a.user.stacks)&&a.user.stacks.every(s=>stackOptions.includes(s))&&Array.isArray(a.user.interests)&&a.user.interests.every(s=>interestOptions.slice(1).includes(s))&&typeof a.user.available==='boolean'&&typeof a.salt==='string'&&typeof a.passwordHash==='string'&&/^[a-f0-9]{64}$/.test(a.passwordHash));}catch{return [];}
}
export function loadAccounts(){try{return parseAccounts(localStorage.getItem(ACCOUNT_KEY));}catch{return [];}}
export function persistAccounts(accounts:StoredAccount[]){try{localStorage.setItem(ACCOUNT_KEY,JSON.stringify({version:1,accounts}));return true;}catch{return false;}}
export function resolveSession(raw:string|null,accounts:StoredAccount[]):AuthUser|null{try{const v=JSON.parse(raw||'null');if(v?.version!==1||typeof v.userId!=='string')return null;return [...demoAccounts,...accounts.map(a=>a.user)].find(u=>u.id===v.userId)||null;}catch{return null;}}
export function loadSession(accounts:StoredAccount[]):AuthUser|null{for(const storage of [()=>sessionStorage,()=>localStorage]){try{const user=resolveSession(storage().getItem(SESSION_KEY),accounts);if(user)return user;}catch{/* Try the other storage. */}}return null;}
export function clearSession(){for(const storage of [()=>localStorage,()=>sessionStorage])try{storage().removeItem(SESSION_KEY);}catch{/* Memory-only session. */}}
export function persistSession(user:AuthUser,remember:boolean){clearSession();try{(remember?localStorage:sessionStorage).setItem(SESSION_KEY,JSON.stringify({version:1,userId:user.id}));return true;}catch{return false;}}
