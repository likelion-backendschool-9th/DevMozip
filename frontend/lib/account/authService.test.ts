import { afterEach, describe, expect, it, vi } from 'vitest';
import { emptySignup } from '@/mocks/auth';
import { emailAvailable, loadAccounts, nicknameAvailable, parseAccounts, validateSignup } from '@/lib/account/authService';
import type { SignupInput, StoredAccount } from '@/types/auth';
const validInput=():SignupInput=>({...structuredClone(emptySignup),email:' New@Example.com ',password:'Testpass123!',confirmPassword:'Testpass123!',nickname:'새개발자',job:'프론트엔드 개발자',career:'2년 차',stacks:['React'],interests:['생산성']});
const storedAccount=():StoredAccount=>({user:{id:'local-user',email:'new@example.com',nickname:'새개발자',role:'free',onboardingCompleted:false,job:'프론트엔드 개발자',career:'2년 차',stacks:['React'],interests:['생산성'],available:true},salt:'salt',passwordHash:'a'.repeat(64)});
afterEach(()=>vi.unstubAllGlobals());
describe('회원가입 입력 검증',()=>{
 it('모든 필수 입력 항목을 검증한다',()=>{const errors=validateSignup(structuredClone(emptySignup));expect(Object.keys(errors).sort()).toEqual(['career','confirmPassword','email','interests','job','nickname','password','stacks']);expect(validateSignup(validInput())).toEqual({});});
 it('비밀번호 일치·길이·직군·기술·분야를 검증한다',()=>{const input=validInput();input.confirmPassword='different';input.password='short';input.job='알 수 없음';input.stacks=['Unknown'];input.interests=['전체 분야'];const errors=validateSignup(input);expect(errors.confirmPassword).toBeTruthy();expect(errors.password).toBeTruthy();expect(errors.job).toBeTruthy();expect(errors.stacks).toBeTruthy();expect(errors.interests).toBeTruthy();});
 it('데모 계정의 이메일/닉네임은 중복 처리한다',()=>{expect(emailAvailable(' MEMBER@DEVMOZIP.DEMO ',[])).toBe(false);expect(nicknameAvailable(' 김하늘 ',[])).toBe(false);expect(emailAvailable('new@example.com',[])).toBe(true);});
 it('이전에 가입한 Mock 계정과도 중복을 검사한다',()=>{const account=storedAccount();expect(emailAvailable(' NEW@example.com ',[account])).toBe(false);expect(nicknameAvailable('새개발자',[account])).toBe(false);});
});
describe('이전에 저장된 Mock 계정 복원',()=>{
 it('저장된 계정은 복원하고 손상된 계정/권한은 제외한다',()=>{const account=storedAccount();expect(parseAccounts(JSON.stringify({version:1,accounts:[account]}))).toEqual([account]);expect(parseAccounts('{broken')).toEqual([]);expect(parseAccounts(JSON.stringify({version:1,accounts:[{...account,user:{...account.user,role:'admin'}}]}))).toEqual([]);});
 it('저장 공간 오류를 처리한다',()=>{vi.stubGlobal('localStorage',{getItem:()=>{throw new Error('blocked');}});expect(loadAccounts()).toEqual([]);});
});
