import { describe, expect, it } from 'vitest';
import { emptySignup } from '@/mocks/auth';
import { validateSignup } from '@/lib/account/authService';
import type { SignupInput } from '@/types/auth';
const validInput=():SignupInput=>({...structuredClone(emptySignup),email:' New@Example.com ',password:'Testpass123!',confirmPassword:'Testpass123!',nickname:'새개발자',job:'프론트엔드 개발자',career:'2년 차',skills:['React'],interests:['생산성']});
describe('회원가입 입력 검증',()=>{
 it('모든 필수 입력 항목을 검증한다',()=>{const errors=validateSignup(structuredClone(emptySignup));expect(Object.keys(errors).sort()).toEqual(['career','confirmPassword','email','interests','job','nickname','password','skills']);expect(validateSignup(validInput())).toEqual({});});
 it('비밀번호 일치·길이·직군·기술·분야를 검증한다',()=>{const input=validInput();input.confirmPassword='different';input.password='short';input.job='';input.skills=[];input.interests=[];const errors=validateSignup(input);expect(errors.confirmPassword).toBeTruthy();expect(errors.password).toBeTruthy();expect(errors.job).toBeTruthy();expect(errors.skills).toBeTruthy();expect(errors.interests).toBeTruthy();});
});
