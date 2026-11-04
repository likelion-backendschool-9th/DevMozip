import { careerOptions } from '@/mocks/auth';
import type { SignupErrors, SignupInput } from '@/types/auth';
// 로그인과 가입 저장, 중복 확인, 직군·기술 스택·관심 분야의 허용 값 검증은 백엔드가 맡는다. 여기에는 화면 입력 검증만 남긴다.
export const normalizeEmail=(value:string)=>value.trim().toLowerCase();
export const validEmail=(value:string)=>/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value.trim())&&value.trim().length<=254;
export const validNickname=(value:string)=>/^[\p{L}\p{N}_ ]{2,20}$/u.test(value.trim());
export function validateSignup(input:SignupInput):SignupErrors{
 const errors:SignupErrors={};
 if(!validEmail(input.email))errors.email='올바른 이메일 주소를 입력해 주세요.';
 if(input.password.length<8||input.password.length>64||!/[A-Za-z]/.test(input.password)||!/[0-9]/.test(input.password))errors.password='영문과 숫자를 포함해 8~64자로 입력해 주세요.';
 if(!input.confirmPassword||input.password!==input.confirmPassword)errors.confirmPassword='비밀번호가 일치하지 않습니다.';
 if(!validNickname(input.nickname))errors.nickname='한글·영문·숫자·공백·밑줄로 2~20자를 입력해 주세요.';
 if(!input.job)errors.job='개발 직군을 선택해 주세요.';
 if(!careerOptions.includes(input.career))errors.career='경력을 선택해 주세요.';
 if(input.stacks.length===0)errors.stacks='기술 스택을 하나 이상 선택해 주세요.';
 if(input.interests.length===0)errors.interests='관심 분야를 하나 이상 선택해 주세요.';
 return errors;
}
